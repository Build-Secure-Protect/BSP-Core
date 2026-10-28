package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The marketplace, posted on {@code MinecraftForge.EVENT_BUS} on the server thread of the server where it happened, after
 * the money and the items have moved. For other mods (ranks, Discord, statistics); listeners only read. The items are
 * copies.
 */
public abstract class MarketEvent extends Event {
    private final MinecraftServer server;
    private final String lotId;
    private final UUID seller;
    private final String sellerName;
    private final long price;
    private final List<ItemStack> items;

    protected MarketEvent(MinecraftServer server, String lotId, UUID seller, String sellerName, long price, List<ItemStack> items) {
        this.server = server;
        this.lotId = lotId;
        this.seller = seller;
        this.sellerName = sellerName;
        this.price = price;
        List<ItemStack> copy = new ArrayList<>();
        items.forEach(s -> copy.add(s.copy()));
        this.items = copy;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public String getLotId() {
        return lotId;
    }

    public UUID getSeller() {
        return seller;
    }

    public String getSellerName() {
        return sellerName;
    }

    /** The price the seller set, in pieces, before tax. */
    public long getPrice() {
        return price;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    /** A player listed a lot; {@code tax} is what they paid to list it. */
    public static class Listed extends MarketEvent {
        private final long tax;

        public Listed(MinecraftServer server, String lotId, UUID seller, String sellerName, long price, List<ItemStack> items, long tax) {
            super(server, lotId, seller, sellerName, price, items);
            this.tax = tax;
        }

        public long getTax() {
            return tax;
        }
    }

    /** A player bought a lot: they paid the price plus {@code tax}; the seller was paid the price. */
    public static class Sold extends MarketEvent {
        private final UUID buyer;
        private final String buyerName;
        private final long tax;

        public Sold(MinecraftServer server, String lotId, UUID seller, String sellerName, long price, List<ItemStack> items, UUID buyer, String buyerName, long tax) {
            super(server, lotId, seller, sellerName, price, items);
            this.buyer = buyer;
            this.buyerName = buyerName;
            this.tax = tax;
        }

        public UUID getBuyer() {
            return buyer;
        }

        public String getBuyerName() {
            return buyerName;
        }

        public long getTax() {
            return tax;
        }
    }
}
