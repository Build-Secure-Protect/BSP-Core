package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refined Storage's crafting and pattern grids: clearing the grid, shift-crafting and JEI recipe filling can reach the player's
 * inventory, so they are refused while locked. Only with Refined Storage.
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.apiimpl.network.node.GridNetworkNode", remap = false)
public abstract class LinkRsGridNetworkNodeMixin {
    @Inject(method = "onClear", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedClear(Player player, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onCraftedShift", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedCraftedShift(Player player, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onRecipeTransfer", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedTransfer(Player player, ItemStack[][] recipe, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }
}
