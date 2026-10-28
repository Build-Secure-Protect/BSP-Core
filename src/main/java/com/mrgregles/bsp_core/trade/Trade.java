package com.mrgregles.bsp_core.trade;

import net.minecraft.world.Container;

import java.util.List;
import java.util.UUID;

/**
 * A live trade as one player's screen sees it: a {@link TradeSession} on the server that runs the trade (the asker's), or a
 * {@link RemoteTrade} on the other player's server when the two are on different servers of the network. Server thread.
 */
public interface Trade {
    String id();

    boolean has(UUID player);

    boolean finished();

    /** True while the offers may not change (the countdown and the hand-over). */
    boolean locked();

    /** The player's own offer slots. */
    Container mine(UUID player);

    /** The other side's offer, to show. */
    Container theirs(UUID player);

    String setPieces(UUID player, long pieces);

    String ready(UUID player);

    String unready(UUID player);

    void say(UUID player, String text);

    /** The player is leaving the trade ({@code reasonKey} under {@code message.bsp_core.trade.cancelled.}): it ends and every item goes back. */
    void leave(UUID player, String reasonKey, String by);

    List<TradeSession.Line> chat();

    /** The two players' names, the asker first. */
    List<String> names();

    /** What {@code player}'s trade screen shows. */
    TradeMenu.State state(UUID player);

    void tick(long now);
}
