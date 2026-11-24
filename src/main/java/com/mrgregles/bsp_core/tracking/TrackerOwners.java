package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Every player's {@link TrackerRecord} on this server, kept in step with the network database when there is one: written there on every
 * change (the newer copy wins), read back at login and whenever another server says the player's record changed. Bought slots, the
 * Remote Control unlock and tags count only in the season they were made in ({@link #record} drops what an older season left), so a
 * season ended anywhere, even while this server was down, takes them away here too. A player reset ({@link #resetPlayer}) clears them
 * on every server it reaches and is never undone by an older copy; the Holo Grid layout and presets stay.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TrackerOwners extends SavedData {
    private static final String DATA_NAME = "bsp_core_tracker_owners";

    private final Map<UUID, TrackerRecord> records = new HashMap<>();

    /** Called on the server thread when a player's record changes here or arrives from another server (the client sync listens). */
    public static final List<BiConsumer<MinecraftServer, UUID>> CHANGED = new ArrayList<>();

    public static TrackerOwners get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TrackerOwners::load, TrackerOwners::new, DATA_NAME);
    }

    /** The season tags and purchases belong to now. */
    public static int season(MinecraftServer server) {
        return SeasonData.get(server).number;
    }

    /**
     * The player's record as it counts this season (a copy: change it through {@link #put}), or a new empty one. What an earlier season
     * left is dropped from the stored record as a side effect.
     */
    public TrackerRecord record(MinecraftServer server, UUID player) {
        TrackerRecord r = records.get(player);
        if (r == null) {
            TrackerRecord fresh = new TrackerRecord();
            fresh.boughtSeason = fresh.remoteSeason = season(server);
            return fresh;
        }
        if (r.prune(season(server))) {
            setDirty(); // not shared: every server prunes for itself, and a share would look like a change the player made
        }
        return r.copy();
    }

    private static final TrackerRecord EMPTY = new TrackerRecord();
    /** Players whose network copy has not been read since they logged in: their changes wait, or they would overwrite it. */
    private static final java.util.Set<UUID> LOADING = new java.util.HashSet<>();

    /**
     * The player's stored record, read-only and without a copy (for the every-tick readers), pruned of earlier seasons in place. Never
     * change what it returns.
     */
    public TrackerRecord peek(MinecraftServer server, UUID player) {
        TrackerRecord r = records.get(player);
        if (r == null) {
            return EMPTY;
        }
        if (r.prune(season(server))) {
            setDirty();
        }
        return r;
    }

    /** Whether the player's record here is the network's (read since they logged in), so their changes may be made. */
    public static boolean ready(UUID player) {
        return !LOADING.contains(player);
    }

    /** Whether there is a stored record for the player (for tests and staff tools). */
    public boolean has(UUID player) {
        return records.containsKey(player);
    }

    /** The players whose records here hold a tag on that machine (whatever its season; {@link #record} drops old ones). */
    public List<UUID> ownersAt(String server, net.minecraft.resources.ResourceLocation dim, net.minecraft.core.BlockPos pos) {
        List<UUID> out = new ArrayList<>();
        records.forEach((id, r) -> {
            if (r.tags.stream().anyMatch(t -> t.at(server, dim, pos))) {
                out.add(id);
            }
        });
        return out;
    }

    /** Every tag here on that machine. */
    public List<TrackedTag> tagsAt(String server, net.minecraft.resources.ResourceLocation dim, net.minecraft.core.BlockPos pos) {
        List<TrackedTag> out = new ArrayList<>();
        records.values().forEach(r -> r.tags.stream().filter(t -> t.at(server, dim, pos)).forEach(out::add));
        return out;
    }

    /** Stores a changed record, shares it with the network and tells listeners. */
    public void put(MinecraftServer server, UUID player, TrackerRecord record) {
        put(server, player, record, true);
    }

    private void put(MinecraftServer server, UUID player, TrackerRecord record, boolean share) {
        TrackerRecord was = records.get(player);
        long floor = was == null ? 0 : was.updatedMs;
        record.updatedMs = Math.max(System.currentTimeMillis(), Math.max(floor, record.updatedMs) + 1);
        records.put(player, record);
        setDirty();
        if (share) {
            NetworkStorage.trackerWrite(player, record.save(), record.updatedMs, record.resetMs);
        }
        CHANGED.forEach(l -> l.accept(server, player));
    }

    /**
     * A record read from the network database: kept when it is newer than this server's. A reset this server knows of and the copy does
     * not is applied to it first, so a copy from before a reset never brings tags or purchases back.
     */
    public void arrived(MinecraftServer server, UUID player, CompoundTag data) {
        TrackerRecord theirs = TrackerRecord.load(data);
        TrackerRecord mine = records.get(player);
        if (mine != null) {
            if (theirs.updatedMs <= mine.updatedMs && theirs.resetMs <= mine.resetMs) {
                return;
            }
            if (theirs.updatedMs <= mine.updatedMs) {
                // only the reset is news: apply it to this server's copy (kept as it is if it changed after the reset)
                mine.resetIfOlder(theirs.resetMs);
                theirs = mine;
            } else if (mine.resetMs > theirs.resetMs) {
                theirs.resetIfOlder(mine.resetMs);
            }
        }
        theirs.prune(season(server));
        records.put(player, theirs);
        setDirty();
        CHANGED.forEach(l -> l.accept(server, player));
    }

    /**
     * The player reset at {@code resetMs} (from {@code /bsp player reset}): their tags, bought slots and Remote Control unlock go; their
     * layout and presets stay. {@code origin}: this server ran the reset, so the network's copy is replaced; another server only resets its
     * own copy (one reached late must not overwrite what the player did since). Returns whether anything here changed.
     */
    public boolean resetPlayer(MinecraftServer server, UUID player, long resetMs, boolean origin) {
        if (origin && NetworkStorage.enabled()) {
            // the network's copy first: this server may never have seen the player, or hold an old copy, and must not write its layout over theirs
            NetworkStorage.trackerRead(server, player, () -> resetOrigin(server, player, resetMs));
            return true;
        }
        if (origin) {
            return resetOrigin(server, player, resetMs);
        }
        TrackerRecord r = records.get(player);
        if (r == null) {
            return false; // nothing here to reset; a copy that turns up later from before the reset is reset by its stamp
        }
        r = r.copy();
        long was = r.resetMs;
        boolean dropped = r.resetIfOlder(resetMs);
        if (r.resetMs == was) {
            return false;
        }
        records.put(player, r); // not shared: the origin wrote the network's copy; this server only catches up
        setDirty();
        if (dropped) {
            CHANGED.forEach(l -> l.accept(server, player));
            BSPCore.LOGGER.info("Machine tracking of {} reset here: what was made before the reset removed", player);
        }
        return dropped;
    }

    private boolean resetOrigin(MinecraftServer server, UUID player, long resetMs) {
        TrackerRecord had = records.get(player);
        TrackerRecord r = had == null ? new TrackerRecord() : had.copy();
        if (!r.reset(resetMs)) {
            return false;
        }
        put(server, player, r, true);
        BSPCore.LOGGER.info("Machine tracking of {} reset: tags, bought slots and the Remote Control unlock removed (shared with the network)", player);
        return true;
    }

    /** Writes every record here up to the network (after the database was out of reach): the newer row wins there. */
    public void pushAll() {
        records.forEach((id, r) -> NetworkStorage.trackerWrite(id, r.save(), r.updatedMs, r.resetMs));
    }

    /** Drops what the season that just ended left from every record here (the rest are pruned when next read). */
    public void seasonEnded(MinecraftServer server) {
        int season = season(server);
        List<UUID> changed = new ArrayList<>();
        records.forEach((id, r) -> {
            if (r.prune(season)) {
                changed.add(id);
            }
        });
        if (!changed.isEmpty()) {
            setDirty();
            changed.forEach(id -> CHANGED.forEach(l -> l.accept(server, id)));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.server;
            UUID id = player.getUUID();
            // the network's copy first (changes made on the server they came from), then whatever listens (the client sync)
            if (NetworkStorage.enabled()) {
                LOADING.add(id);
            }
            NetworkStorage.trackerRead(server, id, () -> {
                LOADING.remove(id);
                CHANGED.forEach(l -> l.accept(server, id));
            });
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOADING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onSeasonEnded(com.mrgregles.bsp_core.api.event.SeasonEvent.Ended event) {
        get(event.getServer()).seasonEnded(event.getServer());
    }

    // ------------------------------------------------------------------ saved data

    public static TrackerOwners load(CompoundTag tag) {
        TrackerOwners o = new TrackerOwners();
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            try {
                if (all.contains(key, Tag.TAG_COMPOUND)) {
                    o.records.put(UUID.fromString(key), TrackerRecord.load(all.getCompound(key)));
                }
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
        return o;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        records.forEach((id, r) -> all.put(id.toString(), r.save()));
        tag.put("players", all);
        return tag;
    }

    /** For tests: forgets the player's record here without telling anyone. */
    public void forgetForTest(UUID player) {
        records.remove(player);
        setDirty();
    }

    /** For tests: the stored record as it is, without the season pruning. */
    @Nullable
    public TrackerRecord rawForTest(UUID player) {
        TrackerRecord r = records.get(player);
        return r == null ? null : r.copy();
    }
}
