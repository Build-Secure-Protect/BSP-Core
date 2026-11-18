package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.market.BuyOrder;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLot;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The marketplace read-only, for other mods (BSP-Server Utilities request SU-BC1: its staff web panel). Lots are what players
 * listed on a Trade Post for one price, bought whole; buy orders are what players want and pay for as it is sent. Amounts are
 * pieces; times are epoch milliseconds; {@code serverId} is the {@code storage.serverId} of the server the lot was listed or
 * the order placed on ("local" on a single server).
 *
 * <p>On a network the lots and orders live in the database (tables {@code market_lots} and {@code market_orders}) and every
 * server keeps a copy, read again at start, every minute and whenever another server changes them. {@link #openLots} and
 * {@link #openOrders} answer at once from that copy; {@link #lotsReadMs} and {@link #ordersReadMs} say when it was last read,
 * and {@link #refresh} reads it again first when the copy must be current. On a single server they read the world's saved
 * data and are always current. While a network server's database is not connected they answer empty lists and read times
 * of 0, never this world's own records in the network's place.
 *
 * <p>Server thread only: from another thread (a web request, say) hand the call to the server with
 * {@code server.execute(...)}. No argument may be null. Nothing here changes the market, except that {@link #refresh} first
 * sends on what this server held while the database was away, as the minute's refresh does, and the staff cancels
 * ({@link #cancelLot}, {@link #cancelOrder}). Since 1.16.0; the staff calls since the Bank &amp; Market page.
 */
public final class BspMarket {
    private BspMarket() {}

    /**
     * An open lot. {@code items} is an unmodifiable list of copies, never the market's own stacks: the stacks may be changed
     * freely. {@code endsMs} is when it runs out and goes back to the seller.
     */
    public record Lot(String id, UUID seller, String sellerName, long price, long listedMs, long endsMs, String serverId, List<ItemStack> items) {
        public Lot {
            items = List.copyOf(items);
        }
    }

    /**
     * An open buy order: the buyer wants {@code wanted} of {@code item} (one plain stack of 1, a copy) at {@code each} pieces
     * apiece; {@code filled} have been sent so far. The whole price ({@link #price}) left the buyer's bank when it was placed.
     */
    public record Order(String id, UUID buyer, String buyerName, ItemStack item, int wanted, int filled, long each, long placedMs, long endsMs, String serverId) {
        /** The whole price: {@code wanted} x {@code each}. */
        public long price() {
            return wanted * each;
        }

        /** How many are still wanted. */
        public int toGo() {
            return Math.max(0, wanted - filled);
        }
    }

    /** The open lots, newest first; lots past their end time (waiting to be closed) are left out. */
    public static List<Lot> openLots(MinecraftServer server) {
        long now = Market.now();
        List<Lot> out = new ArrayList<>();
        if (NetworkStorage.down()) {
            return out;
        }
        for (MarketLot l : Market.store(server).open(server)) {
            if (!l.endedBy(now)) {
                out.add(new Lot(l.id, l.seller, l.sellerName, l.price, l.listedMs, l.endsMs, l.server, l.items()));
            }
        }
        return out;
    }

    /** The open buy orders, newest first; orders past their end time (waiting to be closed) and orders with nothing left to send are left out. */
    public static List<Order> openOrders(MinecraftServer server) {
        long now = Market.now();
        List<Order> out = new ArrayList<>();
        if (NetworkStorage.down()) {
            return out;
        }
        for (BuyOrder o : BuyOrders.store(server).open(server)) {
            if (!o.endedBy(now) && o.toGo() > 0) {
                out.add(new Order(o.id, o.buyer, o.buyerName, o.item(), o.wanted, o.filled, o.each, o.placedMs, o.endsMs, o.server));
            }
        }
        return out;
    }

    /**
     * When the lots {@link #openLots} answers from were last read: now on a single server; 0 when they are not the network's
     * (a network server that has not read them yet, or whose database is not connected).
     */
    public static long lotsReadMs(MinecraftServer server) {
        return NetworkStorage.down() ? 0 : Market.store(server).readMs();
    }

    /** As {@link #lotsReadMs}, for {@link #openOrders}. */
    public static long ordersReadMs(MinecraftServer server) {
        return NetworkStorage.down() ? 0 : BuyOrders.store(server).readMs();
    }

    /**
     * Reads the lots and the orders again from the database, then runs {@code then} on the server thread (whether the reads
     * worked or not: compare {@link #lotsReadMs} to see). On a single server {@code then} runs at once. If the server stops
     * first, {@code then} may never run.
     */
    public static void refresh(MinecraftServer server, Runnable then) {
        Objects.requireNonNull(then, "then");
        Market.store(server).refresh(server, () -> BuyOrders.store(server).refresh(server, then));
    }

    // ------------------------------------------------------------------ staff: one player's lots and orders (the Bank & Market page)

    /** The seller's open lots, newest first, as {@link #openLots} has them. */
    public static List<Lot> lotsOf(MinecraftServer server, UUID seller) {
        return openLots(server).stream().filter(l -> l.seller().equals(seller)).toList();
    }

    /** The buyer's open orders, newest first, as {@link #openOrders} has them. */
    public static List<Order> ordersOf(MinecraftServer server, UUID buyer) {
        return openOrders(server).stream().filter(o -> o.buyer().equals(buyer)).toList();
    }

    /** What a staff cancel did: {@code key} "ok" or why not; {@code piecesBack} the pieces an order's buyer got back (0 for a lot). */
    public record Cancelled(String key, long piecesBack) {}

    /**
     * Takes a lot off the market as staff: its items go to the seller's Collect box (picked up at any Trade Post), the listing tax
     * stays paid, and the seller is told, at once or at their next login. {@code by} (who did it) goes in the server log. No
     * permission is checked here. {@code then} gets "ok", "gone" (already sold, cancelled or run out) or "unreachable". Server
     * thread only; {@code then} runs on it.
     */
    public static void cancelLot(MinecraftServer server, String id, String by, Consumer<Cancelled> then) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(then, "then");
        Market.cancelByStaff(server, id, by, r -> then.accept(new Cancelled(r.ok() ? "ok" : r.key(), 0)));
    }

    /**
     * Takes a buy order off the market as staff: what it still holds goes back to the buyer's bank ({@code piecesBack}) and the buyer
     * is told, at once or at their next login. {@code by} goes in the server log. No permission is checked here. {@code then} gets
     * "ok", "gone" or "unreachable". Server thread only; {@code then} runs on it.
     */
    public static void cancelOrder(MinecraftServer server, String id, String by, Consumer<Cancelled> then) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(then, "then");
        BuyOrders.cancelByStaff(server, id, by, a -> then.accept(new Cancelled(a.ok() ? "ok" : a.key(), a.ok() ? a.refund() : 0)));
    }
}
