package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Safety Deposit Box block. It holds nothing itself (the box lives on the account); it exists so the renderer can show the viewer's own state. */
public class SafetyDepositBoxBlockEntity extends BlockEntity {
    public SafetyDepositBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAFETY_DEPOSIT_BOX.get(), pos, state);
    }
}
