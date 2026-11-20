package com.mrgregles.bsp_core.compass;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Compass Tuner block (concept A, the Gimbal Cradle). The rules are in {@link CompassTunerBlockEntity}. Only the owner
 * and their teammates can mine it: to anyone else it does not break, and explosions and pistons cannot move it.
 */
public class CompassTunerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CompassTunerBlock() {
        // hardness -1 like bedrock, so machines and mobs that break blocks pass it by; the owner and team mine it at 3.5 (below),
        // and it drops with its upgrades whatever they mine it with
        super(Properties.of().mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.DEEPSLATE_TILES).noOcclusion().pushReaction(PushReaction.BLOCK));
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

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompassTunerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof CompassTunerBlockEntity tuner) {
                tuner.serverTick((ServerLevel) lvl);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CompassTunerBlockEntity tuner) {
            tuner.setOwner(player);
        }
    }

    /** Hardness the owner and team mine it at, as an ordinary stone block. */
    public static final float HARDNESS = 3.5F;

    /** Anyone but the owner and their team cannot mine it at all; they mine it as a block of hardness 3.5. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CompassTunerBlockEntity tuner) || !tuner.mayUse(player)) {
            return 0f;
        }
        return player.getDigSpeed(state, pos) / HARDNESS / 30f;
    }

    /** The server's own check: a block broken by anyone outside the team (a fake player, a cheat) stays. Admins in creative may. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof CompassTunerBlockEntity tuner && !tuner.mayUse(player)
                && !(player.isCreative() && com.mrgregles.bsp_core.admin.Admins.isAdmin(player))) {
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** No mob breaks it: the Wither, the dragon and any mob that asks the block first. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof CompassTunerBlockEntity tuner) {
            tuner.onBroken(sl);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof CompassTunerBlockEntity tuner) {
            if (tuner.getOwner() == null) {
                tuner.setOwner(sp); // placed by a command or a machine: the first player to use it owns it
            }
            if (!tuner.mayUse(sp)) {
                sp.displayClientMessage(Component.translatable("message.bsp_core.compass_tuner.not_yours", tuner.getOwnerName()).withStyle(ChatFormatting.RED), true);
            } else {
                net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new CompassTunerMenu(id, inv, tuner), Component.translatable("block.bsp_core.compass_tuner")), pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
