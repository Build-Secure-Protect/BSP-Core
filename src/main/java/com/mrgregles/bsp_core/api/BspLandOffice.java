package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.land.LandOfficeMenu;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Land Office screen, for BSP Region Guard: when a player finishes outlining ground with the
 * wand, Region Guard calls {@link #open(ServerPlayer, int, int, int, int)} and the office opens on
 * the map with that ground drawn, priced if it lies in an open area. The ground is kept on the
 * server for the purchase; the client never sends one.
 */
public final class BspLandOffice {
    private BspLandOffice() {}

    /** Opens the office with no outlined ground (what the Land Office block does). */
    public static void open(ServerPlayer player) {
        LandOfficeMenu.open(player, (int[]) null);
    }

    /** Opens the office with the outlined ground {@code minX..maxX} by {@code minZ..maxZ} (either order; y does not matter). */
    public static void open(ServerPlayer player, int x1, int z1, int x2, int z2) {
        LandOfficeMenu.open(player, new int[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)});
    }
}
