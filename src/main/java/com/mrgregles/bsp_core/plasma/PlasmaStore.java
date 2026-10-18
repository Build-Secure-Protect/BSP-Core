package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.nbt.CompoundTag;

/**
 * A buffer of Wave Plasma that remembers the totem powers its plasma carries, the rule the Plasma Tank uses: of what is stored, how
 * much carries each power and at what level; plasma that comes in with a power credits that power's share, plasma that goes out takes a
 * proportional share of every power (the plasma is mixed), and a power is passed on until the plasma carrying it is used up. Used by
 * the Plasma Accelerator and Decelerator; the tank keeps its own copy of the rule for now.
 */
public final class PlasmaStore {
    private final java.util.function.LongSupplier capacity;
    private long stored;
    private final long[] buffedMb = new long[Buff.values().length];
    private final int[] buffedLevel = new int[buffedMb.length];

    public PlasmaStore(java.util.function.LongSupplier capacity) {
        this.capacity = capacity;
    }

    public long capacity() {
        return capacity.getAsLong();
    }

    public long stored() {
        return stored;
    }

    public long room() {
        return Math.max(0, capacity() - stored);
    }

    /** Puts up to {@code mB} in, carrying {@code offered} (levels by Buff ordinal, or null for plain plasma); returns what fitted. */
    public long add(long mB, int[] offered) {
        long took = Math.max(0, Math.min(mB, room()));
        stored += took;
        if (took <= 0 || offered == null) {
            return took;
        }
        for (int i = 0; i < buffedMb.length && i < offered.length; i++) {
            if (offered[i] > 0) {
                buffedMb[i] = Math.min(stored, buffedMb[i] + took);
                buffedLevel[i] = Math.max(buffedLevel[i], offered[i]);
            }
        }
        return took;
    }

    /** Takes up to {@code mB} out; returns what came. Each power's share leaves in proportion. */
    public long drain(long mB) {
        long before = stored;
        long took = Math.max(0, Math.min(mB, stored));
        stored -= took;
        if (took > 0) {
            for (int i = 0; i < buffedMb.length; i++) {
                if (buffedMb[i] > 0) {
                    buffedMb[i] = Math.max(0, Math.min(stored, buffedMb[i] - Math.round(buffedMb[i] * (double) took / before)));
                    if (buffedMb[i] == 0) {
                        buffedLevel[i] = 0;
                    }
                }
            }
        }
        return took;
    }

    /** The powers the stored plasma still carries, by Buff ordinal (0 = none). */
    public int[] offered() {
        int[] out = new int[buffedMb.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = buffedMb[i] > 0 ? buffedLevel[i] : 0;
        }
        return out;
    }

    public long buffedMb(int ordinal) {
        return buffedMb[ordinal];
    }

    public void clear() {
        stored = 0;
        java.util.Arrays.fill(buffedMb, 0);
        java.util.Arrays.fill(buffedLevel, 0);
    }

    public void save(CompoundTag tag, String key) {
        tag.putLong(key, stored);
        tag.putLongArray(key + "BuffMb", buffedMb.clone());
        tag.putIntArray(key + "BuffLevel", buffedLevel.clone());
    }

    public void load(CompoundTag tag, String key) {
        stored = Math.max(0, tag.getLong(key));
        long[] mb = tag.getLongArray(key + "BuffMb");
        int[] lv = tag.getIntArray(key + "BuffLevel");
        for (int i = 0; i < buffedMb.length; i++) {
            buffedMb[i] = i < mb.length ? Math.min(stored, Math.max(0, mb[i])) : 0;
            buffedLevel[i] = i < lv.length ? Math.max(0, lv[i]) : 0;
        }
    }
}
