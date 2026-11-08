package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

/**
 * Totem radiation: how far away a tracking Totem Compass can see a placed totem. Harmless, and apart from Voltaic's radiation.
 *
 * <ul>
 *   <li>A totem's radiation level is its tier steps (0 for Tier I to 4 for Tier V) plus every upgrade level it has bought.</li>
 *   <li>Its seen-at distance is {@code maxSeenAt x (level / fullLevel) ^ exponent}, at most {@code maxSeenAt}: with the default
 *       exponent 2 it follows the Stefan-Boltzmann law (power with the fourth power of the level, spread with the square of the
 *       distance). Level 0 is never seen.</li>
 *   <li>Each layer of Totem Radiation Coating round the totem takes {@code layerCut} of that distance away, up to
 *       {@code maxLayers} layers. A layer is a whole wall: the count is the fewest coated blocks any way out has to cross
 *       ({@link #countLayers}).</li>
 *   <li>The distance is a straight line, so depth counts; ordinary blocks in between do not.</li>
 *   <li>In the outer half of the seen-at distance the needle points at a spot beside the totem: at the very edge
 *       {@code blurFraction} of the distance off, down to none at half the seen-at distance. The spot's direction is fixed for
 *       the totem for a day (UTC), so bearings taken from two places cross on that spot, not on the totem.</li>
 * </ul>
 * Everything is read from the totem ledger, so no chunk is loaded. Settings: server config {@code totemRadiation}.
 */
public final class TotemRadiation {
    private static final long DAY_MS = 86_400_000L;

    private TotemRadiation() {}

    public static boolean enabled() {
        return BSPConfig.getOr(BSPConfig.RADIATION_ENABLED, true);
    }

    /** Radiation level from a tier (0 to 4) and the sum of the upgrade levels. */
    public static int level(int tier, int upgradeLevels) {
        return Math.max(0, tier) + Math.max(0, upgradeLevels);
    }

    /** The placed totem's radiation level, from the ledger. */
    public static int level(TotemLedger ledger, GlobalPos pos) {
        return level(ledger.tierAt(pos), ledger.levelSum(pos));
    }

    /** How far away a totem of {@code level} is seen with no coating; {@link Integer#MAX_VALUE} when radiation is turned off. */
    public static int bareSeenAt(int level) {
        if (!enabled()) {
            return Integer.MAX_VALUE;
        }
        if (level <= 0) {
            return 0;
        }
        int full = BSPConfig.getOr(BSPConfig.RADIATION_FULL_LEVEL, 120), max = BSPConfig.getOr(BSPConfig.RADIATION_MAX_SEEN, 2500);
        double share = Math.min(1.0, level / (double) full);
        return (int) Math.round(max * Math.pow(share, BSPConfig.getOr(BSPConfig.RADIATION_EXPONENT, 2.0)));
    }

    /** Coated layers that count, at most {@code maxLayers}. */
    public static int countedLayers(int layers) {
        return Math.max(0, Math.min(layers, BSPConfig.getOr(BSPConfig.RADIATION_MAX_LAYERS, 4)));
    }

    /** How far away a totem of {@code level} inside {@code layers} coated layers is seen. */
    public static int seenAt(int level, int layers) {
        int bare = bareSeenAt(level);
        if (bare == Integer.MAX_VALUE || bare == 0) {
            return bare;
        }
        double keep = Math.pow(1.0 - BSPConfig.getOr(BSPConfig.RADIATION_LAYER_CUT, 0.2), countedLayers(layers));
        return (int) Math.round(bare * keep);
    }

    /** How far away the placed totem at {@code pos} is seen, from the ledger. */
    public static int seenAt(TotemLedger ledger, GlobalPos pos) {
        return seenAt(level(ledger, pos), ledger.coatedLayers(pos));
    }

    /** Whether a compass at {@code from} sees a totem at {@code totem} seen from {@code seenAt} blocks. */
    public static boolean sees(BlockPos from, BlockPos totem, int seenAt) {
        if (seenAt == Integer.MAX_VALUE) {
            return true;
        }
        return seenAt > 0 && from.distSqr(totem) <= (double) seenAt * seenAt;
    }

    /**
     * Where the needle points for the totem at {@code totem}, seen from {@code from}: the totem itself within half its seen-at
     * distance, otherwise a spot beside it, off by up to {@code blurFraction} of the distance at the edge. The direction depends
     * on the totem's place and the day ({@code nowMs}, UTC), never on who looks or from where.
     */
    public static BlockPos pointAt(GlobalPos totem, BlockPos from, int seenAt, long nowMs) {
        BlockPos at = totem.pos();
        if (seenAt == Integer.MAX_VALUE || seenAt <= 0) {
            return at;
        }
        double d = Math.sqrt(from.distSqr(at)), half = seenAt / 2.0;
        double edge = Math.max(0.0, Math.min(1.0, (d - half) / half));
        double off = BSPConfig.getOr(BSPConfig.RADIATION_BLUR, 0.2) * d * edge;
        if (off < 1.0) {
            return at;
        }
        double angle = direction(totem, nowMs / DAY_MS);
        return at.offset((int) Math.round(Math.cos(angle) * off), 0, (int) Math.round(Math.sin(angle) * off));
    }

