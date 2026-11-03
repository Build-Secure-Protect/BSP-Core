package com.mrgregles.bsp_core.market;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where buy orders live and how their money moves: {@link LocalOrders} on a single server (this world's {@link OrderLedger},
 * the local bank and the local Collect boxes), {@link NetworkOrders} on a network (the database, one transaction per call).
 * Each call that moves money or items is one change, whole or not at all; two sellers filling the same order at the same
 * moment never send more than it wants. Callbacks run on the server thread; on a single server at once. GameTests swap the
 * store with {@link BuyOrders#useStore}, setting and restoring it within one tick.
 */
public interface OrderStore {
    /**
     * An answer: {@code key} "ok" or why not; {@code order} the order as it stands after the change (for a fill that closed
     * it, its last state; for a close, the order closed); {@code sent} how many items a fill took and {@code back} how many
     * of the rest are the caller's to give back to the seller (on a network the rest went to the seller's Collect box in the
     * same step); {@code paid} what the seller was paid after {@code tax}; {@code refund} what a close gave back to the
     * buyer; {@code closed} when a fill completed the order.
     */
    record Answer(String key, @Nullable BuyOrder order, int sent, int back, long paid, long tax, long refund, boolean closed) {
        public static Answer no(String key) {
            return new Answer(key, null, 0, 0, 0, 0, 0, false);
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Takes the order's whole price from the buyer and puts it on the market. Answers "ok", "short", "orders" (the buyer has
     * {@code maxOrders} open), "unreachable" (nothing happened) or "unsure" (the answer was lost: placed or not, the bank and
     * the orders agree; the next refresh shows which).
     */
    void place(MinecraftServer server, BuyOrder order, int maxOrders, Consumer<Answer> then);

    /** The open orders this server knows of, newest first. On a network, a copy a moment behind the database. */
    List<BuyOrder> open(MinecraftServer server);

    /**
     * Sends {@code count} of the order's item (already taken from the seller) to the order {@code seen} (this server's copy
     * of it): as many as it still wants go to the buyer's Collect box, the seller is paid for them less the tax, the tax goes
     * to the pot, and the order closes when it has all it wants. Answers "ok" (the answer's {@code sent} items went, and
     * {@code back} of the rest are the caller's to give back), "gone" (filled, cancelled or ended first; every item is the caller's to give back),
     * "own", "seller" (no bank account to pay), "unreachable" (nothing happened) or "unsure" (the answer was lost: the store
     * keeps the items, and they either went to the order or go to the seller's Collect box).
     */
    void fill(MinecraftServer server, BuyOrder seen, UUID seller, String sellerName, int count, Consumer<Answer> then);

    /** Takes an order off the market and gives the buyer back what is still held for it. Answers "ok", "gone" or "unreachable". */
    void close(MinecraftServer server, String id, Consumer<Answer> then);
}
