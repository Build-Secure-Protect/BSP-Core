package com.mrgregles.bsp_core.shop;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Where a network keeps shop counts and rotating draws (spawn shops request SS-5): the database, or
 * a GameTest's stand-in. A count is a card's stock or sell room, for everyone (player null) or for
 * one player. The store hands out a card side's two counts, or a rotating shop's draw, under a lock,
 * so two servers cannot both take the last lot or make two draws. Every method blocks: the
 * database's run on the storage thread, a stand-in's inline.
 */
public interface CountStore {
    /**
     * Under a lock: the everyone count and this player's count of one card side (left -1 when there
     * is none yet, so full); {@code change} works on them and the store keeps what it leaves (a
     * player count left at -1 is dropped).
     */
    <T> T locked(String shop, int card, boolean selling, UUID player, BiFunction<ShopLedger.Count, ShopLedger.Count, T> change) throws Exception;

    /** Every count of a shop, keyed by {@link #key}. */
    Map<String, ShopLedger.Count> read(String shop) throws Exception;

    /** Drops the counts of cards {@code from} to {@code to}, so they are full again. */
    void reset(String shop, int from, int to) throws Exception;

    /** Drops one player's count if it still holds what was read (the sweep of counts that are full again). */
    void dropIf(String shop, int card, boolean selling, UUID player, int left, long at) throws Exception;

    /** Copies {@code counts} (shop id to {@link #key} to count) in for the first server that asks; true for that server only. */
    boolean moveOnce(String serverId, Map<String, Map<String, ShopLedger.Count>> counts) throws Exception;

    /**
     * Under a lock: a rotating shop's draw (empty, with times 0, when there is none yet); {@code
     * change} may make a new one in it, and the store keeps what it leaves and returns it.
     */
    ShopLedger.Draw drawLocked(String shop, java.util.function.UnaryOperator<ShopLedger.Draw> change) throws Exception;

    /** Forgets a shop's draw (the shop was deleted). */
    void dropDraw(String shop) throws Exception;

    /** "card:B|S:player", with an empty player for the everyone count. */
    static String key(int card, boolean selling, @Nullable UUID player) {
        return card + ":" + (selling ? "S" : "B") + ":" + (player == null ? "" : player.toString());
    }
}
