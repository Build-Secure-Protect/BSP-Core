package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.RemoteOperate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** As {@link RemoteMenuServerPlayerMixin}, for the same check in {@code Player.tick}, which a server player also runs. */
@Mixin(Player.class)
public abstract class RemoteMenuPlayerMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"), require = 0)
    private boolean bsp_core$remoteMenuValid(AbstractContainerMenu menu, Player player) {
        if ((Object) this instanceof ServerPlayer sp) {
            Boolean remote = RemoteOperate.stillValid(sp, menu);
            if (remote != null) {
                return remote;
            }
        }
        return menu.stillValid(player);
    }
}
