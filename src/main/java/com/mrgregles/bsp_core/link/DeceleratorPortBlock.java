package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
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
 * Decelerator Port: the back block of a Plasma Decelerator, placed directly behind a Decelerator Core. The plasma that arrives in the
 * core leaves through this block into the cables on its free faces. It has no facing of its own: the core's facing decides where
 * it goes.
 */
public class DeceleratorPortBlock extends Block implements EntityBlock {
    public DeceleratorPortBlock() {
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
        return new DeceleratorPortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.DECELERATOR_PORT.get()) {
            return null;
        }
        return (lvl, p, st, be) -> ((DeceleratorPortBlockEntity) be).serverTick((net.minecraft.server.level.ServerLevel) lvl);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LinkPartBlockEntity part && part.controller() != null
                && level.getBlockEntity(part.controller()) instanceof DeceleratorBlockEntity core) {
            core.partBroken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
