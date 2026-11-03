package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.BuyOrder;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.LocalOrders;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketDb;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.market.NetworkOrders;
import com.mrgregles.bsp_core.market.OrderDb;
import com.mrgregles.bsp_core.market.OrderLedger;
import com.mrgregles.bsp_core.market.OrderStore;
import com.mrgregles.bsp_core.market.OrderSummary;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameTests for buy orders: placing, filling, cancelling and running out on one server, and on a network the races and the
 * lost answers. Batch {@code trade_post}. Each test gives the orders and the market blank stores of their own and puts the
 * world's back before the tick ends; bank accounts are fresh, and the lottery pot is read as a difference.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class OrderGameTests {
    private static final String EMPTY = "empty";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static ServerPlayer player(GameTestHelper h, long pieces) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        Bank.ensure(server(h), p.getUUID(), p.getGameProfile().getName(), g -> {});
        Bank.set(server(h), p.getUUID(), pieces, "gametest", r -> {});
        return p;
    }

    private static OrderStore.Answer place(MinecraftServer server, ServerPlayer buyer, net.minecraft.world.item.Item item, int count, long each) {
        OrderStore.Answer[] a = new OrderStore.Answer[1];
        BuyOrders.place(server, buyer.getUUID(), buyer.getGameProfile().getName(), item, count, each, x -> a[0] = x);
        return a[0];
    }

    private static OrderStore.Answer fill(MinecraftServer server, ServerPlayer seller, String id, int count) {
        OrderStore.Answer[] a = new OrderStore.Answer[1];
        BuyOrders.fill(server, seller, id, count, x -> a[0] = x);
        return a[0];
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(item)) {
                n += p.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    private static int count(List<ItemStack> box) {
        return box.stream().mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void buy_orders_hold_the_price_fill_bit_by_bit_and_give_back_what_is_left(GameTestHelper h) {
        var server = server(h);
        MarketLedger boxes = new MarketLedger();
        OrderLedger ledger = new OrderLedger();
        MarketStore oldMarket = Market.useStore(new LocalMarket(boxes));
        OrderStore oldOrders = BuyOrders.useStore(new LocalOrders(ledger));
        try {
            ServerPlayer buyer = player(h, 1000), seller = player(h, 0), other = player(h, 0);
            long pot = Market.pot(server);
            // what can never be ordered, and limits: nothing is charged
            h.assertTrue(place(server, buyer, ModItems.SHATTER_TOTEM.get(), 1, 10).key().equals("refused"), "a Shatter Totem cannot be ordered");
            h.assertTrue(place(server, buyer, ModItems.TOTEM_CORE.get(), 1, 10).key().equals("refused"), "nor a Totem Core");
            h.assertTrue(place(server, buyer, Items.COMMAND_BLOCK, 1, 10).key().equals("refused"), "nor an operator's block");
            h.assertTrue(place(server, buyer, Items.CACTUS, 0, 10).key().equals("count"), "at least one");
            h.assertTrue(place(server, buyer, Items.CACTUS, Market.maxStacks() * 64 + 1, 1).key().equals("count"), "at most " + Market.maxStacks() + " stacks");
            h.assertTrue(BuyOrders.maxCount(BuyOrders.plain(Items.DIAMOND_PICKAXE)) == Market.maxStacks(), "of a tool, that is one per stack");
            h.assertTrue(place(server, buyer, Items.CACTUS, 64, 0).key().equals("price"), "at least a piece each");
            h.assertTrue(place(server, buyer, Items.CACTUS, 64, 100).key().equals("short"), "the whole price must be in the bank");
            h.assertTrue(Bank.pieces(server, buyer.getUUID()) == 1000 && ledger.open().isEmpty(), "nothing refused cost anything");
            // the order: the whole price leaves the bank at once
            OrderStore.Answer a = place(server, buyer, Items.CACTUS, 64, 12);
            h.assertTrue(a.ok() && a.order() != null && ledger.open().size() == 1, "placed: " + a);
            BuyOrder order = a.order();
            h.assertTrue(Bank.pieces(server, buyer.getUUID()) == 1000 - 64 * 12 && order.held() == 64 * 12, "768 pieces held for it");
            h.assertTrue(order.endsMs - order.placedMs == Market.listingDays() * 24L * 60 * 60 * 1000, "it lasts " + Market.listingDays() + " days");
            // only plain cactus counts: a named one is left alone
            seller.getInventory().setItem(0, new ItemStack(Items.CACTUS, 30));
            seller.getInventory().setItem(1, new ItemStack(Items.CACTUS, 7));
            ItemStack named = new ItemStack(Items.CACTUS, 5);
            named.setHoverName(net.minecraft.network.chat.Component.literal("Prickles"));
            seller.getInventory().setItem(2, named);
            h.assertTrue(BuyOrders.carried(seller, order) == 37, "37 plain cactus carried, the named ones not: " + BuyOrders.carried(seller, order));
            h.assertTrue(fill(server, buyer, order.id, 5).key().equals("own"), "the buyer cannot fill their own order");
            h.assertTrue(fill(server, other, order.id, 5).key().equals("none"), "someone with none sends nothing");
            // a fill of more than they carry sends what they carry; they are paid less the tax, the pot gets the tax
            a = fill(server, seller, order.id, 50);
            long pay = 37 * 12, tax = BuyOrders.tax(pay);
            h.assertTrue(a.ok() && a.sent() == 37 && a.back() == 0 && !a.closed(), "37 sent: " + a);
            h.assertTrue(Bank.pieces(server, seller.getUUID()) == pay - tax && Market.pot(server) == pot + tax, "the seller got " + (pay - tax) + ", the pot " + tax);
            h.assertTrue(count(seller, Items.CACTUS) == 5 && seller.getInventory().getItem(2).hasCustomHoverName(), "the named cactus stayed");
            h.assertTrue(count(boxes.peekCollect(buyer.getUUID())) == 37, "the buyer's Collect box holds the 37");
            BuyOrder now = ledger.get(order.id);
            h.assertTrue(now != null && now.filled == 37 && now.toGo() == 27 && now.held() == 27 * 12, "27 still wanted, their price still held");
            // the rest by another seller: only 27 go, the rest stays with them, and the order closes
            other.getInventory().setItem(0, new ItemStack(Items.CACTUS, 40));
            a = fill(server, other, order.id, 40);
            h.assertTrue(a.ok() && a.sent() == 27 && a.closed() && ledger.open().isEmpty(), "27 sent and the order is filled: " + a);
            h.assertTrue(count(other, Items.CACTUS) == 13, "the 13 it did not need stayed in their inventory");
            h.assertTrue(count(boxes.peekCollect(buyer.getUUID())) == 64, "the buyer has all 64");
            h.assertTrue(fill(server, other, order.id, 1).key().equals("gone"), "a filled order is gone");
            h.assertTrue(Bank.pieces(server, buyer.getUUID()) == 1000 - 768, "the buyer paid exactly 768 for 64");
            // cancelling gives back what is still held; only the buyer may, or an admin
            a = place(server, buyer, Items.OAK_LOG, 20, 5);
            h.assertTrue(a.ok(), "a second order");
            String id = a.order().id;
            other.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 8));
            h.assertTrue(fill(server, other, id, 8).sent() == 8, "8 of 20 sent");
            OrderStore.Answer[] c = new OrderStore.Answer[1];
            BuyOrders.cancel(server, other.getUUID(), id, false, x -> c[0] = x);
            h.assertTrue(c[0].key().equals("not_yours"), "only the buyer cancels");
            long before = Bank.pieces(server, buyer.getUUID());
            BuyOrders.cancel(server, buyer.getUUID(), id, false, x -> c[0] = x);
            h.assertTrue(c[0].ok() && c[0].refund() == 12 * 5 && Bank.pieces(server, buyer.getUUID()) == before + 60 && ledger.open().isEmpty(), "12 x 5 back: " + c[0]);
            // an admin may cancel anyone's order; the buyer is the one paid back
            a = place(server, buyer, Items.STRING, 4, 3);
            before = Bank.pieces(server, buyer.getUUID());
            BuyOrders.cancel(server, other.getUUID(), a.order().id, true, x -> c[0] = x);
            h.assertTrue(c[0].ok() && Bank.pieces(server, buyer.getUUID()) == before + 12, "an admin cancels it and the buyer gets the 12 back");
            // a fill paying a single piece still pays the seller that piece: the tax never takes all of it
            a = place(server, buyer, Items.STICK, 1, 1);
            other.getInventory().setItem(2, new ItemStack(Items.STICK, 1));
            long otherBefore = Bank.pieces(server, other.getUUID());
            OrderStore.Answer one = fill(server, other, a.order().id, 1);
            h.assertTrue(one.ok() && one.closed() && one.tax() == 0 && Bank.pieces(server, other.getUUID()) == otherBefore + 1, "1 piece for 1 stick, no tax: " + one);
            h.assertTrue(BuyOrders.tax(1) == 0 && BuyOrders.tax(2) == 1 && BuyOrders.tax(1000) == Market.tax(1000), "the order tax leaves at least a piece");
            // worn armour is never taken, even when it is the plain item the order wants
            a = place(server, buyer, Items.IRON_HELMET, 1, 50);
            ServerPlayer wearer = player(h, 0);
            wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, BuyOrders.plain(Items.IRON_HELMET));
            h.assertTrue(BuyOrders.carried(wearer, a.order()) == 0 && fill(server, wearer, a.order().id, 1).key().equals("none"), "the helmet worn does not count");
            wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, BuyOrders.plain(Items.IRON_HELMET));
            h.assertTrue(fill(server, wearer, a.order().id, 1).ok() && wearer.getOffhandItem().isEmpty() && !wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),
                    "one in the off hand is sent; the worn one stays");
            // running out does the same
            a = place(server, buyer, Items.BONE, 10, 2);
            long[] clock = {a.order().endsMs + 1};
            before = Bank.pieces(server, buyer.getUUID());
            Market.useClock(() -> clock[0]);
            try {
                h.assertTrue(fill(server, other, a.order().id, 1).key().equals("gone"), "an order past its end takes nothing");
                h.assertTrue(BuyOrders.expireDue(server) == 1 && ledger.open().isEmpty(), "the sweep closes it");
            } finally {
                Market.useClock(null);
            }
            h.assertTrue(Bank.pieces(server, buyer.getUUID()) == before + 20, "and gives back the 20 held");
        } finally {
            Market.useStore(oldMarket);
            BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void buy_orders_limit_each_player_and_show_on_the_screen_and_the_board(GameTestHelper h) {
        var server = server(h);
        OrderLedger ledger = new OrderLedger();
        MarketStore oldMarket = Market.useStore(new LocalMarket(new MarketLedger()));
        OrderStore oldOrders = BuyOrders.useStore(new LocalOrders(ledger));
        try {
            ServerPlayer buyer = player(h, 1_000_000), viewer = player(h, 0);
            for (int i = 0; i < BuyOrders.maxOrders(); i++) {
                h.assertTrue(place(server, buyer, Items.DIRT, 1, 1 + i).ok(), "order " + i);
            }
            h.assertTrue(place(server, buyer, Items.DIRT, 1, 1).key().equals("orders"), "at most " + BuyOrders.maxOrders() + " open");
            for (BuyOrder o : ledger.open()) {
                if (o.each > 2) {
                    ledger.remove(o.id);
                }
            }
            h.assertTrue(place(server, buyer, Items.CACTUS, 64, 12).ok() && place(server, buyer, Items.IRON_INGOT, 32, 40).ok(), "two more");
            viewer.getInventory().setItem(0, new ItemStack(Items.CACTUS, 9));
            var v = TradePostMenu.ordersView(server, viewer, viewer.getUUID(), "", 1, 0);
            h.assertTrue(v.total() == 4 && v.orders().size() == 4 && v.orders().get(0).each() == 40, "best pay first: " + v.orders());
            v = TradePostMenu.ordersView(server, viewer, viewer.getUUID(), "", 2, 0);
            h.assertTrue(v.orders().get(0).item().is(Items.CACTUS) && v.orders().get(0).carried() == 9, "what the viewer can fill first, with how many they carry");
            h.assertTrue(TradePostMenu.ordersView(server, viewer, viewer.getUUID(), "cact", 0, 0).orders().size() == 1, "the search finds the item");
            h.assertTrue(TradePostMenu.ordersView(server, buyer, buyer.getUUID(), "", 0, 0).mine() == 4, "the buyer's own count");
            var board = OrderSummary.build(server);
            h.assertTrue(board.size() == 4 && board.get(0).each() == 40 && board.get(0).toGo() == 32, "the Wanted board: best pay first: " + board);
        } finally {
            Market.useStore(oldMarket);
            BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ on a network

    /** An in-memory network database for orders: balances, orders, Collect boxes, attempts; {@code lose} makes the next change happen and its answer fail. */
    private static class FakeOrderDb implements OrderDb {
        final Map<UUID, Long> pieces = new HashMap<>();
        final Map<String, BuyOrder> orders = new LinkedHashMap<>();
        final Map<UUID, List<ItemStack>> boxes = new HashMap<>();
        final java.util.Set<String> attempts = new java.util.HashSet<>();
        boolean down, lose, downAfterLose;

        private void check() throws java.sql.SQLException {
            if (down) {
                throw new MarketDb.Untouched("down (gametest)");
            }
        }

        private void answer() throws java.sql.SQLException {
            if (lose) {
                lose = false;
                down = downAfterLose;
                throw new java.sql.SQLException("answer lost (gametest)");
            }
        }

        long bal(UUID id) {
            return pieces.getOrDefault(id, 0L);
        }

        int box(UUID id) {
            return count(boxes.getOrDefault(id, List.of()));
        }

        @Override
        public Done place(BuyOrder o, int maxOrders) throws Exception {
            check();
            if (orders.values().stream().filter(x -> x.buyer.equals(o.buyer)).count() >= maxOrders) {
                return Done.no("orders");
            }
            if (bal(o.buyer) < o.held()) {
                return Done.no("short");
            }
            pieces.put(o.buyer, bal(o.buyer) - o.held());
            orders.put(o.id, o);
            attempts.add(o.id);
            answer();
            return new Done("ok", o, 0, 0, 0, 0, false, bal(o.buyer), -1);
        }

        @Override
        public List<BuyOrder> orders() throws Exception {
            check();
            List<BuyOrder> out = new ArrayList<>(orders.values());
            java.util.Collections.reverse(out);
            return out;
        }

        @Override
        public Done fill(String id, UUID seller, int count, String attempt, long now) throws Exception {
            check();
            BuyOrder o = orders.get(id);
            if (o == null || o.endedBy(now) || o.toGo() <= 0) {
                return Done.no("gone");
            }
            if (o.buyer.equals(seller)) {
                return Done.no("own");
            }
            int n = Math.min(count, o.toGo());
            long pay = n * o.each, tax = BuyOrders.tax(pay);
            pieces.put(seller, bal(seller) + pay - tax);
            pieces.put(Market.POT, bal(Market.POT) + tax);
            boxes.computeIfAbsent(o.buyer, k -> new ArrayList<>()).addAll(BuyOrders.stacks(o.item(), n));
            if (count > n) {
                boxes.computeIfAbsent(seller, k -> new ArrayList<>()).addAll(BuyOrders.stacks(o.item(), count - n));
            }
            BuyOrder after = o.filledBy(n);
            if (after.toGo() <= 0) {
                orders.remove(id);
            } else {
                orders.put(id, after);
            }
            attempts.add(attempt);
            answer();
            return new Done("ok", after, n, pay - tax, tax, 0, after.toGo() <= 0, bal(seller), bal(Market.POT));
        }

        /** A buyer whose bank row is gone: closing their orders fails and leaves them open. */
        UUID noBank;

        @Override
        public Done close(String id) throws Exception {
            check();
            if (orders.containsKey(id) && orders.get(id).buyer.equals(noBank)) {
                return Done.no("buyer");
            }
            BuyOrder o = orders.remove(id);
            if (o == null) {
                return Done.no("gone");
            }
            pieces.put(o.buyer, bal(o.buyer) + o.held());
            answer();
            return new Done("ok", o, 0, 0, 0, o.held(), false, bal(o.buyer), -1);
        }

        @Override
        public boolean committed(String attempt) throws Exception {
            check();
            return attempts.contains(attempt);
        }

        @Override
        public void settleFill(String attempt, UUID seller, List<ItemStack> items) throws Exception {
            check();
            if (attempts.add(attempt)) {
                boxes.computeIfAbsent(seller, k -> new ArrayList<>()).addAll(items);
            }
            answer();
        }
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void buy_orders_on_a_network_never_overfill_and_lose_nothing_with_an_answer(GameTestHelper h) {
        var server = server(h);
        FakeOrderDb db = new FakeOrderDb();
        OrderLedger heldA = new OrderLedger();
        List<UUID> boxNotices = new ArrayList<>();
        NetworkOrders a = new NetworkOrders(db, Runnable::run, Runnable::run, () -> {}, s -> heldA, boxNotices::add);
        NetworkOrders b = new NetworkOrders(db, Runnable::run, Runnable::run, () -> {}, s -> new OrderLedger(), id -> {});
        MarketStore oldMarket = Market.useStore(new LocalMarket(new MarketLedger()));
        OrderStore oldOrders = BuyOrders.useStore(a);
        try {
            ServerPlayer buyer = player(h, 0), s1 = player(h, 0), s2 = player(h, 0);
            db.pieces.put(buyer.getUUID(), 1000L);
            Bank.set(server, buyer.getUUID(), 1000, "gametest", r -> {});
            OrderStore.Answer r = place(server, buyer, Items.CACTUS, 30, 10);
            h.assertTrue(r.ok() && db.bal(buyer.getUUID()) == 700 && db.orders.size() == 1, "placed in the database, 300 held: " + r);
            String id = r.order().id;
            // two servers fill the same order at once, each from its own copy: the second sends only what is still wanted
            BuyOrders.useStore(b);
            b.refresh(server);
            s1.getInventory().setItem(0, new ItemStack(Items.CACTUS, 20));
            s2.getInventory().setItem(0, new ItemStack(Items.CACTUS, 20));
            BuyOrders.useStore(a);
            OrderStore.Answer f1 = fill(server, s1, id, 20);
            h.assertTrue(f1.ok() && f1.sent() == 20 && db.orders.get(id).toGo() == 10, "the first sends 20: " + f1);
            BuyOrders.useStore(b); // b's copy still says 30 to go
            OrderStore.Answer f2 = fill(server, s2, id, 20);
            h.assertTrue(f2.ok() && f2.sent() == 10 && f2.closed() && db.orders.isEmpty(), "the second sends only 10 and fills it: " + f2);
            h.assertTrue(db.box(buyer.getUUID()) == 30, "the buyer's box holds exactly 30");
            h.assertTrue(count(s2, Items.CACTUS) == 0 && db.box(s2.getUUID()) == 10, "the 10 it did not need went to the second seller's Collect box in the same step");
            h.assertTrue(db.bal(s1.getUUID()) == 200 - BuyOrders.tax(200) && db.bal(s2.getUUID()) == 100 - BuyOrders.tax(100), "each was paid for what went, less the tax");
            BuyOrders.useStore(a);
            h.assertTrue(boxNotices.contains(buyer.getUUID()), "the buyer's box is read again and the other servers told");
            // a fill whose answer is lost after the database took it: nothing is handed back, and settling later adds nothing
            r = place(server, buyer, Items.BONE, 10, 5);
            h.assertTrue(r.ok(), "another order");
            String bone = r.order().id;
            s1.getInventory().setItem(1, new ItemStack(Items.BONE, 4));
            db.lose = true;
            db.downAfterLose = true; // and then the database is gone, so even asking about it fails
            OrderStore.Answer lost = fill(server, s1, bone, 4);
            h.assertTrue(lost.key().equals("unsure") && count(s1, Items.BONE) == 0 && heldA.unsure().size() == 1, "unsure: the items are held, not handed back: " + lost);
            h.assertTrue(db.orders.get(bone).filled == 4 && db.box(buyer.getUUID()) == 34, "the fill went in");
            db.down = false;
            db.downAfterLose = false;
            a.refresh(server);
            h.assertTrue(heldA.unsure().isEmpty() && db.box(s1.getUUID()) == 0, "settled once it answers: it went in, so nothing goes to the seller's box");
            // one that failed before the database took it (the caller cannot tell): the items go to the seller's box, once
            s1.getInventory().setItem(1, new ItemStack(Items.BONE, 3));
            db.lose = false;
            String[] attempt = new String[1];
            FakeOrderDb failing = new FakeOrderDb() {
                @Override
                public Done fill(String id2, UUID seller, int count, String att, long now) throws Exception {
                    attempt[0] = att;
                    throw new java.sql.SQLException("commit failed (gametest)");
                }
            };
            failing.orders.putAll(db.orders);
            OrderLedger heldC = new OrderLedger();
            NetworkOrders c = new NetworkOrders(failing, Runnable::run, Runnable::run, () -> {}, s -> heldC, x -> {});
            BuyOrders.useStore(c);
            c.refresh(server);
            OrderStore.Answer gone = fill(server, s1, bone, 3);
            h.assertTrue(gone.key().equals("unsure") && count(s1, Items.BONE) == 0, "unsure again, nothing handed back: " + gone);
            h.assertTrue(heldC.unsure().isEmpty() && failing.box(s1.getUUID()) == 3 && failing.attempts.contains(attempt[0]), "it never went in: settled at once, the 3 are in the seller's Collect box");
            c.refresh(server);
            h.assertTrue(failing.box(s1.getUUID()) == 3, "and only once");
            BuyOrders.useStore(a);
            // the database down: nothing moves, the items stay
            db.down = true;
            s1.getInventory().setItem(1, new ItemStack(Items.BONE, 2));
            a.refresh(server);
            OrderStore.Answer down = fill(server, s1, bone, 2);
            h.assertTrue(down.key().equals("unreachable") && count(s1, Items.BONE) == 2, "unreachable: the 2 are handed back: " + down);
            db.down = false;
            // an order closed on another server leaves this server's copy at its next refresh
            r = place(server, buyer, Items.STRING, 5, 1);
            String str = r.order().id;
            BuyOrders.useStore(b);
            b.refresh(server);
            OrderStore.Answer[] other = new OrderStore.Answer[1];
            BuyOrders.cancel(server, buyer.getUUID(), str, false, x -> other[0] = x);
            BuyOrders.useStore(a);
            h.assertTrue(other[0].ok() && a.open(server).stream().anyMatch(o -> o.id.equals(str)), "closed on b; a still shows it until it reads");
            a.refresh(server);
            h.assertTrue(a.open(server).stream().noneMatch(o -> o.id.equals(str)), "a's refresh drops it");
            // a refund that cannot be paid leaves the order open, here and in the database
            r = place(server, buyer, Items.STRING, 5, 1);
            String stuck = r.order().id;
            db.noBank = buyer.getUUID();
            BuyOrders.cancel(server, buyer.getUUID(), stuck, false, x -> other[0] = x);
            h.assertTrue(other[0].key().equals("buyer") && db.orders.containsKey(stuck) && a.open(server).stream().anyMatch(o -> o.id.equals(stuck)), "still open: " + other[0]);
            db.noBank = null;
            BuyOrders.cancel(server, buyer.getUUID(), stuck, false, x -> other[0] = x);
            // cancelling on the network gives back what is held for what was not sent
            OrderStore.Answer[] cancel = new OrderStore.Answer[1];
            long before = db.bal(buyer.getUUID());
            BuyOrders.cancel(server, buyer.getUUID(), bone, false, x -> cancel[0] = x);
            h.assertTrue(cancel[0].ok() && cancel[0].refund() == 6 * 5 && db.bal(buyer.getUUID()) == before + 30 && db.orders.isEmpty(), "6 x 5 back: " + cancel[0]);
        } finally {
            Market.useStore(oldMarket);
            BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }
}
