package com.mrgregles.bsp_core.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The proxy relay (a Velocity plugin in front of the network). With {@code proxy.relayChat} on,
 * BSP-Core sends every public chat line to the proxy on {@link #CHANNEL} after this server's
 * listeners ran. A mod that shows chat another way (re-sending blocked lines as system messages,
 * say) may hand its public lines to the relay too.
 */
public final class BspProxy {
    private BspProxy() {}

    public static final ResourceLocation CHANNEL = com.mrgregles.bsp_core.proxy.ProxyRelay.CHANNEL;

    /** Whether lines go to the proxy at all ({@code proxy.relayChat}). */
    public static boolean relayEnabled() {
        return com.mrgregles.bsp_core.proxy.ProxyRelay.enabled();
    }

    /** Sends one public line as the player's, decorated by the chat type the way the server would show it. Nothing without the switch. */
    public static void relayChat(ServerPlayer player, Component message) {
        com.mrgregles.bsp_core.proxy.ProxyRelay.relay(player, message);
    }
}
