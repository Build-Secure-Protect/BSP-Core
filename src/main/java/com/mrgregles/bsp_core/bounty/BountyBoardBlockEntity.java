package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Bounty Board's controller. It keeps nothing: the renderer draws the posters from the bounty summary every client has
 * ({@link BountySummary}), so a board needs no data of its own.
 */
public class BountyBoardBlockEntity extends BlockEntity {
    public BountyBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOUNTY_BOARD.get(), pos, state);
    }

    /** The board reaches a block up and to the side (and its roof forward), so it must not be culled with the controller alone. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5);
    }
}
