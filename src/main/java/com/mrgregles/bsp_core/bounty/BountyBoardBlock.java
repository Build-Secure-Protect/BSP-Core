package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Bounty Board, the "Frontier Notice Board" (owner's pick, 2026-11-18): two blocks wide, two tall, one deep, turned to face the
 * player who places it. Players craft it and place it in their own bases. As with the Trade Post, the lower block on the right as you
 * face it is the controller (it holds the block entity and draws the whole board); the column on your left is its clockwise
 * neighbour ({@code wing}). It breaks like wood (an axe is quickest) and drops itself once, whichever part is broken; an explosion
 * that reaches it drops it once too. Who may break it is the claim mod's call. Right-clicking a labelled key on the shelf (BOARD,
 * PLACE, HUNTS) opens the screen on that page; anywhere else opens BOARD.
 */
public class BountyBoardBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty WING = BooleanProperty.create("wing");

    /** A page of the Bounty Board screen; the keys' x span on the shelf, in model pixels (as tools/gen_bounty_board_assets.py). */
    public enum Key {
        BOARD(21.0, 27.0), PLACE(13.0, 19.0), HUNTS(5.0, 11.0);

        public final double x0, x1;

        Key(double x0, double x1) {
            this.x0 = x0;
            this.x1 = x1;
        }
    }

    /** What a click opens: the Bounty Board screen on that page (GameTests may swap it). */
    private static BiConsumer<ServerPlayer, Opening> opener = BountyScreens::open;

    /** A click on a Bounty Board: the controller's place and the page. */
    public record Opening(GlobalPos board, Key key) {}

    public static void useOpener(BiConsumer<ServerPlayer, Opening> o) {
        opener = o;
    }

    // the outline and collision box of each part, facing north: the lower parts reach forward to the shelf, the upper ones are the boards
    private static final VoxelShape[] LOWER = shapes(2, 10), UPPER = shapes(5, 10);

    /** The box from z0 to z1 (model px, front at z 0 facing north) for each facing, in Direction's horizontal order (S, W, N, E). */
    private static VoxelShape[] shapes(double z0, double z1) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out[d.get2DDataValue()] = switch (d) {
                case SOUTH -> Block.box(0, 0, 16 - z1, 16, 16, 16 - z0);
                case EAST -> Block.box(16 - z1, 0, 0, 16 - z0, 16, 16);
                case WEST -> Block.box(z0, 0, 0, z1, 16, 16);
                default -> Block.box(0, 0, z0, 16, 16, z1);
            };
        }
        return out;
    }

    public BountyBoardBlock() {
        super(Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 3.0F).sound(SoundType.WOOD).noOcclusion().noLootTable()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(HALF) == DoubleBlockHalf.UPPER ? 0 : 7));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(WING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, WING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER)[state.getValue(FACING).get2DDataValue()];
    }

    public static boolean isController(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && !state.getValue(WING);
    }

    /** The controller's position for any part. */
    public static BlockPos controller(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(WING) ? p.relative(state.getValue(FACING).getCounterClockWise()) : p;
    }

    /** The four parts' positions, the controller first. */
    public static List<BlockPos> parts(BlockPos controller, Direction facing) {
        BlockPos wing = controller.relative(facing.getClockWise());
        return List.of(controller, controller.above(), wing, wing.above());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new BountyBoardBlockEntity(pos, state) : null;
    }

    // ------------------------------------------------------------------ four parts

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        if (!roomFor(ctx.getLevel(), ctx.getClickedPos(), facing)) {
            return null;
        }
        // the claim mod is asked about the other three places too, not only the clicked one
        if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide) {
            List<BlockPos> p = parts(ctx.getClickedPos(), facing);
            for (BlockPos part : p.subList(1, p.size())) {
                if (net.minecraftforge.event.ForgeEventFactory.onBlockPlace(ctx.getPlayer(),
                        net.minecraftforge.common.util.BlockSnapshot.create(ctx.getLevel().dimension(), ctx.getLevel(), part), Direction.UP)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Whether a board facing {@code facing} with its controller at {@code controller} has its other three places free. */
    public static boolean roomFor(Level level, BlockPos controller, Direction facing) {
        for (BlockPos p : parts(controller, facing)) {
            if (p.getY() >= level.getMaxBuildHeight() || !p.equals(controller) && !level.getBlockState(p).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        placeParts(level, pos, state.getValue(FACING));
    }

    /** Sets the three parts beside and above a controller (placing, and GameTests). */
    public static void placeParts(Level level, BlockPos controller, Direction facing) {
        BlockState base = level.getBlockState(controller);
        List<BlockPos> p = parts(controller, facing);
        level.setBlock(p.get(1), base.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlock(p.get(2), base.setValue(WING, true), 3);
        level.setBlock(p.get(3), base.setValue(HALF, DoubleBlockHalf.UPPER).setValue(WING, true), 3);
    }

    /** A part whose vertical or side neighbour in the board has gone goes too, so a board is never left half standing. */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        boolean upper = state.getValue(HALF) == DoubleBlockHalf.UPPER, wing = state.getValue(WING);
        Direction facing = state.getValue(FACING);
        Direction vertical = upper ? Direction.DOWN : Direction.UP;
        Direction side = wing ? facing.getCounterClockWise() : facing.getClockWise();
        if (dir == vertical || dir == side) {
            boolean ok = neighbour.is(this) && neighbour.getValue(FACING) == facing
                    && (dir == vertical ? neighbour.getValue(HALF) != state.getValue(HALF) && neighbour.getValue(WING) == wing
                                        : neighbour.getValue(WING) != wing && neighbour.getValue(HALF) == state.getValue(HALF));
            if (!ok) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }

    /** Broken by a player (the claim mod has let them): one Bounty Board drops at the controller unless in creative, and all four parts go. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        BlockPos c = controller(state, pos);
        // the claim mod has allowed the part that was hit; the board only comes down if it allows every other part too
        if (!level.isClientSide) {
            for (BlockPos part : parts(c, state.getValue(FACING))) {
                if (!part.equals(pos) && level.getBlockState(part).getBlock() instanceof BountyBoardBlock
                        && net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, part, level.getBlockState(part), player))) {
                    level.sendBlockUpdated(pos, state, state, 3);
                    return false;
                }
            }
        }
        if (!level.isClientSide && willHarvest && !player.isCreative() && level.getBlockState(c).getBlock() instanceof BountyBoardBlock) {
            popResource(level, c, new ItemStack(ModItems.BOUNTY_BOARD.get()));
        }
        removeBoard(level, c, state.getValue(FACING), pos);
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Blown up: the first part the explosion reaches drops one Bounty Board and takes the other parts with it. */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        BlockPos c = controller(state, pos);
        if (!level.isClientSide && level.getBlockState(c).getBlock() instanceof BountyBoardBlock) {
            popResource(level, c, new ItemStack(ModItems.BOUNTY_BOARD.get()));
            removeBoard(level, c, state.getValue(FACING), pos);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    /** Removes every part but {@code keep} (the one the caller breaks), dropping nothing. */
    public static void removeBoard(Level level, BlockPos controller, Direction facing, @Nullable BlockPos keep) {
        for (BlockPos p : parts(controller, facing)) {
            if (!p.equals(keep) && level.getBlockState(p).getBlock() instanceof BountyBoardBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            BlockPos c = controller(state, pos);
            opener.accept(sp, new Opening(GlobalPos.of(level.dimension(), c), keyAt(state.getValue(FACING), c, hit.getLocation())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The page a click opens: a key on the shelf, else BOARD. {@code hit} is in world coordinates. */
    public static Key keyAt(Direction facing, BlockPos controller, Vec3 hit) {
        double wx = (hit.x - controller.getX()) * 16, wy = (hit.y - controller.getY()) * 16, wz = (hit.z - controller.getZ()) * 16;
        double mx, mz;
        switch (facing) {
            case EAST -> {
                mx = wz;
                mz = 16 - wx;
            }
            case SOUTH -> {
                mx = 16 - wx;
                mz = 16 - wz;
            }
            case WEST -> {
                mx = 16 - wz;
                mz = wx;
            }
            default -> {
                mx = wx;
                mz = wz;
            }
        }
        // the key plaques stand on the shelf's front edge, y 0.6 to 3, their faces at z 2
        if (wy < 0.3 || wy > 3.3 || mz < 1.5 || mz > 3.0) {
            return Key.BOARD;
        }
        for (Key k : Key.values()) {
            if (mx >= k.x0 - 0.3 && mx <= k.x1 + 0.3) {
                return k;
            }
        }
        return Key.BOARD;
    }
}
