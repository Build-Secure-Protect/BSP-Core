package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** JEI's recipe filling takes from the player's inventory: refused in a screen locked without the Inventory Link. Applies only with JEI. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.BasicRecipeTransferHandlerServer", remap = false)
public abstract class LinkJeiMixin {
    @Inject(method = "setItems", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bsp_core$lockedFill(Player player, List<?> ops, List<?> crafting, List<?> inventory, boolean maxTransfer, boolean all, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "setItemsWithResult", at = @At("HEAD"), cancellable = true, require = 0)
    private static void bsp_core$lockedFillResult(Player player, List<?> ops, List<?> crafting, List<?> inventory, boolean maxTransfer, boolean all,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (InventoryLink.locked(player)) {
            cir.setReturnValue(false);
        }
    }
}
