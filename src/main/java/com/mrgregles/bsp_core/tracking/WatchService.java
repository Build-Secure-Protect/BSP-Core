package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerSnapshotPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends each player the readings of the machines they are looking at (owner decision MT6/MT37): the boxes of the Holo Grid page that is
 * open, the popout's page and the pinned box, which the client lists in a watch packet. On this server a machine is read every tick and
 * sent only when its reading changed, so a box keeps pace with the machine's own screen while costing nothing when nothing moves. A machine
 * on another server goes through {@link TrackerRelay}. A machine is read only while its chunk is loaded (never loaded for it) and only
 * while the player could still open it; one reading per machine per tick serves every player watching it.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class WatchService {
    /** The most tags one player may watch at once (a page, the popout's page and the pin). */
    public static final int MAX_WATCH = 32;

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private static final class Watch {
        final Set<String> ids = new LinkedHashSet<>();
        /** What was last sent per tag, without its reading time. */
        final Map<String, CompoundTag> sent = new HashMap<>();
        /** Whether each tag's machine can be operated from here now (checked once a second). */
        final Map<String, Boolean> operable = new HashMap<>();
        long operableTick = Long.MIN_VALUE;
    }

    private WatchService() {}

    /** The player now watches these tags (their own; unknown ids are ignored). An empty list stops all watching. */
    private static final Map<UUID, long[]> WATCH_COUNTS = new HashMap<>();

    static void forget(UUID player) {
        WATCH_COUNTS.remove(player);
    }

    public static void watch(ServerPlayer player, List<String> ids) {
        if (!TrackerActions.allow(WATCH_COUNTS, player.getUUID(), 20, 5_000L)) {
            return; // the client sends a list only when what it shows changes
        }
        if (ids.isEmpty()) {
            WATCHES.remove(player.getUUID());
            TrackerRelay.unwatch(player);
            return;
        }
        TrackerRecord r = TrackerOwners.get(player.server).peek(player.server, player.getUUID());
        Watch w = WATCHES.computeIfAbsent(player.getUUID(), k -> new Watch());
        Set<String> next = new LinkedHashSet<>();
        for (String id : ids) {
            if (next.size() >= MAX_WATCH) {
                break;
            }
            if (r.tags.stream().anyMatch(t -> t.id().equals(id))) {
                next.add(id);
            }
        }
        w.sent.keySet().retainAll(next);
        w.operableTick = Long.MIN_VALUE; // checked again for the new list at the next tick
        w.ids.clear();
        w.ids.addAll(next);
        TrackerRelay.watch(player, r, next);
    }

    /** The tags the player watches now (for tests). */
    public static Set<String> watching(UUID player) {
        Watch w = WATCHES.get(player);
        return w == null ? Set.of() : Set.copyOf(w.ids);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WATCHES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Map<String, CompoundTag> readThisTick = new HashMap<>();
        List<UUID> gone = new ArrayList<>();
        WATCHES.forEach((id, w) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                gone.add(id);
                return;
            }
            tick(player, w, readThisTick);
        });
        gone.forEach(WATCHES::remove);
    }

    /** Reads and sends what changed for one player. */
    static void tick(ServerPlayer player, Watch w, Map<String, CompoundTag> readThisTick) {
        MinecraftServer server = player.server;
        TrackerRecord r = TrackerOwners.get(server).peek(server, player.getUUID());
        String here = TrackerRules.serverId();
        long now = server.getTickCount();
        if (now - w.operableTick >= 20) {
            w.operableTick = now;
            boolean unlocked = r.remoteIn(TrackerOwners.season(server));
            w.operable.clear();
            for (TrackedTag t : r.tags) {
                if (w.ids.contains(t.id())) {
                    w.operable.put(t.id(), unlocked && t.server().equals(here) && player.level().dimension().location().equals(t.dim())
                            && player.serverLevel().isLoaded(t.pos()) && poweredFor(player, t));
                }
            }
        }
        for (String id : w.ids) {
            TrackedTag t = r.tags.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
            if (t == null || !t.server().equals(here)) {
                continue; // removed since (the next watch packet drops it), or on another server: the relay sends it
            }
            CompoundTag reading = readHere(server, t, player, readThisTick);
            if (Boolean.TRUE.equals(w.operable.get(id)) && reading.getByte("st") == MachineSnapshot.OK) {
                reading = reading.copy();
                reading.putBoolean("op", true); // a box the player may click to open the machine's own screen
            }
            CompoundTag last = w.sent.get(id);
            if (reading.equals(last)) {
                continue;
            }
            w.sent.put(id, reading);
            CompoundTag out = reading.copy();
            out.putLong("ms", System.currentTimeMillis());
            BSPNetwork.sendChecked(player, new TrackerSnapshotPacket(id, out), TrackerSnapshotPacket::encode, "a machine reading");
        }
    }

    /**
     * The reading of the tag's machine on this server for this player: the machine's snapshot with status {@code st} OK, or only a
     * status (asleep, gone, no access). {@code player} may be null for a reading another server asked for, which then checks only the
     * machine's own security by the owner's id.
     */
    public static CompoundTag readHere(MinecraftServer server, TrackedTag t, @Nullable ServerPlayer player, Map<String, CompoundTag> readThisTick) {
        return readHere(server, t, player, null, readThisTick);
    }

    public static CompoundTag readHere(MinecraftServer server, TrackedTag t, @Nullable ServerPlayer player, @Nullable UUID owner, Map<String, CompoundTag> readThisTick) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, t.dim()));
        if (level == null) {
            return status(MachineSnapshot.GONE);
        }
        if (!level.isLoaded(t.pos())) {
            return status(MachineSnapshot.ASLEEP);
        }
        if (!TrackerRules.stillThere(level, t)) {
            TrackerRules.queueGoneCheck(level, t.pos());
            return status(MachineSnapshot.GONE);
        }
        boolean allowed = player != null ? TrackerAccess.mayUse(level, t.pos(), player)
                : owner != null && TrackerAccess.mekanismAllows(owner, level.getBlockEntity(t.pos()));
        if (!allowed) {
            return status(MachineSnapshot.NO_ACCESS);
        }
        String key = t.dim() + "|" + t.pos().asLong();
        CompoundTag reading = readThisTick.get(key);
        if (reading == null) {
            BlockEntity be = level.getBlockEntity(t.pos());
            reading = MachineSnapshot.read(level, be);
            reading.putByte("st", MachineSnapshot.OK);
            readThisTick.put(key, reading);
        }
        return reading;
    }

    private static boolean poweredFor(ServerPlayer player, TrackedTag t) {
        RemoteTransmitterBlockEntity tx = RemoteOperate.transmitterFor(player, player.serverLevel(), t.pos());
        return tx != null && tx.powered();
    }

    static CompoundTag status(byte st) {
        CompoundTag c = new CompoundTag();
        c.putByte("st", st);
        return c;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHES.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) {
            TrackerRelay.unwatch(sp);
        }
    }

    /** For tests: the level key of a tag's world. */
    static ResourceKey<Level> world(TrackedTag t) {
        return ResourceKey.create(Registries.DIMENSION, t.dim());
    }
}
