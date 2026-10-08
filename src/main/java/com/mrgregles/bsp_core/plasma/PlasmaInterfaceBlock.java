package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * The Plasma Interface ("Header Rack"): routes plasma from the extractors it touches into the cables
 * plugged into it. Touching interfaces join into one group of up to {@code plasma.interfaceMax}
 * blocks; a block beyond that, or one touching an extractor another group already serves, shows a red seam.
 */
public class PlasmaInterfaceBlock extends BaseEntityBlock {
    public static final BooleanProperty REFUSED = BooleanProperty.create("refused");

    /** What a side joins to: nothing (a plain panel), a Plasma Extractor ("drum" in saved worlds: a tube into its porthole), another interface (the face vanishes), or a cable (a nozzle). */
    public enum Link implements net.minecraft.util.StringRepresentable {
        NONE, DRUM, JOIN, CABLE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Link>[] SIDES = new net.minecraft.world.level.block.state.properties.EnumProperty[6];

    static {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            SIDES[d.get3DDataValue()] = net.minecraft.world.level.block.state.properties.EnumProperty.create(d.getSerializedName(), Link.class);
        }
    }

    private static Link linkTo(net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction d) {
        BlockState other = level.getBlockState(pos.relative(d));
        if (other.getBlock() instanceof PlasmaExtractorBlock) {
            return Link.DRUM;
        }
        if (other.getBlock() instanceof PlasmaInterfaceBlock) {
            return Link.JOIN;
        }
        boolean cable = other.getBlock() instanceof com.mrgregles.bsp_core.projector.TotemCableBlock
                || (other.getBlock() instanceof PlasmaValveBlock && PlasmaValveBlock.joins(other, d.getOpposite()))
                || (other.getBlock() instanceof PlasmaRepeaterBlock && PlasmaRepeaterBlock.joins(other, d.getOpposite()));
        return cable ? Link.CABLE : Link.NONE;
    }

    private BlockState connect(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            state = state.setValue(SIDES[d.get3DDataValue()], linkTo(level, pos, d));
        }
        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return connect(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction dir, BlockState neighbour, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(SIDES[dir.get3DDataValue()], linkTo(level, pos, dir));
    }

    public PlasmaInterfaceBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        BlockState state = stateDefinition.any().setValue(REFUSED, false);
        for (var side : SIDES) {
            state = state.setValue(side, Link.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REFUSED);
        builder.add(SIDES);
    }

    /** Right-click opens the interface's screen: the group in 3D with the flow through every cable. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sendView(sp, pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sends the player the view of the group the interface at {@code pos} belongs to, worked out by its master. */
    public static void sendView(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof PlasmaInterfaceBlockEntity be) {
            BlockPos master = be.master() == null ? pos : be.master();
            PlasmaInterfaceBlockEntity owner = player.level().getBlockEntity(master) instanceof PlasmaInterfaceBlockEntity m ? m : be;
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(player, owner.network().view(player.serverLevel()));
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Faces between two interfaces are not drawn, so a touching group reads as one body. */
    @Override
    public boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction dir) {
        return neighbour.getBlock() instanceof PlasmaInterfaceBlock || super.skipRendering(state, neighbour, dir);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PLASMA_INTERFACE.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }
}
