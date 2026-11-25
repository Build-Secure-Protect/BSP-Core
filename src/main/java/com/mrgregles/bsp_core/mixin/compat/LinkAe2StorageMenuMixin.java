package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2's terminals: the actions that move items from the network into the player's inventory (shift-click, scroll, move region, craft
 * into the inventory) are refused in a screen locked without the Inventory Link; picking up onto the cursor still works. Only with AE2.
 */
@Pseudo
@Mixin(targets = "appeng.menu.me.common.MEStorageMenu", remap = false)
public abstract class LinkAe2StorageMenuMixin {
    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedInteraction(long serial, @Coerce Object action, CallbackInfo ci) {
        if (!InventoryLink.allowAe2((AbstractContainerMenu) (Object) this, action)) {
            ci.cancel();
        }
    }
}
