package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Trade Post, the "Exchange Desk": two blocks wide, two tall, one deep, turned to face the player who places it. The
 * lower block on the right as you face it is the controller (it holds the block entity and draws the whole desk); the
 * column on your left is its clockwise neighbour ({@code wing}). Placed by admins only (creative, an
 * {@link com.mrgregles.bsp_core.admin.AdminBlockItem}); players cannot mine, push or blow it up; breaking any part as an admin
 * removes all four and ends the trades of everyone using it. Right-clicking a labelled key on the deck (MARKET, SELL,
 * COLLECT, TRADE) opens the screen on that page; anywhere else opens MARKET.
 */
public class TradePostBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty WING = BooleanProperty.create("wing");

    /** A page of the Trade Post screen; the keys' x span on the deck, in model pixels (as tools/gen_trade_post_assets.py). */
    public enum Tab {
        MARKET(23.0, 28.6), SELL(16.3, 21.9), COLLECT(9.6, 15.2), TRADE(2.9, 8.5);

        public final double x0, x1;

        Tab(double x0, double x1) {
            this.x0 = x0;
            this.x1 = x1;
        }
    }

    /** What a click opens: the Trade Post screen on that page (GameTests may swap it). */
    private static BiConsumer<ServerPlayer, Opening> opener = TradePostMenu::open;

    /** A click on a Trade Post: the controller's place and the page. */
    public record Opening(GlobalPos post, Tab tab) {}

    public static void useOpener(BiConsumer<ServerPlayer, Opening> o) {
        opener = o;
    }

    public TradePostBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> 7));
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
        return isController(state) ? new TradePostBlockEntity(pos, state) : null;
    }

    // ------------------------------------------------------------------ four parts

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return roomFor(ctx.getLevel(), ctx.getClickedPos(), facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    /** Whether a post facing {@code facing} with its controller at {@code controller} has its other three places free. */
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

    /** A part whose vertical or side neighbour in the post has gone goes too, so a post is never left half standing. */
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

    /** Even in creative mode only an admin removes it; then all four parts go and everyone trading at it is let go. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!Admins.isAdmin(player)) {
            return false;
        }
        removePost(level, controller(state, pos), state.getValue(FACING), pos);
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Removes every part but {@code keep} (the one the caller breaks) and ends the trades of the players using this post. */
    public static void removePost(Level level, BlockPos controller, Direction facing, @Nullable BlockPos keep) {
        if (!level.isClientSide) {
            Trades.hub().postBroken(GlobalPos.of(level.dimension(), controller));
        }
        for (BlockPos p : parts(controller, facing)) {
            if (!p.equals(keep) && level.getBlockState(p).getBlock() instanceof TradePostBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            BlockPos c = controller(state, pos);
            opener.accept(sp, new Opening(GlobalPos.of(level.dimension(), c), tabAt(state.getValue(FACING), c, hit.getLocation())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The page a click opens: a key on the deck, else MARKET. {@code hit} is in world coordinates. */
    public static Tab tabAt(Direction facing, BlockPos controller, Vec3 hit) {
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
        // the deck runs from the front edge (y 8.2 to 9.7) up toward the screen (about y 11.8 at z 5)
        if (wy < 8 || wy > 12.5 || mz < -0.5 || mz > 5.5) {
            return Tab.MARKET;
        }
        for (Tab t : Tab.values()) {
            if (mx >= t.x0 - 0.3 && mx <= t.x1 + 0.3) {
                return t;
            }
        }
        return Tab.MARKET;
    }
}
