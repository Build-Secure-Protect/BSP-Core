package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Tank Extractor, a Separator Drum: set it against a Tank Port and it sends one of the tank's pools into the cables touching it. The
 * two buttons on its front step through the pools (◀ back, ▶ on); a click anywhere else steps on. The sight glass shows the pool's colour
 * ({@link #POOL}, tinted on the client).
 */
public class TankExtractorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty POOL = IntegerProperty.create("pool", 0, TankExtractorBlockEntity.POOLS - 1);

    public TankExtractorBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POOL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POOL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof TankExtractorBlockEntity be) {
            be.setOwner(player.getUUID()); // the team its plasma's powers know
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** -1 for the ◀ button (the left half of the front as you face it), +1 otherwise. */
    public static int step(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
        Direction front = state.getValue(FACING);
        if (face != front) {
            return 1;
        }
        Direction right = front.getCounterClockWise(); // facing the front, this is on your right
        double along = (hit.x - pos.getX() - 0.5) * right.getStepX() + (hit.z - pos.getZ() - 0.5) * right.getStepZ();
        return along < 0 ? -1 : 1;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TankExtractorBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            be.setPool(be.pool() + step(state, pos, hit.getLocation(), hit.getDirection()));
            Component line = be.attachedTank() == null ? Component.translatable("message.bsp_core.extractor.no_tank").withStyle(ChatFormatting.RED)
                    : Component.translatable("message.bsp_core.extractor.pool", TankExtractorBlockEntity.poolName(be.pool()), String.format("%,d", be.available()));
            player.displayClientMessage(line, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankExtractorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.TANK_EXTRACTOR.get()) {
            return null;
        }
        return (lvl, p, st, be) -> ((TankExtractorBlockEntity) be).serverTick((ServerLevel) lvl);
    }
}
