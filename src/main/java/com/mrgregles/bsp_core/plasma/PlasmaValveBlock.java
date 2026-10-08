package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.network.BSPNetwork;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Plasma Valve ("Gate Wheel"): sits in a cable run along one axis and limits what passes, set from its screen.
 * A redstone signal shuts it (a lever on it, or any wire), unless the owner turns redstone control off on the screen.
 * It passes powers unchanged and counts as one cable of reach; it does not start a fresh run. The wrench turns its axis.
 */
public class PlasmaValveBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape X = Block.box(0, 3.5, 3.5, 16, 15.5, 12.5), Y = Block.box(0.5, 0, 3.5, 12.5, 16, 12.5), Z = Block.box(3.5, 3.5, 0, 12.5, 15.5, 16);

    public PlasmaValveBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(2.5F, 6.0F).sound(SoundType.COPPER).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(AXIS, ctx.getNearestLookingDirection().getAxis()).setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    /** True when a cable in direction {@code dir} from the valve meets one of its two ends. */
    public static boolean joins(BlockState state, Direction dir) {
        return state.getValue(AXIS) == dir.getAxis();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), 3);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof PlasmaValveBlockEntity valve) {
            if (!valve.mayUse(player)) {
                player.displayClientMessage(Component.translatable("message.bsp_core.access.machines").withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            BSPNetwork.sendTo(sp, valve.view());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            default -> Z;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaValveBlockEntity(pos, state);
    }

}
