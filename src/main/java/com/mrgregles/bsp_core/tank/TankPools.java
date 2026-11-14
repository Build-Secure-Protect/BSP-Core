package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.totem.TotemUpgrades.Branch;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.nbt.CompoundTag;

/**
 * The plasma in a Plasma Tank, kept in pools that never mix:
 * <ul>
 *   <li><b>plain</b>: plasma that carries no power;</li>
 *   <li><b>totem</b>: plasma a totem fed, carrying the totem's powers. Of it, {@link #mb(Buff)} carries each totem power at {@link #level(Buff)}
 *   (the shares overlap: one mB can carry several powers);</li>
 *   <li>one <b>mob pool</b> per Mob Chamber power ({@link Branch#MOB}), kept by level: {@link #mbAt} holds each of levels I to III,
 *   {@link #mb(Buff)} the whole pool and {@link #level(Buff)} its highest level. Levels never mix, so plasma fed at level III stays III
 *   beside a kill's level I tags, and nothing is lifted to a higher level for free. The pool gives out its highest level first.</li>
 * </ul>
 * Ports give out the plain and totem plasma together, as tanks always have ({@link #drainOpen}); a mob pool leaves only through a Tank
 * Extractor. Arrays are indexed by {@link Buff#ordinal()}.
 */
public final class TankPools {
    private static final int N = Buff.values().length;
    private long plain, totem;
    private final long[] mb = new long[N];
    private final int[] level = new int[N];
    private final long[] fedAt = new long[N];
    /** Mob pools by level: {@code byLevel[ordinal][1..3]}; {@code mb} and {@code level} of a mob power are its sum and highest level. */
    private final long[][] byLevel = new long[N][Buff.MOB_LEVELS + 1];

    private static boolean mob(int i) {
        return Buff.values()[i].branch() == Branch.MOB;
    }

    public long plain() {
        return plain;
    }

    public long totem() {
        return totem;
    }

    /** For a totem power, how much of the totem pool carries it; for a mob power, its pool. */
    public long mb(Buff b) {
        return mb[b.ordinal()];
    }

    public long mb(int ordinal) {
        return mb[ordinal];
    }

    /** The level that plasma carries; 0 when there is none. */
    public int level(Buff b) {
        return level(b.ordinal());
    }

    public int level(int ordinal) {
        return mb[ordinal] > 0 ? level[ordinal] : 0;
    }

    /** Of mob power {@code b}'s pool, how much is at level {@code lvl} (1 to 3). */
    public long mbAt(Buff b, int lvl) {
        return lvl < 1 || lvl > Buff.MOB_LEVELS ? 0 : byLevel[b.ordinal()][lvl];
    }

    private void relevel(int i) {
        long sum = 0;
        int top = 0;
        for (int l = 1; l <= Buff.MOB_LEVELS; l++) {
            sum += byLevel[i][l];
            if (byLevel[i][l] > 0) {
                top = l;
            }
        }
        mb[i] = sum;
        level[i] = top;
    }

    /** Game time plasma carrying this power last came in. */
    public long fedAt(int ordinal) {
        return fedAt[ordinal];
    }

    /** Every mob pool together. */
    public long mobTotal() {
        long s = 0;
        for (int i = 0; i < N; i++) {
            if (mob(i)) {
                s += mb[i];
            }
        }
        return s;
    }

    public long total() {
        return plain + totem + mobTotal();
    }

    /** What a port may give out: the plain and the totem plasma. */
    public long open() {
        return plain + totem;
    }

    /**
     * Puts {@code mB} in as it arrives from a cable, carrying {@code offered} (levels by Buff ordinal, or null). Plasma with no power is plain;
     * with totem powers it joins the totem pool and its shares; with a mob power it joins that power's pool. A stream carrying several kinds
     * at once is shared evenly between them, so nothing is counted twice. The caller has checked there is room.
     */
    public void add(long mB, int[] offered, long now) {
        if (mB <= 0) {
            return;
        }
        boolean anyTotem = false;
        int mobs = 0;
        if (offered != null) {
            for (int i = 0; i < N && i < offered.length; i++) {
                if (offered[i] > 0) {
                    if (mob(i)) {
                        mobs++;
                    } else {
                        anyTotem = true;
                    }
                }
            }
        }
        int parts = mobs + (anyTotem ? 1 : 0);
        if (parts == 0) {
            plain += mB;
            return;
        }
        long share = mB / parts, rest = mB - share * parts;
        boolean first = true;
        if (anyTotem) {
            long put = share + rest;
            first = false;
            totem += put;
            for (int i = 0; i < N && i < offered.length; i++) {
                if (offered[i] > 0 && !mob(i)) {
                    mb[i] = Math.min(totem, mb[i] + put);
                    level[i] = Math.max(level[i], offered[i]);
                    fedAt[i] = now;
                }
            }
        }
        for (int i = 0; i < N && i < offered.length; i++) {
            if (offered[i] > 0 && mob(i)) {
                addMob(Buff.values()[i], offered[i], share + (first ? rest : 0), now);
                first = false;
            }
        }
    }

