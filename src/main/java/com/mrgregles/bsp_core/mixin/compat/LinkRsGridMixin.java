package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Refined Storage's item grids: shift-click extraction (straight into the inventory) and scroll transfers between the grid and the
 * inventory are refused in a screen locked without the Inventory Link; taking onto the cursor and putting the held item in still work.
 * Only with Refined Storage.
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler", remap = false)
public abstract class LinkRsGridMixin {
    private static final int EXTRACT_SHIFT = 4;

    @Inject(method = "onExtract(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedExtract(ServerPlayer player, UUID id, int preferredSlot, int flags, CallbackInfo ci) {
        if ((flags & EXTRACT_SHIFT) != 0 && InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onExtract(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedExtractStack(ServerPlayer player, ItemStack stack, int preferredSlot, int flags, CallbackInfo ci) {
        if ((flags & EXTRACT_SHIFT) != 0 && InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onInventoryScroll(Lnet/minecraft/server/level/ServerPlayer;IZZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedInventoryScroll(ServerPlayer player, int slot, boolean shift, boolean up, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onGridScroll(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;ZZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedGridScroll(ServerPlayer player, UUID id, boolean shift, boolean up, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onInventoryScroll(Lcom/refinedmods/refinedstorage/api/network/grid/handler/IItemGridHandler;Lnet/minecraft/server/level/ServerPlayer;IZZLcom/refinedmods/refinedstorage/api/network/INetwork;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void bsp_core$lockedInventoryScrollStatic(@Coerce Object handler, ServerPlayer player, int slot, boolean shift, boolean up,
                                                            @Coerce Object network, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onGridScroll(Lcom/refinedmods/refinedstorage/api/network/grid/handler/IItemGridHandler;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;ZZLcom/refinedmods/refinedstorage/api/network/INetwork;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void bsp_core$lockedGridScrollStatic(@Coerce Object handler, ServerPlayer player, UUID id, boolean shift, boolean up,
                                                       @Coerce Object network, CallbackInfo ci) {
        if (InventoryLink.locked(player)) {
            ci.cancel();
        }
    }
}
