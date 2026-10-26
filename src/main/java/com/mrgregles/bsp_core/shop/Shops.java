package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The rules of buying and selling. Every purchase and sale goes through here: the vendor and stall
 * screens, the Shopping Network, the configurator's test mode and the command backups. Prices are
 * in pieces (through the bank) or in a coin tier (coins from the inventory, change given).
 */
public final class Shops {
    private Shops() {}

    /** What a buy or sell came to: the lots moved, what it cost or paid, the message key, the balance after. */
    public record Outcome(boolean ok, String key, int lots, long amount, long pieces) {
        static Outcome fail(String key) {
            return new Outcome(false, key, 0, 0, -1);
        }
    }

    // ------------------------------------------------------------------ shops

    public static ShopLedger ledger(MinecraftServer server) {
        return ShopLedger.get(server);
    }

    /** Creates a shop with a slug id; null if the id is taken or not a slug. */
    @Nullable
    public static Shop create(MinecraftServer server, String id, String name, Shop.Kind kind) {
        id = id.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_]{1,32}") || ledger(server).shop(id) != null) {
            return null;
        }
        Shop s = new Shop(id);
        s.name = name;
        s.kind = kind;
        save(server, s);
        return s;
    }

    /** Writes a shop locally and, on a network, to the database (which tells the other servers). */
    public static void save(MinecraftServer server, Shop shop) {
        ledger(server).put(shop);
        NetworkStorage.shopWrite(shop.id, shop.name, shop.save().toString());
    }

    /** Deletes a shop here and on the network, with its counts. */
    public static void delete(MinecraftServer server, String id) {
        ledger(server).remove(id);
        NetworkStorage.shopDelete(id);
    }

    /** The price of one lot now, with a live special offer applied. */
    public static long price(Shop shop, ShopItem item, boolean buy, long now) {
        long p = buy ? item.buy : item.sell;
        if (buy && shop.offerActive(now)) {
            p = p * (100 - shop.offerPercent) / 100;
        }
        return p;
    }

    // ------------------------------------------------------------------ stock and rotation

    /** The time the shop rules read: the wall clock, or a GameTest's. */
    private static LongSupplier clock = System::currentTimeMillis;

    public static long now() {
        return clock.getAsLong();
    }

    /** GameTests: run the shop rules at a chosen time (null for the wall clock again). Set and put back within one tick. */
    public static void useClock(@Nullable LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    /** The zone fixed refill times are read in: {@code shop.timeZone}, or the server machine's. */
    public static ZoneId zone() {
        String z = BSPConfig.SHOP_TIME_ZONE.get().trim();
        if (!z.isEmpty()) {
            try {
                return ZoneId.of(z);
            } catch (Exception e) {
                if (!badZoneTold) {
                    badZoneTold = true;
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("shop.timeZone \"{}\" is not a time zone; using the server's own ({})", z, ZoneId.systemDefault());
                }
            }
        }
        return ZoneId.systemDefault();
    }

    private static boolean badZoneTold;

    /** Lots this player may still buy (shop-wide and per player, whichever is smaller); -1 unlimited. Refills counts that are due. */
    public static int stockLeft(MinecraftServer server, Shop shop, int index, UUID player, long now) {
        ShopItem item = shop.item(index);
        if (item == null) {
            return 0;
        }
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        return room(server, shop, st.stock, st.buyers, player, item.stockGlobal, item.stockPlayer, item.stockRefill, item.stockPlayerRule(), now);
    }

    /** Lots the shop will still buy from this player (shop-wide and per player, whichever is smaller); -1 unlimited. */
    public static int sellLeft(MinecraftServer server, Shop shop, int index, UUID player, long now) {
        ShopItem item = shop.item(index);
        if (item == null) {
            return 0;
        }
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        return room(server, shop, st.sell, st.sellers, player, item.sellGlobal, item.sellPlayer, item.sellRefill, item.sellPlayerRule(), now);
    }

    /** What is left of a card's shop-wide count (stock, or sell room when {@code selling}), refilled if due; -1 when it has no shop-wide limit. */
    public static int everyoneLeft(MinecraftServer server, Shop shop, int index, boolean selling, long now) {
        ShopItem item = shop.item(index);
        if (item == null) {
            return -1;
        }
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        return selling ? room(server, shop, st.sell, st.sellers, net.minecraft.Util.NIL_UUID, item.sellGlobal, -1, item.sellRefill, Refill.NEVER, now)
                : room(server, shop, st.stock, st.buyers, net.minecraft.Util.NIL_UUID, item.stockGlobal, -1, item.stockRefill, Refill.NEVER, now);
    }

    private static int room(MinecraftServer server, Shop shop, ShopLedger.Count all, Map<UUID, ShopLedger.Count> players, UUID player, int maxAll, int maxPlayer, Refill allRule, Refill playerRule, long now) {
        long drawn = drawStamp(server, shop, allRule, playerRule, now);
        boolean changed = false;
        int left = -1;
        if (maxAll >= 0) {
            changed = refill(all, maxAll, allRule, now, drawn);
            left = all.left;
        }
        if (maxPlayer >= 0) {
            ShopLedger.Count mine = players.get(player); // no count yet means full; it is made at the first trade
            if (mine != null) {
                changed |= refill(mine, maxPlayer, playerRule, now, drawn);
                if (mine.left >= maxPlayer) { // full again: no count needed
                    players.remove(player);
                    mine = null;
                    changed = true;
                }
            }
            int m = mine == null ? maxPlayer : mine.left;
            left = left < 0 ? m : Math.min(left, m);
        }
        if (changed) {
            ledger(server).setDirty();
        }
        return left;
    }

    /** The draw a rotation rule counts by: when the rotating shop's current draw was made (redrawing if due); 0 otherwise. */
    private static long drawStamp(MinecraftServer server, Shop shop, Refill a, Refill b, long now) {
        if (shop.kind != Shop.Kind.ROTATING || a.kind() != Refill.Kind.ROTATION && b.kind() != Refill.Kind.ROTATION) {
            return 0;
        }
        visible(server, shop, now);
        return ledger(server).draw(shop.id).drawnAt;
    }

    /**
     * Fills a count when it is due by its rule: never counted yet; a countdown over; a fixed refill
     * time passed since it was last filled; a new draw made. Returns true when it changed.
     */
    private static boolean refill(ShopLedger.Count c, int max, Refill rule, long now, long drawn) {
        long stamp = switch (rule.kind()) {
            case DAILY, WEEKLY -> rule.periodStart(now, zone());
            case ROTATION -> drawn;
            default -> 0;
        };
        boolean due = c.left < 0
                || rule.kind() == Refill.Kind.AFTER && c.left == 0 && c.at > 0 && now >= c.at
                || rule.fixedTime() && c.at < stamp
                || rule.kind() == Refill.Kind.ROTATION && c.at < stamp; // draws only move on: an older stamp (a server not yet told of the new draw) never refills
        if (due) {
            c.left = max;
            c.at = stamp;
            return true;
        }
        if (rule.kind() == Refill.Kind.AFTER && c.left == 0 && c.at == 0) { // ran out before it had a countdown (a card saved before the refill rules)
            c.at = now + rule.minutes() * 60_000L;
            return true;
        }
        if (c.left > max) { // the limit was lowered
            c.left = max;
            return true;
        }
        return false;
    }

    /**
     * Takes up to {@code want} lots from a card side's everyone count and player count (each
     * refilled first if due) and returns how many it took. A player count that ends full is left at
     * -1, so it needs no row. Pure: the same rule runs on this server's counts and inside the
     * network's lock.
     */
    static int take(ShopLedger.Count all, ShopLedger.Count mine, int maxAll, int maxPlayer, Refill allRule, Refill playerRule, int want, long now, long drawn) {
        if (maxAll >= 0) {
            refill(all, maxAll, allRule, now, drawn);
        }
        if (maxPlayer >= 0) {
            refill(mine, maxPlayer, playerRule, now, drawn);
        }
        int room = maxAll >= 0 ? all.left : -1;
        if (maxPlayer >= 0) {
            room = room < 0 ? mine.left : Math.min(room, mine.left);
        }
        int n = room < 0 ? want : Math.min(want, room);
        if (n > 0) {
            if (maxAll >= 0) {
                use(all, n, allRule, now);
            }
            if (maxPlayer >= 0) {
                use(mine, n, playerRule, now);
            }
        }
        if (maxPlayer >= 0 && mine.left == maxPlayer) {
            mine.left = -1;
            mine.at = 0;
        }
        return n;
    }

    /** Gives back lots that were held for a trade that did not go through. */
    static void giveBack(ShopLedger.Count all, ShopLedger.Count mine, int maxAll, int maxPlayer, Refill allRule, Refill playerRule, int lots) {
        back(all, maxAll, allRule, lots);
        back(mine, maxPlayer, playerRule, lots);
        if (maxPlayer >= 0 && mine.left == maxPlayer) {
            mine.left = -1;
            mine.at = 0;
        }
    }

    private static void back(ShopLedger.Count c, int max, Refill rule, int lots) {
        if (max < 0 || c.left < 0) {
            return;
        }
        c.left = Math.min(max, c.left + lots);
        if (c.left > 0 && rule.kind() == Refill.Kind.AFTER) {
            c.at = 0; // not run out any more: no countdown
        }
    }

    private static void use(ShopLedger.Count c, int lots, Refill rule, long now) {
        c.left = Math.max(0, c.left - lots);
        if (c.left == 0 && rule.kind() == Refill.Kind.AFTER) {
            c.at = now + rule.minutes() * 60_000L;
        }
    }

    // ------------------------------------------------------------------ counts across the network (SS-5)

    /** A GameTest's stand-in for the network's counts, or null. */
    @Nullable
    private static CountStore testStore;

    /** GameTests: keep counts in {@code store} as a network would (null: as configured). Set and put back within one tick. */
    public static void useCountStore(@Nullable CountStore store) {
        testStore = store;
    }

    /** True when the counts live on the network (network storage wanted, whether or not the database answers now). */
    public static boolean sharedCounts() {
        return testStore != null || com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted();
    }

    /** Work on the network's counts: inline on a GameTest's stand-in, otherwise on the storage thread; {@code then} gets null when it failed. */
    private static <T> void counted(String what, com.mrgregles.bsp_core.storage.NetworkStorage.CountWork<T> work, @Nullable String notifyShop, Consumer<T> then) {
        CountStore t = testStore;
        if (t == null) {
            com.mrgregles.bsp_core.storage.NetworkStorage.shopCounts(what, work, notifyShop, then);
            return;
        }
        T out = null;
        try {
            out = work.run(t);
        } catch (Exception e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core shops: could not {}: {}", what, e.toString());
        }
        then.accept(out);
    }

    private record Held(int lots, ShopLedger.Count all, ShopLedger.Count mine) {}

    /**
     * Holds up to {@code want} lots of one side of a card for a trade: from this server's counts, or
     * on a network from the database's under its lock (this server's copy updated from the answer).
     * {@code then} gets the lots held, or -1 when the network's counts cannot be reached. A card
     * side with no limit holds everything at once.
     */
    public static void hold(MinecraftServer server, Shop shop, int index, UUID player, boolean selling, int want, long now, java.util.function.IntConsumer then) {
        ShopItem item = shop.item(index);
        if (item == null) {
            then.accept(0);
            return;
        }
        int maxAll = selling ? item.sellGlobal : item.stockGlobal, maxPlayer = selling ? item.sellPlayer : item.stockPlayer;
        Refill allRule = selling ? item.sellRefill : item.stockRefill, playerRule = selling ? item.sellPlayerRule() : item.stockPlayerRule();
        if (maxAll < 0 && maxPlayer < 0) {
            then.accept(want);
            return;
        }
        long drawn = drawStamp(server, shop, allRule, playerRule, now);
        if (!sharedCounts()) {
            ShopLedger.Stock st = ledger(server).stock(shop.id, index);
            Map<UUID, ShopLedger.Count> players = selling ? st.sellers : st.buyers;
            ShopLedger.Count mine = players.computeIfAbsent(player, k -> new ShopLedger.Count());
            int got = take(selling ? st.sell : st.stock, mine, maxAll, maxPlayer, allRule, playerRule, want, now, drawn);
            if (mine.left < 0) {
                players.remove(player);
            }
            ledger(server).setDirty();
            then.accept(got);
            return;
        }
        counted("hold shop stock", store -> store.locked(shop.id, index, selling, player, (all, mine) -> new Held(take(all, mine, maxAll, maxPlayer, allRule, playerRule, want, now, drawn), all.copy(), mine.copy())),
                shop.id, h -> {
                    if (h == null) {
                        then.accept(-1);
                        return;
                    }
                    cache(server, shop, index, selling, player, h.all(), h.mine());
                    then.accept(h.lots());
                });
    }

    /** Gives back {@code lots} held by {@link #hold} for a trade that did not go through. */
    public static void release(MinecraftServer server, Shop shop, int index, UUID player, boolean selling, int lots) {
        ShopItem item = shop.item(index);
        if (item == null || lots <= 0) {
            return;
        }
        int maxAll = selling ? item.sellGlobal : item.stockGlobal, maxPlayer = selling ? item.sellPlayer : item.stockPlayer;
        Refill allRule = selling ? item.sellRefill : item.stockRefill, playerRule = selling ? item.sellPlayerRule() : item.stockPlayerRule();
        if (maxAll < 0 && maxPlayer < 0) {
            return;
        }
        if (!sharedCounts()) {
            ShopLedger.Stock st = ledger(server).stock(shop.id, index);
            Map<UUID, ShopLedger.Count> players = selling ? st.sellers : st.buyers;
            ShopLedger.Count mine = players.computeIfAbsent(player, k -> new ShopLedger.Count());
            giveBack(selling ? st.sell : st.stock, mine, maxAll, maxPlayer, allRule, playerRule, lots);
            if (mine.left < 0) {
                players.remove(player);
            }
            ledger(server).setDirty();
            return;
        }
        counted("give back shop stock", store -> store.locked(shop.id, index, selling, player, (all, mine) -> {
            giveBack(all, mine, maxAll, maxPlayer, allRule, playerRule, lots);
            return new Held(lots, all.copy(), mine.copy());
        }), shop.id, h -> {
            if (h != null) {
                cache(server, shop, index, selling, player, h.all(), h.mine());
            }
        });
    }

    /** Copies a card side's counts, as the network answered them, into this server's ledger. */
    private static void cache(MinecraftServer server, Shop shop, int index, boolean selling, UUID player, ShopLedger.Count all, ShopLedger.Count mine) {
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        ShopLedger.Count here = selling ? st.sell : st.stock;
        here.left = all.left;
        here.at = all.at;
        Map<UUID, ShopLedger.Count> players = selling ? st.sellers : st.buyers;
        if (mine.left < 0) {
            players.remove(player);
        } else {
            players.put(player, mine.copy());
        }
        ledger(server).setDirty();
    }

    /**
     * On a network, copies a shop's counts (and a rotating shop's draw) from the database into this
     * server's ledger, then runs {@code then} (at once without a network).
     */
    public static void pull(MinecraftServer server, Shop shop, Runnable then) {
        if (!sharedCounts()) {
            then.run();
            return;
        }
        if (shop.kind == Shop.Kind.ROTATING && !ledger(server).draw(shop.id).asking) {
            ShopLedger.Draw mine = ledger(server).draw(shop.id);
            syncDraw(server, shop, now(), mine.shown.isEmpty() ? null : mine.copy());
        }
        counted("read shop counts", store -> store.read(shop.id), null, rows -> {
            if (rows != null) {
                ShopLedger ledger = ledger(server);
                ledger.clearCounts(shop.id);
                for (var e : rows.entrySet()) {
                    String[] k = e.getKey().split(":", 3);
                    try {
                        ShopLedger.Stock st = ledger.stock(shop.id, Integer.parseInt(k[0]));
                        boolean selling = k[1].equals("S");
                        if (k[2].isEmpty()) {
                            ShopLedger.Count here = selling ? st.sell : st.stock;
                            here.left = e.getValue().left;
                            here.at = e.getValue().at;
                        } else {
                            (selling ? st.sellers : st.buyers).put(UUID.fromString(k[2]), e.getValue().copy());
                        }
                    } catch (RuntimeException ignored) {
                        // not a count this version reads
                    }
                }
            }
            then.run();
        });
    }

    /** A shop replaced whole (an import): all its counts and its draw start again, here and on the network. */
    public static void restart(MinecraftServer server, Shop shop) {
        resetCounts(server, shop, 0, Integer.MAX_VALUE);
        ledger(server).clearCounts(shop.id);
        ledger(server).clearDraw(shop.id);
        if (sharedCounts()) {
            counted("start a replaced shop's draw again", store -> {
                store.dropDraw(shop.id);
                return Boolean.TRUE;
            }, shop.id, ok -> {});
        }
    }

    /** Every count of cards {@code from} to {@code to} full again, here and on the network (an edit, Refill now, cards that changed place). */
    public static void resetCounts(MinecraftServer server, Shop shop, int from, int to) {
        ShopLedger ledger = ledger(server);
        for (var e : ledger.counts().entrySet()) {
            String k = e.getKey();
            if (k.startsWith(shop.id + ":")) {
                try {
                    int i = Integer.parseInt(k.substring(shop.id.length() + 1));
                    if (i >= from && i <= to) {
                        e.getValue().reset();
                    }
                } catch (NumberFormatException ignored) {
                    // not a card count
                }
            }
        }
        ledger.setDirty();
        if (sharedCounts()) {
            counted("reset shop counts", store -> {
                store.reset(shop.id, from, to);
                return Boolean.TRUE;
            }, shop.id, ok -> {});
        }
    }

    /**
     * The first start with network storage: offers this server's counts to the database once. The
     * first server to do it keeps its counts there; the others drop theirs and take the database's.
     */
    public static void moveCountsOnce(MinecraftServer server, String serverId) {
        ShopLedger ledger = ledger(server);
        if (ledger.countsMoved || !sharedCounts()) {
            return;
        }
        Map<String, Map<String, ShopLedger.Count>> mine = new java.util.HashMap<>();
        for (var e : ledger.counts().entrySet()) {
            int cut = e.getKey().lastIndexOf(':');
            String shopId = e.getKey().substring(0, cut);
            int card;
            try {
                card = Integer.parseInt(e.getKey().substring(cut + 1));
            } catch (NumberFormatException ex) {
                continue;
            }
            Map<String, ShopLedger.Count> rows = mine.computeIfAbsent(shopId, k -> new java.util.HashMap<>());
            ShopLedger.Stock st = e.getValue();
            if (st.stock.left >= 0) {
                rows.put(CountStore.key(card, false, null), st.stock.copy());
            }
            if (st.sell.left >= 0) {
                rows.put(CountStore.key(card, true, null), st.sell.copy());
            }
            st.buyers.forEach((p, c) -> rows.put(CountStore.key(card, false, p), c.copy()));
            st.sellers.forEach((p, c) -> rows.put(CountStore.key(card, true, p), c.copy()));
        }
        counted("move this server's shop counts to the database", store -> store.moveOnce(serverId, mine), null, won -> {
            if (won == null) {
                return; // tried again at the next start
            }
            ledger.countsMoved = true;
            if (!won) {
                for (String id : List.copyOf(ledger.all().keySet())) {
                    ledger.clearCounts(id);
                }
            }
            ledger.setDirty();
            com.mrgregles.bsp_core.BSPCore.LOGGER.info("BSP-Core shops: {}", won ? "this server's shop counts are now the network's" : "another server's shop counts were moved first; this server's were dropped");
        });
    }

    /** The daily sweep: drops players' counts that are full again by their rule (lifetime limits stay). */
    public static void sweep(MinecraftServer server) {
        if (!sharedCounts()) {
            return;
        }
        long now = now();
        for (Shop shop : List.copyOf(ledger(server).all().values())) {
            boolean perPlayer = shop.all().stream().anyMatch(it -> it.stockPlayer >= 0 || it.sellPlayer >= 0);
            if (!perPlayer) {
                continue;
            }
            counted("read shop counts", store -> store.read(shop.id), null, rows -> {
                if (rows == null) {
                    return;
                }
                for (var e : rows.entrySet()) {
                    String[] k = e.getKey().split(":", 3);
                    if (k.length < 3 || k[2].isEmpty()) {
                        continue;
                    }
                    try {
                        int card = Integer.parseInt(k[0]);
                        boolean selling = k[1].equals("S");
                        ShopItem it = shop.item(card);
                        int max = it == null ? -1 : selling ? it.sellPlayer : it.stockPlayer;
                        ShopLedger.Count c = e.getValue().copy();
                        if (it != null && max >= 0) {
                            Refill rule = selling ? it.sellPlayerRule() : it.stockPlayerRule();
                            refill(c, max, rule, now, drawStamp(server, shop, rule, rule, now));
                        }
                        if (it == null || max < 0 || c.left < 0 || c.left >= max) {
                            UUID p = UUID.fromString(k[2]);
                            ShopLedger.Count read = e.getValue();
                            counted("sweep a shop count", store -> {
                                store.dropIf(shop.id, card, selling, p, read.left, read.at);
                                return Boolean.TRUE;
                            }, null, ok -> {});
                        }
                    } catch (RuntimeException ignored) {
                        // not a count this version reads
                    }
                }
            });
        }
    }

    /**
     * One side of a card as this player sees it: lots left and out of how many (the per-player
     * limit when it is the one that binds), what is left for everyone beside it, the rule that
     * refills the count shown and the time to that refill (-1 unknown).
     */
    public static ShopMenu.Limit limit(MinecraftServer server, Shop shop, int index, UUID player, boolean selling, long now) {
        ShopItem item = shop.item(index);
        if (item == null) {
            return ShopMenu.Limit.NONE;
        }
        int left = selling ? sellLeft(server, shop, index, player, now) : stockLeft(server, shop, index, player, now);
        if (left < 0) {
            return ShopMenu.Limit.NONE;
        }
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        int maxAll = selling ? item.sellGlobal : item.stockGlobal, maxPlayer = selling ? item.sellPlayer : item.stockPlayer;
        ShopLedger.Count all = selling ? st.sell : st.stock, mine = (selling ? st.sellers : st.buyers).get(player);
        int mineLeft = maxPlayer < 0 ? -1 : mine == null ? maxPlayer : mine.left;
        boolean perPlayer = maxPlayer >= 0 && (maxAll < 0 || mineLeft <= all.left);
        Refill rule = perPlayer ? (selling ? item.sellPlayerRule() : item.stockPlayerRule()) : (selling ? item.sellRefill : item.stockRefill);
        ShopLedger.Count shown = perPlayer ? mine : all;
        long refillIn = switch (rule.kind()) {
            case AFTER -> shown != null && shown.left == 0 && shown.at > now ? shown.at - now : -1;
            case DAILY, WEEKLY -> rule.nextAt(now, zone()) - now;
            case ROTATION -> shop.kind == Shop.Kind.ROTATING ? Math.max(0, nextChange(server, shop, now) - now) : -1;
            default -> -1;
        };
        return new ShopMenu.Limit(left, perPlayer ? maxPlayer : maxAll, perPlayer && maxAll >= 0 ? all.left : -1, rule.text(), refillIn);
    }

    /** The item indexes on show now: every visible item, or for a rotating shop the current draw (redrawn when due). */
    public static List<Integer> visible(MinecraftServer server, Shop shop, long now) {
        List<ShopItem> all = shop.all();
        List<Integer> out = new ArrayList<>();
        if (shop.kind != Shop.Kind.ROTATING) {
            for (int i = 0; i < all.size(); i++) {
                if (!all.get(i).hidden) {
                    out.add(i);
                }
            }
            return out;
        }
        ShopLedger.Draw d = ledger(server).draw(shop.id);
        if (due(d, shop, now)) {
            if (!sharedCounts()) {
                redraw(d, shop, now);
                ledger(server).setDirty();
                announce(server, shop, d);
            } else if (!d.asking && now >= d.askAfter) {
                // one draw for the network: the first server to ask makes it, the others take it; the old draw shows until the answer
                syncDraw(server, shop, now, null);
            }
        }
        List<Integer> shown = new ArrayList<>();
        for (int i : d.shown) {
            if (i < all.size()) {
                shown.add(i);
            }
        }
        return shown;
    }

    /**
     * True when a draw must be made: none yet; it names cards the shop no longer has; its time is up.
     * With a fixed change time, the time is up once that time has passed since the draw was made and
     * since the fixed time was set (a draw from before waits for the next one); otherwise at the
     * interval's end.
     */
    private static boolean due(ShopLedger.Draw d, Shop shop, long now) {
        int size = shop.all().size();
        if (d.shown.isEmpty() || d.shown.stream().anyMatch(i -> i >= size)) {
            return true;
        }
        if (shop.rotateAt.fixedTime()) {
            long last = shop.rotateAt.periodStart(now, zone());
            return last > d.drawnAt && last > shop.rotateSince;
        }
        return now >= d.nextAt;
    }

    /** When a rotating shop next changes: its fixed time, or the end of the current draw's interval. */
    public static long nextChange(MinecraftServer server, Shop shop, long now) {
        return shop.rotateAt.fixedTime() ? shop.rotateAt.nextAt(now, zone()) : ledger(server).draw(shop.id).nextAt;
    }

    /**
     * A new draw in {@code d}: {@code displaySlots} of the shop's cards that are not hidden and weigh
     * more than 0, picked by weight. With no repeats, from the cards not shown yet this cycle; when
     * fewer are left than there are places, those show and the rest come from a fresh cycle, never
     * the same card twice in one draw.
     */
    private static void redraw(ShopLedger.Draw d, Shop shop, long now) {
        List<ShopItem> all = shop.all();
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (!all.get(i).hidden && all.get(i).weight > 0) {
                pool.add(i);
            }
        }
        Random random = new Random(now ^ shop.id.hashCode());
        int slots = Math.min(shop.displaySlots, pool.size());
        List<Integer> picks;
        if (shop.noRepeat) {
            List<Integer> left = new ArrayList<>(pool);
            left.removeAll(d.used);
            if (left.size() >= slots) {
                picks = pick(shop, left, slots, random);
                d.used.retainAll(pool);
                d.used.addAll(picks);
            } else {
                picks = pick(shop, left, left.size(), random);
                List<Integer> fresh = new ArrayList<>(pool);
                fresh.removeAll(picks);
                List<Integer> more = pick(shop, fresh, slots - picks.size(), random);
                picks.addAll(more);
                d.used.clear(); // a new cycle, begun with the cards that filled the places
                d.used.addAll(more);
            }
        } else {
            picks = pick(shop, pool, slots, random);
            d.used.clear();
        }
        d.shown.clear();
        d.shown.addAll(picks);
        d.drawnAt = now;
        d.nextAt = shop.rotateAt.fixedTime() ? shop.rotateAt.nextAt(now, zone()) : now + Math.max(60_000L, shop.intervalMs);
    }

    /** Picks {@code n} of {@code from}, each pick by the cards' weights, none twice. */
    public static List<Integer> pick(Shop shop, List<Integer> from, int n, Random random) {
        List<Integer> left = new ArrayList<>(from), out = new ArrayList<>();
        while (out.size() < n && !left.isEmpty()) {
            int total = 0;
            for (int i : left) {
                total += Math.max(0, shop.item(i).weight);
            }
            int r = total <= 0 ? random.nextInt(left.size()) : random.nextInt(total), at = 0;
            if (total > 0) {
                for (int k = 0; k < left.size(); k++) {
                    r -= Math.max(0, shop.item(left.get(k)).weight);
                    if (r < 0) {
                        at = k;
                        break;
                    }
                }
            } else {
                at = r;
            }
            out.add(left.remove(at));
        }
        return out;
    }

    /** No repeats: the cycle starts again (cards changed place in the Workbench), here and on the network. */
    public static void restartCycle(MinecraftServer server, Shop shop) {
        if (shop.kind != Shop.Kind.ROTATING) {
            return;
        }
        ledger(server).draw(shop.id).used.clear();
        ledger(server).setDirty();
        if (sharedCounts()) {
            counted("start a rotating shop's cycle again", store -> store.drawLocked(shop.id, row -> {
                row.used.clear();
                return row;
            }), null, row -> {});
        }
    }

    // ------------------------------------------------------------------ announcing a new draw

    /** GameTests: hears every announcement instead of the players (null: back to the chat). */
    @Nullable
    private static Consumer<Component> announcements;

    public static void listenAnnouncements(@Nullable Consumer<Component> listener) {
        announcements = listener;
    }

    /**
     * Tells this server's players that a rotating shop with Announce on changed its cards, once per
     * draw: a draw newer than the last one told. The first draw a server sees is only noted.
     */
    private static void announce(MinecraftServer server, Shop shop, ShopLedger.Draw d) {
        if (d.announcedAt == 0 || d.drawnAt <= d.announcedAt) {
            if (d.announcedAt == 0) {
                d.announcedAt = d.drawnAt;
                ledger(server).setDirty();
            }
            return;
        }
        d.announcedAt = d.drawnAt;
        ledger(server).setDirty();
        if (!shop.broadcast || d.shown.isEmpty()) {
            return;
        }
        Component line = announcement(shop, d, d.drawnAt);
        if (announcements != null) {
            announcements.accept(line);
        } else {
            server.getPlayerList().broadcastSystemMessage(line, false);
        }
    }

    /** "[Shops] Challenge now buys Steel Casing, Machine Frame and Fluix Block. Next change Monday 00:00." Card names show the item when hovered. */
    public static Component announcement(Shop shop, ShopLedger.Draw d, long now) {
        List<ShopItem> cards = new ArrayList<>();
        for (int i : d.shown) {
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                cards.add(it);
            }
        }
        boolean buys = cards.stream().allMatch(it -> it.sellable() && !it.buyable()), sells = cards.stream().allMatch(it -> it.buyable() && !it.sellable());
        net.minecraft.network.chat.MutableComponent names = Component.empty();
        for (int k = 0; k < cards.size(); k++) {
            if (k > 0) {
                names.append(Component.literal(k == cards.size() - 1 ? " and " : ", ").withStyle(net.minecraft.ChatFormatting.WHITE));
            }
            ItemStack stack = cards.get(k).stack;
            names.append(stack.getHoverName().copy().withStyle(st -> st.withColor(net.minecraft.ChatFormatting.AQUA)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_ITEM, new net.minecraft.network.chat.HoverEvent.ItemStackInfo(stack)))));
        }
        String verb = buys ? "buys" : sells ? "sells" : "trades";
        Component next = shop.rotateAt.fixedTime()
                ? Component.translatable("message.bsp_core.shop.rotated_next_at", (shop.rotateAt.kind() == Refill.Kind.WEEKLY ? shop.rotateAt.day().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " : "") + shop.rotateAt.clock())
                : Component.translatable("message.bsp_core.shop.rotated_next_in", Math.max(1, (d.nextAt - now + 3_599_999L) / 3_600_000L));
        return Component.translatable("message.bsp_core.shop.rotated_tag").withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.translatable("message.bsp_core.shop.rotated_" + verb, shop.name, names).withStyle(net.minecraft.ChatFormatting.WHITE))
                .append(Component.literal(" "))
                .append(next.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** The minute check: rotating shops that announce make their new draw on time, whether or not anyone is looking. */
    public static void tickRotations(MinecraftServer server) {
        long now = now();
        for (Shop shop : List.copyOf(ledger(server).all().values())) {
            if (shop.kind == Shop.Kind.ROTATING && shop.broadcast) {
                visible(server, shop, now);
            }
        }
    }

    /**
     * On a network: takes the rotating shop's draw from the database under its lock, making a new
     * one there if it is due. With {@code adopt}, a database that has no draw yet takes that one
     * (this server's from before the network kept draws). This server's copy is updated from the
     * answer, and open screens of the shop are sent again when the draw changed.
     */
    private static void syncDraw(MinecraftServer server, Shop shop, long now, @Nullable ShopLedger.Draw adopt) {
        ShopLedger.Draw d = ledger(server).draw(shop.id);
        d.asking = true;
        counted("draw a rotating shop", store -> store.drawLocked(shop.id, row -> {
            if (adopt != null && row.shown.isEmpty() && row.nextAt == 0 && !adopt.shown.isEmpty()) {
                row.set(adopt);
            }
            if (due(row, shop, now)) {
                redraw(row, shop, now);
            }
            return row;
        }), shop.id, row -> {
            d.asking = false;
            if (row == null) {
                d.askAfter = now + 10_000L; // the database did not answer: ask again in a while
                return;
            }
            boolean changed = row.drawnAt != d.drawnAt || !row.shown.equals(d.shown);
            d.set(row);
            ledger(server).setDirty();
            announce(server, shop, d); // each server tells its own players, once, whoever made the draw
            if (changed) {
                ShopOpener.resyncOpen(server, shop.id);
            }
        });
    }

    // ------------------------------------------------------------------ buying and selling

    /** What locks card {@code index} for this player: the shop's own requirement first, then the card's; null when it is open to them. */
    @Nullable
    public static Requirement.Lock lock(Shop shop, int index, Player player) {
        Requirement.Lock l = shop.requires.check(player);
        if (l != null) {
            return l;
        }
        ShopItem it = shop.item(index);
        return it == null ? null : it.requires.check(player);
    }

    /** How many lots of {@code stack} fit in the player's inventory now. */
    public static int fits(Player player, ItemStack stack, int lots) {
        if (stack.isEmpty() || lots <= 0) {
            return 0;
        }
        var inv = player.getInventory();
        int room = 0, size = stack.getCount();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(s, stack)) {
                room += Math.max(0, s.getMaxStackSize() - s.getCount());
            }
        }
        return Math.min(lots, room / size);
    }

    /**
     * Buys {@code lots} of item {@code index}: checks the shop is open to buying, the stock, what fits,
     * pays (pieces through the bank, or coins from the inventory), gives the items and counts the stock.
     * With {@code test} nothing moves; the outcome says what would have happened.
     */
    public static void buy(MinecraftServer server, Player player, Shop shop, int index, int lots, boolean test, Consumer<Outcome> then) {
        long now = now();
        ShopItem item = shop.item(index);
        if (item == null || !item.buyable() || item.hidden) {
            then.accept(Outcome.fail("not_for_sale"));
            return;
        }
        if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(item.stack)) { // a card made from a placeholder would hand out copies of the item inside it
            then.accept(Outcome.fail("placeholder"));
            return;
        }
        if (lock(shop, index, player) != null) {
            then.accept(Outcome.fail("locked"));
            return;
        }
        if (shop.kind == Shop.Kind.ROTATING && !visible(server, shop, now).contains(index)) {
            then.accept(Outcome.fail("not_drawn"));
            return;
        }
        int left = stockLeft(server, shop, index, player.getUUID(), now);
        if (left == 0) {
            then.accept(Outcome.fail("sold_out"));
            return;
        }
        int n = Math.max(1, lots);
        if (left > 0) {
            n = Math.min(n, left);
        }
        n = fits(player, item.stack, n);
        if (n <= 0) {
            then.accept(Outcome.fail("no_room"));
            return;
        }
        if (test) {
            long cost = price(shop, item, true, now) * n;
            if (!canPay(server, player, item.currency, cost)) {
                then.accept(Outcome.fail("short"));
                return;
            }
            then.accept(new Outcome(true, "test_bought", n, cost, Bank.pieces(server, player.getUUID())));
            return;
        }
        // the lots are held first (on a network, under the database's lock), then paid for; a payment that fails gives them back
        hold(server, shop, index, player.getUUID(), false, n, now, held -> {
            if (held < 0) {
                then.accept(Outcome.fail("counts_unreachable"));
                return;
            }
            if (held == 0) {
                then.accept(Outcome.fail("sold_out"));
                return;
            }
            pay(server, player, shop, item, index, held, now, then);
        });
    }

    private static void pay(MinecraftServer server, Player player, Shop shop, ShopItem item, int index, int count, long now, Consumer<Outcome> then) {
        long cost = price(shop, item, true, now) * count;
        Consumer<BankResult> paid = r -> {
            if (!r.ok()) {
                release(server, shop, index, player.getUUID(), false, count);
                then.accept(Outcome.fail(r.key().equals("short") ? "short" : r.key()));
                return;
            }
            for (int i = 0; i < count; i++) {
                ItemStack give = item.stack.copy();
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
            }
            then.accept(new Outcome(true, "bought", count, cost, r.pieces()));
        };
        String what = count * item.stack.getCount() + " " + item.stack.getHoverName().getString();
        if (item.currency < 0) {
            Bank.take(server, player.getUUID(), cost, BankTransaction.Type.BOUGHT, shop.name, what, shop.id, itemId(item), paid);
        } else {
            CoinTier tier = CoinTier.values()[item.currency];
            if (!CoinWallet.pay(player, (int) Math.min(Integer.MAX_VALUE, cost * tier.value()))) {
                release(server, shop, index, player.getUUID(), false, count);
                then.accept(Outcome.fail("coins_short"));
                return;
            }
            Bank.record(server, player.getUUID(), new BankTransaction(now, BankTransaction.Type.BOUGHT, shop.name, what + ", " + cost + " " + Bank.tierName(tier) + " coins", 0, item.currency, (int) -cost, Bank.serverId(), shop.id, itemId(item)));
            paid.accept(BankResult.ok(Bank.pieces(server, player.getUUID())));
        }
    }

    /**
     * Sells up to {@code lots} of item {@code index} from the player's inventory; fails when the shop
     * is buy-only, the card is not in a rotating shop's draw, they hold none, or its sell limit is
     * used up. A sale that would pass the limit sells only what is left ({@code sold_capped}).
     */
    public static void sell(MinecraftServer server, Player player, Shop shop, int index, int lots, boolean test, Consumer<Outcome> then) {
        long now = now();
        ShopItem item = shop.item(index);
        if (item == null || shop.buyOnly || !item.sellable()) {
            then.accept(Outcome.fail("not_bought"));
            return;
        }
        if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(item.stack)) { // sold, the original inside a placeholder would be gone for good
            then.accept(Outcome.fail("placeholder"));
            return;
        }
        if (lock(shop, index, player) != null) {
            then.accept(Outcome.fail("locked"));
            return;
        }
        if (shop.kind == Shop.Kind.ROTATING && !visible(server, shop, now).contains(index)) {
            then.accept(Outcome.fail("not_drawn"));
            return;
        }
        int want = Math.min(Math.max(1, lots), held(player, shop, index) / item.stack.getCount());
        if (want <= 0) {
            then.accept(Outcome.fail(refused(player, shop, index)));
            return;
        }
        int room = sellLeft(server, shop, index, player.getUUID(), now);
        if (room == 0) {
            then.accept(Outcome.fail("sell_full"));
            return;
        }
        int n = room > 0 ? Math.min(want, room) : want;
        long unit = price(shop, item, false, now);
        if (test) {
            then.accept(new Outcome(true, "test_sold", n, unit * n, Bank.pieces(server, player.getUUID())));
            return;
        }
        // the room is held first; what the player holds is counted again when the answer comes (on a network it comes a moment later)
        hold(server, shop, index, player.getUUID(), true, n, now, got -> {
            if (got < 0) {
                then.accept(Outcome.fail("counts_unreachable"));
                return;
            }
            if (got == 0) {
                then.accept(Outcome.fail("sell_full"));
                return;
            }
            int lots2 = Math.min(got, held(player, shop, index) / item.stack.getCount());
            if (lots2 < got) {
                release(server, shop, index, player.getUUID(), true, got - lots2);
            }
            if (lots2 <= 0) {
                then.accept(Outcome.fail("none_held"));
                return;
            }
            String done = lots2 < want ? "sold_capped" : "sold";
            long paid = unit * lots2;
            player.getInventory().clearOrCountMatchingItems(s -> sells(shop, index, s), lots2 * item.stack.getCount(), player.inventoryMenu.getCraftSlots());
            player.inventoryMenu.broadcastChanges();
            String what = lots2 * item.stack.getCount() + " " + item.stack.getHoverName().getString();
            if (item.currency < 0) {
                Bank.give(server, player.getUUID(), paid, BankTransaction.Type.SOLD, shop.name, what, shop.id, itemId(item), r -> then.accept(new Outcome(r.ok(), r.ok() ? done : r.key(), lots2, paid, r.pieces())));
            } else {
                CoinTier tier = CoinTier.values()[item.currency];
                CoinWallet.give(player, (int) Math.min(Integer.MAX_VALUE, paid * tier.value()));
                Bank.record(server, player.getUUID(), new BankTransaction(now, BankTransaction.Type.SOLD, shop.name, what + ", " + paid + " " + Bank.tierName(tier) + " coins", 0, item.currency, (int) paid, Bank.serverId(), shop.id, itemId(item)));
                then.accept(new Outcome(true, done, lots2, paid, Bank.pieces(server, player.getUUID())));
            }
        });
    }

    /** A card's item as the bank log names it for the shop report: its registry id. */
    public static String itemId(ShopItem item) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** How many items the player holds that may be sold to card {@code index} ({@link #sells}). */
    private static int held(Player player, Shop shop, int index) {
        int held = 0;
        for (ItemStack s : player.getInventory().items) {
            if (sells(shop, index, s)) {
                held += s.getCount();
            }
        }
        return held;
    }

    /**
     * True when {@code s} may be sold to card {@code index}: the card takes it and does not refuse
     * it, and, for a card matching by item, no card of the shop takes it exactly (the exact card wins).
     */
    public static boolean sells(Shop shop, int index, ItemStack s) {
        ShopItem item = shop.item(index);
        if (item == null || s.isEmpty() || !item.takes(s) || item.refusal(s) != null) {
            return false;
        }
        if (item.matchItem) {
            for (ShopItem other : shop.all()) {
                if (other != item && !other.matchItem && other.sellable() && ItemStack.isSameItemSameTags(s, other.stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Why a sale found nothing to sell: what the card refused the player's items for, or that they hold none. */
    private static String refused(Player player, Shop shop, int index) {
        ShopItem item = shop.item(index);
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && item.takes(s) && item.refusal(s) != null) {
                return item.refusal(s);
            }
        }
        return "none_held";
    }

    /**
     * Sells everything the player holds that this shop buys (a rotating shop: only what is on show),
     * one item after another, each up to its sell limit; {@code then} gets the lots and the pieces
     * total, with {@code sold_all_capped} when a limit kept some back.
     */
    public static void sellAll(MinecraftServer server, Player player, Shop shop, boolean test, Consumer<Outcome> then) {
        List<Integer> indexes = new ArrayList<>();
        List<ShopItem> all = shop.all();
        List<Integer> shown = shop.kind == Shop.Kind.ROTATING ? visible(server, shop, now()) : null;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).sellable() && all.get(i).currency < 0 && !com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(all.get(i).stack) && (shown == null || shown.contains(i))
                    && lock(shop, i, player) == null) {
                indexes.add(i);
            }
        }
        indexes.sort((x, y) -> Boolean.compare(all.get(x).matchItem, all.get(y).matchItem)); // exact cards first: they win their items
        sellNext(server, player, shop, indexes, 0, 0, 0, false, test, then);
    }

    private static void sellNext(MinecraftServer server, Player player, Shop shop, List<Integer> indexes, int at, int lots, long total, boolean capped, boolean test, Consumer<Outcome> then) {
        if (at >= indexes.size()) {
            String key = lots > 0 ? (test ? "test_sold" : capped ? "sold_all_capped" : "sold_all") : capped ? "sell_full" : "none_held";
            then.accept(new Outcome(lots > 0, key, lots, total, Bank.pieces(server, player.getUUID())));
            return;
        }
        sell(server, player, shop, indexes.get(at), Integer.MAX_VALUE, test, o -> sellNext(server, player, shop, indexes, at + 1, lots + (o.ok() ? o.lots() : 0), total + (o.ok() ? o.amount() : 0),
                capped || o.key().equals("sold_capped") || o.key().equals("sell_full"), test, then));
    }

    private static boolean canPay(MinecraftServer server, Player player, int currency, long cost) {
        if (currency < 0) {
            return Bank.pieces(server, player.getUUID()) >= cost;
        }
        return CoinWallet.totalValue(player) >= cost * CoinTier.values()[currency].value();
    }

    /** True while any visible item card can still trade (see {@link #live}). */
    public static boolean anyStock(MinecraftServer server, Shop shop) {
        long now = now();
        for (int i : visible(server, shop, now)) {
            if (live(server, shop, i, now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True while an item card can still trade for someone: a card players buy while it has stock; a
     * card the shop only buys while it has room shop-wide; a card with neither side on is live (it
     * only shows the item). Per-player limits are not counted here (each player has their own).
     */
    public static boolean live(MinecraftServer server, Shop shop, int index, long now) {
        ShopItem it = shop.item(index);
        if (it == null || !it.section.isEmpty() || it.stack.isEmpty()) {
            return false;
        }
        boolean buys = it.buyable(), sells = it.sellable() && !shop.buyOnly;
        if (buys) {
            return stockLeft(server, shop, index, net.minecraft.Util.NIL_UUID, now) != 0;
        }
        return !sells || sellLeft(server, shop, index, net.minecraft.Util.NIL_UUID, now) != 0;
    }

    /** The shop's first visible item card, for a stall's counter; empty if it has none. */
    public static net.minecraft.world.item.ItemStack firstItem(MinecraftServer server, Shop shop) {
        for (int i : visible(server, shop, now())) {
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                return it.stack.copy();
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
