package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Plasma Injector: a column placed against a machine (it attaches to the block it is placed on; {@link #FACING} points at that
 * block) with a plasma socket on the far end. Fed by cable it speeds the machine up by the curve in {@link PlasmaBoost}. On a
 * Shatter Coin Factory it stands in one of the three Motivator cells and speeds that slice only. On a BSP-Core machine it is
 * read by the machine; on any other mod's machine it ticks the block extra.
 */
public class PlasmaInjectorBlock extends BaseEntityBlock {
    /** The direction of the machine it serves: the face it was placed against. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape ALONG_Y = Block.box(2, 0, 2, 14, 16, 14), ALONG_X = Block.box(0, 2, 2, 16, 14, 14), ALONG_Z = Block.box(2, 2, 0, 14, 14, 16);

    public PlasmaInjectorBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 7).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    /** Whether a cable reaching the injector by moving in {@code dir} may plug in: only through the port, the face away from the machine. */
    public static boolean joins(BlockState state, Direction dir) {
        return state.hasProperty(FACING) && state.getValue(FACING) == dir;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING).getAxis()) { case X -> ALONG_X; case Y -> ALONG_Y; case Z -> ALONG_Z; };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaInjectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PLASMA_INJECTOR.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.injector").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bsp_core.injector.curve", PlasmaBoost.topRate(), String.format("%.1f", PlasmaBoost.factor(PlasmaBoost.topRate()))).withStyle(ChatFormatting.DARK_GRAY));
    }
}
