package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/** The two back blocks of a factory slice: the Blank Hatch (items in) and the Power Port (RF in). */
public class FactoryPortBlock extends BaseEntityBlock {
    public FactoryPortBlock() {
        super(Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(StructurePartBlock.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StructurePartBlock.FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(StructurePartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryPortBlockEntity(pos, state);
    }
}
