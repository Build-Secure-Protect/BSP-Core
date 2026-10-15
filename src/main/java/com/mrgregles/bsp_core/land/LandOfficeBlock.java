package com.mrgregles.bsp_core.land;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Land Office: where players buy plots and open ground from BSP Region Guard, give claims up
 * and enlarge them, on a map of the land around them. Admin-only, placed from the creative menu;
 * cannot be mined, pushed or blown up. Right-click opens the office with a page-turn sound; a ground
 * outlined with Region Guard's wand opens it by itself, through {@code api.BspLandOffice}. The look is
 * the Deeds Board: a notice board on two posts with the land map, pinned deed cards, a shelf with a
 * bell and a stamp, and a lamp above.
 */
public class LandOfficeBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public LandOfficeBlock() {
        super(Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> 5));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return Admins.isAdmin(player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandOfficeBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 1.0f);
            LandOfficeMenu.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
