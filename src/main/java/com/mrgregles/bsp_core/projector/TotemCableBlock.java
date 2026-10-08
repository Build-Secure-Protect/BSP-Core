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
public class TotemCableBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public enum Kind {
        TETRIUM, MAGNATITE, ILLYRIUM, CHARGED_ILLYRIUM;

        /** The longest run, in cable blocks, this kind can carry a totem's powers over. */
        public int reach() {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CABLE_REACH, List.<Integer>of()), ordinal() + 1, new int[]{15, 25, 40, 80}[ordinal()]);
        }

        /** The most mB per tick this kind carries; a run is held to its weakest cable. */
        public int throughput() {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CABLE_THROUGHPUT, List.<Integer>of()), ordinal() + 1, new int[]{250, 500, 1000, 1000}[ordinal()]);
        }
    }

    public static final BooleanProperty[] SIDES = {BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.EAST}; // indexed by Direction.get3DDataValue()
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = {Block.box(5, 0, 5, 11, 5, 11), Block.box(5, 11, 5, 11, 16, 11), Block.box(5, 5, 0, 11, 11, 5), Block.box(5, 5, 11, 11, 11, 16),
            Block.box(0, 5, 5, 5, 11, 11), Block.box(11, 5, 5, 16, 11, 11)};

    public final Kind kind;
    /** The dye this cable is, or null for a plain one. A coloured cable joins only its own colour; a plain one joins only plain ones. */
    @javax.annotation.Nullable
    public final net.minecraft.world.item.DyeColor colour;

    public TotemCableBlock(Kind kind) {
        this(kind, null);
    }

    public TotemCableBlock(Kind kind, @javax.annotation.Nullable net.minecraft.world.item.DyeColor colour) {
        super(Properties.of().mapColor(colour == null ? MapColor.METAL : colour.getMapColor()).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        this.colour = colour;
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

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaCableBlockEntity(pos, state);
    }

    /** The setting of the end of the cable at {@code pos} facing {@code dir}; Normal for anything that is not a cable. */
    public static PlasmaCableBlockEntity.End end(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof PlasmaCableBlockEntity cable ? cable.end(dir) : PlasmaCableBlockEntity.End.NORMAL;
    }

    /** Two cables of the same colour (both plain, or both the same dye). */
    public static boolean sameColour(BlockState a, BlockState b) {
        return a.getBlock() instanceof TotemCableBlock x && b.getBlock() instanceof TotemCableBlock y && x.colour == y.colour;
    }

    /** True when the block in direction {@code dir} is a cable of another colour, which the wrench can still Link. */
    public static boolean foreignCable(BlockGetter level, BlockPos pos, Direction dir) {
        BlockState there = level.getBlockState(pos.relative(dir));
        return there.getBlock() instanceof TotemCableBlock && !sameColour(level.getBlockState(pos), there);
    }

    /**
     * Whether plasma may move out of the block at {@code from} into the block at {@code to}, one step in direction {@code dir}:
     * the end it leaves through must let it out, the end it enters through must let it in, and two cables must be the same colour
     * unless one end is set to Link. Blocks that are not cables have Normal ends.
     */
    public static boolean passes(BlockGetter level, BlockPos from, BlockPos to, Direction dir) {
        PlasmaCableBlockEntity.End a = end(level, from, dir), b = end(level, to, dir.getOpposite());
        if (!a.out() || !b.in()) {
            return false;
        }
        BlockState sa = level.getBlockState(from), sb = level.getBlockState(to);
        if (sa.getBlock() instanceof TotemCableBlock && sb.getBlock() instanceof TotemCableBlock) {
            return a == PlasmaCableBlockEntity.End.LINK || b == PlasmaCableBlockEntity.End.LINK || sameColour(sa, sb);
        }
        return true;
    }

    /** Whether a cable at {@code pos} joins to whatever is in direction {@code dir}. */
    public static boolean joins(BlockGetter level, BlockPos pos, Direction dir) {
        BlockState there = level.getBlockState(pos.relative(dir));
        Block other = there.getBlock();
        PlasmaCableBlockEntity.End mine = end(level, pos, dir), theirs = end(level, pos.relative(dir), dir.getOpposite());
        if (mine == PlasmaCableBlockEntity.End.OFF || theirs == PlasmaCableBlockEntity.End.OFF) {
            return false;
        }
        if (other instanceof TotemCableBlock) {
            return mine == PlasmaCableBlockEntity.End.LINK || theirs == PlasmaCableBlockEntity.End.LINK || sameColour(level.getBlockState(pos), there);
        }
        if (other instanceof com.mrgregles.bsp_core.plasma.ProjectorBaseBlock || other instanceof com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock) {
            return true;
        }
        // a repeater or a valve only takes cables at its two ends
        if (other instanceof com.mrgregles.bsp_core.plasma.BatteryChargerBlock) {
            return com.mrgregles.bsp_core.plasma.BatteryChargerBlock.joins(level.getBlockState(pos.relative(dir)), dir);
        }
        if (other instanceof com.mrgregles.bsp_core.plasma.PlasmaValveBlock) {
            return com.mrgregles.bsp_core.plasma.PlasmaValveBlock.joins(level.getBlockState(pos.relative(dir)), dir);
        }
        return other instanceof com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlock && com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlock.joins(level.getBlockState(pos.relative(dir)), dir);
    }

    /** The cable's state with every side worked out afresh, after an end is changed with the wrench. */
    public static BlockState refresh(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TotemCableBlock)) {
            return state;
        }
        for (Direction dir : Direction.values()) {
            state = state.setValue(SIDES[dir.get3DDataValue()], joins(level, pos, dir));
        }
        return state;
    }

    /** A cable picked up with the wrench keeps its end settings on the item; placing it puts them back. */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.getTag() != null && stack.getTag().contains(PlasmaCableBlockEntity.TAG_ENDS) && level.getBlockEntity(pos) instanceof PlasmaCableBlockEntity cable) {
            cable.setEnds(stack.getTag().getByteArray(PlasmaCableBlockEntity.TAG_ENDS));
            level.setBlock(pos, refresh(level, pos), 3);
        }
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @javax.annotation.Nullable BlockGetter level, List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        if (stack.getTag() != null && stack.getTag().contains(PlasmaCableBlockEntity.TAG_ENDS)) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.bsp_core.cable.ends").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
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

    /** Breaking a cable empties the cables around it at once; anything further along drains by itself when no report arrives. */
    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(pos.relative(d)) instanceof PlasmaCableBlockEntity cable) {
                    cable.report(0, null, null);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
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
