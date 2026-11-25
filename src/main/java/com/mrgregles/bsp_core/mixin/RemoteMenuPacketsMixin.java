package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import com.mrgregles.bsp_core.tracking.RemoteOperate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Clicks in a screen opened from the Holo Grid: Minecraft refuses a slot click, a button click or a recipe placement in a menu that is
 * no longer valid, which it judges by distance. For a Holo Grid screen {@link RemoteOperate#stillValid} answers instead. In a screen
 * locked without the Inventory Link ({@link InventoryLink}), a slot click that would reach the player's items is not made (the client is
 * then sent the true slots), the recipe book, which fills from the player's inventory, places nothing, and what a menu button hands over
 * is put back. These three hooks are required: without them the lock would silently not exist.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RemoteMenuPacketsMixin {
    @Shadow
    public ServerPlayer player;

    @Redirect(method = {"handleContainerClick", "handleContainerButtonClick"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"), require = 0)
    private boolean bsp_core$remoteMenuValid(AbstractContainerMenu menu, Player p) {
        Boolean remote = RemoteOperate.stillValid(this.player, menu);
        return remote != null ? remote : menu.stillValid(p);
    }

    @Redirect(method = "handlePlaceRecipe",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean bsp_core$remoteRecipeValid(AbstractContainerMenu menu, Player p) {
        Boolean remote = RemoteOperate.stillValid(this.player, menu);
        boolean valid = remote != null ? remote : menu.stillValid(p);
        return valid && !InventoryLink.locked(menu);
    }

    @Redirect(method = "handleContainerClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
    private void bsp_core$lockedClick(AbstractContainerMenu menu, int slot, int button, ClickType type, Player p) {
        if (InventoryLink.allowClick(menu, slot, button, type)) {
            menu.clicked(slot, button, type, p);
        }
    }

    /** A menu button in a locked screen: the lectern's "take book" is refused; anything another button hands over is put back after it. */
    @Redirect(method = "handleContainerButtonClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"))
    private boolean bsp_core$lockedButton(AbstractContainerMenu menu, Player p, int id) {
        if (InventoryLink.locked(menu) && menu instanceof net.minecraft.world.inventory.LecternMenu && id == 3) {
            return false;
        }
        boolean done = menu.clickMenuButton(p, id);
        InventoryLink.afterButton(menu);
        return done;
    }
}
