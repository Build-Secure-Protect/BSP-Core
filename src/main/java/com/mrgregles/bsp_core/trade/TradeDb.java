package com.mrgregles.bsp_core.trade;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The trades' side of the network database. Each call is one transaction on the storage thread; a failure throws and
 * changes nothing. Every attempt to finish a trade has an id with one outcome row, "done" or "aborted": whichever is
 * written first is final, so a server that loses touch with the other can always find out what happened.
 */
public interface TradeDb {
    /** {@code key} "ok", "short_a" or "short_b" (that side cannot pay; nothing changed), or "aborted" (the attempt was already given up). Balances after when ok. */
    record Settled(String key, long aPieces, long bPieces) {
        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Finishes an attempt in one transaction: writes its "done" row, moves the pieces ({@code aPieces} from a to b,
     * {@code bPieces} from b to a), turns {@code hostItems} into items waiting for {@code hostItemsTo}, and the items
     * {@code escrowOwner} put in escrow for this attempt into items waiting for {@code escrowTo}. With no other server
     * involved the item arguments are empty and null and only the money moves.
     */
    Settled settle(String attempt, UUID a, long aPieces, UUID b, long bPieces, List<ItemStack> hostItems, @Nullable UUID hostItemsTo,
                   @Nullable UUID escrowOwner, @Nullable UUID escrowTo) throws Exception;

    /** Holds a player's offer for an attempt, out of every server's hands until the attempt's outcome; records that it was escrowed. */
    void escrow(String attempt, UUID owner, List<ItemStack> items) throws Exception;

    /**
     * What became of an escrow whose answer was lost: {@code outcome} the attempt's ("done", or "aborted", written now if it
     * was undecided); {@code escrowed} whether the escrow ever went in; {@code items} what was still in escrow for the owner,
     * taken out now (empty when the attempt is done, or when the sweep already sent them to the owner as pending items).
     */
    record Recovered(String outcome, boolean escrowed, List<ItemStack> items) {}

    /** In one transaction: decides the attempt aborted unless done, then takes back the owner's escrow; see {@link Recovered}. */
    Recovered recoverEscrow(String attempt, UUID owner) throws Exception;

    /** Writes the "aborted" row unless the attempt is already decided; returns the outcome, "done" or "aborted". */
    String decideAbort(String attempt) throws Exception;

    /** Takes back what the owner put in escrow for an attempt (after it was aborted). */
    List<ItemStack> takeEscrow(String attempt, UUID owner) throws Exception;

    /** Takes the items waiting for a player (what a finished trade sent them), to deliver at once. */
    List<ItemStack> takePending(UUID player) throws Exception;

    /** Escrow older than {@code beforeMs} from attempts nobody finished: aborts them and sends the items to their owners. Returns the stacks returned. */
    int sweep(long beforeMs) throws Exception;
}
