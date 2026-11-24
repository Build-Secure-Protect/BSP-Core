package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.RemoteOperate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Machine tracking's remote operation: a screen opened from the Holo Grid through a Remote Control Transmitter stays open while the
 * transmitter's rules hold ({@link RemoteOperate#stillValid}), however far the player is from the machine. Every other screen keeps
 * Minecraft's own check. {@code require = 0}: if another mod changed this call, the screen closes as it would without BSP-Core.
 */
@Mixin(ServerPlayer.class)
public abstract class RemoteMenuServerPlayerMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"), require = 0)
    private boolean bsp_core$remoteMenuValid(AbstractContainerMenu menu, Player player) {
        Boolean remote = RemoteOperate.stillValid((ServerPlayer) (Object) this, menu);
        return remote != null ? remote : menu.stillValid(player);
    }
}
