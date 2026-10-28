package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.market.Market;
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
        s.name = name.length() > Shop.NAME_MAX ? name.substring(0, Shop.NAME_MAX) : name;
        s.kind = kind;
        save(server, s);
        return s;
    }

    /** {@code text} cut from the front to at most {@code max} characters, with "…" where it was cut (a long path of section names). */
    public static String fit(String text, int max) {
        return text.length() <= max ? text : "…" + text.substring(text.length() - (max - 1));
    }

    /** Writes a shop locally and, on a network, to the database (which tells the other servers). */
    public static void save(MinecraftServer server, Shop shop) {
        touch(shop);
        ledger(server).put(shop);
        NetworkStorage.shopWrite(shop.id, shop.name, shop.save().toString());
    }

    /** Marks the record changed: its edit stamp moves on (never back, whatever the clocks of the servers say) and is returned. */
    static long touch(Shop shop) {
        shop.edited = Math.max(now(), shop.edited + 1);
        return shop.edited;
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
                countNotice(shop.id, index, selling, player), h -> {
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
        }), countNotice(shop.id, index, selling, player), h -> {
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
            // the network's draw as it stands, read without a lock; a draw that is due is made by the next look (visible)
            counted("read a rotating shop's draw", store -> store.readDraw(shop.id), null, row -> {
                if (row != null && (row.drawnAt != 0 || row.forShop != 0)) { // a row never drawn and never emptied by an import says nothing
                    took(server, shop, row);
                }
            });
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

    /**
     * The notice other servers hear after a trade: the shop, the card side and the player whose counts
     * changed ("shop|card|B or S|player"), so they read just those two rows. A notice of the shop id
     * alone (a reset, a moved card, a new draw) has them read the whole shop.
     */
    public static String countNotice(String shopId, int card, boolean selling, UUID player) {
        return shopId + "|" + card + "|" + (selling ? "S" : "B") + "|" + player;
    }

    /** Another server changed a shop's counts or draw (Redis kind {@code shopcount}): read what the notice names, then send open screens of the shop again. */
    public static void heard(MinecraftServer server, String notice) {
        String[] p = notice.split("\\|");
        Shop shop = ledger(server).shop(p[0]);
        if (shop == null) {
            return;
        }
        if (p.length != 4) {
            pull(server, shop, () -> ShopOpener.resyncOpen(server, shop.id));
            return;
        }
        int card;
        boolean selling = p[2].equals("S");
        UUID player;
        try {
            card = Integer.parseInt(p[1]);
            player = UUID.fromString(p[3]);
        } catch (RuntimeException e) {
            return; // not a notice this version reads
        }
        counted("read a card's counts", store -> store.readCard(shop.id, card, selling, player), null, rows -> {
            if (rows == null) {
                return;
            }
            ShopLedger.Stock st = ledger(server).stock(shop.id, card);
            ShopLedger.Count all = rows.get(CountStore.key(card, selling, null)), mine = rows.get(CountStore.key(card, selling, player));
            ShopLedger.Count here = selling ? st.sell : st.stock;
            here.left = all == null ? -1 : all.left; // no row: full
            here.at = all == null ? 0 : all.at;
            if (mine == null) {
                (selling ? st.sellers : st.buyers).remove(player);
            } else {
                (selling ? st.sellers : st.buyers).put(player, mine.copy());
            }
            ledger(server).setDirty();
            ShopOpener.resyncOpen(server, shop.id);
        });
    }

    /**
     * A shop replaced whole (an import), with {@code shop} the new record already saved: all its
     * counts and its draw start again, here and on the network. The network's draw is emptied for
     * this record, so a server still holding the old one cannot draw for the old cards.
     */
    public static void restart(MinecraftServer server, Shop shop) {
        resetCounts(server, shop, 0, Integer.MAX_VALUE);
        ledger(server).clearCounts(shop.id);
        ledger(server).clearDraw(shop.id);
        if (sharedCounts()) {
            long record = shop.edited;
            counted("start a replaced shop's draw again", store -> store.drawLocked(shop.id, row -> {
                row.clear();
                row.forShop = record;
                return row;
            }), shop.id, row -> {});
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
        for (Shop shop : List.copyOf(ledger.all().values())) {
            offerDraw(server, shop);
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

    /**
     * The first start with network storage: offers a rotating shop's draw from before the network kept
     * draws; the network takes it only when it has none yet. Never later: a server's own draw may be
     * for cards an import or the Workbench has since moved.
     */
    public static void offerDraw(MinecraftServer server, Shop shop) {
        ShopLedger.Draw mine = ledger(server).draw(shop.id);
        if (shop.kind == Shop.Kind.ROTATING && sharedCounts() && !mine.asking && !mine.shown.isEmpty()) {
            syncDraw(server, shop, now(), mine.copy());
        }
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
        Pool pool = Pool.of(shop);
        if (due(d, pool, now)) {
            if (!sharedCounts()) {
                redraw(d, pool, now);
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
     * What a draw reads of a shop, copied on the server thread so a draw made on the storage thread
     * never reads the record while the Workbench changes it: how many cards, each card's weight (-1
     * hidden), the places on show, no repeats, the change time and since when, the interval, the
     * record's edit stamp, and the shop's own seed for the draw.
     */
    record Pool(int size, int[] weights, int slots, boolean noRepeat, Refill rotateAt, long rotateSince, long intervalMs, long edited, int seed) {
        static Pool of(Shop shop) {
            List<ShopItem> all = shop.all();
            int[] w = new int[all.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = all.get(i).hidden ? -1 : all.get(i).weight;
            }
            return new Pool(w.length, w, shop.displaySlots, shop.noRepeat, shop.rotateAt, shop.rotateSince, shop.intervalMs, shop.edited, shop.id.hashCode());
        }

        /** True when any card can be drawn. */
        boolean any() {
            for (int w : weights) {
                if (w > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * True when a draw must be made: none yet; it names cards the shop no longer has; it is empty while
     * cards could be drawn; its time is up. With a fixed change time, the time is up once that time
     * has passed since the draw was made and since the fixed time was set (a draw from before waits
     * for the next one); otherwise at the interval's end. An empty draw of a shop with nothing to draw
     * waits for its time like any other.
     */
    private static boolean due(ShopLedger.Draw d, Pool pool, long now) {
        if (d.drawnAt == 0 || d.shown.stream().anyMatch(i -> i >= pool.size()) || d.shown.isEmpty() && pool.any()) {
            return true;
        }
        if (pool.rotateAt().fixedTime()) {
            long last = pool.rotateAt().periodStart(now, zone());
            return last > d.drawnAt && last > pool.rotateSince();
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
    private static void redraw(ShopLedger.Draw d, Pool p, long now) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < p.size(); i++) {
            if (p.weights()[i] > 0) {
                pool.add(i);
            }
        }
        Random random = new Random(now ^ p.seed());
        int slots = Math.min(p.slots(), pool.size());
        List<Integer> picks;
        if (p.noRepeat()) {
            List<Integer> left = new ArrayList<>(pool);
            left.removeAll(d.used);
            if (left.size() >= slots) {
                picks = pick(p.weights(), left, slots, random);
                d.used.retainAll(pool);
                d.used.addAll(picks);
            } else {
                picks = pick(p.weights(), left, left.size(), random);
                List<Integer> fresh = new ArrayList<>(pool);
                fresh.removeAll(picks);
                List<Integer> more = pick(p.weights(), fresh, slots - picks.size(), random);
                picks.addAll(more);
                d.used.clear(); // a new cycle, begun with the cards that filled the places
                d.used.addAll(more);
            }
        } else {
            picks = pick(p.weights(), pool, slots, random);
            d.used.clear();
        }
        d.shown.clear();
        d.shown.addAll(picks);
        d.drawnAt = now;
        d.nextAt = p.rotateAt().fixedTime() ? p.rotateAt().nextAt(now, zone()) : now + Math.max(60_000L, p.intervalMs());
    }

    /** Picks {@code n} of {@code from}, each pick by the cards' weights, none twice. */
    public static List<Integer> pick(Shop shop, List<Integer> from, int n, Random random) {
        return pick(Pool.of(shop).weights(), from, n, random);
    }

    private static List<Integer> pick(int[] weights, List<Integer> from, int n, Random random) {
        List<Integer> left = new ArrayList<>(from), out = new ArrayList<>();
        while (out.size() < n && !left.isEmpty()) {
            int total = 0;
            for (int i : left) {
                total += Math.max(0, weights[i]);
            }
            int r = total <= 0 ? random.nextInt(left.size()) : random.nextInt(total), at = 0;
            if (total > 0) {
                for (int k = 0; k < left.size(); k++) {
                    r -= Math.max(0, weights[left.get(k)]);
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

    /**
     * Cards changed place in the Workbench ({@code before}: the cards as they were): a rotating shop's
     * draw on show and its no-repeat cycle follow the cards to their new places, here and on the
     * network, so the same cards stay on show; a removed card leaves the draw one short until the next
     * change.
     */
    public static void cardsMoved(MinecraftServer server, Shop shop, List<ShopItem> before) {
        if (shop.kind != Shop.Kind.ROTATING) {
            return;
        }
        List<ShopItem> after = shop.all();
        int[] to = new int[before.size()];
        for (int i = 0; i < to.length; i++) {
            to[i] = -1;
            for (int k = 0; k < after.size(); k++) {
                if (after.get(k) == before.get(i)) {
                    to[i] = k;
                    break;
                }
            }
        }
        long record = touch(shop);
        move(ledger(server).draw(shop.id), to);
        ledger(server).setDirty();
        if (sharedCounts()) {
            counted("move a rotating shop's draw with its cards", store -> store.drawLocked(shop.id, row -> {
                move(row, to);
                row.forShop = record;
                return row;
            }), shop.id, row -> {});
        }
    }

    /** Each place in the draw and the cycle to where its card went ({@code to}: old place to new, -1 removed). */
    private static void move(ShopLedger.Draw d, int[] to) {
        for (List<Integer> places : List.of(d.shown, d.used)) {
            List<Integer> moved = new ArrayList<>();
            for (int i : places) {
                if (i >= 0 && i < to.length && to[i] >= 0) {
                    moved.add(to[i]);
                }
            }
            places.clear();
            places.addAll(moved);
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
        Pool pool = Pool.of(shop); // the storage thread reads this copy, never the record
        java.util.concurrent.atomic.AtomicBoolean made = new java.util.concurrent.atomic.AtomicBoolean();
        counted("draw a rotating shop", store -> store.drawLocked(shop.id, row -> {
            if (adopt != null && row.shown.isEmpty() && row.drawnAt == 0 && row.forShop <= pool.edited() && !adopt.shown.isEmpty()) {
                row.set(adopt);
                row.forShop = pool.edited();
                made.set(true);
            }
            if (pool.edited() >= row.forShop && due(row, pool, now)) { // a server with an older record of the shop never draws
                redraw(row, pool, now);
                row.forShop = pool.edited();
                made.set(true);
            }
            return row;
        }), null, row -> {
            d.asking = false;
            if (row == null) {
                d.askAfter = now + 10_000L; // the database did not answer: ask again in a while
                return;
            }
            if (made.get()) {
                tell(shop.id); // only the server that made or adopted the draw tells the others
            }
            took(server, shop, row);
        });
    }

    /** This server's copy takes the network's draw; its players hear of a new one once, and open screens are sent again when it changed. */
    private static void took(MinecraftServer server, Shop shop, ShopLedger.Draw row) {
        ShopLedger.Draw d = ledger(server).draw(shop.id);
        boolean changed = row.drawnAt != d.drawnAt || !row.shown.equals(d.shown);
        d.set(row);
        ledger(server).setDirty();
        announce(server, shop, d); // each server tells its own players, once, whoever made the draw
        if (changed) {
            ShopOpener.resyncOpen(server, shop.id);
        }
    }

    /** Tells the other servers a shop's counts or draw changed (nothing with a GameTest's stand-in). */
    private static void tell(String notice) {
        if (testStore == null) {
            com.mrgregles.bsp_core.storage.NetworkStorage.tellShopCount(notice);
        }
    }

    // ------------------------------------------------------------------ buying and selling

    // ------------------------------------------------------------------ the shop tree and requirements

    /** The most steps a walk up a shop's parents takes, and the deepest a tree may be: a loop the checks missed can never hang the server. */
    public static final int MAX_DEPTH = 16;

    /** The shop's parents, nearest first: at most {@link #MAX_DEPTH}, ending at a missing parent or one already passed. */
    public static List<Shop> parents(MinecraftServer server, Shop shop) {
        List<Shop> out = new ArrayList<>();
        Shop p = shop;
        while (out.size() < MAX_DEPTH && !p.parent.isEmpty()) {
            p = ledger(server).shop(p.parent);
            if (p == null || p == shop || out.contains(p)) {
                break;
            }
            out.add(p);
        }
        return out;
    }

    /**
     * True when shop {@code id} under {@code parent} would make its parents go round in a loop (or
     * deeper than {@link #MAX_DEPTH}), with {@code parentOf} giving each shop's parent (null for a
     * shop it does not know).
     */
    public static boolean loops(String id, String parent, java.util.function.Function<String, String> parentOf) {
        String p = parent;
        for (int step = 0; step <= MAX_DEPTH; step++) {
            if (p == null || p.isEmpty()) {
                return false;
            }
            if (p.equals(id)) {
                return true;
            }
            p = parentOf.apply(p);
        }
        return true;
    }

    /** What locks a whole shop for this player: its own requirement, then each parent's (a shop's requirement covers everything under it); null when it is open to them. */
    @Nullable
    public static Requirement.Lock shopLock(@Nullable MinecraftServer server, Shop shop, Player player) {
        Requirement.Lock l = shop.requires.check(player);
        if (l != null || server == null) {
            return l;
        }
        for (Shop p : parents(server, shop)) {
            l = p.requires.check(player);
            if (l != null) {
                return l;
            }
        }
        return null;
    }

    /**
     * What locks card {@code index} for this player: the shop's requirement and its parents' first,
     * then the card's; a section card is also locked as the shop it opens. Null when it is open to them.
     */
    @Nullable
    public static Requirement.Lock lock(Shop shop, int index, Player player) {
        MinecraftServer server = player.getServer();
        return cardLock(server, shopLock(server, shop, player), shop, index, player);
    }

    /** As {@link #lock}, with the shop's own lock ({@link #shopLock}) already known: one look-up of the shop for a whole page. */
    @Nullable
    public static Requirement.Lock cardLock(@Nullable MinecraftServer server, @Nullable Requirement.Lock shopLock, Shop shop, int index, Player player) {
        Requirement.Lock l = shopLock;
        if (l != null) {
            return l;
        }
        ShopItem it = shop.item(index);
        if (it == null) {
            return null;
        }
        l = it.requires.check(player);
        if (l != null || it.section.isEmpty() || server == null) {
            return l;
        }
        Shop opens = ledger(server).shop(it.section);
        return opens == null ? null : shopLock(server, opens, player);
    }

    /**
     * True when a requirement hides card {@code index} from some players (its own, or its shop's or a
     * parent's): such a card never shows on a stall's counter or a Stall Display, which every
     * passer-by sees, and does not count as the venue's stock.
     */
    public static boolean hiddenFromSome(MinecraftServer server, Shop shop, int index) {
        if (hides(shop.requires) || parents(server, shop).stream().anyMatch(p -> hides(p.requires))) {
            return true;
        }
        ShopItem it = shop.item(index);
        return it != null && hides(it.requires);
    }

    private static boolean hides(Requirement r) {
        return r.any() && r.hide();
    }

    /**
     * True when the player has left: logged out, gone to another server, or replaced by a respawn.
     * Their inventory is saved by then, so a trade answered later must not touch this old copy.
     */
    public static boolean gone(Player player) {
        return player.isRemoved() || player instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected();
    }

    /** GameTests: sees every bank answer to a trade first and may change it (null: as the bank answered). Set and put back within one tick. */
    @Nullable
    private static java.util.function.UnaryOperator<BankResult> bankAnswer;

    public static void useBankAnswer(@Nullable java.util.function.UnaryOperator<BankResult> answer) {
        bankAnswer = answer;
    }

    private static BankResult answered(BankResult r) {
        var a = bankAnswer;
        return a == null ? r : a.apply(r);
    }

    /**
     * Items owed to a player whose trade ended after they could take them: into their inventory
     * (dropped if full) while they are here, otherwise handed over where they next log in.
     */
    private static void owe(MinecraftServer server, Player player, List<ItemStack> items) {
        if (gone(player)) {
            com.mrgregles.bsp_core.admin.AdminService.deliver(server, player.getUUID(), items, "message.bsp_core.shop.owed");
            return;
        }
        for (ItemStack s : items) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
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
            if (gone(player)) { // left while the network answered: nothing is paid or handed out
                release(server, shop, index, player.getUUID(), false, held);
                then.accept(Outcome.fail("gone"));
                return;
            }
            pay(server, player, shop, item, index, held, now, then);
        });
    }

    private static void pay(MinecraftServer server, Player player, Shop shop, ShopItem item, int index, int count, long now, Consumer<Outcome> then) {
        long cost = price(shop, item, true, now) * count;
        String what = count * item.stack.getCount() + " " + item.stack.getHoverName().getString();
        // what was really paid, in pieces: coins by their bank worth, and only as many as the wallet takes (it takes an int of copper)
        long worth = item.currency < 0 ? cost : Math.min(Integer.MAX_VALUE, cost * CoinTier.values()[item.currency].value()) * BSPConfig.getOr(BSPConfig.BANK_PIECES_PER_COPPER, 1000);
        Consumer<BankResult> paid = answer -> {
            BankResult r = answered(answer);
            if (!r.ok()) {
                release(server, shop, index, player.getUUID(), false, count);
                then.accept(Outcome.fail(r.key().equals("short") ? "short" : r.key()));
                return;
            }
            if (gone(player)) { // paid after they left (the network's bank answers a moment later): the items wait for their next login
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    items.add(item.stack.copy());
                }
                owe(server, player, items);
            } else {
                for (int i = 0; i < count; i++) {
                    ItemStack give = item.stack.copy();
                    if (!player.getInventory().add(give)) {
                        player.drop(give, false);
                    }
                }
            }
            Market.toPot(server, lotteryShare(worth), player.getName().getString(), "shop share: " + what + " at " + shop.name);
            then.accept(new Outcome(true, "bought", count, cost, r.pieces()));
        };
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

    /** The lottery pot's share of a purchase worth {@code pieces}: {@code shop.lotteryPercent} of it, rounded down. */
    public static long lotteryShare(long pieces) {
        int pct = BSPConfig.getOr(BSPConfig.SHOP_LOTTERY_PERCENT, 50);
        return pieces <= 0 ? 0 : pieces / 100 * pct + pieces % 100 * pct / 100; // never overflows, whatever the price
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
            if (gone(player)) { // left while the network answered: their saved inventory keeps the items, so nothing is taken or paid
                release(server, shop, index, player.getUUID(), true, got);
                then.accept(Outcome.fail("gone"));
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
            List<ItemStack> taken = takeItems(player, shop, index, lots2 * item.stack.getCount());
            player.inventoryMenu.broadcastChanges();
            String what = lots2 * item.stack.getCount() + " " + item.stack.getHoverName().getString();
            if (item.currency < 0) {
                Bank.give(server, player.getUUID(), paid, BankTransaction.Type.SOLD, shop.name, what, shop.id, itemId(item), answer -> {
                    BankResult r = answered(answer);
                    if (!r.ok()) { // the bank did not take the credit: the items and the room go back
                        owe(server, player, taken);
                        release(server, shop, index, player.getUUID(), true, lots2);
                        then.accept(Outcome.fail("credit_failed"));
                        return;
                    }
                    then.accept(new Outcome(true, done, lots2, paid, r.pieces()));
                });
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

    /** Takes {@code count} of the items {@link #held} counts from the inventory and returns them as taken, to give back if the sale fails. */
    private static List<ItemStack> takeItems(Player player, Shop shop, int index, int count) {
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (count <= 0) {
                break;
            }
            if (sells(shop, index, s)) {
                ItemStack part = s.split(Math.min(count, s.getCount()));
                count -= part.getCount();
                taken.add(part);
            }
        }
        return taken;
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
            for (List<ShopItem> page : shop.pages) {
                for (ShopItem other : page) {
                    if (other != item && !other.matchItem && other.sellable() && ItemStack.isSameItemSameTags(s, other.stack)) {
                        return false;
                    }
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
        Requirement.Lock shopLock = shopLock(server, shop, player);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).sellable() && all.get(i).currency < 0 && !com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(all.get(i).stack) && (shown == null || shown.contains(i))
                    && cardLock(server, shopLock, shop, i, player) == null) {
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
        sell(server, player, shop, indexes.get(at), Integer.MAX_VALUE, test, o -> sellNext(server, player, shop, indexes, o.key().equals("gone") ? indexes.size() : at + 1, // they left: no more cards
                lots + (o.ok() ? o.lots() : 0), total + (o.ok() ? o.amount() : 0), capped || o.key().equals("sold_capped") || o.key().equals("sell_full"), test, then));
    }

    private static boolean canPay(MinecraftServer server, Player player, int currency, long cost) {
        if (currency < 0) {
            return Bank.pieces(server, player.getUUID()) >= cost;
        }
        return CoinWallet.totalValue(player) >= cost * CoinTier.values()[currency].value();
    }

    /** True while any visible item card can still trade (see {@link #live}; cards a requirement hides from some players never count). */
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
        if (it == null || !it.section.isEmpty() || it.stack.isEmpty() || hiddenFromSome(server, shop, index)) {
            return false;
        }
        boolean buys = it.buyable(), sells = it.sellable() && !shop.buyOnly;
        if (buys) {
            return stockLeft(server, shop, index, net.minecraft.Util.NIL_UUID, now) != 0;
        }
        return !sells || sellLeft(server, shop, index, net.minecraft.Util.NIL_UUID, now) != 0;
    }

    /** The shop's first visible item card that no requirement hides, for a stall's counter; empty if it has none. */
    public static net.minecraft.world.item.ItemStack firstItem(MinecraftServer server, Shop shop) {
        for (int i : visible(server, shop, now())) {
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty() && !hiddenFromSome(server, shop, i)) {
                return it.stack.copy();
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
