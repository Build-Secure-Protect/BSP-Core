package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Vendor Post: BSP-Core's own vendor. An admin places it and right-clicks it to assign a shop,
 * opening hours and the mob that stands on it. Players right-click it (or the mob) to open the
 * shop. While the shop is open the post powers the block beneath (strong, level 15) for signs and
 * other cosmetics. Admin-only, cannot be mined, pushed or blown up.
 */
public class VendorPostBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public VendorPostBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(OPEN) ? 7 : 2));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VendorPostBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, com.mrgregles.bsp_core.registry.ModBlockEntities.VENDOR_POST.get(), (l, p, s, be) -> VendorPostBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) l, p, s, be));
    }

    // ------------------------------------------------------------------ redstone: the block beneath

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(OPEN) && side == Direction.UP ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return Admins.isAdmin(player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof VendorPostBlockEntity post) {
            use(sp, post);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shared by the post and the figure on it: admins with an empty hand (or an unassigned post) get the assignment screen, everyone else the shop. */
    public static void use(ServerPlayer sp, VendorPostBlockEntity post) {
        boolean staff = Admins.isAdmin(sp) || Admins.isModerator(sp);
        Shop shop = post.assigned() ? Shops.ledger(sp.server).shop(post.shopId) : null;
        if (shop == null || (staff && sp.getMainHandItem().isEmpty() && sp.isShiftKeyDown())) {
            if (staff) {
                VendorMenu.open(sp, post);
            } else {
                sp.displayClientMessage(Component.translatable("message.bsp_core.vendor.no_shop").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        ShopOpener.open(sp, shop, post.getBlockPos(), false, post.hours);
    }
}
