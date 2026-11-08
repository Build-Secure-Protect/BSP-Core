package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.RankUpHook;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * New-player protection from totem radiation. While a totem's owner has not yet reached what the admins set (a BSP-RankUp rank
 * or higher, or so many hours of playtime), their totems up to Tier II give off no totem radiation, so no compass finds them.
 * Admins choose RANK, PLAYTIME or OFF on the admin panel's RADIATION tab; on a network the choice is one record for every server
 * (the {@code state} table).
 *
 * <p>Ranks and playtime are read from BSP-RankUp (its own record on this server, so for offline players as of their last visit
 * here; playtime as the spawn shops count it) and remembered here as the highest rung and the most minutes ever seen for each
 * player, on a network in the shared table {@code protection_seen}: a player who ranks up on one server is no longer protected on
 * any. The memory only goes up and belongs to one season: every record carries the season number, so a new season starts empty and
 * a record written by a server that has not heard of the new season yet is ignored. Players are looked at when they log in and
 * every minute (the online ones and the owners of totems placed here; an offline owner's playtime without RankUp is read from
 * Minecraft's statistics file). Without RankUp (or a ladder) rank mode protects nobody, and the log says so once per rank.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class NewPlayerProtection extends SavedData {
    private static final String NAME = BSPCore.MODID + "_protection";
    /** Totem tiers (0 = Tier I) the protection covers. */
    public static final int MAX_TIER = 1;

    public enum Mode { RANK, PLAYTIME, OFF }

    public Mode mode = Mode.RANK;
    /** The RankUp group (lower-cased) that ends the protection in RANK mode. */
    public String rank = "sentinel";
    /** Hours of playtime that end it in PLAYTIME mode. */
    public int hours = 24;
    /** Highest rung index and most playtime minutes seen per player, in {@link #season}. */
    private final Map<UUID, long[]> seen = new HashMap<>();
    /** The season {@link #seen} belongs to. */
    private int season = -1;
    private static String warnedFor;
    /** When protection settings were last saved here (ms): a network read that started before it is stale and not applied. */
    public static volatile long lastLocalSaveMs;

    public static NewPlayerProtection get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not loaded");
        }
        return overworld.getDataStorage().computeIfAbsent(NewPlayerProtection::load, NewPlayerProtection::new, NAME);
    }

    // ------------------------------------------------------------------ the rule

    /** Whether the protection hides a totem of tier {@code tier} (0 = Tier I) owned by {@code owner}. */
    public static boolean hides(MinecraftServer server, UUID owner, int tier) {
        if (owner == null || tier > MAX_TIER) {
            return false;
        }
        return get(server).protects(server, owner);
    }

    /** Whether {@code player} is still protected under the current settings. */
    public boolean protects(MinecraftServer server, UUID player) {
        return switch (mode) {
            case OFF -> false;
            case PLAYTIME -> hours > 0 && minutes(player) < hours * 60L;
            case RANK -> {
                int need = needed(server);
                if (need < 0) {
                    if (!rank.equals(warnedFor)) {
                        warnedFor = rank;
                        BSPCore.LOGGER.warn("New-player protection is set to the rank '{}', which is not on a BSP-RankUp ladder here (or RankUp is missing): nobody is protected", rank);
                    }
                    yield false;
                }
                yield rung(player) < need;
            }
        };
    }

    /** The chosen rank's place on RankUp's ladder, looked up at most every 5 s (every loaded totem asks once a second). */
    private int needed(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (!rank.equals(neededFor) || now - neededAt > 5000) {
            neededFor = rank;
            neededAt = now;
            neededIndex = RankUpHook.rungIndex(server, rank);
        }
        return neededIndex;
    }

    private transient String neededFor = "";
    private transient long neededAt;
    private transient int neededIndex = -1;

    public int rung(UUID player) {
        long[] s = seen.get(player);
        return s == null ? -1 : (int) s[0];
    }

    public long minutes(UUID player) {
        long[] s = seen.get(player);
        return s == null ? 0 : s[1];
    }

    // ------------------------------------------------------------------ settings

    /**
     * Sets the protection here and, on a network, for every server. {@code done} hears whether it was stored (on the server
     * thread; at once without a network). If the network write fails, the settings here go back to what they were.
     */
    public void setSettings(Mode mode, String rank, int hours, java.util.function.Consumer<Boolean> done) {
        Mode oldMode = this.mode;
        String oldRank = this.rank;
        int oldHours = this.hours;
        applySettings(mode, rank, hours);
        lastLocalSaveMs = System.currentTimeMillis();
        if (!NetworkStorage.enabled()) {
            done.accept(true);
            return;
        }
        NetworkStorage.protectionSettingsWrite(settingsTag().toString(), ok -> {
            if (!ok) {
                applySettings(oldMode, oldRank, oldHours);
            }
            done.accept(ok);
        });
    }

    public void applySettings(Mode mode, String rank, int hours) {
        Mode m = mode == null ? Mode.RANK : mode;
        String r = rank == null ? "" : rank.toLowerCase(Locale.ROOT);
        int h = Math.max(0, Math.min(100_000, hours));
        if (m == this.mode && r.equals(this.rank) && h == this.hours) {
            return; // the network's regular read: nothing changed
        }
        this.mode = m;
        this.rank = r;
        this.hours = h;
        setDirty();
    }

    public CompoundTag settingsTag() {
        CompoundTag t = new CompoundTag();
        t.putString("Mode", mode.name());
        t.putString("Rank", rank);
        t.putInt("Hours", hours);
        return t;
    }

    /** Takes the network's settings (SNBT of {@link #settingsTag}). */
    public void applySnbt(String snbt) {
        // (applySettings ignores a read that changes nothing, so the regular network read neither marks the file nor repeats a warning)
        try {
            CompoundTag t = net.minecraft.nbt.TagParser.parseTag(snbt);
            Mode m;
            try {
                m = Mode.valueOf(t.getString("Mode"));
            } catch (IllegalArgumentException e) {
                m = Mode.RANK;
            }
            applySettings(m, t.getString("Rank"), t.getInt("Hours"));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            BSPCore.LOGGER.warn("The network's new-player protection settings could not be read: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ what was seen

    /** Raises what was seen of {@code player} (never lowers it). Returns whether anything went up. */
    public boolean raise(UUID player, int rung, long minutes) {
        long[] s = seen.get(player);
        if (s == null) {
            if (rung < 0 && minutes <= 0) {
                return false; // nothing to keep
            }
            s = new long[] {-1, 0};
            seen.put(player, s);
        }
        boolean up = false;
        if (rung > s[0]) {
            s[0] = rung;
            up = true;
        }
        if (minutes > s[1]) {
            s[1] = minutes;
            up = true;
        }
        if (up) {
            setDirty();
        }
        return up;
    }

    public void clearSeen() {
        seen.clear();
        setDirty();
    }

    /** The season this record belongs to. */
    public int season() {
        return season;
    }

    /** Starts an empty record when the season is not the one it belongs to. */
    public void ensureSeason(int now) {
        if (season != now) {
            seen.clear();
            season = now;
            setDirty();
        }
    }

    public static int currentSeason(MinecraftServer server) {
        return com.mrgregles.bsp_core.admin.SeasonData.get(server).number;
    }

    /** An offline player's playtime from Minecraft's statistics file on this server, minutes; -1 when there is none. */
    static long vanillaMinutes(MinecraftServer server, UUID player) {
        java.nio.file.Path f = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR).resolve(player + ".json");
        if (!java.nio.file.Files.isRegularFile(f)) {
            return -1;
        }
        try {
            var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
            var custom = root.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
            return custom == null || !custom.has("minecraft:play_time") ? -1 : custom.get("minecraft:play_time").getAsLong() / 1200L;
        } catch (java.io.IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Looks at {@code player} now (rank from RankUp, playtime as the shops count it) and shares anything new with the network. */
    public static void observe(MinecraftServer server, UUID player, @Nullable ServerPlayer online) {
        NewPlayerProtection p = get(server);
        int now = currentSeason(server);
        p.ensureSeason(now);
        int rung = RankUpHook.rungOf(server, player);
        long minutes = online != null ? com.mrgregles.bsp_core.shop.Requirement.playMinutes(online) : RankUpHook.playtimeMinutes(server, player);
        if (minutes < 0 && online == null && p.mode == Mode.PLAYTIME && p.protects(server, player)) {
            minutes = vanillaMinutes(server, player); // no RankUp: Minecraft's own count, read only while it could still lift the protection
        }
        if (p.raise(player, rung, minutes) && NetworkStorage.enabled()) {
            NetworkStorage.protectionRaise(player, now, rung, minutes);
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            observe(p.server, p.getUUID(), p);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.overworld().getGameTime() % 1200 != 0) {
            return;
        }
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            observe(server, p.getUUID(), p);
            done.add(p.getUUID());
        }
        for (UUID owner : TotemLedger.get(server).allPlaced().keySet()) {
            if (done.add(owner)) {
                observe(server, owner, null); // offline: RankUp's record on this server
            }
        }
    }

    /** A new season starts an empty record (the network's rows carry their season, so the old ones are simply not read). */
    @SubscribeEvent
    public static void onSeasonEnded(com.mrgregles.bsp_core.api.event.SeasonEvent.Ended event) {
        get(event.getServer()).ensureSeason(currentSeason(event.getServer()));
    }

    // ------------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Settings", settingsTag());
        tag.putInt("Season", season);
        ListTag list = new ListTag();
        seen.forEach((id, s) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Rung", (int) s[0]);
            e.putLong("Minutes", s[1]);
            list.add(e);
        });
        tag.put("Seen", list);
        return tag;
    }

    private static NewPlayerProtection load(CompoundTag tag) {
        NewPlayerProtection p = new NewPlayerProtection();
        if (tag.contains("Settings")) {
            CompoundTag t = tag.getCompound("Settings");
            Mode m;
            try {
                m = Mode.valueOf(t.getString("Mode"));
            } catch (IllegalArgumentException e) {
                m = Mode.RANK;
            }
            p.mode = m;
            p.rank = t.getString("Rank");
            p.hours = t.getInt("Hours");
        }
        p.season = tag.contains("Season") ? tag.getInt("Season") : -1;
        for (Tag raw : tag.getList("Seen", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id")) {
                p.seen.put(e.getUUID("Id"), new long[] {e.getInt("Rung"), e.getLong("Minutes")});
            }
        }
        return p;
    }
}
