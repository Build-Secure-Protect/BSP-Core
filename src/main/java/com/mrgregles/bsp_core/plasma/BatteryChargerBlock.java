package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * The Battery Charger ("Open Cradle"): fed by a cable into its back, it fills the battery or cell standing in it and stamps
 * powers into it. Placed with its open front toward the player; the wrench turns it.
 */
public class BatteryChargerBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final double[][] BOXES = {{0, 0, 0, 16, 2, 16}, {0, 2, 12, 16, 16, 16}, {0, 2, 4, 3, 11, 16}, {13, 2, 4, 16, 11, 16}, {3, 13, 8, 13, 15, 14}};
    private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(net.minecraft.core.Direction.class);

    static {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES) {
                double[] a = turned(b, d);
                shape = Shapes.or(shape, Block.box(a[0], a[1], a[2], a[3], a[4], a[5]));
            }
            SHAPES.put(d, shape);
        }
    }

    /** The model faces north; a box turned to face {@code d} about the block's centre, as the blockstate turns the model. */
    private static double[] turned(double[] b, net.minecraft.core.Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (d) {
            case EAST -> new double[]{16 - z1, b[1], x0, 16 - z0, b[4], x1};
            case SOUTH -> new double[]{16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0};
            case WEST -> new double[]{z0, b[1], 16 - x1, z1, b[4], 16 - x0};
            default -> b.clone();
        };
    }

    public BatteryChargerBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); // the open front toward the player, the back away
    }

    /** The side the cable plugs into: the back. */
    public static net.minecraft.core.Direction back(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** True for a cable that reaches the charger by moving in direction {@code dir}: only from behind, so moving the way the charger faces. */
    public static boolean joins(BlockState state, net.minecraft.core.Direction dir) {
        return dir == state.getValue(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(net.minecraft.core.Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.BATTERY_CHARGER.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            return InteractionResult.PASS; // the wrench turns it instead
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) {
            if (!charger.mayUse(player)) {
                player.displayClientMessage(Component.translatable("message.bsp_core.access.machines").withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            NetworkHooks.openScreen(sp, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("block.bsp_core.battery_charger");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BatteryChargerMenu(id, inv, charger);
                }
            }, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), charger.getItems().getStackInSlot(0));
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
