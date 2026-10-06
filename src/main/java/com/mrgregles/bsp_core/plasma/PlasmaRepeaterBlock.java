package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Plasma Repeater ("Inline Coil"): sits in a cable run and pumps plasma one way, in through its
 * back and out through its lit front, starting a fresh run. Placed pointing the way you look;
 * right-click with an empty hand turns it round. Its cost is worked out by the interface feeding the projector.
 */
public class PlasmaRepeaterBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape X = Block.box(0, 3.5, 3.5, 16, 12.5, 12.5), Y = Block.box(3.5, 0, 3.5, 12.5, 16, 12.5), Z = Block.box(3.5, 3.5, 0, 12.5, 12.5, 16);

    public PlasmaRepeaterBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(2.5F, 6.0F).sound(SoundType.COPPER).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection());
    }

    /** True when a cable in direction {@code dir} from the repeater meets one of its two ends. */
    public static boolean joins(BlockState state, Direction dir) {
        return state.getValue(FACING).getAxis() == dir.getAxis();
    }

    /** The side plasma comes in. */
    public static Direction input(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** The side plasma goes out. */
    public static Direction output(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getOpposite()), 3);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.repeater.turned"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaRepeaterBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }
}
