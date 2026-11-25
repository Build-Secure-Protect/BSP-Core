package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EMI's recipe filling takes from the player's inventory: refused in a screen locked without the Inventory Link. Applies only with EMI. */
@Pseudo
@Mixin(targets = "dev.emi.emi.network.FillRecipeC2SPacket", remap = false)
public abstract class LinkEmiMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedFill(Player player, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }
}
