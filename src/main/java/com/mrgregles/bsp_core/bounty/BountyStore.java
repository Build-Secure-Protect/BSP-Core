package com.mrgregles.bsp_core.bounty;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where the bounties live and how their money moves: {@link LocalBounties} on a single server (this world's {@link BountyLedger}
 * and the local bank), a network store in the database. Each call that moves money is one change: it happens whole or not at
 * all, and only one caller ever closes a bounty. Callbacks run on the server thread; on a single server at once. GameTests
 * swap the store with {@link Bounties#useStore}, setting and restoring it within one tick.
 *
 * <p>Where money goes when a bounty closes: pieces to the bank, coins to the Collect box (the Trade Post's, opened at the Bounty
 * Board too). A bounty placed by {@link Bounties#SERVER} pays out but gives nothing back.
 */
public interface BountyStore {
    /** How a bounty closed. */
    enum Ending {
        /** A hunter who accepted it killed the target: the reward (and the bonus in time) to the hunter, a missed bonus back to the placer. */
        CLAIMED,
        /** The placer took it back before anyone accepted: reward and bonus back, the fee kept. */
        CANCELLED,
        /** It ran out: reward and bonus back, the fee kept. */
        EXPIRED,
        /** Staff removed it: reward and bonus back, the fee kept. */
        REMOVED,
        /** Staff removed it and kept the money: nothing back. */
        REMOVED_NO_REFUND,
        /** The target became staff: everything back, the fee too. */
        TARGET_STAFF
    }

    /** A closed bounty, kept for the history: how it ended, when, who claimed it (null otherwise) and who closed it. */
    record Closed(Bounty bounty, Ending ending, long closedMs, @Nullable UUID hunter, String hunterName, String by, boolean bonusPaid) {}

    /**
     * An answer: {@code key} "ok" or why not ("gone", "already", "too_many", "not_hunter", "ended", "short", "max", "unreachable"); the
     * bounty as it is after the call; for an extension, the end it had before ({@code endsBefore}, 0 otherwise).
     */
    record Answer(String key, @Nullable Bounty bounty, boolean bonusPaid, long endsBefore) {
        public Answer(String key, @Nullable Bounty bounty) {
            this(key, bounty, false, 0);
        }

        public Answer(String key, @Nullable Bounty bounty, boolean bonusPaid) {
            this(key, bounty, bonusPaid, 0);
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Puts a new bounty up. For a bounty in pieces placed by a player, takes the reward, the bonus and the fee from the placer's
     * bank in the same step ("short" and nothing changes when they lack it); a coin bounty's coins were taken by the caller. The
     * fee goes into the lottery pot. Answers "ok", "short" or "unreachable".
     */
    void place(MinecraftServer server, Bounty bounty, Consumer<String> then);

    /** The open bounties this server knows of, newest first. On a network, a copy a moment behind the database. */
    List<Bounty> open(MinecraftServer server);

    /** Closed bounties, newest first (at most a few hundred). */
    List<Closed> closed(MinecraftServer server);

    /** Adds a hunter. Answers "ok", "gone", "already" or "too_many" (they already hold {@code maxPerHunter} open bounties). */
    void accept(MinecraftServer server, String id, Bounty.Hunter hunter, int maxPerHunter, Consumer<Answer> then);

    /** Drops a hunter. Answers "ok", "gone" or "not_hunter". */
    void abandon(MinecraftServer server, String id, UUID hunter, Consumer<Answer> then);

    /** Closes a bounty in any way but a claim, giving back what {@code ending} gives back. {@code by}: who closed it, for the history. */
    void end(MinecraftServer server, String id, Ending ending, String by, Consumer<Answer> then);

    /**
     * Closes a bounty as claimed by {@code hunter} for a kill at {@code killMs}: pays the reward (and the bonus when the kill came
     * before its deadline; otherwise the bonus goes back to the placer). Answers "ok" (with {@code bonusPaid}), "gone", "not_hunter"
     * or "ended" (the kill came after the end).
     */
    void claim(MinecraftServer server, String id, UUID hunter, String hunterName, long killMs, Consumer<Answer> then);

    /**
     * Gives a bounty {@code addMs} more from the end it has in the store (from {@code now} when that has passed), never past
     * {@code capMs} ({@link Bounties#extendedEnd}). Worked out from the stored bounty in the same step as the write, so two servers
     * extending at once both count. Answers "ok" (with {@code endsBefore}), "gone" or "max" (it already ends at {@code capMs} or later).
     */
    void extend(MinecraftServer server, String id, long addMs, long now, long capMs, Consumer<Answer> then);

    /** Replaces the notes. Answers "ok" or "gone". */
    void setNotes(MinecraftServer server, String id, String notes, Consumer<Answer> then);
}