    /** Puts {@code mB} into mob power {@code b}'s pool at level {@code lvl} (1 to 3); the other levels are untouched. */
    public void addMob(Buff b, int lvl, long mB, long now) {
        int i = b.ordinal();
        if (mB <= 0 || !mob(i) || lvl <= 0) {
            return;
        }
        byLevel[i][Math.min(Buff.MOB_LEVELS, lvl)] += mB;
        relevel(i);
        fedAt[i] = now;
    }

    /**
     * Turns up to {@code mB} of plain plasma into mob power {@code b}'s pool at {@code lvl}, as a kill (level I) or feeding does. Returns how
     * much was turned: no more than the plain plasma there is.
     */
    public long tagFromPlain(Buff b, int lvl, long mB, long now) {
        if (!mob(b.ordinal()) || lvl <= 0) {
            return 0;
        }
        long took = drainPlain(mB);
        addMob(b, lvl, took, now);
        return took;
    }

    public void addPlain(long mB) {
        plain += Math.max(0, mB);
    }

    /** Takes up to {@code mB} of plain and totem plasma together, each in proportion, as a port gives out; returns what came. */
    public long drainOpen(long mB) {
        long before = open(), took = Math.max(0, Math.min(mB, before));
        if (took <= 0) {
            return 0;
        }
        long fromTotem = Math.round(took * (double) totem / before);
        fromTotem = Math.max(Math.max(0, took - plain), Math.min(fromTotem, totem));
        plain -= took - fromTotem;
        takeTotem(fromTotem);
        return took;
    }

    public long drainPlain(long mB) {
        long took = Math.max(0, Math.min(mB, plain));
        plain -= took;
        return took;
    }

    /** Takes up to {@code mB} from the totem pool; each power's share leaves in proportion. */
    public long drainTotem(long mB) {
        long took = Math.max(0, Math.min(mB, totem));
        takeTotem(took);
        return took;
    }

    private void takeTotem(long took) {
        if (took <= 0) {
            return;
        }
        long before = totem;
        totem -= took;
        for (int i = 0; i < N; i++) {
            if (!mob(i) && mb[i] > 0) {
                mb[i] = Math.max(0, Math.min(totem, mb[i] - Math.round(mb[i] * (double) took / before)));
                if (mb[i] == 0) {
                    level[i] = 0;
                }
            }
        }
    }

    /** Takes up to {@code mB} from mob power {@code b}'s pool, its highest level first. */
    public long drainMob(Buff b, long mB) {
        return drainMob(b, mB, true);
    }

    private long drainMob(Buff b, long mB, boolean highestFirst) {
        int i = b.ordinal();
        if (!mob(i) || mB <= 0) {
            return 0;
        }
        long took = 0;
        for (int k = 1; k <= Buff.MOB_LEVELS && took < mB; k++) {
            int l = highestFirst ? Buff.MOB_LEVELS + 1 - k : k;
            long t = Math.min(mB - took, byLevel[i][l]);
            byLevel[i][l] -= t;
            took += t;
        }
        relevel(i);
        return took;
    }

    /** Takes up to {@code mB} from mob power {@code b}'s pool at level {@code lvl} only. */
    public long drainMobAt(Buff b, int lvl, long mB) {
        int i = b.ordinal();
        if (!mob(i) || lvl < 1 || lvl > Buff.MOB_LEVELS) {
            return 0;
        }
        long took = Math.max(0, Math.min(mB, byLevel[i][lvl]));
        byLevel[i][lvl] -= took;
        relevel(i);
        return took;
    }

    /** The totem powers a port passes on with what it gives out (levels by Buff ordinal, 0 = none). Mob pools are not given out by ports. */
    public int[] offered() {
        int[] out = new int[N];
        for (int i = 0; i < N; i++) {
            out[i] = !mob(i) && mb[i] > 0 ? level[i] : 0;
        }
        return out;
    }

