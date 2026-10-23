package com.mrgregles.bsp_core.totem;

import net.minecraft.core.Direction;

/** Where the upgrade orbs stand round a placed totem's plinth: the arithmetic only, so it can be checked without a client. */
public final class TotemOrbLayout {
    private TotemOrbLayout() {}

    /**
     * Where the {@code k}th of {@code count} orbs sits on the ring, in radians (pi/2 is the totem's front, -pi/2 its back): evenly
     * spaced all the way round, laid out symmetrically from the back (one orb alone sits at the back; an even number straddles it).
     */
    public static double angle(int k, int count) {
        int m = Math.max(1, count);
        return -Math.PI / 2 + 2 * Math.PI * k / m + (m % 2 == 0 ? Math.PI / m : 0);
    }

    /** The blockstate's turn of the model about Y for a facing, in degrees (north 0, east 90, south 180, west 270). */
    public static int modelTurn(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }
}
