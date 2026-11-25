package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A screen locked without the Inventory Link closing (or its player leaving): what is held on the cursor goes back into the machine
 * ({@link InventoryLink#beforeRemoved}) before Minecraft would put it into the player's inventory. Every other menu is untouched.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class InventoryLinkMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void bsp_core$lockedRemoved(Player player, CallbackInfo ci) {
        InventoryLink.beforeRemoved((AbstractContainerMenu) (Object) this, player);
    }
}
