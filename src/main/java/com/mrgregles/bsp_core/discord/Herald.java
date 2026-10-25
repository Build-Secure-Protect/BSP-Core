package com.mrgregles.bsp_core.discord;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.storage.NetworkStorage;

/**
 * The Herald upgrade's switch: Discord steal warnings work only on a server of the network with {@code discord.enabled} on,
 * never in a single-player or LAN world. Read on both sides (the server config is synced to clients), so the tree shows
 * Herald locked exactly where the server refuses to sell it.
 */
public final class Herald {
    private Herald() {}

    /** Whether Herald may be bought and warns here. */
    public static boolean available() {
        return available(BSPConfig.getOr(BSPConfig.DISCORD_ENABLED, false), NetworkStorage.singlePlayer());
    }

    public static boolean available(boolean enabled, boolean singlePlayer) {
        return enabled && !singlePlayer;
    }
}
