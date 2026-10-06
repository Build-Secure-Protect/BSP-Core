package com.mrgregles.bsp_core.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** The Totem Projector block ("Totem Obelisk"). The rules are in {@link TotemProjectorBlockEntity}. */
public class TotemProjectorBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(6, 4, 6, 10, 16, 10));

    public TotemProjectorBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 8));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TotemProjectorBlockEntity(pos, state);
    }

    /** A broken projector lets go of the chunks picked around it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            com.mrgregles.bsp_core.chunk.ChunkLoading.projectorRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Right-click opens the projector's screen: what it is doing, the powers reaching it, and its chunk picker. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof TotemProjectorBlockEntity projector) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof com.mrgregles.bsp_core.plasma.ChannelExpanderItem && !projector.hasExpander() && projector.mayEdit(player)) {
                // a Channel Expander in hand is fitted; sneak + right-click with an empty hand takes it back out
                projector.setExpander(true);
                held.shrink(1);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.projector.expander_fitted"), true);
            } else if (player.isShiftKeyDown() && held.isEmpty() && projector.hasExpander() && projector.mayEdit(player)) {
                projector.setExpander(false);
                net.minecraft.world.item.ItemStack back = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.CHANNEL_EXPANDER.get());
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
            } else {
                com.mrgregles.bsp_core.chunk.ChunkLoading.sendView(sp, pos, true);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof TotemProjectorBlockEntity projector) {
                projector.serverTick((ServerLevel) lvl);
            }
        };
    }
}
