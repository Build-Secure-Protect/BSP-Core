package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/** A block at the end of a cable run that a Plasma Interface feeds: a Projector or a Battery Charger. */
public interface PlasmaReceiver {
    /** mB this block would like this second, before pressure loss. */
    int wanted();

    /** Called once a second by the interface group at {@code master}. {@code deliveredPerTick} is what arrives after repeaters. */
    void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, PlasmaAccess access, @Nullable BlockPos anchorTotem);
}
