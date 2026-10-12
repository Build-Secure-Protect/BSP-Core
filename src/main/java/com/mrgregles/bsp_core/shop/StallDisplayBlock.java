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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Stall Display: an admin-only block that shows a shop's wares floating above it. An admin
 * sneaks and right-clicks it for its screen: pick the shop from the list, pin an item, set the
 * interval, size, hover height and style. Right-clicking it without sneaking opens the shop, so it
 * works as a shop front too. It shows its style's neglected look while nothing floats. Cannot be
 * mined, pushed or blown up.
 */
public class StallDisplayBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The style, chosen on the display's screen. */
    public static final EnumProperty<StallSkin> SKIN = MarketStallBlock.SKIN;
    /** Neglected look while nothing floats: no shop, the shop closed or missing. */
    public static final BooleanProperty BROKEN = MarketStallBlock.BROKEN;

    public StallDisplayBlock() {
        super(Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> !s.getValue(BROKEN) && s.getValue(SKIN).lit ? 5 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SKIN, StallSkin.VANILLA).setValue(BROKEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SKIN, BROKEN);
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
        return new StallDisplayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, com.mrgregles.bsp_core.registry.ModBlockEntities.STALL_DISPLAY.get(),
                (l, p, s, be) -> StallDisplayBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) l, p, s, be));
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return Admins.isAdmin(player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof StallDisplayBlockEntity display)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (Admins.isAdmin(sp) && sp.isShiftKeyDown()) {
            StallDisplayMenu.open(sp, display);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        Shop shop = display.shop(sp.serverLevel());
        if (shop != null) {
            ShopOpener.open(sp, shop, pos, false, shop.hours);
        } else {
            sp.displayClientMessage(Component.translatable("message.bsp_core.display.unlinked").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
