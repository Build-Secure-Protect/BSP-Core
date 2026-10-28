package com.mrgregles.bsp_core.market;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where the marketplace lives and how its money moves: {@link LocalMarket} on a single server (this world's
 * {@link MarketLedger} and the local bank), {@link NetworkMarket} on a network (the database, one transaction per call).
 * Each call that moves money or items is one change: it happens whole or not at all, and only one caller ever gets a lot.
 * Callbacks run on the server thread; on a single server at once. GameTests swap the store with {@link Market#useStore},
 * setting and restoring it within one tick.
 */
public interface MarketStore {
    /**
     * An answer: {@code key} "ok" or why not ("gone", "short", "unreachable", "unsure_buy"); {@code lot} the lot bought or
     * closed; {@code boxed} when a purchase's items are already in the buyer's Collect box (on a network).
     */
    record Answer(String key, @Nullable MarketLot lot, boolean boxed) {
        public Answer(String key, @Nullable MarketLot lot) {
            this(key, lot, false);
        }

        public boolean ok() {
            return key.equals("ok");
        }
    }

    /**
     * Takes the listing tax from the seller into the lottery pot and puts the lot on the market. Answers "ok", "short",
     * "unreachable" (nothing happened: the stacks are the caller's to give back) or "unsure" (the database's answer was
     * lost: the stacks are kept, and either the lot is listed or they go to the seller's Collect box).
     */
    void list(MinecraftServer server, MarketLot lot, long tax, Consumer<String> then);

    /** The open lots this server knows of, newest first. On a network, a copy a moment behind the database. */
    List<MarketLot> open(MinecraftServer server);

    /**
     * Buys a lot whole: takes it off the market, the price plus {@code tax} from the buyer, the price to the seller, the tax to
     * the pot. Answers "ok" with the lot, "gone" (bought, cancelled or ended first), "short", "unreachable" or "unsure_buy"
     * (on a network, the answer was lost: if it went through, the items are in the buyer's Collect box). The items are the
     * caller's to deliver unless the answer says they are {@code boxed}.
     */
    void buy(MinecraftServer server, String id, UUID buyer, String buyerName, long tax, Consumer<Answer> then);

    /** Takes a lot off the market (cancelled or ended) and puts its items in the seller's Collect box. Answers "ok" with the lot, "gone" or "unreachable". */
    void close(MinecraftServer server, String id, Consumer<Answer> then);

    /** Closes several lots (the expiry sweep); {@code each} hears every answer. On a network one call, and one notice to the other servers at the end. */
    default void closeAll(MinecraftServer server, List<String> ids, Consumer<Answer> each) {
        ids.forEach(id -> close(server, id, each));
    }

    /** Adds items to a player's Collect box (never lost: on a network that cannot be reached they wait on this server). */
    void addCollect(MinecraftServer server, UUID player, List<ItemStack> items);

    /** Empties a player's Collect box and hands its items over; empty when it could not be reached (nothing was taken). */
    void takeCollect(MinecraftServer server, UUID player, Consumer<Optional<List<ItemStack>>> then);

    /** What waits in a player's Collect box, as this server last knew it (copies). */
    List<ItemStack> peekCollect(MinecraftServer server, UUID player);
}
