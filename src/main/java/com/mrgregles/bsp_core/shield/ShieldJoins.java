package com.mrgregles.bsp_core.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How Radiation Shielding Panels and Windows join, face by face, the way connected-texture blocks do: a face's edge shows its border
 * (and a panel's studs) only where the block next to it in the face's plane is not one of the same kind; where both sides of a corner
 * join but the block across the corner does not (an inside corner), a small corner piece closes the border. Windows join windows of the
 * same {@link ShieldingWindowBlock#AXIS} only. The rule is shared by the client's model ({@code client/ShieldJoinedModel}) and the
 * GameTests.
 *
 * <p>Edges are named as a face is seen from outside, with its texture upright: {@link #up} and {@link #left} give the world direction
 * of each, matching Minecraft's default face uvs (north: u runs west, so left is east; up face: u runs east and v runs south, so up is
 * north; down face: v runs north, so up is south).
 */
public final class ShieldJoins {
    /** Bits of a face's mask. */
    public static final int TOP = 1, BOTTOM = 2, LEFT = 4, RIGHT = 8, INNER_TL = 16, INNER_TR = 32, INNER_BL = 64, INNER_BR = 128;

    private ShieldJoins() {}

    /** The world direction of a face's top edge, as the face is seen from outside. */
    public static Direction up(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    /** The world direction of a face's left edge, as the face is seen from outside. */
    public static Direction left(Direction face) {
        return switch (face) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            default -> Direction.WEST; // up and down: u runs east
        };
    }

    /** Whether {@code other} counts as the same kind of block as {@code state} for joining. */
    public static boolean joins(BlockState state, BlockState other) {
        if (other.getBlock() != state.getBlock()) {
            return false;
        }
        return !state.hasProperty(ShieldingWindowBlock.AXIS) || other.getValue(ShieldingWindowBlock.AXIS) == state.getValue(ShieldingWindowBlock.AXIS);
    }

    /**
     * Whether the block at {@code at} joins face {@code face} of {@code state}: the same kind, and its own face on that side can be
     * seen. A neighbour whose face is covered by a solid block (a panel wall running on below a stone floor, or meeting a panel floor)
     * is not joined, so the border runs along where the covering block meets it.
     */
    private static boolean joined(BlockGetter level, BlockState state, BlockPos at, Direction face) {
        if (!joins(state, level.getBlockState(at))) {
            return false;
        }
        BlockPos front = at.relative(face);
        return !level.getBlockState(front).isSolidRender(level, front);
    }

    /** The mask of face {@code face} of the block at {@code pos}: which edges show their border and which inside corners close. */
    public static int mask(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        Direction up = up(face), left = left(face), down = up.getOpposite(), right = left.getOpposite();
        boolean t = joined(level, state, pos.relative(up), face), b = joined(level, state, pos.relative(down), face),
                l = joined(level, state, pos.relative(left), face), r = joined(level, state, pos.relative(right), face);
        int m = (t ? 0 : TOP) | (b ? 0 : BOTTOM) | (l ? 0 : LEFT) | (r ? 0 : RIGHT);
        if (t && l && !joined(level, state, pos.relative(up).relative(left), face)) {
            m |= INNER_TL;
        }
        if (t && r && !joined(level, state, pos.relative(up).relative(right), face)) {
            m |= INNER_TR;
        }
        if (b && l && !joined(level, state, pos.relative(down).relative(left), face)) {
            m |= INNER_BL;
        }
        if (b && r && !joined(level, state, pos.relative(down).relative(right), face)) {
            m |= INNER_BR;
        }
        return m;
    }

    /** A face on its own: every edge shows (the item, and a block with nothing beside it). */
    public static final int ALONE = TOP | BOTTOM | LEFT | RIGHT;

    /**
     * Which of the five face textures a quarter of a face takes: {@code top} and {@code left} say whether that quarter is on the top and
     * on the left of the face. FULL: both of its edges show; H: only its top or bottom edge; V: only its left or right edge; INNER:
     * neither, but the corner is an inside corner; PLAIN: neither.
     */
    public enum Piece { FULL, H, V, INNER, PLAIN }

    public static Piece piece(int mask, boolean top, boolean left) {
        boolean h = (mask & (top ? TOP : BOTTOM)) != 0, v = (mask & (left ? LEFT : RIGHT)) != 0;
        if (h && v) {
            return Piece.FULL;
        }
        if (h) {
            return Piece.H;
        }
        if (v) {
            return Piece.V;
        }
        int inner = top ? (left ? INNER_TL : INNER_TR) : (left ? INNER_BL : INNER_BR);
        return (mask & inner) != 0 ? Piece.INNER : Piece.PLAIN;
    }
}
