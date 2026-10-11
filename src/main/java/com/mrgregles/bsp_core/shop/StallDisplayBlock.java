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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Stall Display: an admin-only block that shows a linked shop's wares floating above it. An
 * admin sneaks and right-clicks it for its settings screen (link, unlink, pin an item, interval,
 * size, hover height); Link waits for a right-click on the Vendor Post or Market Stall it should
 * follow (within 32 blocks). Right-clicking it without sneaking opens the linked shop, so it works
 * as a shop front too. It takes the style of the Market Stall it follows (player-built otherwise)
 * and shows that style's neglected look while nothing floats. Cannot be mined, pushed or blown up.
 */
@Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public class StallDisplayBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The style, copied from the Market Stall it follows (player-built with a Vendor Post or unlinked). */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<StallSkin> SKIN = MarketStallBlock.SKIN;
    /** Neglected look while nothing floats: unlinked, the shop closed or missing. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty BROKEN = MarketStallBlock.BROKEN;
    /** Admins who have clicked a display and now choose its post or stall: player -> display position. */
    private static final Map<UUID, BlockPos> LINKING = new HashMap<>();

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
        if (display.linked != null && level.getBlockEntity(display.linked) instanceof VendorPostBlockEntity post) {
            VendorPostBlock.use(sp, post);
        } else if (display.linked != null && level.getBlockEntity(display.linked) instanceof Assignable a && !a.shopId().isEmpty()) {
            Shop shop = Shops.ledger(sp.server).shop(a.shopId());
            if (shop != null) {
                ShopOpener.open(sp, shop, display.linked, false, shop.effectiveHours(a.hours()));
            }
        } else {
            sp.displayClientMessage(Component.translatable("message.bsp_core.display.unlinked").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The second click of the linking flow: an admin who clicked a display right-clicks a post or stall. */
    @SubscribeEvent
    public static void onClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        BlockPos displayPos = LINKING.get(sp.getUUID());
        if (displayPos == null) {
            return;
        }
        if (!(sp.level().getBlockEntity(event.getPos()) instanceof Assignable)) {
            return; // not a post or stall: keep waiting
        }
        LINKING.remove(sp.getUUID());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(sp.level().getBlockEntity(displayPos) instanceof StallDisplayBlockEntity display)) {
            return;
        }
        if (!displayPos.closerThan(event.getPos(), 32)) {
            sp.displayClientMessage(Component.translatable("message.bsp_core.display.too_far").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (display.link(event.getPos())) {
            sp.displayClientMessage(Component.translatable("message.bsp_core.display.linked", event.getPos().toShortString()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** The Link key: the admin's next right-click on a post or stall links this display. */
    public static void startLinking(ServerPlayer sp, StallDisplayBlockEntity display) {
        LINKING.put(sp.getUUID(), display.getBlockPos().immutable());
        sp.displayClientMessage(Component.translatable("message.bsp_core.display.pick").withStyle(ChatFormatting.GOLD), true);
    }
}
