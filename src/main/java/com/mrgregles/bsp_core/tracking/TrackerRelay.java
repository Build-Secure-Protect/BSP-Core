package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerSnapshotPacket;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Machines on another server of the network (owner decisions MT5 and MT6): the player's server tells the machine's server over Redis
 * which tags the player is watching (renewed every {@link #RENEW_MS}), and the machine's server reads them every
 * {@code tracking.networkSeconds} while their chunks are loaded and sends back only the readings that changed. A watch not renewed for
 * {@link #EXPIRE_MS} is dropped there. A box whose server says nothing for {@link #OFFLINE_MS} shows the server as offline. Without Redis
 * the boxes stay "waiting" and then "offline". Readings across servers check the machine's own security by the owner's id (Mekanism);
 * a claim's membership is checked on the player's own server only (Region Guard has no lookup by player id across servers).
 *
 * <p>Messages: {@code tracker_watch} = {@code <machine server>|<player server>|<player uuid>|<id;dim;x;y;z;block>|...} (no tags: stop), and
 * {@code tracker_snap} = {@code <player server>|<player uuid>|<tag id>|<reading, NBT base64>}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TrackerRelay {
    public static final long RENEW_MS = 10_000L, EXPIRE_MS = 30_000L, OFFLINE_MS = 15_000L;

    /** On the player's server: what each watching player asked of other servers, and when each tag last heard back. */
    private static final Map<UUID, Asked> ASKED = new HashMap<>();
    /** On a machine's server: what players elsewhere watch here. */
    private static final Map<UUID, Remote> REMOTE = new HashMap<>();

    private static final class Asked {
        final Map<String, List<TrackedTag>> byServer = new HashMap<>();
        final Map<String, Long> heard = new HashMap<>();
        final Map<String, Byte> shown = new HashMap<>();
        long sentMs;
    }

    private static final class Remote {
        String from = "";
        final List<TrackedTag> tags = new ArrayList<>();
        final Map<String, CompoundTag> sent = new HashMap<>();
        long renewedMs, readMs;
    }

    private TrackerRelay() {}

    // ------------------------------------------------------------------ the player's server

    /** The player now watches {@code ids}: the ones on other servers are asked of those servers. */
    static void watch(ServerPlayer player, TrackerRecord r, Set<String> ids) {
        String here = TrackerRules.serverId();
        Map<String, List<TrackedTag>> byServer = new HashMap<>();
        for (TrackedTag t : r.tags) {
            if (ids.contains(t.id()) && !t.server().equals(here)) {
                byServer.computeIfAbsent(t.server(), k -> new ArrayList<>()).add(t);
            }
        }
        Asked old = ASKED.remove(player.getUUID());
        if (old != null) {
            for (String server : old.byServer.keySet()) {
                if (!byServer.containsKey(server)) {
                    send(server, player.getUUID(), List.of()); // that server is no longer watched
                }
            }
        }
        if (byServer.isEmpty()) {
            return;
        }
        Asked a = new Asked();
        a.byServer.putAll(byServer);
        a.sentMs = old != null ? old.sentMs : 0;
        long now = System.currentTimeMillis();
        byServer.values().forEach(list -> list.forEach(t -> {
            a.heard.put(t.id(), old != null && old.heard.containsKey(t.id()) ? old.heard.get(t.id()) : now);
            if (old != null && old.shown.containsKey(t.id())) {
                a.shown.put(t.id(), old.shown.get(t.id()));
            }
        }));
        ASKED.put(player.getUUID(), a);
        // only the servers whose list changed are told now; the rest hear it at the next renewal
        for (Map.Entry<String, List<TrackedTag>> e : byServer.entrySet()) {
            List<TrackedTag> was = old == null ? null : old.byServer.get(e.getKey());
            if (was == null || !ids(was).equals(ids(e.getValue()))) {
                send(e.getKey(), player.getUUID(), e.getValue());
            }
        }
        for (List<TrackedTag> list : byServer.values()) {
            for (TrackedTag t : list) {
                if (!a.shown.containsKey(t.id())) {
                    show(player, a, t.id(), MachineSnapshot.WAITING);
                }
            }
        }
    }

    private static List<String> ids(List<TrackedTag> tags) {
        return tags.stream().map(TrackedTag::id).toList();
    }

    static void unwatch(ServerPlayer player) {
        Asked old = ASKED.remove(player.getUUID());
        if (old != null) {
            old.byServer.keySet().forEach(server -> send(server, player.getUUID(), List.of()));
        }
    }

    private static boolean send(String server, UUID player, List<TrackedTag> tags) {
        StringBuilder b = new StringBuilder(server).append('|').append(TrackerRules.serverId()).append('|').append(player);
        for (TrackedTag t : tags) {
            b.append('|').append(t.id()).append(';').append(t.dim()).append(';').append(t.pos().getX()).append(';').append(t.pos().getY()).append(';').append(t.pos().getZ()).append(';').append(t.block());
        }
        return NetworkStorage.trackerPublish("tracker_watch", b.toString());
    }

    /** A status-only reading for a box (waiting, offline), sent only when it changes. */
    private static void show(ServerPlayer player, Asked a, String id, byte status) {
        Byte was = a.shown.get(id);
        if (was != null && was == status) {
            return;
        }
        a.shown.put(id, status);
        CompoundTag c = WatchService.status(status);
        c.putLong("ms", System.currentTimeMillis());
        BSPNetwork.sendTo(player, new TrackerSnapshotPacket(id, c));
    }

    /** A reading arrived from the machine's server for a player here. */
    public static void onSnap(MinecraftServer server, String payload) {
        String[] p = payload.split("\\|", 4);
        if (p.length < 4 || !p[0].equals(TrackerRules.serverId())) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(p[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        Asked a = ASKED.get(id);
        if (player == null || a == null || !a.heard.containsKey(p[2])) {
            return;
        }
        CompoundTag reading = NetworkStorage.lookData(p[3]);
        if (reading == null) {
            return;
        }
        a.heard.put(p[2], System.currentTimeMillis());
        a.shown.put(p[2], reading.getByte("st"));
        reading.putLong("ms", System.currentTimeMillis());
        BSPNetwork.sendChecked(player, new TrackerSnapshotPacket(p[2], reading), TrackerSnapshotPacket::encode, "a machine reading from another server");
    }

    // ------------------------------------------------------------------ the machine's server

    /** A player elsewhere watches (or stops watching) machines here. */
    public static void onWatch(MinecraftServer server, String payload) {
        String[] p = payload.split("\\|");
        if (p.length < 3 || !p[0].equals(TrackerRules.serverId())) {
            return;
        }
        UUID player;
        try {
            player = UUID.fromString(p[2]);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (p.length == 3) {
            Remote gone = REMOTE.get(player);
            if (gone != null && gone.from.equals(p[1])) {
                REMOTE.remove(player); // only the server that asked can stop it (a late stop from a server they left must not)
            }
            return;
        }
        Remote r = REMOTE.computeIfAbsent(player, k -> new Remote());
        r.from = p[1];
        r.renewedMs = System.currentTimeMillis();
        r.sent.clear(); // every renewal sends the readings again: the player's server takes them as a sign this one is alive
        List<TrackedTag> tags = new ArrayList<>();
        for (int i = 3; i < p.length && tags.size() < WatchService.MAX_WATCH; i++) {
            TrackedTag t = parse(p[i]);
            if (t != null) {
                tags.add(t);
            }
        }
        r.sent.keySet().removeIf(k -> tags.stream().noneMatch(t -> t.id().equals(k)));
        r.tags.clear();
        r.tags.addAll(tags);
        r.readMs = 0; // read at once
    }

    @Nullable
    private static TrackedTag parse(String s) {
        String[] q = s.split(";");
        if (q.length != 6) {
            return null;
        }
        ResourceLocation dim = ResourceLocation.tryParse(q[1]), block = ResourceLocation.tryParse(q[5]);
        if (dim == null || block == null) {
            return null;
        }
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(q[2]), Integer.parseInt(q[3]), Integer.parseInt(q[4]));
            return new TrackedTag(q[0], TrackerRules.serverId(), dim, pos, "", block, 0, 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        MinecraftServer server = event.getServer();
        // the player's server: renew the watches, and mark silent servers offline
        ASKED.forEach((id, a) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                return;
            }
            if (now - a.sentMs >= RENEW_MS) {
                a.sentMs = now;
                a.byServer.forEach((srv, tags) -> send(srv, id, tags));
            }
            a.heard.forEach((tag, heard) -> {
                if (now - heard >= OFFLINE_MS) {
                    show(player, a, tag, MachineSnapshot.OFFLINE);
                }
            });
        });
        ASKED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        // the machine's server: read what others watch here, every networkSeconds, and send what changed
        if (REMOTE.isEmpty()) {
            return;
        }
        long every = 1000L * com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.TRACKING_NETWORK_SECONDS, 1);
        REMOTE.entrySet().removeIf(e -> now - e.getValue().renewedMs > EXPIRE_MS);
        Map<String, CompoundTag> readThisTick = new HashMap<>();
        REMOTE.forEach((player, r) -> {
            if (now - r.readMs < every) {
                return;
            }
            r.readMs = now;
            for (TrackedTag t : r.tags) {
                CompoundTag reading = WatchService.readHere(server, t, null, player, readThisTick);
                if (reading.equals(r.sent.get(t.id()))) {
                    continue;
                }
                r.sent.put(t.id(), reading);
                NetworkStorage.trackerPublish("tracker_snap", r.from + "|" + player + "|" + t.id() + "|" + NetworkStorage.lookData(reading));
            }
        });
    }

    /** For tests: the players elsewhere watching machines here. */
    public static Set<UUID> remoteWatchers() {
        return Set.copyOf(REMOTE.keySet());
    }

    /** For tests: forgets every watch. */
    public static void clearForTest() {
        ASKED.clear();
        REMOTE.clear();
    }
}
