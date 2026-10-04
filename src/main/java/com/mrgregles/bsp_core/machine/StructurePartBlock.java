package com.mrgregles.bsp_core.machine;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A plain structural block of a multiblock (casing, core). While it belongs to a formed structure
 * its own cube is not drawn, because the controller renders the complete machine in its place. It
 * keeps its collision and can still be broken, which un-forms the structure.
 */
public class StructurePartBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public StructurePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
