package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.RemoteOperate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Clicks in a screen opened from the Holo Grid: Minecraft refuses a slot click, a button click or a recipe placement in a menu that is
 * no longer valid, which it judges by distance. For a Holo Grid screen {@link RemoteOperate#stillValid} answers instead.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RemoteMenuPacketsMixin {
    @Shadow
    public ServerPlayer player;

    @Redirect(method = {"handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"), require = 0)
    private boolean bsp_core$remoteMenuValid(AbstractContainerMenu menu, Player p) {
        Boolean remote = RemoteOperate.stillValid(this.player, menu);
        return remote != null ? remote : menu.stillValid(p);
    }
}
