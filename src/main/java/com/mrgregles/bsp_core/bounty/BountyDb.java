package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.bank.BankTransaction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The bounties' side of the network database, for {@link NetworkBounties}. Each call is one transaction and runs on the storage
 * thread; a failure throws and changes nothing ({@link com.mrgregles.bsp_core.market.MarketDb.Untouched} when it surely changed
 * nothing; any other failure may have come after the commit, with only the answer lost). Money moves inside the same
 * transaction: pieces in {@code bank_accounts} with their {@code bank_log} rows, coins as rows of the market's Collect boxes.
 * GameTests give {@code NetworkBounties} an in-memory one.
 */
public interface BountyDb {
    /** A bank account the call changed: its balance after and the log row the database wrote for it. */
    record Moved(UUID id, long piecesAfter, BankTransaction row) {}

    /** An answer: "ok" or why not; the bounty as it is after the call; the accounts changed; the players whose Collect box gained coins. */
    record Done(String key, @Nullable Bounty bounty, boolean bonusPaid, List<Moved> moved, List<UUID> boxes) {
        public static Done no(String key) {
            return new Done(key, null, false, List.of(), List.of());
        }

        public static Done no(String key, @Nullable Bounty bounty) {
            return new Done(key, bounty, false, List.of(), List.of());
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Inserts a new bounty. For one in pieces from a player, takes reward, bonus and fee from the placer ("short" when they lack
     * it); the fee goes into the lottery pot (for a coin bounty, its worth in pieces). "too_many" when the placer already has
     * {@code maxOpen} open bounties (counted here, so two servers cannot pass the limit).
     */
    Done place(Bounty bounty, int maxOpen) throws Exception;

    /** Whether a bounty with this id was ever placed (open or closed). Asked when a placing's answer was lost. */
    boolean placed(String id) throws Exception;

    /** Every open bounty, newest first. */
    List<Bounty> open() throws Exception;

    /** The newest {@code limit} closed bounties, newest first. */
    List<BountyStore.Closed> closed(int limit) throws Exception;

    /** Adds a hunter: "gone" when not open or ended by {@code now}, "already", "too_many" (they hold {@code maxPerHunter}, counted here). */
    Done accept(String id, Bounty.Hunter hunter, int maxPerHunter, long now) throws Exception;

    Done abandon(String id, UUID hunter) throws Exception;

    /** Closes a bounty in any way but a claim and gives back what {@code ending} gives back; "gone" when it is not open. */
    Done end(String id, BountyStore.Ending ending, String by, long now) throws Exception;

    /** Closes a bounty as claimed and pays the hunter; "gone", "not_hunter" or "ended" (the kill came after the end). */
    Done claim(String id, UUID hunter, String hunterName, long killMs, long now) throws Exception;

    Done extend(String id, long endsMs) throws Exception;

    Done setNotes(String id, String notes) throws Exception;

    /** Forgets closed bounties older than {@code beforeMs}. */
    void sweep(long beforeMs) throws Exception;
}
