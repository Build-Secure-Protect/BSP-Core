package com.mrgregles.bsp_core.mixin.client;

import com.mrgregles.bsp_core.client.tracking.RealScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While BSP-Core builds a mod's real menu for a Holo Grid box (owner decision MS2), the menu's lookup of its machine at the stand-in's
 * sentinel place (far below the world) finds the stand-in, in the client's world on the render thread. Nothing else is touched.
 */
@Mixin(Level.class)
public abstract class StandInBlockEntityMixin {
    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void bspCore$standIn(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BlockEntity standIn = RealScreens.building();
        // only the client's own world, on the render thread: never the single-player server's world or another thread
        if (standIn != null && RealScreens.SENTINEL.equals(pos) && ((Level) (Object) this).isClientSide()
                && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            cir.setReturnValue(standIn);
        }
    }
}
