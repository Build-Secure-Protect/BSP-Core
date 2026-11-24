package com.mrgregles.bsp_core.tracking;

import net.minecraft.server.level.ServerPlayer;

/** Opens machine tracking's screens for a player. */
public final class TrackerScreens {
    private TrackerScreens() {}

    /** The Machine Tagger's screen (right-click in the air): the client opens it; the server sends the latest view to fill it. */
    public static void openTagger(ServerPlayer player) {
        TrackerSync.send(player);
    }
}
