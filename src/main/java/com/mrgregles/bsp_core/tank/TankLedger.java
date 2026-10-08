package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plasma remembered for tanks that have come apart. Break one block and the tank goes dormant here; put it back, or rebuild any box
 * that overlaps the old one, and the plasma comes back (as much as fits). The entry is forgotten when the last block of the old shell
 * is gone. Kept per dimension in {@code data/bsp_core_tanks.dat}.
 */
public class TankLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_tanks";

    /** A dormant tank: its box, what it held, and how many of its shell blocks still stand. */
    public static final class Entry {
        public BlockPos min;
        public int w, h, d, remaining;
        public long stored;

        boolean onShell(BlockPos p) {
            int x = p.getX() - min.getX(), y = p.getY() - min.getY(), z = p.getZ() - min.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                return false;
            }
            return x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1;
        }

        boolean overlaps(BlockPos omin, int ow, int oh, int od) {
            return min.getX() < omin.getX() + ow && omin.getX() < min.getX() + w && min.getY() < omin.getY() + oh && omin.getY() < min.getY() + h
                    && min.getZ() < omin.getZ() + od && omin.getZ() < min.getZ() + d;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static TankLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TankLedger::load, TankLedger::new, DATA_NAME);
    }

    /** A tank came apart: remember its plasma. */
    public void remember(BlockPos min, int w, int h, int d, long stored, int remaining) {
        Entry e = new Entry();
        e.min = min.immutable();
        e.w = w;
        e.h = h;
        e.d = d;
        e.stored = stored;
        e.remaining = remaining;
        entries.add(e);
        setDirty();
    }

    /** A tank formed over {@code min..dims}: the plasma of every dormant tank it overlaps, which are then forgotten. */
    public long take(BlockPos min, int w, int h, int d) {
        long sum = 0;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.overlaps(min, w, h, d)) {
                sum += e.stored;
                it.remove();
                setDirty();
            }
        }
        return sum;
    }

    /** A tank block was broken while not part of a formed tank: a dormant tank it belonged to has one block fewer, and is forgotten with its last. */
    public void blockBroken(BlockPos pos) {
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.onShell(pos)) {
                e.remaining--;
                setDirty();
                if (e.remaining <= 0) {
                    it.remove();
                }
            }
        }
    }

    public static TankLedger load(CompoundTag tag) {
        TankLedger ledger = new TankLedger();
        for (Tag t : tag.getList("Tanks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry();
            e.min = BlockPos.of(c.getLong("Min"));
            e.w = c.getInt("W");
            e.h = c.getInt("H");
            e.d = c.getInt("D");
            e.stored = c.getLong("Stored");
            e.remaining = c.getInt("Remaining");
            ledger.entries.add(e);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putLong("Min", e.min.asLong());
            c.putInt("W", e.w);
            c.putInt("H", e.h);
            c.putInt("D", e.d);
            c.putLong("Stored", e.stored);
            c.putInt("Remaining", e.remaining);
            list.add(c);
        }
        tag.put("Tanks", list);
        return tag;
    }
}
