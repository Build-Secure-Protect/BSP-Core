package com.mrgregles.bsp_core.plasma;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A count, per chunk, of the changes to plasma run blocks there: a cable, repeater or valve placed, removed or changed, a cable's ends
 * set, a valve's settings. Saved with each world. A network's run memory notes the count when it reads a chunk and trusts what it
 * remembers there only while the count is the same, so a run changed while no network was walking it is never walked from memory.
 */
public final class RunChanges extends SavedData {
    private static final String NAME = "bsp_core_plasma_runs";

    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    static RunChanges get(ServerLevel sl) {
        return sl.getDataStorage().computeIfAbsent(RunChanges::read, RunChanges::new, NAME);
    }

    /** Called wherever a run block at {@code pos} changes. Server side only; does nothing on a client. */
    public static void bump(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            RunChanges r = get(sl);
            r.counts.addTo(ChunkPos.asLong(pos), 1);
            r.setDirty();
        }
    }

    /** The change count of the chunk holding {@code pos}. */
    int count(BlockPos pos) {
        return counts.get(ChunkPos.asLong(pos));
    }

    private static RunChanges read(CompoundTag tag) {
        RunChanges r = new RunChanges();
        long[] chunks = tag.getLongArray("Chunks");
        int[] counts = tag.getIntArray("Counts");
        for (int i = 0; i < chunks.length && i < counts.length; i++) {
            r.counts.put(chunks[i], counts[i]);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] chunks = new long[counts.size()];
        int[] values = new int[counts.size()];
        int i = 0;
        for (Long2IntOpenHashMap.Entry e : counts.long2IntEntrySet()) {
            chunks[i] = e.getLongKey();
            values[i] = e.getIntValue();
            i++;
        }
        tag.putLongArray("Chunks", chunks);
        tag.putIntArray("Counts", values);
        return tag;
    }
}
