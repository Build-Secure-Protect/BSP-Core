package com.mrgregles.bsp_core.trade;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * This server's live trades: the front door to its {@link TradeHub}, which network storage connects to the other servers
 * at start. Server thread only.
 */
public final class Trades {
    private Trades() {}

    public static final long REQUEST_MS = TradeHub.REQUEST_MS;
    public static final double REACH = TradeHub.REACH;

    private static final TradeHub HUB = new TradeHub("local");

    static {
        HUB.useOpener(TradeMenu::open);
    }

    public static TradeHub hub() {
        return HUB;
    }

    public static long now() {
        return HUB.now();
    }

    /** GameTests: run the trades at a chosen time (null for the wall clock again). Set and put back within one tick. */
    public static void useClock(@Nullable LongSupplier c) {
        HUB.useClock(c);
    }

    public static void useOpener(@Nullable BiConsumer<Player, Trade> o) {
        HUB.useOpener(o);
    }

    public static void join(Player player, @Nullable GlobalPos post) {
        HUB.join(player, post);
    }

    public static void leave(Player player, String reasonKey) {
        HUB.leave(player, reasonKey);
    }

    public static Optional<TradeHub.Present> present(UUID id) {
        return HUB.present(id);
    }

    public static boolean busy(UUID id) {
        return HUB.busy(id);
    }

    /** In a trade that has started (not just an accepted request still waiting for the other server). */
    public static boolean inTrade(UUID id) {
        return HUB.inTrade(id);
    }

    public static List<TradeHub.Trader> traders(UUID viewer) {
        return HUB.traders(viewer);
    }

    public static Optional<TradeHub.Request> requestFrom(UUID from) {
        return HUB.requestFrom(from);
    }

    public static List<TradeHub.Request> requestsTo(UUID to) {
        return HUB.requestsTo(to);
    }

    public static String ask(Player from, UUID to) {
        return HUB.ask(from, to);
    }

    public static void withdraw(UUID from) {
        HUB.withdraw(from);
    }

    public static String answer(Player to, UUID from, boolean accept) {
        return HUB.answer(to, from, accept);
    }

    public static Optional<TradeSession> session(UUID player) {
        return HUB.session(player);
    }

    public static Optional<Trade> trade(UUID player) {
        return HUB.trade(player);
    }

    public static void tick() {
        HUB.tick();
    }

    public static void stopAll() {
        HUB.stopAll();
    }

    /**
     * Gives a trade's items to a player: into the inventory of the player as they are now (a player object kept from before
     * a respawn is swapped for the current one by UUID), with what does not fit dropped at their feet; into their market
     * Collect box when they are dead, gone or logged out, so nothing goes into an inventory that will not be saved.
     */
    public static void give(net.minecraft.server.MinecraftServer server, Player kept, UUID id, List<net.minecraft.world.item.ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Player now = kept;
        if (now == null || now.isRemoved()) {
            now = server.getPlayerList().getPlayer(id);
        }
        boolean here = now != null && now.isAlive() && !now.isRemoved() && !(now instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected());
        if (!here) {
            com.mrgregles.bsp_core.market.Market.store(server).addCollect(server, id, items);
            return;
        }
        for (net.minecraft.world.item.ItemStack it : com.mrgregles.bsp_core.market.Market.intoInventory(now, items)) {
            now.drop(it, false);
        }
    }
}
