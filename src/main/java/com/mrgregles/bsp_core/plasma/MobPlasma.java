package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.totem.TotemUpgrades.Branch;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.nbt.CompoundTag;

/**
 * Plasma carrying a Mob Chamber power, by amount: {@code mob[ordinal][level]} mB (levels 1 to 3). It travels beside a feed's list of
 * powers, not in it: a run, a link, a base and a tank port keep it apart from plain and totem plasma, so a little of it never labels the
 * rest, and a Projector gives a mob power only while that power's own plasma lasts.
 */
public final class MobPlasma {
    public static final int N = Buff.values().length, LEVELS = Buff.MOB_LEVELS + 1;

    private MobPlasma() {}

    public static long[][] empty() {
        return new long[N][LEVELS];
    }

    public static boolean isMob(int ordinal) {
        return ordinal >= 0 && ordinal < N && Buff.values()[ordinal].branch() == Branch.MOB;
    }

    public static long total(long[][] m) {
        long s = 0;
        for (long[] row : m) {
            for (int l = 1; l < LEVELS && l < row.length; l++) {
                s += row[l];
            }
        }
        return s;
    }

    public static void addInto(long[][] to, long[][] from) {
        for (int i = 0; i < N && i < from.length; i++) {
            for (int l = 1; l < LEVELS && l < from[i].length; l++) {
                to[i][l] += Math.max(0, from[i][l]);
            }
        }
    }

    /** Every amount times {@code k}, rounded down (what a lossy hop lets through). */
    public static long[][] scaled(long[][] m, double k) {
        long[][] out = empty();
        for (int i = 0; i < N && i < m.length; i++) {
            for (int l = 1; l < LEVELS && l < m[i].length; l++) {
                out[i][l] = (long) Math.floor(m[i][l] * k);
            }
        }
        return out;
    }

    /** Takes up to {@code mB} from {@code m}, each amount in proportion to its size; returns what was taken, also added to {@code out} if not null. */
    public static long takeShare(long[][] m, long mB, long[][] out) {
        long have = total(m);
        if (have <= 0 || mB <= 0) {
            return 0;
        }
        long want = Math.min(mB, have), took = 0;
        for (int i = 0; i < N; i++) {
            for (int l = 1; l < LEVELS; l++) {
                if (m[i][l] <= 0) {
                    continue;
                }
                long t = Math.min(m[i][l], Math.min(want - took, (long) Math.floor(m[i][l] * (double) want / have)));
                m[i][l] -= t;
                took += t;
                if (out != null) {
                    out[i][l] += t;
                }
            }
        }
        // rounding left a little: the rest in power order, highest level first
        for (int i = 0; i < N && took < want; i++) {
            for (int l = LEVELS - 1; l >= 1 && took < want; l--) {
                long t = Math.min(m[i][l], want - took);
                m[i][l] -= t;
                took += t;
                if (out != null) {
                    out[i][l] += t;
                }
            }
        }
        return took;
    }

    /** A copy of a feed's list of powers without the mob powers (they travel as amounts). */
    public static int[] withoutMob(int[] offered) {
        int[] out = offered == null ? new int[N] : offered.clone();
        for (int i = 0; i < out.length; i++) {
            if (isMob(i)) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static void save(CompoundTag tag, String key, long[][] m) {
        long[] flat = new long[N * LEVELS];
        for (int i = 0; i < N; i++) {
            System.arraycopy(m[i], 0, flat, i * LEVELS, LEVELS);
        }
        tag.putLongArray(key, flat);
    }

    public static long[][] load(CompoundTag tag, String key) {
        long[][] m = empty();
        long[] flat = tag.getLongArray(key);
        for (int i = 0; i < N && (i + 1) * LEVELS <= flat.length; i++) {
            if (isMob(i)) {
                for (int l = 1; l < LEVELS; l++) {
                    m[i][l] = Math.max(0, flat[i * LEVELS + l]);
                }
            }
        }
        return m;
    }
}
