package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

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

    /** Lots left for this player (global and per player, whichever is smaller); -1 unlimited. Refills countdowns that are due. */
    public static int stockLeft(MinecraftServer server, Shop shop, int index, UUID player, long now) {
        ShopItem item = shop.item(index);
        if (item == null) {
            return 0;
        }
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        int left = -1;
        if (item.stockGlobal >= 0) {
            if (st.left < 0 || (st.left == 0 && item.restockMs > 0 && st.restockAt > 0 && now >= st.restockAt)) {
                st.left = item.stockGlobal;
                st.restockAt = 0;
                st.perPlayer.clear();
                ledger(server).setDirty();
            }
            left = st.left;
        }
        if (item.stockPlayer >= 0) {
            int mine = st.perPlayer.getOrDefault(player, item.stockPlayer);
            left = left < 0 ? mine : Math.min(left, mine);
        }
        return left;
    }

    private static void takeStock(MinecraftServer server, Shop shop, int index, UUID player, int lots, long now) {
        ShopItem item = shop.item(index);
        ShopLedger.Stock st = ledger(server).stock(shop.id, index);
        if (item.stockGlobal >= 0) {
            st.left = Math.max(0, st.left - lots);
            if (st.left == 0 && item.restockMs > 0) {
                st.restockAt = now + item.restockMs;
            }
        }
        if (item.stockPlayer >= 0) {
            st.perPlayer.put(player, Math.max(0, st.perPlayer.getOrDefault(player, item.stockPlayer) - lots));
        }
        ledger(server).setDirty();
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
        if (d.shown.isEmpty() || now >= d.nextAt || d.shown.stream().anyMatch(i -> i >= all.size())) {
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (!all.get(i).hidden) {
                    pool.add(i);
                }
            }
            Collections.shuffle(pool, new Random(now ^ shop.id.hashCode()));
            d.shown.clear();
            d.shown.addAll(pool.subList(0, Math.min(shop.displaySlots, pool.size())));
            d.nextAt = now + Math.max(60_000L, shop.intervalMs);
            ledger(server).setDirty();
        }
        return new ArrayList<>(d.shown);
    }

    // ------------------------------------------------------------------ buying and selling

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
        long now = System.currentTimeMillis();
        ShopItem item = shop.item(index);
        if (item == null || !item.buyable() || item.hidden) {
            then.accept(Outcome.fail("not_for_sale"));
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
        long unit = price(shop, item, true, now), cost = unit * n;
        int count = n;
        Consumer<BankResult> paid = r -> {
            if (!r.ok()) {
                then.accept(Outcome.fail(r.key().equals("short") ? "short" : r.key()));
                return;
            }
            if (!test) {
                for (int i = 0; i < count; i++) {
                    ItemStack give = item.stack.copy();
                    if (!player.getInventory().add(give)) {
                        player.drop(give, false);
                    }
                }
                takeStock(server, shop, index, player.getUUID(), count, now);
            }
            then.accept(new Outcome(true, test ? "test_bought" : "bought", count, cost, r.pieces()));
        };
        if (test) {
            if (!canPay(server, player, item.currency, cost)) {
                then.accept(Outcome.fail("short"));
                return;
            }
            paid.accept(BankResult.ok(Bank.pieces(server, player.getUUID())));
            return;
        }
        String what = count * item.stack.getCount() + " " + item.stack.getHoverName().getString();
        if (item.currency < 0) {
            Bank.take(server, player.getUUID(), cost, BankTransaction.Type.BOUGHT, shop.name, what, paid);
        } else {
            CoinTier tier = CoinTier.values()[item.currency];
            if (!CoinWallet.pay(player, (int) Math.min(Integer.MAX_VALUE, cost * tier.value()))) {
                then.accept(Outcome.fail("coins_short"));
                return;
            }
            Bank.record(server, player.getUUID(), new BankTransaction(now, BankTransaction.Type.BOUGHT, shop.name, what + ", " + cost + " " + Bank.tierName(tier) + " coins", 0, item.currency, (int) -cost, Bank.serverId()));
            paid.accept(BankResult.ok(Bank.pieces(server, player.getUUID())));
        }
    }

    /** Sells up to {@code lots} of item {@code index} from the player's inventory; fails when the shop is buy-only or they hold none. */
    public static void sell(MinecraftServer server, Player player, Shop shop, int index, int lots, boolean test, Consumer<Outcome> then) {
        long now = System.currentTimeMillis();
        ShopItem item = shop.item(index);
        if (item == null || shop.buyOnly || !item.sellable()) {
            then.accept(Outcome.fail("not_bought"));
            return;
        }
        int held = 0;
        for (ItemStack s : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(s, item.stack)) {
                held += s.getCount();
            }
        }
        int n = Math.min(Math.max(1, lots), held / item.stack.getCount());
        if (n <= 0) {
            then.accept(Outcome.fail("none_held"));
            return;
        }
        long unit = price(shop, item, false, now), paid = unit * n;
        if (test) {
            then.accept(new Outcome(true, "test_sold", n, paid, Bank.pieces(server, player.getUUID())));
            return;
        }
        player.getInventory().clearOrCountMatchingItems(s -> ItemStack.isSameItemSameTags(s, item.stack), n * item.stack.getCount(), player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        String what = n * item.stack.getCount() + " " + item.stack.getHoverName().getString();
        int count = n;
        if (item.currency < 0) {
            Bank.give(server, player.getUUID(), paid, BankTransaction.Type.SOLD, shop.name, what, r -> then.accept(new Outcome(r.ok(), r.ok() ? "sold" : r.key(), count, paid, r.pieces())));
        } else {
            CoinTier tier = CoinTier.values()[item.currency];
            CoinWallet.give(player, (int) Math.min(Integer.MAX_VALUE, paid * tier.value()));
            Bank.record(server, player.getUUID(), new BankTransaction(now, BankTransaction.Type.SOLD, shop.name, what + ", " + paid + " " + Bank.tierName(tier) + " coins", 0, item.currency, (int) paid, Bank.serverId()));
            then.accept(new Outcome(true, "sold", count, paid, Bank.pieces(server, player.getUUID())));
        }
    }

    /** Sells everything the player holds that this shop buys, one item after another; {@code then} gets the lots and the pieces total. */
    public static void sellAll(MinecraftServer server, Player player, Shop shop, boolean test, Consumer<Outcome> then) {
        List<Integer> indexes = new ArrayList<>();
        List<ShopItem> all = shop.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).sellable() && all.get(i).currency < 0) {
                indexes.add(i);
            }
        }
        sellNext(server, player, shop, indexes, 0, 0, 0, test, then);
    }

    private static void sellNext(MinecraftServer server, Player player, Shop shop, List<Integer> indexes, int at, int lots, long total, boolean test, Consumer<Outcome> then) {
        if (at >= indexes.size()) {
            then.accept(new Outcome(lots > 0, lots > 0 ? (test ? "test_sold" : "sold_all") : "none_held", lots, total, Bank.pieces(server, player.getUUID())));
            return;
        }
        sell(server, player, shop, indexes.get(at), Integer.MAX_VALUE, test, o -> sellNext(server, player, shop, indexes, at + 1, lots + (o.ok() ? o.lots() : 0), total + (o.ok() ? o.amount() : 0), test, then));
    }

    private static boolean canPay(MinecraftServer server, Player player, int currency, long cost) {
        if (currency < 0) {
            return Bank.pieces(server, player.getUUID()) >= cost;
        }
        return CoinWallet.totalValue(player) >= cost * CoinTier.values()[currency].value();
    }

    /** True while any visible item card can still be bought: unlimited, or a count above zero (refilling countdowns that are due). */
    public static boolean anyStock(MinecraftServer server, Shop shop) {
        long now = System.currentTimeMillis();
        for (int i : visible(server, shop, now)) {
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty() && stockLeft(server, shop, i, net.minecraft.Util.NIL_UUID, now) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The shop's first visible item card, for a stall's counter; empty if it has none. */
    public static net.minecraft.world.item.ItemStack firstItem(MinecraftServer server, Shop shop) {
        for (int i : visible(server, shop, System.currentTimeMillis())) {
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                return it.stack.copy();
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
