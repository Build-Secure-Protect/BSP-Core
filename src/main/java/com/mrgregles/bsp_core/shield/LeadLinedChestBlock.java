package com.mrgregles.bsp_core.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * The Lead-Lined Chest: a chest whose contents never radiate (the rule is on {@link LeadLinedChestBlockEntity}). Shaped, mined and
 * blown up like a vanilla chest (the same hardness and blast resistance, a pickaxe instead of an axe), so it changes nothing for raids.
 * Breaking it spills the contents. Comparators read how full it is (the whole double chest when joined).
 *
 * <p>Two placed side by side facing the same way join into a double chest exactly as vanilla chests do ({@link #TYPE}, the same
 * placement and neighbour rules). In the joined handler the RIGHT half's slots come first, as vanilla's {@code ChestBlock.getBlockType}.
 */
public class LeadLinedChestBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public LeadLinedChestBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, ChestType.SINGLE).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE, WATERLOGGED);
    }

    /** Vanilla's rule: a plain placement joins a single neighbour facing the same way; sneaking against a chest's side joins that one; sneaking otherwise stays single. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        ChestType type = ChestType.SINGLE;
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        boolean sneaking = ctx.isSecondaryUseActive();
        Direction clicked = ctx.getClickedFace();
        if (clicked.getAxis().isHorizontal() && sneaking) {
            Direction partner = partnerFacing(ctx, clicked.getOpposite());
            if (partner != null && partner.getAxis() != clicked.getAxis()) {
                facing = partner;
                type = partner.getCounterClockWise() == clicked.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }
        if (type == ChestType.SINGLE && !sneaking) {
            if (facing == partnerFacing(ctx, facing.getClockWise())) {
                type = ChestType.LEFT;
            } else if (facing == partnerFacing(ctx, facing.getCounterClockWise())) {
                type = ChestType.RIGHT;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(TYPE, type)
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER);
    }

    /**
     * The facing of a single lead-lined chest on side {@code side} of the placement, or null. Null also when the placing player may not
     * open that chest (Region Guard's chest access): joining would let them open it from their own half, outside the claim.
     */
    @Nullable
    private Direction partnerFacing(BlockPlaceContext ctx, Direction side) {
        BlockPos at = ctx.getClickedPos().relative(side);
        BlockState near = ctx.getLevel().getBlockState(at);
        if (!near.is(this) || near.getValue(TYPE) != ChestType.SINGLE) {
            return null;
        }
        if (ctx.getLevel() instanceof net.minecraft.server.level.ServerLevel sl
                && !com.mrgregles.bsp_core.compat.regionguard.RegionGuardZones.mayOpenChest(sl, at, ctx.getPlayer())) {
            return null;
        }
        return near.getValue(FACING);
    }

    /** Which side the other half of a double chest is on (meaningless for a single chest). */
    public static Direction connectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    /** The other half of the double chest at {@code pos}, or null when it is single or the other half does not match it. */
    @Nullable
    public static LeadLinedChestBlockEntity partner(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(TYPE) || state.getValue(TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos other = pos.relative(connectedDirection(state));
        BlockState os = level.getBlockState(other);
        if (os.getBlock() != state.getBlock() || os.getValue(TYPE) != state.getValue(TYPE).getOpposite() || os.getValue(FACING) != state.getValue(FACING)) {
            return null;
        }
        return level.getBlockEntity(other) instanceof LeadLinedChestBlockEntity be ? be : null;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState near, LevelAccessor level, BlockPos pos, BlockPos nearPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (near.is(this) && dir.getAxis().isHorizontal()) {
            ChestType nearType = near.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE && nearType != ChestType.SINGLE && state.getValue(FACING) == near.getValue(FACING)
                    && connectedDirection(near) == dir.getOpposite()) {
                return state.setValue(TYPE, nearType.getOpposite()); // the chest placed beside this one joined it
            }
        } else if (state.getValue(TYPE) != ChestType.SINGLE && connectedDirection(state) == dir) {
            return state.setValue(TYPE, ChestType.SINGLE); // the other half is gone
        }
        return super.updateShape(state, dir, near, level, pos, nearPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state.rotate(mirror.getRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? turned : turned.setValue(TYPE, turned.getValue(TYPE).getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LeadLinedChestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide
                ? (lvl, pos, st, be) -> {
                    if (be instanceof LeadLinedChestBlockEntity chest) {
                        chest.clientTick();
                    }
                }
                : (lvl, pos, st, be) -> {
                    if (be instanceof LeadLinedChestBlockEntity chest) {
                        chest.serverTick();
                    }
                };
    }

    /** The body is the block model; {@link com.mrgregles.bsp_core.client.LeadLinedChestRenderer} draws the lid, its light and the gauge's needle. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof LeadLinedChestBlockEntity chest) {
            int rows = chest.handler().getSlots() / 9;
            NetworkHooks.openScreen(sp, chest, buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(rows);
            });
            sp.awardStat(Stats.CUSTOM.get(Stats.OPEN_CHEST));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof LeadLinedChestBlockEntity chest) {
                chest.dropContents(level, pos); // only this half's 27: the other half keeps its own
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LeadLinedChestBlockEntity chest ? ItemHandlerHelper.calcRedstoneFromInventory(chest.handler()) : 0;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
