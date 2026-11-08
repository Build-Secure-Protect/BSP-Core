package com.mrgregles.bsp_core.shield;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The Radiation Shielding Panel: a full lead block, with {@link TotemCoating#COATED}. */
public class ShieldingPanelBlock extends Block {
    public ShieldingPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TotemCoating.COATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TotemCoating.COATED);
    }
}
