package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's Totem Compass wait: the real-clock time (ms) until which they may not start tracking again, with any compass.
 * A compass keeps its own wait in its NBT too, but a new compass starts with none, so without this record a player could craft
 * another compass and track again straight away. On a network the {@code compass_waits} table is the record and this is the
 * server's copy: read when the player arrives, written when a start is claimed (see {@code TotemCompassItem.startTracking}).
 */
public class CompassCooldowns extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_compass_cooldowns";
    private final Map<UUID, Long> until = new HashMap<>();

    public static CompassCooldowns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CompassCooldowns::load, CompassCooldowns::new, DATA_NAME);
    }

    /** The time (ms) until which {@code id} may not start tracking; 0 when they never have. */
    public long until(UUID id) {
        return until.getOrDefault(id, 0L);
    }

    /** {@code id} started tracking (here or elsewhere): they may not start again before {@code ms}. */
    public void start(UUID id, long ms) {
        until.merge(id, ms, Math::max);
        setDirty();
    }

    /** Ends {@code id}'s wait (admin tools and tests). */
    public void clear(UUID id) {
        if (until.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        until.forEach((id, t) -> players.putLong(id.toString(), t));
        tag.put("Until", players);
        return tag;
    }

    public static CompassCooldowns load(CompoundTag tag) {
        CompassCooldowns c = new CompassCooldowns();
        CompoundTag players = tag.getCompound("Until");
        for (String k : players.getAllKeys()) {
            try {
                c.until.put(UUID.fromString(k), players.getLong(k));
            } catch (IllegalArgumentException e) {
                // not a player id
            }
        }
        return c;
    }

    /** Forgets waits that ended before {@code now}, so the file does not grow with every player who ever tracked. */
    public void prune(long now) {
        if (until.values().removeIf(t -> t <= now)) {
            setDirty();
        }
    }
}
