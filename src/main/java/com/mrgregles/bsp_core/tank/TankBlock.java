package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The three blocks of the Plasma Tank: Tank Casing for the edges, Tank Glass to close the faces, Tank Ports where cables meet it.
 * A hollow box of them, 3 to 12 blocks a side, forms by itself when the last block goes in ({@link TankStructure}); every block
 * then knows the master (the lowest corner), which holds the plasma. Right-click any block for the tank's screen.
 */
public class TankBlock extends Block implements EntityBlock {
    public enum Part { CASING, GLASS, PORT }

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public final Part part;

    public TankBlock(Part part) {
        super(part == Part.GLASS
                ? Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F, 6.0F).sound(SoundType.GLASS).noOcclusion().isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false)
                : Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Glass against glass draws no face between, so a wall of Tank Glass is one sheet. */
    @Override
    public boolean skipRendering(BlockState state, BlockState neighbour, Direction dir) {
        return part == Part.GLASS && neighbour.getBlock() instanceof TankBlock other && other.part == Part.GLASS || super.skipRendering(state, neighbour, dir);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this) && level instanceof ServerLevel sl) {
            TankStructure.tryForm(sl, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            TankStructure.broken(sl, pos, state);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof com.mrgregles.bsp_core.plasma.WrenchItem) {
            return InteractionResult.PASS; // the wrench sets a port's direction
        }
        if (player instanceof ServerPlayer sp) {
            TankStructure.sendView(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return part == Part.PORT ? new TankPortBlockEntity(pos, state) : new TankPartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (part == Part.PORT && type == ModBlockEntities.TANK_PORT.get()) {
            return (lvl, p, st, be) -> ((TankPortBlockEntity) be).serverTick((ServerLevel) lvl);
        }
        if (part != Part.PORT && type == ModBlockEntities.TANK_PART.get()) {
            return (lvl, p, st, be) -> ((TankPartBlockEntity) be).serverTick((ServerLevel) lvl);
        }
        return null;
    }
}
