package com.mrgregles.bsp_core.market;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The marketplace's side of the network database, for {@link NetworkMarket}. Each call is one transaction and runs on the
 * storage thread; a failure throws and changes nothing. The lottery pot's bank row ({@link Market#POT}) is created, empty,
 * by the first call that pays into it. GameTests give {@code NetworkMarket} an in-memory one.
 */
public interface MarketDb {
    /**
     * Thrown when a call failed before the database could have changed anything (no connection, or an error before the
     * commit, rolled back). Any other failure may have come after the commit, with only the answer lost.
     */
    final class Untouched extends java.sql.SQLException {
        public Untouched(java.sql.SQLException cause) {
            super(cause.getMessage(), cause.getSQLState(), cause.getErrorCode(), cause);
        }

        public Untouched(String message) {
            super(message);
        }
    }

    /** Balances after a change: the player's (seller or buyer), the other party's (the seller on a purchase, else -1) and the pot's. */
    record Done(String key, @Nullable MarketLot lot, long pieces, long otherPieces, long potPieces) {
        public static Done no(String key) {
            return new Done(key, null, -1, -1, -1);
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Takes {@code tax} from the seller into the pot and inserts the lot, recording its id as an attempt. "lots" when the
     * seller already has {@code maxLots} lots on the market (counted here, so listings on two servers at once cannot pass
     * the limit), "short" when they cannot pay the tax.
     */
    Done list(MarketLot lot, long tax, int maxLots) throws Exception;

    /** Whether an attempt reached the database: a listing (its lot id) or a {@link #flush}. Asked when an answer was lost. */
    boolean committed(String attempt) throws Exception;

    /**
     * Writes what this server held, in one transaction and at most once per {@code attempt}: {@code lots} from before the
     * network (each at most once), {@code unsure} listings whose answer was lost (dropped if the listing went in, else their
     * items to the seller's Collect box), and Collect box items.
     */
    void flush(String attempt, List<MarketLot> lots, List<MarketLot> unsure, java.util.Map<UUID, List<ItemStack>> boxes) throws Exception;

    /** The ids of every open lot, newest first (cheap: no lot data). */
    List<String> openIds() throws Exception;

    /** The lots with these ids that are still open (a lot never changes once listed, so a server fetches only those it does not have). */
    List<MarketLot> lots(List<String> ids) throws Exception;

    /**
     * Removes the lot, moves the money (price + {@code tax} from the buyer, the price to the seller, the tax to the pot) and
     * puts the lot's items in the buyer's Collect box, all in one step, so the items are never lost with an answer. "gone"
     * when the lot is not there or ended by {@code now}, "own" for the seller's own lot, "short" when the buyer cannot pay,
     * "seller" when the seller has no bank account on the network to be paid into.
     * {@code pieces} is the buyer's balance after, {@code otherPieces} the seller's.
     */
    Done buy(String id, UUID buyer, long tax, long now) throws Exception;

    /** Removes the lot and puts its items in the seller's Collect box. "gone" when it is not there. */
    Done close(String id) throws Exception;

    void addCollect(UUID player, List<ItemStack> items) throws Exception;

    /** Marks what is in the player's Collect box as being taken by {@code attempt} and returns it; the rows go only with {@link #taken}. */
    List<ItemStack> takeCollect(UUID player, String attempt) throws Exception;

    /** The items of {@code attempt} were delivered: removes them. */
    void taken(String attempt) throws Exception;

    /** The items of {@code attempt} were not delivered: back into the box. */
    void untake(String attempt) throws Exception;

    /** What is in the player's box, less what is being taken. */
    List<ItemStack> peekCollect(UUID player) throws Exception;

    /** Puts back stacks left half taken for ten minutes and forgets attempts older than 30 days. */
    void sweep(long now) throws Exception;
}
