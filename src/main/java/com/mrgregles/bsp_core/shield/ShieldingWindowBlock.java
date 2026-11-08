package com.mrgregles.bsp_core.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Radiation Shielding Window: a thick pane, 6 pixels deep in the middle of its block (B's window in the mock-ups), standing across
 * the way the player faced when placing it ({@link #AXIS} is the way it runs: X runs east to west, thin north to south). Light and sight
 * pass through it. Windows beside, above or below one another in the same plane draw no faces between them, so their glass reads as
 * one sheet. Voltaic reads a block, not its shape: the whole block lets a tenth through.
 */
public class ShieldingWindowBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Pixels 5 to 11 of the block's depth. */
    private static final VoxelShape RUNS_X = Block.box(0, 0, 5, 16, 16, 11), RUNS_Z = Block.box(5, 0, 0, 11, 16, 16);

    public ShieldingWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(TotemCoating.COATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TotemCoating.COATED);
    }

    /** Across the player's view: facing north or south it runs east to west. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.X ? RUNS_X : RUNS_Z;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    /** True when {@code near} is a window in the same plane on side {@code dir}: neither draws the face between them. */
    public static boolean sameSheet(BlockState state, BlockState near, Direction dir) {
        return near.getBlock() == state.getBlock() && near.getValue(AXIS) == state.getValue(AXIS) && dir.getAxis() != thinAxis(state);
    }

    /** The axis the pane is thin along. */
    public static Direction.Axis thinAxis(BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState near, Direction dir) {
        return sameSheet(state, near, dir) || super.skipRendering(state, near, dir);
    }

    /** Mobs do not try to walk through a pane, as with iron bars. */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
