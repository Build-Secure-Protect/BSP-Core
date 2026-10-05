package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ZoneOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Anti Totem block: an admin-only block that keeps Shatter Totems and chosen BSP blocks out of
 * a box around it (see {@link ZoneLedger}). Only an admin can place it, open its settings or
 * remove it; it cannot be mined, pushed or blown up. Things that were in the zone before it was
 * set up are left alone.
 */
public class AntiTotemBlock extends BaseEntityBlock {
    public AntiTotemBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> 7));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntiTotemBlockEntity(pos, state);
    }

    /** Even in creative mode, only an admin removes it. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return Admins.isAdmin(player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            ZoneLedger.get(sl.getServer()).remove(GlobalPos.of(sl.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && Admins.isAdmin(sp)) {
            BSPNetwork.sendTo(sp, new ZoneOpenPacket(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
