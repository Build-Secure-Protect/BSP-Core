package com.mrgregles.bsp_core.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the server's entry across a Velocity server switch made with Forge Client Reset Packet (mod id {@code clientresetpacket}).
 * On a switch its {@code ClientReset.handleReset} calls {@code handleClear}, which clears the level on the main thread; with the
 * player gone {@link Minecraft#getCurrentServer()} (read from the player's connection in 1.20.1) answers null, and handleReset then
 * reads that null and gives it to the new {@code ClientHandshakePacketListenerImpl}, so the whole next server is played with no
 * server entry. Distant Horizons takes "no server entry and no single-player server" for a Replay Mod recording and files the
 * LODs under {@code Distant_Horizons_server_data/REPLAY} with a "Replay detected" message.
 * Here the entry is read before the clear and handed back where handleReset stores its null. The mod is optional: without it this
 * mixin is skipped ({@code @Pseudo}), and the injectors may find nothing ({@code require = 0}) so a changed version cannot crash
 * the game.
 */
@Pseudo
@Mixin(targets = "gg.chaldea.client.reset.packet.ClientReset", remap = false)
public abstract class ClientResetServerDataMixin {
    @Unique
    private static volatile ServerData bsp_core$savedServer;

    @Inject(method = "handleClear", at = @At("HEAD"), require = 0)
    private static void bsp_core$saveServer(NetworkEvent.Context ctx, CallbackInfoReturnable<Boolean> cir) {
        bsp_core$savedServer = Minecraft.getInstance().getCurrentServer();
    }

    @ModifyVariable(method = "handleReset", at = @At("STORE"), ordinal = 0, require = 0)
    private static ServerData bsp_core$restoreServer(ServerData current) {
        ServerData saved = bsp_core$savedServer;
        bsp_core$savedServer = null;
        return current == null && saved != null ? saved : current;
    }
}
