package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.totem.HarvestWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change in a loaded chunk passes through {@code LevelChunk.setBlockState}, which returns the block that was there (null
 * when nothing changed). Harvest listens here rather than on Forge's break event, which only a player's mining fires.
 */
@Mixin(LevelChunk.class)
public abstract class HarvestChunkMixin {
    @Shadow @Final Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void bsp_core$harvestWatch(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old != null) {
            HarvestWatch.changed(level, pos, old, state);
        }
    }
}
