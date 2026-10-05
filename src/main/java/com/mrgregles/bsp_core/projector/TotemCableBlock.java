package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Totem Cable: carries a Totem Generator's powers to a Totem Projector. It joins to other Totem
 * Cables of any kind, to the sides and bottom of a generator, and to any face of a projector. Each
 * kind has a longest run ({@code projector.cableReach}); a run of mixed kinds is limited by the
 * shortest-reach kind in it.
 */
public class TotemCableBlock extends Block {
    public enum Kind {
        TETRIUM, MAGNATITE, ILLYRIUM, CHARGED_ILLYRIUM;

        /** The longest run, in cable blocks, this kind can carry a totem's powers over. */
        public int reach() {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CABLE_REACH, List.<Integer>of()), ordinal() + 1, new int[]{15, 25, 40, 80}[ordinal()]);
        }
    }

    public static final BooleanProperty[] SIDES = {BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.EAST}; // indexed by Direction.get3DDataValue()
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = {Block.box(5, 0, 5, 11, 5, 11), Block.box(5, 11, 5, 11, 16, 11), Block.box(5, 5, 0, 11, 11, 5), Block.box(5, 5, 11, 11, 11, 16),
            Block.box(0, 5, 5, 5, 11, 11), Block.box(11, 5, 5, 16, 11, 11)};

    public final Kind kind;

    public TotemCableBlock(Kind kind) {
        super(Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES);
    }

    /** Whether a cable at {@code pos} joins to whatever is in direction {@code dir}. */
    public static boolean joins(BlockGetter level, BlockPos pos, Direction dir) {
        Block other = level.getBlockState(pos.relative(dir)).getBlock();
        if (other instanceof TotemCableBlock || other instanceof TotemProjectorBlock) {
            return true;
        }
        return other instanceof TotemGeneratorBlock && dir != Direction.DOWN; // a generator takes cables on its sides and bottom, never its top
    }

    private BlockState connect(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(SIDES[dir.get3DDataValue()], joins(level, pos, dir));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(SIDES[dir.get3DDataValue()], joins(level, pos, dir));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        for (int i = 0; i < 6; i++) {
            if (state.getValue(SIDES[i])) {
                shape = Shapes.or(shape, ARMS[i]);
            }
        }
        return shape;
    }
}
