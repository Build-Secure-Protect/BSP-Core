package com.mrgregles.bsp_core.mixin.client;

import com.mrgregles.bsp_core.client.tracking.RealScreens;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nothing a machine's real screen sends while it is built or drawn as a Holo Grid picture ({@link RealScreens}) reaches the server: such
 * a screen is no screen the player has open (XNet's controller, for one, asks the server for its lists as it draws). Only on the render
 * thread while a picture is made; every other packet is sent as usual.
 */
@Mixin(Connection.class)
public abstract class StandInQuietMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bspCore$quietSend(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (RealScreens.quiet()) {
            ci.cancel();
        }
    }
}
