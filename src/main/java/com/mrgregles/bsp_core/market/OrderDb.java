package com.mrgregles.bsp_core.market;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Buy orders' side of the network database, for {@link NetworkOrders}. Each call is one transaction and runs on the storage
 * thread; a failure throws and changes nothing ({@link MarketDb.Untouched} when that is sure). GameTests give
 * {@code NetworkOrders} an in-memory one.
 */
public interface OrderDb {
    /**
     * After a change: {@code key} "ok" or why not; {@code order} the order after it (for a close, the order closed);
     * {@code sent}, {@code paid} and {@code tax} for a fill; {@code refund} for a close; {@code pieces} the balance after of the
     * player paid or charged (the seller on a fill, the buyer otherwise), {@code potPieces} the pot's after a fill.
     */
    record Done(String key, @Nullable BuyOrder order, int sent, long paid, long tax, long refund, boolean closed, long pieces, long potPieces) {
        public static Done no(String key) {
            return new Done(key, null, 0, 0, 0, 0, false, -1, -1);
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Takes the order's whole price from the buyer and inserts it, recording its id as an attempt. "orders" when the buyer has
     * {@code maxOrders} open (counted here, so orders placed on two servers at once cannot pass the limit), "short" when they
     * cannot pay.
     */
    Done place(BuyOrder order, int maxOrders) throws Exception;

    /** Every open order, newest first. */
    List<BuyOrder> orders() throws Exception;

    /**
     * Fills the order with up to {@code count} items: locks it, sends as many as it still wants (the plain item, into the
     * buyer's Collect box) and the rest back into the seller's, pays the seller for those sent less the tax
     * ({@link BuyOrders#tax}) and the tax to the pot, and deletes the order once it has all it wants, recording {@code attempt}. "gone" when it is not there, ended by {@code now} or full,
     * "own" for the buyer's own order, "seller" when the seller has no bank account on the network.
     */
    Done fill(String id, UUID seller, int count, String attempt, long now) throws Exception;

    /** Deletes the order and gives the buyer back what is still held for it. "gone" when it is not there. */
    Done close(String id) throws Exception;

    /** Whether an attempt reached the database: a placing (its order id) or a fill. */
    boolean committed(String attempt) throws Exception;

    /** A fill whose answer was lost: unless {@code attempt} went in, {@code items} go to the seller's Collect box; either way the attempt is then recorded, so this happens once. */
    void settleFill(String attempt, UUID seller, List<ItemStack> items) throws Exception;
}
