package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A block at the end of a cable run that a Plasma Interface feeds: a Projector or a Battery Charger. */
public interface PlasmaReceiver {
    /** mB this block would like this second, before pressure loss. */
    int wanted();

    /** Whether plasma may enter from a cable or interface that reaches this block by moving in direction {@code d}. */
    default boolean accepts(BlockPos from, BlockState state, net.minecraft.core.Direction d) {
        return true;
    }

    /**
     * Called once a second by the interface group at {@code master}. {@code pressurePerTick} is the pressure on the run after repeaters
     * (the extractors' share, whether or not this block had room for it); {@code movedMB} is what actually went into the tank this second.
     */
    void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access, @Nullable BlockPos anchorTotem);
}
