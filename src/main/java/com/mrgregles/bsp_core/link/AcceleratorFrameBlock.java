package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * Accelerator Frame: seven of them round an Accelerator Intake make the ring of a Plasma Accelerator (3 high, 3 wide, 1 deep,
 * hollow in the middle). While the ring is formed the block carries {@code formed}; the Intake draws the ring and takes RF
 * through any frame. Breaking a frame un-forms the ring within a second.
 */
public class AcceleratorFrameBlock extends Block implements EntityBlock {
    public AcceleratorFrameBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.COPPER).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(StructurePartBlock.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StructurePartBlock.FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(StructurePartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; // formed: the controller draws the whole machine
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkPartBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LinkPartBlockEntity part && part.controller() != null
                && level.getBlockEntity(part.controller()) instanceof AcceleratorBlockEntity intake) {
            intake.partBroken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