    /** Fits the plasma into {@code cap}: plain goes first, then the totem pool, then the mob pools in order, the lowest level of each first. */
    public void trimTo(long cap) {
        long over = total() - Math.max(0, cap);
        if (over <= 0) {
            return;
        }
        over -= drainPlain(over);
        over -= drainTotem(over);
        for (int i = 0; i < N && over > 0; i++) {
            if (mob(i)) {
                over -= drainMob(Buff.values()[i], over, false);
            }
        }
    }

    /** Pours {@code other} in: amounts add up; a totem share keeps the higher level, a mob pool keeps each level apart. */
    public void merge(TankPools other) {
        plain += other.plain;
        totem += other.totem;
        for (int i = 0; i < N; i++) {
            if (other.mb[i] <= 0) {
                continue;
            }
            if (mob(i)) {
                for (int l = 1; l <= Buff.MOB_LEVELS; l++) {
                    byLevel[i][l] += other.byLevel[i][l];
                }
                relevel(i);
            } else {
                mb[i] = Math.min(totem, mb[i] + other.mb[i]);
                level[i] = Math.max(level[i], other.level[i]);
            }
            fedAt[i] = Math.max(fedAt[i], other.fedAt[i]);
        }
    }

    public void clear() {
        plain = 0;
        totem = 0;
        java.util.Arrays.fill(mb, 0);
        java.util.Arrays.fill(level, 0);
        for (long[] l : byLevel) {
            java.util.Arrays.fill(l, 0);
        }
    }

    public TankPools copy() {
        TankPools out = new TankPools();
        out.merge(this);
        return out;
    }

    public static TankPools plainOf(long mB) {
        TankPools out = new TankPools();
        out.addPlain(mB);
        return out;
    }

    public void save(CompoundTag tag) {
        tag.putLong("Plain", plain);
        tag.putLong("Totem", totem);
        tag.putLongArray("BuffMb", mb.clone());
        tag.putIntArray("BuffLevel", level.clone());
        long[] flat = new long[N * (Buff.MOB_LEVELS + 1)];
        for (int i = 0; i < N; i++) {
            System.arraycopy(byLevel[i], 0, flat, i * (Buff.MOB_LEVELS + 1), Buff.MOB_LEVELS + 1);
        }
        tag.putLongArray("MobLevels", flat);
    }

    /**
     * Reads what {@link #save} wrote. A tank saved before the pools has only {@code Stored} and the totem shares: the most any power carried
     * becomes the totem pool, the rest plain.
     */
    public void load(CompoundTag tag) {
        clear();
        long[] m = tag.getLongArray("BuffMb");
        int[] l = tag.getIntArray("BuffLevel");
        for (int i = 0; i < N; i++) {
            mb[i] = i < m.length ? Math.max(0, m[i]) : 0;
            level[i] = i < l.length ? Math.max(0, l[i]) : 0;
        }
        if (tag.contains("Plain")) {
            plain = Math.max(0, tag.getLong("Plain"));
            totem = Math.max(0, tag.getLong("Totem"));
        } else {
            long stored = Math.max(0, tag.getLong("Stored")), carried = 0;
            for (int i = 0; i < N; i++) {
                if (mob(i)) {
                    mb[i] = 0; // an old save has no mob pools
                    level[i] = 0;
                } else {
                    mb[i] = Math.min(stored, mb[i]);
                    carried = Math.max(carried, mb[i]);
                }
            }
            totem = carried;
            plain = stored - carried;
        }
        long[] flat = tag.getLongArray("MobLevels");
        int stride = Buff.MOB_LEVELS + 1;
        for (int i = 0; i < N; i++) {
            if (mob(i)) {
                if (flat.length >= (i + 1) * stride) {
                    for (int lv = 1; lv < stride; lv++) {
                        byLevel[i][lv] = Math.max(0, flat[i * stride + lv]);
                    }
                } else if (mb[i] > 0) { // saved with one level per pool
                    byLevel[i][Math.max(1, Math.min(Buff.MOB_LEVELS, level[i]))] = mb[i];
                }
                relevel(i);
                continue;
            }
            mb[i] = Math.min(totem, mb[i]);
            if (mb[i] == 0) {
                level[i] = 0;
            }
        }
    }
}