    // ------------------------------------------------------------------ coated layers

    /**
     * Layers of Totem Radiation Coating round the totem at {@code totem}: the fewest coated blocks any way out of the cube of
     * {@code radius} blocks round it has to pass through, moving face to face from block to block (a coated door only while it is
     * shut; every other block is open). So it counts whole walls: a box of coated shielding round the totem is one layer, two boxes
     * two, and a box with an opening is none. At most {@code maxLayers}; the world's top and bottom count as open.
     *
     * <p>First the chunk sections in the cube are asked whether their palettes hold any coated shielding at all, so a totem with
     * none near it costs a few look-ups. Otherwise a search ordered by layers crossed (Dial's algorithm), which stops at the first
     * way out or at {@code maxLayers}. Returns -1 when a chunk in the cube is not ready (it is never loaded or waited for).
     */
    public static int countLayers(net.minecraft.server.level.ServerLevel level, BlockPos totem, int radius) {
        int cap = countedLayers(Integer.MAX_VALUE);
        if (cap <= 0 || radius <= 0) {
            return 0;
        }
        int x0 = totem.getX() - radius, y0 = totem.getY() - radius, z0 = totem.getZ() - radius, side = 2 * radius + 1;
        int cx0 = x0 >> 4, cz0 = z0 >> 4, cx1 = (x0 + side - 1) >> 4, cz1 = (z0 + side - 1) >> 4;
        int yLo = Math.max(level.getMinBuildHeight(), y0), yHi = Math.min(level.getMaxBuildHeight() - 1, y0 + side - 1);
        net.minecraft.world.level.chunk.LevelChunk[][] chunks = new net.minecraft.world.level.chunk.LevelChunk[cx1 - cx0 + 1][cz1 - cz0 + 1];
        boolean any = false;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return -1;
                }
                chunks[cx - cx0][cz - cz0] = chunk;
                for (int s = level.getSectionIndex(yLo); !any && s <= level.getSectionIndex(yHi); s++) {
                    var section = chunk.getSection(s);
                    any = !section.hasOnlyAir() && section.maybeHas(TotemRadiation::shields);
                }
            }
        }
        if (!any) {
            return 0;
        }
        byte[] best = new byte[side * side * side];
        java.util.Arrays.fill(best, Byte.MAX_VALUE);
        it.unimi.dsi.fastutil.ints.IntArrayList[] buckets = new it.unimi.dsi.fastutil.ints.IntArrayList[cap + 1];
        for (int c = 0; c <= cap; c++) {
            buckets[c] = new it.unimi.dsi.fastutil.ints.IntArrayList();
        }
        int startIndex = (radius * side + radius) * side + radius;
        best[startIndex] = 0;
        buckets[0].add(startIndex);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int c = 0; c <= cap; c++) {
            it.unimi.dsi.fastutil.ints.IntArrayList bucket = buckets[c];
            while (!bucket.isEmpty()) {
                int i = bucket.removeInt(bucket.size() - 1);
                if (best[i] != c) {
                    continue; // reached more cheaply since
                }
                int lx = i % side, ly = (i / side) % side, lz = i / (side * side);
                for (int[] d : steps) {
                    int nx = lx + d[0], ny = ly + d[1], nz = lz + d[2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= side || ny >= side || nz >= side) {
                        return c; // a way out of the cube crossing c layers, the fewest there are
                    }
                    int wx = x0 + nx, wy = y0 + ny, wz = z0 + nz;
                    if (wy < level.getMinBuildHeight() || wy >= level.getMaxBuildHeight()) {
                        return c; // past the top or bottom of the world: open
                    }
                    int ni = (nz * side + ny) * side + nx;
                    p.set(wx, wy, wz);
                    int nc = c + (shields(chunks[(wx >> 4) - cx0][(wz >> 4) - cz0].getBlockState(p)) ? 1 : 0);
                    if (nc < best[ni] && nc <= cap) {
                        best[ni] = (byte) nc;
                        buckets[nc].add(ni);
                    }
                }
            }
        }
        return cap; // no way out crossing fewer than the most that count
    }

    /** A coated shielding block, and for a door only while it is shut. */
    public static boolean shields(net.minecraft.world.level.block.state.BlockState state) {
        if (!com.mrgregles.bsp_core.shield.TotemCoating.coated(state)) {
            return false;
        }
        return !(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) || !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
    }

    /** Coated layers with the configured radius ({@code checkRadius}). */
    public static int countLayers(net.minecraft.server.level.ServerLevel level, BlockPos totem) {
        return countLayers(level, totem, BSPConfig.getOr(BSPConfig.RADIATION_CHECK_RADIUS, 32));
    }

    /** The blur spot's direction (radians) for a totem on a given day. */
    static double direction(GlobalPos totem, long day) {
        long h = totem.pos().asLong() * 0x9E3779B97F4A7C15L ^ totem.dimension().location().hashCode() * 0xC2B2AE3D27D4EB4FL ^ day * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53 * 2 * Math.PI;
    }
}
