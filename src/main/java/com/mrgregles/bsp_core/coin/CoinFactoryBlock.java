package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Controller of one Shatter Coin Factory slice: the bottom front block of a structure 1 wide, 2 high
 * and 3 long. Slices built side by side, facing the same way and owned by the same player, run as
 * one machine. While its slice is complete the controller's own cube is hidden and the renderer
 * draws the whole slice.
 */
public class CoinFactoryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CoinFactoryBlock() {
        super(Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(StructurePartBlock.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StructurePartBlock.FORMED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(StructurePartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoinFactoryBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CoinFactoryBlockEntity factory) {
            factory.setOwner(player);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // sneak + right-click on an unfinished slice opens the Assembly Guide
        if (player.isShiftKeyDown() && !state.getValue(StructurePartBlock.FORMED)) {
            if (level.isClientSide) {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.mrgregles.bsp_core.client.AssemblyGuideScreen.open(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CoinFactoryBlockEntity factory) {
            factory.checkStructure();
            if (!factory.isFormed()) {
                serverPlayer.displayClientMessage(Component.translatable("message.bsp_core.factory.incomplete", factory.missingParts().size()).withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            List<CoinFactoryBlockEntity> slices = factory.group();
            long now = System.currentTimeMillis();
            slices.forEach(s -> s.advance(now));
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new CoinFactoryMenu(id, inv, factory, slices),
                            Component.translatable("block.bsp_core.shatter_coin_factory")),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(slices.size());
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CoinFactoryBlockEntity factory) {
                factory.showParts(true); // the slice is gone: its blocks become ordinary cubes again
                for (int i = 0; i < factory.getItems().getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), factory.getItems().getStackInSlot(i));
                }
            }
            if (level instanceof ServerLevel serverLevel) {
                FactoryLedger.get(serverLevel.getServer()).remove(GlobalPos.of(serverLevel.dimension(), pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.COIN_FACTORY.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }
}
