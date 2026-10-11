package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Market Stall: a two-block shop front in one of eight skins. An admin places it (vanilla skin,
 * broken) and sneaks and right-clicks it empty-handed for the setup screen: the shop, the opening
 * hours and the skin. While no shop is assigned the stall stands broken and out of order. Players
 * right-click either half for the shop. The lower half holds the block entity and the whole model
 * and powers the block beneath (strong, 15) while the shop is open; the upper half only keeps the
 * space. Admin-only; cannot be mined, pushed or blown up; breaking either half as an admin removes both.
 */
public class MarketStallBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<StallSkin> SKIN = EnumProperty.create("skin", StallSkin.class);
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    public MarketStallBlock() {
        super(Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .lightLevel(s -> s.getValue(HALF) == DoubleBlockHalf.LOWER && !s.getValue(BROKEN) && s.getValue(SKIN).lit ? 7 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SKIN, StallSkin.VANILLA).setValue(BROKEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SKIN, BROKEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The lower half's position for either half. */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new MarketStallBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER ? null
                : createTickerHelper(type, com.mrgregles.bsp_core.registry.ModBlockEntities.MARKET_STALL.get(),
                (l, p, s, be) -> MarketStallBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) l, p, s, be));
    }

    // ------------------------------------------------------------------ two halves

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean other = half == DoubleBlockHalf.LOWER ? dir == Direction.UP : dir == Direction.DOWN;
        if (other && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }

    /** Even in creative mode, only an admin removes it; then both halves go. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!Admins.isAdmin(player)) {
            return false;
        }
        BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState other = level.getBlockState(otherPos);
        if (other.is(this)) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Sets a property on both halves (skin, broken), keeping the block entity. */
    public static void setBoth(Level level, BlockPos lower, java.util.function.UnaryOperator<BlockState> change) {
        for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MarketStallBlock) {
                BlockState n = change.apply(s);
                if (n != s) {
                    level.setBlock(p, n, 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ redstone: the block beneath the lower half

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && side == Direction.UP
                && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall && stall.open ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(lower(state, pos)) instanceof MarketStallBlockEntity stall) {
            use(sp, stall);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Staff with an empty hand while sneaking (or an unassigned stall) get the setup screen, everyone else the shop. */
    public static void use(ServerPlayer sp, MarketStallBlockEntity stall) {
        boolean staff = Admins.isAdmin(sp) || Admins.isModerator(sp);
        Shop shop = stall.assigned() ? Shops.ledger(sp.server).shop(stall.shopId) : null;
        if (shop == null || (staff && sp.getMainHandItem().isEmpty() && sp.isShiftKeyDown())) {
            if (staff) {
                VendorMenu.open(sp, stall);
            } else {
                sp.displayClientMessage(Component.translatable("message.bsp_core.vendor.no_shop").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        ShopOpener.open(sp, shop, stall.getBlockPos(), false, shop.effectiveHours(stall.hours));
    }
}
