package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Refined Storage's fluid grids fill a bucket from the player's inventory: refused while locked. Only with Refined Storage. */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.apiimpl.network.grid.handler.FluidGridHandler", remap = false)
public abstract class LinkRsFluidGridMixin {
    @Inject(method = "onExtract", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedExtract(ServerPlayer player, UUID id, boolean shift, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }
}
