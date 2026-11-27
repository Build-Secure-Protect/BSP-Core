package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Shops changed, posted on the Forge bus ({@code MinecraftForge.EVENT_BUS}) on the server thread. Notifications only:
 * nothing can be cancelled, and listeners must not change shops from inside them (hand that to the next tick). Read the
 * shops again with {@code BspShops}. Since BSP-Core's shop api ({@code BspShops.VERSION} 1).
 */
public abstract class ShopEvent extends Event {
    private final MinecraftServer server;

    protected ShopEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /**
     * A shop record changed or was deleted: after a save or a delete on this server (the Workbench, a command, an import, a
     * staff call through {@code BspShops}), and when this server reads another server's change (its Redis notice, or the
     * five-minute catch-up finding a newer record). One event per shop: a delete with sections posts one for each, and the
     * parent whose section card left posts its own. The counts left on a card (bought and sold) are not shop changes and post
     * nothing.
     */
    public static final class Changed extends ShopEvent {
        private final String shopId;
        private final boolean deleted, heard;

        public Changed(MinecraftServer server, String shopId, boolean deleted, boolean heard) {
            super(server);
            this.shopId = shopId;
            this.deleted = deleted;
            this.heard = heard;
        }

        public String getShopId() {
            return shopId;
        }

        /** True when the shop is gone. */
        public boolean isDeleted() {
            return deleted;
        }

        /** True when the change was made on another server and read here; false for a change made on this server. */
        public boolean isHeard() {
            return heard;
        }
    }

    /**
     * This server's own Market Stalls, Vendor Posts or Stall Displays changed (placed, assigned, changed, broken, or learned
     * as their chunk loaded); read them with {@code BspShops.venues}. Never posted for another server's.
     */
    public static final class VenuesChanged extends ShopEvent {
        @Nullable
        private final String shopId;

        public VenuesChanged(MinecraftServer server, @Nullable String shopId) {
            super(server);
            this.shopId = shopId;
        }

        /** The shop the changed venue is (or was) assigned to; null when unassigned or not known. */
        @Nullable
        public String getShopId() {
            return shopId;
        }
    }
}
