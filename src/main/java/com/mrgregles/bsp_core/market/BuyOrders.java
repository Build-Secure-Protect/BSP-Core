package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Buy orders: a player asks for {@code count} of one plain item at a price apiece, paying the whole of it up front; anyone
 * else sends some or all of it from their inventory and is paid at once for what they send, less the market tax
 * ({@link Market#tax}, into the lottery pot). The items wait in the buyer's Collect box. An order lasts
 * {@code market.listingDays}; what is not spent when it is cancelled or runs out goes back to the buyer's bank. A player may
 * have {@code market.maxOrdersPerPlayer} open at once, each for at most {@code market.maxStacks} stacks of the item.
 *
 * <p>Only the plain item counts: its own default form, with no enchantments, damage or name. Items the market refuses
 * ({@link Market#refused}) and operator-only blocks can never be ordered. Every call answers through {@code then} on the
 * server thread (at once on a single server); the money and the items move in one step of the {@link OrderStore}.
 */
public final class BuyOrders {
    private BuyOrders() {}

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    @Nullable
    private static OrderStore storeOverride;
    private static final LocalOrders LOCAL = new LocalOrders(null);

    /** The test's store, else the network's while network storage is on, else this world's. */
    public static OrderStore store(MinecraftServer server) {
        if (storeOverride != null) {
            return storeOverride;
        }
        NetworkOrders net = NetworkStorage.orders();
        return net != null ? net : LOCAL;
    }

    /** GameTests: use this store (null for the usual one again); returns the one it replaced. Set and put back within one tick. */
    @Nullable
    public static OrderStore useStore(@Nullable OrderStore store) {
        OrderStore old = storeOverride;
        storeOverride = store;
        return old;
    }

    // ------------------------------------------------------------------ rules read by the screens too

    public static int maxOrders() {
        return BSPConfig.getOr(BSPConfig.MARKET_MAX_ORDERS, 10);
    }

    /** The most of {@code item} one order may ask for: {@code market.maxStacks} stacks of it. */
    public static int maxCount(ItemStack item) {
        return Market.maxStacks() * Math.max(1, item.getMaxStackSize());
    }

    /** Blocks only an operator can have: never ordered. */
    private static final Set<Item> OPERATOR_ONLY = Set.of(Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK, Items.COMMAND_BLOCK_MINECART,
            Items.STRUCTURE_BLOCK, Items.STRUCTURE_VOID, Items.JIGSAW, Items.BARRIER, Items.LIGHT, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK);

    /** The plain form of an item, the one an order asks for: its default stack, one of it. */
    public static ItemStack plain(Item item) {
        return item.getDefaultInstance().copyWithCount(1);
    }

    /** Whether an item may be ordered: not air, not operator-only, and not refused by the market. */
    public static boolean orderable(Item item) {
        return item != Items.AIR && !OPERATOR_ONLY.contains(item) && !Market.refused(plain(item));
    }

    /** The tax on what a fill pays: the market tax ({@link Market#tax}), but always leaving the seller at least a piece. */
    public static long tax(long pay) {
        return Math.max(0, Math.min(Market.tax(pay), pay - 1));
    }

    /** {@code n} of {@code item} as stacks of at most its stack size. */
    public static List<ItemStack> stacks(ItemStack item, int n) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, item.getMaxStackSize());
        while (n > 0 && !item.isEmpty()) {
            int k = Math.min(n, max);
            out.add(item.copyWithCount(k));
            n -= k;
        }
        return out;
    }

    // ------------------------------------------------------------------ reading

    /** Every open order, newest first. */
    public static List<BuyOrder> open(MinecraftServer server) {
        return store(server).open(server);
    }

    public static Optional<BuyOrder> find(MinecraftServer server, String id) {
        return open(server).stream().filter(o -> o.id.equals(id)).findFirst();
    }

    public static int ordersOf(MinecraftServer server, UUID buyer) {
        return (int) open(server).stream().filter(o -> o.buyer.equals(buyer)).count();
    }

    /** How many of the order's plain item the player carries: their inventory and their off hand, never what they wear. */
    public static int carried(ServerPlayer player, BuyOrder order) {
        int n = 0;
        for (int i : slots(player.getInventory())) {
            if (order.takes(player.getInventory().getItem(i))) {
                n += player.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    /** The inventory slots a fill may take from: the 36 of the inventory and the off hand (not the armour). */
    private static int[] slots(Inventory inv) {
        int[] out = new int[inv.items.size() + 1];
        for (int i = 0; i < inv.items.size(); i++) {
            out[i] = i;
        }
        out[inv.items.size()] = inv.items.size() + inv.armor.size(); // the off hand comes after the armour
        return out;
    }

    // ------------------------------------------------------------------ placing

    /**
     * Places an order for {@code count} of {@code item} at {@code each} pieces apiece, the whole price from the buyer's bank
     * now. Fails with "refused", "count", "price", "orders" (the buyer has {@code market.maxOrdersPerPlayer} open), "short",
     * "unreachable" or "unsure" (the network's answer was lost; the order shows within a minute if it went in).
     */
    public static void place(MinecraftServer server, UUID buyer, String name, Item item, int count, long each, Consumer<OrderStore.Answer> then) {
        ItemStack plain = plain(item);
        String fail = NetworkStorage.down() ? "unreachable"
                : !orderable(item) ? "refused"
                : count < 1 || count > maxCount(plain) ? "count"
                : each < 1 || each > Market.MAX_PRICE / count ? "price"
                : ordersOf(server, buyer) >= maxOrders() ? "orders"
                : Bank.pieces(server, buyer) < count * each ? "short" : null;
        if (fail != null) {
            then.accept(OrderStore.Answer.no(fail));
            return;
        }
        long now = Market.now();
        BuyOrder order = new BuyOrder(newId(server), buyer, name, plain, count, 0, each, now, now + Market.listingDays() * DAY_MS, Bank.serverId());
        store(server).place(server, order, maxOrders(), a -> {
            if (a.ok()) {
                BSPCore.LOGGER.info("BSP-Core orders: {} placed order {} for {} at {} each ({} held)", name, order.id, order.describe(), each, Bank.format(order.held()));
            }
            then.accept(a);
        });
    }

    // ------------------------------------------------------------------ filling

    /**
     * Sends up to {@code count} of the order's plain item from the seller's inventory: as many as the order still wants and
     * they carry. They are paid at once for what goes, less the tax, and the buyer is told. Fails with "gone" (filled,
     * cancelled or run out), "own", "none" (they carry none), "seller", "unreachable" or "unsure" (the network's answer was
     * lost; the items either went to the order or wait in the seller's Collect box). Nothing is taken on a failure.
     */
    public static void fill(MinecraftServer server, ServerPlayer seller, String id, int count, Consumer<OrderStore.Answer> then) {
        BuyOrder seen = find(server, id).orElse(null);
        if (seen == null || seen.endedBy(Market.now()) || seen.toGo() <= 0) {
            then.accept(OrderStore.Answer.no("gone"));
            return;
        }
        if (seen.buyer.equals(seller.getUUID())) {
            then.accept(OrderStore.Answer.no("own"));
            return;
        }
        if (NetworkStorage.down()) {
            then.accept(OrderStore.Answer.no("unreachable"));
            return;
        }
        int n = Math.min(Math.min(count, seen.toGo()), carried(seller, seen));
        if (n <= 0) {
            then.accept(OrderStore.Answer.no("none"));
            return;
        }
        take(seller, seen, n);
        String name = seller.getGameProfile().getName();
        store(server).fill(server, seen, seller.getUUID(), name, n, a -> {
            if (!a.ok()) {
                if (!a.key().equals("unsure")) {
                    com.mrgregles.bsp_core.trade.TradePostMenu.giveBack(seller, stacks(seen.item(), n));
                }
                then.accept(a);
                return;
            }
            if (a.back() > 0) {
                com.mrgregles.bsp_core.trade.TradePostMenu.giveBack(seller, stacks(seen.item(), a.back()));
            }
            BuyOrder after = a.order();
            String what = a.sent() + " " + after.item().getHoverName().getString();
            NetworkStorage.tell(server, after.buyer, Component.translatable(a.closed() ? "message.bsp_core.order.done" : "message.bsp_core.order.sent_you",
                    name, what, after.filled, after.wanted));
            BSPCore.LOGGER.info("BSP-Core orders: {} sent {} to {}'s order {} for {} pieces + {} tax{}", name, what, after.buyerName, after.id, a.paid(), a.tax(), a.closed() ? "; the order is filled" : "");
            then.accept(a);
        });
    }

    /** Takes {@code n} of the order's plain item out of the player's inventory (they carry at least that many). */
    private static void take(ServerPlayer player, BuyOrder order, int n) {
        Inventory inv = player.getInventory();
        for (int i : slots(inv)) {
            if (n <= 0) {
                break;
            }
            ItemStack s = inv.getItem(i);
            if (order.takes(s)) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                if (s.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                n -= k;
            }
        }
        inv.setChanged();
    }

    // ------------------------------------------------------------------ cancelling and running out

    /** Takes an open order off the market: by its buyer, or by an admin ({@code admin}). What is still held goes back to the buyer's bank. */
    public static void cancel(MinecraftServer server, UUID by, String id, boolean admin, Consumer<OrderStore.Answer> then) {
        BuyOrder seen = find(server, id).orElse(null);
        if (seen == null) {
            then.accept(OrderStore.Answer.no("gone"));
            return;
        }
        if (!admin && !seen.buyer.equals(by)) {
            then.accept(OrderStore.Answer.no("not_yours"));
            return;
        }
        if (NetworkStorage.down()) {
            then.accept(OrderStore.Answer.no("unreachable"));
            return;
        }
        store(server).close(server, id, a -> {
            if (a.ok()) {
                BuyOrder o = a.order();
                if (!o.buyer.equals(by)) {
                    NetworkStorage.tell(server, o.buyer, Component.translatable("message.bsp_core.order.removed", o.describe(), Bank.format(a.refund())));
                }
                BSPCore.LOGGER.info("BSP-Core orders: order {} ({}) of {} cancelled{}, {} back", o.id, o.describe(), o.buyerName, admin && !o.buyer.equals(by) ? " by an admin" : "", Bank.format(a.refund()));
            }
            then.accept(a);
        });
    }

    /**
     * Closes every order past its end and gives back what is still held; on a network each server closes the orders placed
     * on it, and any server those more than {@link Market#SWEEP_GRACE_MS} past their end. Returns how many were due.
     */
    public static int expireDue(MinecraftServer server) {
        if (NetworkStorage.down()) {
            return 0; // the network's orders are not reachable: never closed on this world's copy in their place
        }
        long now = Market.now();
        String here = Bank.serverId();
        int n = 0;
        for (BuyOrder o : open(server)) {
            if (o.endedBy(now) && (o.server.equals(here) || now >= o.endsMs + Market.SWEEP_GRACE_MS)) {
                n++;
                store(server).close(server, o.id, a -> {
                    if (a.ok()) {
                        BuyOrder c = a.order();
                        NetworkStorage.tell(server, c.buyer, Component.translatable("message.bsp_core.order.expired", c.describe(), c.filled, Bank.format(a.refund())));
                        BSPCore.LOGGER.info("BSP-Core orders: order {} ({}) of {} ran out with {} sent; {} back", c.id, c.describe(), c.buyerName, c.filled, Bank.format(a.refund()));
                    }
                });
            }
        }
        return n;
    }

    private static String newId(MinecraftServer server) {
        List<BuyOrder> open = open(server);
        while (true) {
            String id = "o" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36 * 36 * 36), 36);
            if (open.stream().noneMatch(o -> o.id.equals(id))) {
                return id;
            }
        }
    }
}
