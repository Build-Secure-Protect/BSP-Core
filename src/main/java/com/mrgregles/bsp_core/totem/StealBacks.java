package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The steal-back rule. A friend steals a player's totem, a kit hands the player a new one (the kit is only for players with no
 * totem), and the player steals the old one back: two totems. So for {@code kit.stealBackHours} (3) after a kit hands a player a
 * totem, that player and their teammates cannot start a steal on any totem stolen from that player this season. The attempt is
 * refused without a reason; only the server log says why.
 * <p>
 * Kept twice: this server's own record (saved with the world: the totems stolen from each player here, and the watches started
 * here), and on a network the shared tables {@code totem_losses} and {@code steal_watch}, read every notice cycle and at once on a
 * Redis notice (kind {@code steal_watch}) into {@link #network}. A check looks at both, so a watch started here works before the
 * database answers and one started elsewhere works once it is read.
 */
public final class StealBacks extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_steal_backs";

    /** The clock watches run on; tests move it. */
    public static LongSupplier clock = System::currentTimeMillis;

    /** A watched player on the network: until when (by this server's clock) and the totems stolen from them this season. */
    public record Watch(long untilMs, Set<UUID> lost) {}

    /** The network's watches with their losses, as last read; empty without network storage. Any thread writes, the server thread reads. */
    private static volatile Map<UUID, Watch> network = Map.of();

    /** Totems stolen from each player on this server: totem id to the season it was stolen in. */
    private final Map<UUID, Map<UUID, Integer>> losses = new HashMap<>();
    /** Watches started on this server: player to the end, by {@link #clock}. */
    private final Map<UUID, Long> watches = new HashMap<>();

    public static StealBacks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StealBacks::load, StealBacks::new, DATA_NAME);
    }

    /** {@code kit.stealBackHours}; 0 means the rule is off. */
    public static int hours() {
        return Math.max(0, BSPConfig.getOr(BSPConfig.KIT_STEAL_BACK_HOURS, 3));
    }

    /** A steal finished: {@code owner} lost the totem with this id. Kept for the season, here and on the network. */
    public static void lost(MinecraftServer server, UUID owner, @Nullable UUID totemId) {
        if (totemId == null) {
            return;
        }
        int season = SeasonData.get(server).number;
        StealBacks d = get(server);
        d.losses.values().forEach(m -> m.values().removeIf(s -> s < season)); // an earlier season's totems are husks now
        d.losses.values().removeIf(Map::isEmpty);
        d.losses.computeIfAbsent(owner, k -> new HashMap<>()).put(totemId, season);
        d.setDirty();
        NetworkStorage.totemLost(owner, totemId, season);
    }

    /** Writes this season's losses kept here to the network (at connect: a steal finished while the database was out of reach). */
    public static void pushLosses(MinecraftServer server) {
        int season = SeasonData.get(server).number;
        get(server).losses.forEach((player, totems) -> totems.forEach((totem, s) -> {
            if (s == season) {
                NetworkStorage.totemLost(player, totem, season);
            }
        }));
    }

    /** A kit handed {@code player} a totem: the watch starts (or runs on, if it already ends later). Nothing when the rule is off. */
    public static void watch(MinecraftServer server, UUID player) {
        int h = hours();
        if (h <= 0) {
            return;
        }
        long until = clock.getAsLong() + h * 3_600_000L;
        StealBacks d = get(server);
        d.watches.merge(player, until, Math::max);
        d.watches.values().removeIf(u -> u <= clock.getAsLong());
        d.setDirty();
        NetworkStorage.stealWatch(player, h * 3600L);
    }

    /**
     * The watched player whose lost totem {@code thief} is trying to steal: the thief themself or a teammate of theirs, watched
     * now, who lost the totem with this id this season. Null when the steal may go ahead (also when the rule is off).
     */
    @Nullable
    public static UUID refuses(MinecraftServer server, UUID thief, @Nullable UUID totemId) {
        if (totemId == null || hours() <= 0) {
            return null;
        }
        List<UUID> who = new ArrayList<>();
        who.add(thief);
        Team team = Teams.enabled() ? Teams.teamOf(server, thief) : null;
        if (team != null) {
            for (UUID m : team.members().keySet()) {
                if (!m.equals(thief)) {
                    who.add(m);
                }
            }
        }
        StealBacks d = get(server);
        int season = SeasonData.get(server).number;
        long now = clock.getAsLong();
        Map<UUID, Watch> net = network;
        for (UUID p : who) {
            Long until = d.watches.get(p);
            Map<UUID, Integer> mine = d.losses.get(p);
            if (until != null && until > now && mine != null && Integer.valueOf(season).equals(mine.get(totemId))) {
                return p;
            }
            Watch w = net.get(p);
            if (w != null && w.untilMs() > now && w.lost().contains(totemId)) {
                return p;
            }
        }
        return null;
    }

    /** How a refused thief is shown "NAUGHTY NAUGHTY" (a packet the client turns into the full-screen stamp); tests swap it. */
    public static java.util.function.Consumer<ServerPlayer> naughty =
            p -> com.mrgregles.bsp_core.network.BSPNetwork.sendTo(p, new com.mrgregles.bsp_core.network.NaughtyPacket());

    /** A refused steal-back: the thief's screen says "NAUGHTY NAUGHTY" (never why), and the log tells staff. */
    public static void refused(ServerLevel level, ServerPlayer thief, UUID watched, UUID totemId, BlockPos pos) {
        naughty.accept(thief);
        String who = "them";
        if (!watched.equals(thief.getUUID())) {
            Team team = Teams.enabled() ? Teams.teamOf(level.getServer(), thief.getUUID()) : null;
            Team.Member m = team == null ? null : team.members().get(watched);
            who = "their teammate " + (m == null ? watched.toString() : m.name);
        }
        BSPCore.LOGGER.info("Steal-back refused: {} tried to steal totem {} at {} in {}. It was stolen from {} this season, and a kit handed {} a totem less than {} h ago.",
                thief.getGameProfile().getName(), totemId, pos, level.dimension().location(), who, who, hours());
    }

    /** Replaces the network's watches with a fresh read. Any thread. */
    public static void applyNetwork(Map<UUID, Watch> read) {
        network = Map.copyOf(read);
    }

    /** Forgets the network's watches (storage stopped, or a test's clean-up). */
    public static void clearNetwork() {
        network = Map.of();
    }

    /** Forgets this server's record for one player (tests). */
    public static void forget(MinecraftServer server, UUID player) {
        StealBacks d = get(server);
        if (d.losses.remove(player) != null | d.watches.remove(player) != null) {
            d.setDirty();
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag lost = new ListTag();
        losses.forEach((player, totems) -> totems.forEach((totem, season) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", player);
            t.putUUID("Totem", totem);
            t.putInt("Season", season);
            lost.add(t);
        }));
        tag.put("Losses", lost);
        ListTag watched = new ListTag();
        long now = clock.getAsLong();
        watches.forEach((player, until) -> {
            if (until > now) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Player", player);
                t.putLong("Until", until);
                watched.add(t);
            }
        });
        tag.put("Watches", watched);
        return tag;
    }

    private static StealBacks load(CompoundTag tag) {
        StealBacks d = new StealBacks();
        for (Tag x : tag.getList("Losses", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            if (t.hasUUID("Player") && t.hasUUID("Totem")) {
                d.losses.computeIfAbsent(t.getUUID("Player"), k -> new HashMap<>()).put(t.getUUID("Totem"), t.getInt("Season"));
            }
        }
        for (Tag x : tag.getList("Watches", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            if (t.hasUUID("Player")) {
                d.watches.put(t.getUUID("Player"), t.getLong("Until"));
            }
        }
        return d;
    }
}
