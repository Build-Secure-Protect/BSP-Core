package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A block at the end of a cable run that a Plasma Interface feeds: a Projector or a Battery Charger. */
public interface PlasmaReceiver {
    /** mB this block would like this second, before pressure loss. */
    int wanted();

    /** Room for plasma that is all a Mob Chamber power's ({@link MobPlasma}): the same as {@link #wanted()} unless the receiver keeps it apart. */
    default int wantedMob() {
        return wanted();
    }

    /** Whether plasma may enter from a cable or interface that reaches this block by moving in direction {@code d}. */
    default boolean accepts(BlockPos from, BlockState state, net.minecraft.core.Direction d) {
        return true;
    }

    /**
     * Called once a second by the interface group at {@code master}. {@code pressurePerTick} is the pressure on the run after repeaters
     * (the extractors' share, whether or not this block had room for it); {@code movedMB} is what actually went into the tank this second.
     * {@code anchor} is the totem whose Anchor levels travel with the plasma, in whatever world it stands (a link may bring it from another),
     * with who owned it and could edit it when the plasma left it.
     */
    void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access, @Nullable Anchor anchor);

    /**
     * The feed with the Mob Chamber powers' plasma among {@code movedMB}, by amount ({@link MobPlasma}); {@code offeredByOrdinal} then lists
     * no mob power. A receiver that keeps it apart (a tank port, a projector base, an accelerator) overrides this; any other takes it as
     * plain plasma.
     */
    default void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access,
                      @Nullable Anchor anchor, long[][] mob) {
        feed(master, offeredByOrdinal, repeaters, pressurePerTick, movedMB, access, anchor);
    }
}
