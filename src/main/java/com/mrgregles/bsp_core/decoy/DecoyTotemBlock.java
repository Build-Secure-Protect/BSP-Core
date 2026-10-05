package com.mrgregles.bsp_core.decoy;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.DecoyFakeOpenPacket;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * The Decoy Totem block. It has the Shatter Totem's shape and is drawn entirely by its renderer,
 * which decides per viewer whether to show the idol or a real-looking totem. The rules are in
 * {@link DecoyTotemBlockEntity}.
 */
public class DecoyTotemBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DecoyTotemBlock() {
        // the same light and feel as the real totem, so nothing but the renderer tells them apart
        super(Properties.of().mapColor(MapColor.GOLD).strength(2.0F, 3_600_000.0F).noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(s -> 10));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** The same outline and collision as a real totem. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ModBlocks.SHATTER_TOTEM.get().defaultBlockState().getShape(level, pos, ctx);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecoyTotemBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof DecoyTotemBlockEntity decoy) {
                decoy.serverTick((ServerLevel) lvl);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof DecoyTotemBlockEntity decoy) {
            decoy.setOwner(player);
        }
    }

    /** A working decoy can only be mined by its owner; a broken or unpowered one by anyone, so it can never be a permanent obstacle. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DecoyTotemBlockEntity decoy && decoy.isActive() && !decoy.isOwner(player)) {
            return 0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            if (level.getBlockEntity(pos) instanceof DecoyTotemBlockEntity decoy) {
                for (int i = 0; i < decoy.getItems().getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), decoy.getItems().getStackInSlot(i));
                }
            }
            DecoyLedger.get(sl.getServer()).remove(GlobalPos.of(sl.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DecoyTotemBlockEntity decoy) {
            if (decoy.isOwner(sp)) {
                NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new DecoyMenu(id, inv, decoy), Component.translatable("block.bsp_core.decoy_totem")), pos);
            } else if (decoy.isActive()) {
                // to anyone else it is a totem: they get what looks like a totem's panel, with its Steal button
                BSPNetwork.sendTo(sp, new DecoyFakeOpenPacket(pos, decoy.getOwnerName()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
