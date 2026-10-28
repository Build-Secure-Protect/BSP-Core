package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.MarketEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketDb;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketLot;
import com.mrgregles.bsp_core.market.MarketResult;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.market.NetworkMarket;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.trade.RemoteTrade;
import com.mrgregles.bsp_core.trade.Trade;
import com.mrgregles.bsp_core.trade.TradeDb;
import com.mrgregles.bsp_core.trade.TradeHub;
import com.mrgregles.bsp_core.trade.TradeLink;
import com.mrgregles.bsp_core.trade.TradeMenu;
import com.mrgregles.bsp_core.trade.TradePostBlock;
import com.mrgregles.bsp_core.trade.TradePostBlockEntity;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import com.mrgregles.bsp_core.trade.TradeSession;
import com.mrgregles.bsp_core.trade.Trades;
import com.mrgregles.bsp_core.api.event.TradeEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GameTests for the Trade Post line: the marketplace, and later the live trades and the block. Batch {@code trade_post}, so
 * they never share the big default batch. Each test gives the market a blank store of its own and puts the world's back
 * before the tick ends; bank accounts are fresh random players, and the lottery pot is read as a difference.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TradePostGameTests {
    private static final String EMPTY = "empty";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    /** A bank account holding exactly {@code pieces}. */
    private static UUID account(MinecraftServer server, UUID id, String name, long pieces) {
        Bank.ensure(server, id, name, g -> {});
        Bank.set(server, id, pieces, "gametest", r -> {});
        return id;
    }

    private static MarketResult list(MinecraftServer server, UUID seller, String name, long price, ItemStack... stacks) {
        MarketResult[] r = new MarketResult[1];
        Market.list(server, seller, name, List.of(stacks), price, x -> r[0] = x);
        return r[0];
    }

    private static MarketResult buy(MinecraftServer server, UUID buyer, String name, String id) {
        MarketResult[] r = new MarketResult[1];
        Market.buy(server, buyer, name, id, x -> r[0] = x);
        return r[0];
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_listing_charges_the_tax_and_refuses_what_it_must(GameTestHelper h) {
        var server = server(h);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 1000);
        long pot = Market.pot(server);
        MarketLedger store = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(store));
        try {
            // totems and Totem Cores never go on the market, not even inside a shulker box; nothing is charged and the stacks come back
            MarketResult r = list(server, seller, "Sella", 100, new ItemStack(Items.DIAMOND, 4), new ItemStack(ModItems.SHATTER_TOTEM.get()));
            h.assertTrue(!r.ok() && r.key().equals("refused") && r.items().size() == 2, "a lot with a totem is refused and handed back: " + r);
            h.assertTrue(!list(server, seller, "Sella", 100, new ItemStack(ModItems.TOTEM_CORE.get())).ok(), "a Totem Core is refused");
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            CompoundTag inside = new ItemStack(ModItems.TOTEM_CORE.get()).save(new CompoundTag());
            inside.putByte("Slot", (byte) 0);
            ListTag items = new ListTag();
            items.add(inside);
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.put("Items", items);
            box.getOrCreateTag().put("BlockEntityTag", blockEntity);
            h.assertTrue(list(server, seller, "Sella", 100, box).key().equals("refused"), "a shulker box holding a Totem Core is refused");
            h.assertTrue(!Market.refused(new ItemStack(Items.SHULKER_BOX)), "an empty shulker box is fine");
            // an empty lot, too many stacks, no price
            h.assertTrue(list(server, seller, "Sella", 100).key().equals("empty"), "an empty lot is refused");
            ItemStack[] many = new ItemStack[Market.maxStacks() + 1];
            for (int i = 0; i < many.length; i++) {
                many[i] = new ItemStack(Items.DIRT, 1);
            }
            h.assertTrue(list(server, seller, "Sella", 100, many).key().equals("stacks"), "more than " + Market.maxStacks() + " stacks is refused");
            h.assertTrue(list(server, seller, "Sella", 0, new ItemStack(Items.DIRT)).key().equals("price"), "a price of 0 is refused");
            h.assertTrue(Bank.pieces(server, seller) == 1000 && store.open().isEmpty(), "nothing refused cost anything or was listed");
            // a good lot: the seller pays the tax at once, it goes into the pot, the market holds the stacks
            long tax = Market.tax(1000);
            r = list(server, seller, "Sella", 1000, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.GOLD_INGOT, 16));
            h.assertTrue(r.ok() && r.lot() != null && r.lot().stacks() == 2 && store.open().size() == 1, "the lot is listed: " + r);
            h.assertTrue(Bank.pieces(server, seller) == 1000 - tax && Market.pot(server) == pot + tax, "the seller paid " + tax + " into the pot");
            h.assertTrue(r.lot().endsMs - r.lot().listedMs == Market.listingDays() * DAY, "the lot runs for " + Market.listingDays() + " days");
            // the tax is rounded up, at least a piece
            h.assertTrue(Market.tax(1) == 1 && Market.tax(10) == 1 && Market.tax(30) == 2 && Market.tax(1001) == 51, "the tax rounds up");
            // the lot limit
            for (int i = 1; i < Market.maxLots(); i++) {
                h.assertTrue(list(server, seller, "Sella", 2, new ItemStack(Items.DIRT)).ok(), "lot " + (i + 1) + " is listed");
            }
            r = list(server, seller, "Sella", 2, new ItemStack(Items.DIRT));
            h.assertTrue(r.key().equals("lots") && r.items().size() == 1, "the lot after the limit is refused and handed back");
            // a seller who cannot pay the tax lists nothing
            UUID poor = account(server, UUID.randomUUID(), "Penny", 0);
            r = list(server, poor, "Penny", 1000, new ItemStack(Items.DIRT));
            h.assertTrue(!r.ok() && r.key().equals("short") && Market.lotsOf(server, poor) == 0, "no pieces for the tax, no lot");
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_buying_moves_pieces_tax_and_items_once(GameTestHelper h) {
        var server = server(h);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 1000);
        UUID buyer = account(server, UUID.randomUUID(), "Bram", 2000);
        UUID late = account(server, UUID.randomUUID(), "Lena", 5000);
        UUID poor = account(server, UUID.randomUUID(), "Penny", 100);
        long pot = Market.pot(server);
        MarketLedger store = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(store));
        List<MarketEvent.Sold> sold = new ArrayList<>();
        Consumer<MarketEvent.Sold> listener = sold::add;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, MarketEvent.Sold.class, listener);
        try {
            long tax = Market.tax(1000);
            String id = list(server, seller, "Sella", 1000, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.GOLD_INGOT, 16)).lot().id;
            h.assertTrue(buy(server, seller, "Sella", id).key().equals("own"), "a seller cannot buy their own lot");
            MarketResult r = buy(server, poor, "Penny", id);
            h.assertTrue(!r.ok() && r.key().equals("short") && Bank.pieces(server, poor) == 100 && Market.find(server, id).isPresent(), "too few pieces: nothing moves, the lot stays");
            // the purchase: the buyer pays the price and the tax, the seller gets the price, the pot gets both taxes
            r = buy(server, buyer, "Bram", id);
            h.assertTrue(r.ok() && r.items().size() == 2, "Bram bought the lot: " + r);
            h.assertTrue(Bank.pieces(server, buyer) == 2000 - 1000 - tax, "Bram paid 1000 + " + tax);
            h.assertTrue(Bank.pieces(server, seller) == 1000 - tax + 1000, "Sella paid the listing tax and was paid 1000");
            h.assertTrue(Market.pot(server) == pot + 2 * tax, "the pot holds both taxes");
            h.assertTrue(Market.find(server, id).isEmpty(), "the lot is off the market");
            // Bram is not online here, so the stacks wait in his Collect box
            List<ItemStack> box = Market.collectBox(server, buyer);
            h.assertTrue(box.size() == 2 && box.get(0).is(Items.DIAMOND) && box.get(0).getCount() == 64, "the stacks wait in Bram's Collect box");
            // a second buyer of the same lot gets nothing and pays nothing
            r = buy(server, late, "Lena", id);
            h.assertTrue(!r.ok() && r.key().equals("gone") && Bank.pieces(server, late) == 5000, "the lot can only be bought once");
            h.assertTrue(sold.stream().filter(e -> e.getLotId().equals(id)).count() == 1 && sold.stream().anyMatch(e -> e.getBuyer().equals(buyer) && e.getTax() == tax), "one Sold event, naming Bram");

            // a payment that fails after the lot was taken off (the buyer spent the pieces meanwhile) puts the lot back
            String id2 = list(server, seller, "Sella", 500, new ItemStack(Items.EMERALD, 8)).lot().id;
            MarketStore racing = new LocalMarket(store) {
                @Override
                public void buy(MinecraftServer srv, String lotId, UUID who, String whoName, long tax, Consumer<Answer> then) {
                    Bank.set(srv, late, 10, "gametest", x -> {});
                    super.buy(srv, lotId, who, whoName, tax, then);
                }
            };
            Market.useStore(racing);
            r = buy(server, late, "Lena", id2);
            Market.useStore(new LocalMarket(store));
            h.assertTrue(!r.ok() && r.key().equals("short") && Market.find(server, id2).isPresent() && Market.collectBox(server, late).isEmpty(), "the failed payment put the lot back: " + r);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_cancel_and_expiry_send_the_lot_to_collect(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID seller = account(server, player.getUUID(), "Sella", 1000);
        UUID other = account(server, UUID.randomUUID(), "Otto", 1000);
        MarketLedger store = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(store));
        long t0 = 1_800_000_000_000L;
        long[] now = {t0};
        Market.useClock(() -> now[0]);
        try {
            String a = list(server, seller, "Sella", 100, new ItemStack(Items.DIAMOND, 4)).lot().id;
            String b = list(server, seller, "Sella", 100, new ItemStack(Items.GOLD_INGOT, 4)).lot().id;
            long afterTax = Bank.pieces(server, seller);
            MarketResult[] r = new MarketResult[1];
            Market.cancel(server, other, a, false, x -> r[0] = x);
            h.assertTrue(!r[0].ok() && r[0].key().equals("not_yours"), "only the seller cancels");
            Market.cancel(server, seller, a, false, x -> r[0] = x);
            h.assertTrue(r[0].ok() && Market.find(server, a).isEmpty(), "Sella took lot A off");
            h.assertTrue(Bank.pieces(server, seller) == afterTax, "the listing tax is not given back");
            h.assertTrue(Market.collectBox(server, seller).size() == 1, "lot A waits in the Collect box");
            // lot B runs out after the listing days, not a moment before
            now[0] = t0 + Market.listingDays() * DAY - 1;
            h.assertTrue(Market.expireDue(server) == 0 && Market.find(server, b).isPresent(), "nothing runs out early");
            now[0] = t0 + Market.listingDays() * DAY;
            h.assertTrue(buy(server, other, "Otto", b).key().equals("gone"), "a lot past its end cannot be bought, even before the sweep");
            h.assertTrue(Market.expireDue(server) == 1, "lot B ran out");
            h.assertTrue(store.open().isEmpty() && Market.collectBox(server, seller).size() == 2, "both lots wait in the Collect box");
            h.assertTrue(Bank.pieces(server, other) == 1000, "Otto paid nothing");
            // collecting into a full inventory: what fits comes out, the rest stays in the box
            var inv = player.getInventory();
            for (int i = 1; i < inv.items.size(); i++) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
            inv.items.set(0, ItemStack.EMPTY);
            Market.collect(server, player, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("collect_full") && r[0].count() == 1, "one stack fitted: " + r[0]);
            h.assertTrue(Market.collectBox(server, seller).size() == 1, "the other stays in the box");
            inv.items.set(5, ItemStack.EMPTY);
            Market.collect(server, player, x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].count() == 1 && Market.collectBox(server, seller).isEmpty(), "the rest came out once there was room");
            h.assertTrue(inv.countItem(Items.DIAMOND) == 4 && inv.countItem(Items.GOLD_INGOT) == 4, "both stacks are in the inventory");
        } finally {
            Market.useClock(null);
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_ledger_keeps_lots_and_collect_boxes_through_a_save(GameTestHelper h) {
        MarketLedger ledger = new MarketLedger();
        UUID seller = UUID.randomUUID();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setHoverName(net.minecraft.network.chat.Component.literal("Edge"));
        ledger.put(new MarketLot("abc12345", seller, "Sella", List.of(sword, new ItemStack(Items.ARROW, 32)), 750, 1000, 2000, "local"));
        ledger.addCollect(seller, List.of(new ItemStack(Items.EMERALD, 3)));
        MarketLedger back = MarketLedger.load(ledger.save(new CompoundTag()));
        h.assertTrue(back.open().size() == 1, "the lot came back");
        MarketLot lot = back.open().get(0);
        h.assertTrue(lot.id.equals("abc12345") && lot.seller.equals(seller) && lot.price == 750 && lot.endsMs == 2000 && lot.stacks() == 2, "its fields came back");
        h.assertTrue(lot.items().get(0).getHoverName().getString().equals("Edge"), "item names (NBT) came back");
        h.assertTrue(back.peekCollect(seller).size() == 1 && back.peekCollect(seller).get(0).getCount() == 3, "the Collect box came back");
        h.succeed();
    }

    // ------------------------------------------------------------------ the market across the network (task 2)

    /**
     * An in-memory network database: balances, lots, Collect boxes (with the stacks being taken, by attempt) and attempt
     * records, each call whole. {@code down} makes every call fail before it changes anything; {@code lose} makes the next
     * call that changes something do it and then fail, as when the link drops at the commit; {@code downAfterLose} also takes
     * the database down at that moment, so even asking afterwards fails.
     */
    private static final class FakeDb implements MarketDb {
        final Map<UUID, Long> pieces = new HashMap<>();
        final Map<String, MarketLot> lots = new LinkedHashMap<>();
        final Map<UUID, List<ItemStack>> boxes = new HashMap<>();
        final Map<String, List<ItemStack>> taking = new HashMap<>();
        final Map<String, UUID> takingOf = new HashMap<>();
        final Map<String, Long> takingMs = new HashMap<>();
        final java.util.Set<String> attempts = new java.util.HashSet<>();
        boolean down, lose, downAfterLose, failTaken, failCommit;
        long clock = 1_800_000_000_000L;

        private void check() throws java.sql.SQLException {
            if (down) {
                throw new MarketDb.Untouched("down (gametest)");
            }
            if (failCommit) { // the commit failed and nothing changed, but the caller cannot tell that from a lost answer
                failCommit = false;
                down = downAfterLose;
                downAfterLose = false;
                throw new java.sql.SQLException("commit failed (gametest)");
            }
        }

        /** After a change: the answer is lost when {@code lose} is set. */
        private void answer() throws java.sql.SQLException {
            if (lose) {
                lose = false;
                down = downAfterLose;
                downAfterLose = false;
                throw new java.sql.SQLException("answer lost (gametest)");
            }
        }

        @Override
        public boolean committed(String attempt) throws Exception {
            check();
            return attempts.contains(attempt);
        }

        @Override
        public void flush(String attempt, List<MarketLot> held, List<MarketLot> unsure, Map<UUID, List<ItemStack>> heldBoxes) throws Exception {
            check();
            if (attempts.add(attempt)) {
                for (MarketLot l : held) {
                    if (attempts.add(l.id)) {
                        lots.putIfAbsent(l.id, l);
                    }
                }
                for (MarketLot l : unsure) {
                    if (attempts.add(l.id)) {
                        boxes.computeIfAbsent(l.seller, k -> new ArrayList<>()).addAll(l.items());
                    }
                }
                heldBoxes.forEach((p, items) -> items.forEach(s -> boxes.computeIfAbsent(p, k -> new ArrayList<>()).add(s.copy())));
            }
            answer();
        }

        @Override
        public void taken(String attempt) throws Exception {
            check();
            if (failTaken) {
                throw new java.sql.SQLException("delete failed (gametest)");
            }
            taking.remove(attempt);
            takingOf.remove(attempt);
            takingMs.remove(attempt);
            answer();
        }

        @Override
        public void untake(String attempt) throws Exception {
            check();
            List<ItemStack> back = taking.remove(attempt);
            UUID p = takingOf.remove(attempt);
            takingMs.remove(attempt);
            if (back != null) {
                boxes.computeIfAbsent(p, k -> new ArrayList<>()).addAll(0, back);
            }
            answer();
        }

        @Override
        public void sweep(long now) throws Exception {
            check();
            for (String a : new ArrayList<>(taking.keySet())) {
                if (takingMs.get(a) < now - 10 * 60_000L) {
                    untake(a);
                }
            }
        }

        int taking(UUID id) {
            return (int) takingOf.values().stream().filter(id::equals).count();
        }

        long bal(UUID id) {
            return pieces.getOrDefault(id, 0L);
        }

        int box(UUID id) {
            return boxes.getOrDefault(id, List.of()).size();
        }

        @Override
        public Done list(MarketLot lot, long tax, int maxLots) throws Exception {
            check();
            if (lots.values().stream().filter(l -> l.seller.equals(lot.seller)).count() >= maxLots) {
                return Done.no("lots");
            }
            if (bal(lot.seller) < tax) {
                return Done.no("short");
            }
            pieces.put(lot.seller, bal(lot.seller) - tax);
            pieces.put(Market.POT, bal(Market.POT) + tax);
            lots.put(lot.id, lot);
            attempts.add(lot.id);
            answer();
            return new Done("ok", lot, bal(lot.seller), -1, bal(Market.POT));
        }

        /** How many lots' data the refreshes have fetched, and how many id reads they made. */
        int fetched, idReads;

        @Override
        public List<String> openIds() throws Exception {
            check();
            idReads++;
            List<String> out = new ArrayList<>(lots.keySet());
            java.util.Collections.reverse(out);
            return out;
        }

        @Override
        public List<MarketLot> lots(List<String> ids) throws Exception {
            check();
            List<MarketLot> out = new ArrayList<>();
            for (String id : ids) {
                MarketLot l = lots.get(id);
                if (l != null) {
                    out.add(l);
                    fetched++;
                }
            }
            return out;
        }

        @Override
        public Done buy(String id, UUID buyer, long tax, long now) throws Exception {
            check();
            MarketLot lot = lots.get(id);
            if (lot == null || lot.endedBy(now)) {
                return Done.no("gone");
            }
            if (lot.seller.equals(buyer)) {
                return Done.no("own");
            }
            if (bal(buyer) < lot.price + tax) {
                return Done.no("short");
            }
            if (!pieces.containsKey(lot.seller)) {
                return Done.no("seller");
            }
            pieces.put(buyer, bal(buyer) - lot.price - tax);
            pieces.put(lot.seller, bal(lot.seller) + lot.price);
            pieces.put(Market.POT, bal(Market.POT) + tax);
            lots.remove(id);
            boxes.computeIfAbsent(buyer, k -> new ArrayList<>()).addAll(lot.items());
            answer();
            return new Done("ok", lot, bal(buyer), bal(lot.seller), bal(Market.POT));
        }

        @Override
        public Done close(String id) throws Exception {
            check();
            MarketLot lot = lots.remove(id);
            if (lot == null) {
                return Done.no("gone");
            }
            boxes.computeIfAbsent(lot.seller, k -> new ArrayList<>()).addAll(lot.items());
            return new Done("ok", lot, -1, -1, -1);
        }

        @Override
        public void addCollect(UUID player, List<ItemStack> items) throws Exception {
            check();
            items.forEach(s -> boxes.computeIfAbsent(player, k -> new ArrayList<>()).add(s.copy()));
        }

        @Override
        public List<ItemStack> takeCollect(UUID player, String attempt) throws Exception {
            check();
            List<ItemStack> box = boxes.remove(player);
            List<ItemStack> out = box == null ? new ArrayList<>() : box;
            taking.put(attempt, out);
            takingOf.put(attempt, player);
            takingMs.put(attempt, clock);
            answer();
            List<ItemStack> copy = new ArrayList<>();
            out.forEach(s -> copy.add(s.copy()));
            return copy;
        }

        @Override
        public List<ItemStack> peekCollect(UUID player) throws Exception {
            check();
            List<ItemStack> out = new ArrayList<>();
            boxes.getOrDefault(player, List.of()).forEach(s -> out.add(s.copy()));
            return out;
        }
    }

    private static void drain(List<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove(0).run();
        }
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_network_two_servers_share_lots_and_sell_each_once(GameTestHelper h) {
        var server = server(h);
        var ari = h.makeMockPlayer();
        UUID seller = account(server, UUID.randomUUID(), "Sella", 1000);
        UUID a = account(server, ari.getUUID(), "Ari", 2000);
        UUID b = account(server, UUID.randomUUID(), "Bea", 2000);
        FakeDb db = new FakeDb();
        db.pieces.put(seller, 1000L);
        db.pieces.put(a, 2000L);
        db.pieces.put(b, 2000L);
        boolean[] heard = {true};
        NetworkMarket[] two = new NetworkMarket[2];
        Runnable announce = () -> {
            if (heard[0]) {
                two[0].refresh(server);
                two[1].refresh(server);
            }
        };
        two[0] = new NetworkMarket(db, Runnable::run, Runnable::run, announce, s -> new MarketLedger());
        two[1] = new NetworkMarket(db, Runnable::run, Runnable::run, announce, s -> new MarketLedger());
        MarketStore old = Market.useStore(two[0]);
        long t0 = 1_800_000_000_000L;
        long[] now = {t0};
        Market.useClock(() -> now[0]);
        try {
            long tax = Market.tax(1000);
            // listed on server A: the database takes the tax into the pot, A's bank copy follows, server B sees the lot
            MarketResult r = list(server, seller, "Sella", 1000, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.GOLD_INGOT, 16));
            h.assertTrue(r.ok(), "listed on A: " + r);
            String id = r.lot().id;
            h.assertTrue(db.bal(seller) == 1000 - tax && db.bal(Market.POT) == tax && Bank.pieces(server, seller) == 1000 - tax, "the tax went from Sella to the pot in the database and in A's copy");
            h.assertTrue(two[1].open(server).size() == 1, "B heard of the lot");
            // Ari on A and Bea on B buy at the same moment: B has not heard of the sale yet, the database sells it once
            heard[0] = false;
            r = buy(server, a, "Ari", id);
            h.assertTrue(r.ok(), "Ari bought it on A: " + r);
            h.assertTrue(db.bal(a) == 2000 - 1000 - tax && db.bal(seller) == 1000 - tax + 1000 && db.bal(Market.POT) == 2 * tax, "the database moved the price and the tax once");
            h.assertTrue(Bank.pieces(server, a) == db.bal(a) && Bank.pieces(server, seller) == db.bal(seller), "A's bank copy follows the database");
            h.assertTrue(two[1].open(server).size() == 1, "B still shows the lot");
            Market.useStore(two[1]);
            r = buy(server, b, "Bea", id);
            h.assertTrue(!r.ok() && r.key().equals("gone") && db.bal(b) == 2000, "Bea gets nothing and pays nothing: " + r);
            h.assertTrue(two[1].open(server).isEmpty(), "B dropped the sold lot from its copy");
            // Ari is not online here, so the stacks went to the database's Collect box; Ari collects them on B
            h.assertTrue(db.box(a) == 2, "the stacks wait in Ari's Collect box on the network");
            MarketResult[] got = new MarketResult[1];
            Market.collect(server, ari, x -> got[0] = x);
            h.assertTrue(got[0].ok() && got[0].count() == 2 && ari.getInventory().countItem(Items.DIAMOND) == 64 && db.box(a) == 0, "Ari collected on another server: " + got[0]);
            // a lot that ends is closed once, though both servers sweep it
            Market.useStore(two[0]);
            heard[0] = true;
            String id2 = list(server, seller, "Sella", 100, new ItemStack(Items.EMERALD, 5)).lot().id;
            heard[0] = false;
            now[0] = t0 + Market.listingDays() * 24L * 60 * 60 * 1000;
            Market.expireDue(server);
            Market.useStore(two[1]);
            Market.expireDue(server);
            h.assertTrue(!db.lots.containsKey(id2) && db.box(seller) == 1 && two[1].open(server).isEmpty(), "the ended lot went to Sella's box once");
        } finally {
            Market.useClock(null);
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_network_failures_and_late_answers_move_nothing_twice(GameTestHelper h) {
        var server = server(h);
        var sella = h.makeMockPlayer();
        UUID seller = account(server, sella.getUUID(), "Sella", 1000);
        UUID buyer = account(server, UUID.randomUUID(), "Bram", 3000);
        FakeDb db = new FakeDb();
        db.pieces.put(seller, 1000L);
        db.pieces.put(buyer, 3000L);
        List<Runnable> queue = new ArrayList<>();
        MarketLedger held = new MarketLedger();
        NetworkMarket net = new NetworkMarket(db, queue::add, Runnable::run, () -> {}, s -> held);
        MarketStore old = Market.useStore(net);
        try {
            MarketResult[] r = new MarketResult[1];
            // the database is down: a listing charges nothing and hands the stack back
            db.down = true;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.DIAMOND, 3)), 500, x -> r[0] = x);
            drain(queue);
            h.assertTrue(!r[0].ok() && r[0].key().equals("unreachable") && r[0].items().size() == 1, "unreachable, the stack comes back: " + r[0]);
            h.assertTrue(db.bal(seller) == 1000 && Bank.pieces(server, seller) == 1000 && net.open(server).isEmpty(), "nothing charged, nothing listed");
            db.down = false;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.DIAMOND, 3)), 500, x -> r[0] = x);
            drain(queue);
            String id = r[0].lot().id;
            // a purchase whose answer comes late: nothing is decided until the database answers
            r[0] = null;
            Market.buy(server, buyer, "Bram", id, x -> r[0] = x);
            h.assertTrue(r[0] == null && db.lots.containsKey(id) && db.bal(buyer) == 3000, "no answer yet, nothing moved");
            drain(queue);
            h.assertTrue(r[0] != null && r[0].ok() && db.bal(buyer) == 3000 - 500 - Market.tax(500), "the late answer finished the purchase: " + r[0]);
            h.assertTrue(db.box(buyer) == 1, "Bram was not online here: the stack waits in his box on the network");
            // a purchase while the database is down: unbought, nothing moved
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.GOLD_INGOT, 2)), 200, x -> r[0] = x);
            drain(queue);
            String id2 = r[0].lot().id;
            long bramBefore = db.bal(buyer);
            db.down = true;
            Market.buy(server, buyer, "Bram", id2, x -> r[0] = x);
            drain(queue);
            db.down = false;
            h.assertTrue(r[0].key().equals("unreachable") && db.lots.containsKey(id2) && net.open(server).stream().anyMatch(l -> l.id.equals(id2)) && db.bal(buyer) == bramBefore,
                    "unreachable: the lot stays on sale, Bram keeps his pieces: " + r[0]);
            // a Collect box that cannot be written waits on this server and goes up at the next refresh
            db.down = true;
            net.addCollect(server, sella.getUUID(), List.of(new ItemStack(Items.EMERALD, 5)));
            drain(queue);
            h.assertTrue(held.peekCollect(sella.getUUID()).size() == 1 && Market.collectBox(server, sella.getUUID()).stream().anyMatch(s -> s.is(Items.EMERALD)), "the emeralds wait here, and the box shows them");
            db.down = false;
            net.refresh(server);
            drain(queue);
            h.assertTrue(held.peekCollect(sella.getUUID()).isEmpty() && db.box(sella.getUUID()) == 1, "the refresh wrote them to the network");
            // collecting while the database is down takes nothing out
            db.down = true;
            Market.collect(server, sella, x -> r[0] = x);
            drain(queue);
            db.down = false;
            h.assertTrue(r[0].key().equals("unreachable") && db.box(sella.getUUID()) == 1 && sella.getInventory().countItem(Items.EMERALD) == 0, "nothing taken while unreachable");
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_network_lost_answers_never_dupe_or_lose_items(GameTestHelper h) {
        var server = server(h);
        Player sella = h.makeMockPlayer(), bram = h.makeMockPlayer();
        UUID seller = account(server, sella.getUUID(), "Sella", 1000);
        UUID buyer = account(server, bram.getUUID(), "Bram", 5000);
        FakeDb db = new FakeDb();
        db.pieces.put(seller, 1000L);
        db.pieces.put(buyer, 5000L);
        MarketLedger held = new MarketLedger();
        NetworkMarket net = new NetworkMarket(db, Runnable::run, Runnable::run, () -> {}, s -> held);
        MarketStore old = Market.useStore(net);
        try {
            MarketResult[] r = new MarketResult[1];
            // a listing whose answer is lost after the database took it: asked again, it is listed, and the stacks do not come back
            db.lose = true;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.DIAMOND, 3)), 500, x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].items().isEmpty() && db.lots.size() == 1 && net.open(server).size() == 1, "listed after all, nothing handed back: " + r[0]);
            String listed = r[0].lot().id;
            // lost, and the database cannot even be asked: the stacks wait as unsure; the next write finds the lot listed and drops them
            db.lose = true;
            db.downAfterLose = true;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.EMERALD, 4)), 300, x -> r[0] = x);
            h.assertTrue(!r[0].ok() && r[0].key().equals("unsure") && r[0].items().isEmpty() && db.lots.size() == 2, "unsure: kept, not handed back: " + r[0]);
            db.down = false;
            net.refresh(server);
            h.assertTrue(db.lots.size() == 2 && db.box(seller) == 0 && Market.collectBox(server, seller).isEmpty(), "the lot is listed once and nothing went to the box");
            // a listing the database never saw: it is handed back at once
            db.down = true;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.GOLD_INGOT, 2)), 200, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("unreachable") && r[0].items().size() == 1, "never reached: handed back: " + r[0]);
            db.down = false;
            // a commit that failed without going in, and the database cannot be asked: unsure, then its stacks reach the seller's box, once
            db.failCommit = true;
            db.downAfterLose = true;
            Market.list(server, seller, "Sella", List.of(new ItemStack(Items.GOLD_INGOT, 2)), 200, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("unsure") && r[0].items().isEmpty(), "unsure again: " + r[0]);
            db.down = false;
            net.refresh(server);
            net.refresh(server);
            h.assertTrue(db.lots.size() == 2 && db.box(seller) == 1 && db.boxes.get(seller).get(0).is(Items.GOLD_INGOT), "the gold went to Sella's box once, unlisted");
            // a purchase whose answer is lost: the pieces moved and the items wait in Bram's box, and collecting gives them once
            db.lose = true;
            Market.buy(server, buyer, "Bram", listed, x -> r[0] = x);
            h.assertTrue(!r[0].ok() && r[0].key().equals("unsure_buy") && db.box(buyer) == 1 && !db.lots.containsKey(listed)
                    && db.bal(buyer) == 5000 - 500 - Market.tax(500), "the purchase went through and its items are in the box: " + r[0]);
            Market.collect(server, bram, x -> r[0] = x);
            h.assertTrue(r[0].ok() && bram.getInventory().countItem(Items.DIAMOND) == 3 && db.box(buyer) == 0 && db.taking(buyer) == 0, "collected once: " + r[0]);
            // emptying a box whose answer is lost: nothing is given and the stacks are back in the box
            db.boxes.put(buyer, new ArrayList<>(List.of(new ItemStack(Items.APPLE, 5))));
            db.lose = true;
            Market.collect(server, bram, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("unreachable") && bram.getInventory().countItem(Items.APPLE) == 0 && db.box(buyer) == 1 && db.taking(buyer) == 0,
                    "nothing given, the apples are back in the box: " + r[0]);
            // delivered, but the rows cannot be deleted: they stay out of the box, a second collect gives nothing, the next refresh deletes them
            db.failTaken = true;
            Market.collect(server, bram, x -> r[0] = x);
            h.assertTrue(r[0].ok() && bram.getInventory().countItem(Items.APPLE) == 5 && db.box(buyer) == 0 && db.taking(buyer) == 1 && held.deletes().size() == 1,
                    "the apples were delivered; their rows wait to be deleted");
            Market.collect(server, bram, x -> r[0] = x);
            h.assertTrue(bram.getInventory().countItem(Items.APPLE) == 5, "a second collect gives nothing more");
            db.failTaken = false;
            net.refresh(server);
            h.assertTrue(db.taking(buyer) == 0 && held.deletes().isEmpty() && db.box(buyer) == 0, "the refresh deleted them");
            // a box left half taken by a server that stopped goes back after ten minutes, not before
            db.boxes.put(buyer, new ArrayList<>(List.of(new ItemStack(Items.BREAD, 2))));
            try {
                db.takeCollect(buyer, "t-stopped");
                db.sweep(db.clock + 5 * 60_000L);
                h.assertTrue(db.box(buyer) == 0 && db.taking(buyer) == 1, "five minutes on, still being taken");
                db.sweep(db.clock + 11 * 60_000L);
            } catch (Exception e) {
                h.fail("the fake database failed: " + e);
            }
            h.assertTrue(db.box(buyer) == 1 && db.taking(buyer) == 0, "ten minutes on, the bread is back in the box");
            // held items whose write's answer is lost are not written twice
            db.down = true;
            net.addCollect(server, sella.getUUID(), List.of(new ItemStack(Items.EMERALD, 7)));
            db.down = false;
            int before = db.box(seller);
            db.lose = true;
            net.refresh(server);
            h.assertTrue(db.box(seller) == before + 1 && Market.collectBox(server, seller).stream().anyMatch(s -> s.is(Items.EMERALD)), "written, the answer lost, still shown");
            net.refresh(server);
            net.refresh(server);
            h.assertTrue(db.box(seller) == before + 1, "the second try of the same batch wrote nothing: " + db.box(seller));
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_network_counts_the_lot_limit_in_the_database_and_needs_the_sellers_account(GameTestHelper h) {
        var server = server(h);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 100_000);
        UUID buyer = account(server, UUID.randomUUID(), "Bram", 5000);
        FakeDb db = new FakeDb();
        db.pieces.put(seller, 100_000L);
        db.pieces.put(buyer, 5000L);
        NetworkMarket a = new NetworkMarket(db, Runnable::run, Runnable::run, () -> {}, x -> new MarketLedger());
        NetworkMarket b = new NetworkMarket(db, Runnable::run, Runnable::run, () -> {}, x -> new MarketLedger());
        MarketStore old = Market.useStore(a);
        try {
            for (int i = 0; i < Market.maxLots(); i++) {
                h.assertTrue(list(server, seller, "Sella", 10, new ItemStack(Items.DIRT, 1 + i)).ok(), "lot " + (i + 1) + " listed on A");
            }
            // server B has not heard of them: its own count says 0, the database's says the limit
            Market.useStore(b);
            h.assertTrue(Market.lotsOf(server, seller) == 0, "B's copy knows of no lots");
            long before = db.bal(seller);
            MarketResult r = list(server, seller, "Sella", 10, new ItemStack(Items.EMERALD, 1));
            h.assertTrue(!r.ok() && r.key().equals("lots") && r.items().size() == 1 && db.lots.size() == Market.maxLots() && db.bal(seller) == before,
                    "the database refused the lot over the limit, nothing charged, the stack handed back: " + r);
            // a lot whose seller has no bank account on the network: nobody could be paid, so it is not sold
            UUID ghost = UUID.randomUUID();
            MarketLot orphan = new MarketLot("orphan01", ghost, "Ghost", List.of(new ItemStack(Items.DIAMOND)), 100, Market.now(), Market.now() + DAY, "s0");
            db.lots.put(orphan.id, orphan);
            b.refresh(server);
            r = buy(server, buyer, "Bram", orphan.id);
            h.assertTrue(!r.ok() && r.key().equals("seller") && db.bal(buyer) == 5000 && db.lots.containsKey(orphan.id) && db.box(buyer) == 0, "refused, nothing moved: " + r);
            // the retry on a deadlock still sees the error code through the "nothing changed" marker
            h.assertTrue(com.mrgregles.bsp_core.storage.SqlRetry.lockFailure(new MarketDb.Untouched(new java.sql.SQLException("deadlock", "40001", 1213)))
                    && com.mrgregles.bsp_core.storage.SqlRetry.lockFailure(new MarketDb.Untouched(new java.sql.SQLException("lock wait", "HY000", 1205)))
                    && !com.mrgregles.bsp_core.storage.SqlRetry.lockFailure(new MarketDb.Untouched(new java.sql.SQLException("syntax", "42000", 1064))), "deadlocks and lock waits are retried, nothing else");
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_network_refresh_reads_only_new_lots_and_each_server_sweeps_its_own(GameTestHelper h) {
        var server = server(h);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 100_000);
        UUID buyer = account(server, UUID.randomUUID(), "Bram", 100_000);
        FakeDb db = new FakeDb();
        db.pieces.put(seller, 100_000L);
        db.pieces.put(buyer, 100_000L);
        int[] notices = {0};
        List<UUID> boxes = new ArrayList<>();
        NetworkMarket a = new NetworkMarket(db, Runnable::run, Runnable::run, () -> notices[0]++, x -> new MarketLedger(), boxes::add);
        NetworkMarket b = new NetworkMarket(db, Runnable::run, Runnable::run, () -> {}, x -> new MarketLedger());
        long[] now = {1_800_000_000_000L};
        MarketStore old = Market.useStore(a);
        Market.useClock(() -> now[0]);
        try {
            for (int i = 0; i < 3; i++) {
                now[0] += 1000;
                list(server, seller, "Sella", 10 + i, new ItemStack(Items.DIRT, 1 + i));
            }
            b.refresh(server);
            h.assertTrue(b.open(server).size() == 3 && db.fetched == 3, "B read the three lots: " + db.fetched);
            now[0] += 1000;
            String fourth = list(server, seller, "Sella", 50, new ItemStack(Items.EMERALD, 1)).lot().id;
            b.refresh(server);
            h.assertTrue(b.open(server).size() == 4 && db.fetched == 4 && b.open(server).get(0).id.equals(fourth), "B fetched only the new lot, and has it first: " + db.fetched);
            h.assertTrue(buy(server, buyer, "Bram", fourth).ok(), "Bram buys it on A");
            b.refresh(server);
            h.assertTrue(b.open(server).size() == 3 && db.fetched == 4, "the sold lot left B's copy with nothing fetched");
            // three notices while a refresh is reading: one more read afterwards, not three
            List<Runnable> queue = new ArrayList<>();
            NetworkMarket c = new NetworkMarket(db, queue::add, Runnable::run, () -> {}, x -> new MarketLedger());
            int reads = db.idReads;
            c.refresh(server);
            c.refresh(server);
            c.refresh(server);
            drain(queue);
            h.assertTrue(db.idReads == reads + 2 && c.open(server).size() == 3, "two reads for three asks: " + (db.idReads - reads));
            // this server's own ended lots close in one sweep with one notice, each seller's box announced; a lot listed on
            // another server (ending a minute later) waits for that server, until ten minutes past its end
            long ownEnd = 0;
            for (MarketLot l : a.open(server)) {
                ownEnd = Math.max(ownEnd, l.endsMs);
            }
            MarketLot foreign = new MarketLot("foreign1", seller, "Sella", List.of(new ItemStack(Items.GOLD_INGOT)), 30, now[0], ownEnd + 60_000, "elsewhere");
            db.lots.put(foreign.id, foreign);
            a.refresh(server);
            now[0] = ownEnd + 1;
            notices[0] = 0;
            boxes.clear();
            Market.expireDue(server);
            h.assertTrue(db.lots.size() == 1 && db.lots.containsKey(foreign.id) && notices[0] == 1 && boxes.size() == 3, "three own lots closed, one notice, three box notices: "
                    + db.lots.size() + " " + notices[0] + " " + boxes.size());
            now[0] = foreign.endsMs + 1;
            Market.expireDue(server);
            h.assertTrue(db.lots.containsKey(foreign.id), "just past its end: left to its own server");
            now[0] = foreign.endsMs + Market.SWEEP_GRACE_MS + 1;
            Market.expireDue(server);
            h.assertTrue(db.lots.isEmpty() && db.box(seller) == 4, "ten minutes on, any server closes the other server's lot");
        } finally {
            Market.useClock(null);
            Market.useStore(old);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ live trades on one server (task 3)

    /** Two players at Trade Posts trading with each other: {@code a} asked, {@code b} accepted. */
    private static TradeSession start(GameTestHelper h, Player a, Player b) {
        Trades.join(a, null);
        Trades.join(b, null);
        h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok"), "the ask goes out");
        h.assertTrue(Trades.answer(b, a.getUUID(), true).equals("ok"), "the trade starts");
        return Trades.session(a.getUUID()).orElseThrow();
    }

    private static void leaveAll(Player... players) {
        for (Player p : players) {
            Trades.leave(p, "closed");
        }
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_requests_last_30_seconds_one_at_a_time(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer(), c = h.makeMockPlayer();
        long[] now = {1_800_000_000_000L};
        Trades.useClock(() -> now[0]);
        try {
            Trades.join(a, null);
            Trades.join(b, null);
            h.assertTrue(Trades.ask(c, a.getUUID()).equals("not_here"), "only a player at a Trade Post can ask");
            h.assertTrue(Trades.ask(a, a.getUUID()).equals("self"), "nobody trades with themself");
            h.assertTrue(Trades.ask(a, c.getUUID()).equals("away"), "only a player at a Trade Post can be asked");
            h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok") && Trades.ask(a, b.getUUID()).equals("waiting"), "one request out at a time");
            h.assertTrue(Trades.requestsTo(b.getUUID()).size() == 1, "B has the request");
            h.assertTrue(Trades.answer(b, a.getUUID(), false).equals("declined") && Trades.requestFrom(a.getUUID()).isEmpty(), "declining ends it");
            h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok"), "A can ask again");
            now[0] += Trades.REQUEST_MS;
            Trades.tick();
            h.assertTrue(Trades.requestFrom(a.getUUID()).isEmpty() && Trades.answer(b, a.getUUID(), true).equals("gone"), "after 30 seconds the request is gone");
            h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok") && Trades.answer(b, a.getUUID(), true).equals("ok"), "accepted: the trade starts");
            h.assertTrue(Trades.busy(a.getUUID()) && Trades.busy(b.getUUID()), "both are busy");
            Trades.join(c, null);
            h.assertTrue(Trades.ask(c, a.getUUID()).equals("busy"), "a busy player cannot be asked");
            var list = Trades.traders(c.getUUID());
            h.assertTrue(list.size() == 2 && list.stream().allMatch(com.mrgregles.bsp_core.trade.TradeHub.Trader::busy), "C sees A and B, both busy");
            h.assertTrue(Trades.traders(a.getUUID()).stream().noneMatch(t -> t.id().equals(a.getUUID())), "nobody sees themself in the list");
            Trades.leave(a, "closed");
            h.assertTrue(!Trades.busy(b.getUUID()) && Trades.present(a.getUUID()).isEmpty(), "A left: the trade ended and A is off the list");
        } finally {
            Trades.useClock(null);
            leaveAll(a, b, c);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_any_change_drops_both_ready_and_the_countdown_locks(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        account(server, a.getUUID(), "Ann", 1000);
        account(server, b.getUUID(), "Ben", 50);
        long[] now = {1_800_000_000_000L};
        Trades.useClock(() -> now[0]);
        try {
            TradeSession s = start(h, a, b);
            TradeSession.Side sa = s.side(a.getUUID()), sb = s.side(b.getUUID());
            TradeMenu ma = new TradeMenu(0, a.getInventory(), s, sa), mb = new TradeMenu(0, b.getInventory(), s, sb);
            // A shift-clicks 64 Diamonds from the inventory into the offer
            a.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 64));
            ma.quickMoveStack(a, TradeMenu.INV);
            h.assertTrue(sa.items().size() == 1 && a.getInventory().countItem(Items.DIAMOND) == 0, "the Diamonds moved into A's offer");
            h.assertTrue(mb.getSlot(TradeMenu.THEIRS).getItem().is(Items.DIAMOND) && !mb.getSlot(TradeMenu.THEIRS).mayPickup(b), "B sees them and cannot take them");
            // totems never go in
            a.getInventory().setItem(10, new ItemStack(ModItems.SHATTER_TOTEM.get()));
            ma.quickMoveStack(a, TradeMenu.INV + 1);
            h.assertTrue(sa.items().size() == 1 && !ma.getSlot(TradeMenu.MINE + 5).mayPlace(new ItemStack(ModItems.TOTEM_CORE.get())), "a totem or a Totem Core is refused");
            // pieces are checked against the bank at READY
            s.setPieces(sb, 100);
            h.assertTrue(s.ready(sb).equals("short"), "Ben has 50 pieces, offers 100: refused");
            h.assertTrue(s.ready(sa).equals("ok") && sa.ready(), "A is ready");
            s.setPieces(sb, 40);
            h.assertTrue(!sa.ready(), "B changing the pieces turned A's READY off");
            h.assertTrue(s.ready(sa).equals("ok") && s.ready(sb).equals("ok") && s.state() == TradeSession.State.COUNTDOWN, "both ready: the countdown runs");
            // the countdown locks the offer
            a.getInventory().setItem(11, new ItemStack(Items.GOLD_INGOT, 8));
            ma.quickMoveStack(a, TradeMenu.INV + 2);
            h.assertTrue(sa.items().size() == 1 && !ma.getSlot(TradeMenu.MINE).mayPickup(a) && s.setPieces(sa, 5).equals("locked"), "nothing changes during the countdown");
            h.assertTrue(s.unready(sb).equals("ok") && s.state() == TradeSession.State.OPEN && !sb.ready(), "un-ready stops the countdown");
            // any change of an item turns the lights off: A is still ready, and takes the Diamonds back out
            h.assertTrue(sa.ready(), "A is still ready");
            ma.quickMoveStack(a, TradeMenu.MINE);
            h.assertTrue(!sa.ready() && !sb.ready() && sa.items().isEmpty() && a.getInventory().countItem(Items.DIAMOND) == 64, "taking the Diamonds back out turned both lights off");
            // a change from outside during a countdown (no screen allows one) stops it
            sa.offer.setItem(0, new ItemStack(Items.DIAMOND, 64));
            s.ready(sa);
            s.ready(sb);
            h.assertTrue(s.state() == TradeSession.State.COUNTDOWN, "counting down again");
            sb.offer.setItem(0, new ItemStack(Items.DIRT, 1));
            h.assertTrue(!sa.ready() && !sb.ready() && s.state() == TradeSession.State.OPEN, "the change stopped the countdown and turned both lights off");
            now[0] += TradeSession.COUNTDOWN_MS;
            Trades.tick();
            h.assertTrue(s.state() == TradeSession.State.OPEN, "no trade happens after a stopped countdown");
            // the chat: formatting codes out, at most 120 characters
            s.say(sa, "\u00a7chello   ");
            s.say(sb, "x".repeat(300));
            var chat = s.chat();
            h.assertTrue(chat.stream().anyMatch(l -> l.text().equals("hello")) && chat.stream().anyMatch(l -> l.text().length() == TradeSession.CHAT_MAX), "chat lines are cleaned and capped: " + chat);
        } finally {
            Trades.useClock(null);
            leaveAll(a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_completes_after_the_countdown_and_moves_items_and_pieces(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        account(server, a.getUUID(), "Ann", 1000);
        account(server, b.getUUID(), "Ben", 1000);
        long t0 = 1_800_000_000_000L;
        long[] now = {t0};
        Trades.useClock(() -> now[0]);
        List<TradeEvent.Completed> done = new ArrayList<>();
        Consumer<TradeEvent.Completed> listener = done::add;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, TradeEvent.Completed.class, listener);
        try {
            TradeSession s = start(h, a, b);
            TradeSession.Side sa = s.side(a.getUUID()), sb = s.side(b.getUUID());
            sa.offer.setItem(0, new ItemStack(Items.DIAMOND, 64));
            sb.offer.setItem(0, new ItemStack(Items.EMERALD, 1));
            s.setPieces(sb, 250);
            s.ready(sa);
            s.ready(sb);
            now[0] = t0 + TradeSession.COUNTDOWN_MS - 1;
            Trades.tick();
            h.assertTrue(s.state() == TradeSession.State.COUNTDOWN, "not before 5 seconds");
            now[0] = t0 + TradeSession.COUNTDOWN_MS;
            Trades.tick();
            h.assertTrue(s.state() == TradeSession.State.DONE && !Trades.busy(a.getUUID()), "the trade is made");
            h.assertTrue(a.getInventory().countItem(Items.EMERALD) == 1 && b.getInventory().countItem(Items.DIAMOND) == 64, "the items crossed over");
            h.assertTrue(sa.items().isEmpty() && sb.items().isEmpty(), "the offers are empty");
            h.assertTrue(Bank.pieces(server, a.getUUID()) == 1250 && Bank.pieces(server, b.getUUID()) == 750, "Ben paid Ann 250 pieces");
            h.assertTrue(done.stream().anyMatch(e -> e.getFirst().equals(a.getUUID()) && e.getSecondPieces() == 250 && e.getFirstGave().size() == 1), "one Completed event");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            Trades.useClock(null);
            leaveAll(a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_space_is_worked_out_and_nothing_moves_when_short(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        account(server, a.getUUID(), "Ann", 1000);
        account(server, b.getUUID(), "Ben", 1000);
        long t0 = 1_800_000_000_000L;
        long[] now = {t0};
        Trades.useClock(() -> now[0]);
        try {
            // stacking counts: 32 Diamonds fit onto a part stack in an otherwise full inventory
            var inv = b.getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
            inv.items.set(3, new ItemStack(Items.DIAMOND, 32));
            h.assertTrue(TradeSession.fits(inv, List.of(new ItemStack(Items.DIAMOND, 32))) == 1, "32 Diamonds top up the part stack");
            h.assertTrue(TradeSession.fits(inv, List.of(new ItemStack(Items.DIAMOND, 33))) == 0, "33 do not");
            inv.items.set(3, ItemStack.EMPTY);
            TradeSession s = start(h, a, b);
            TradeSession.Side sa = s.side(a.getUUID()), sb = s.side(b.getUUID());
            sa.offer.setItem(0, new ItemStack(Items.DIAMOND, 64));
            sa.offer.setItem(1, new ItemStack(Items.GOLD_INGOT, 64));
            sa.offer.setItem(2, new ItemStack(Items.IRON_INGOT, 64));
            s.setPieces(sa, 100);
            s.ready(sa);
            s.ready(sb);
            now[0] = t0 + TradeSession.COUNTDOWN_MS;
            Trades.tick();
            h.assertTrue(s.state() == TradeSession.State.OPEN && !sa.ready() && !sb.ready(), "Ben has room for 1 of 3: back to open, both lights off");
            h.assertTrue(sa.items().size() == 3 && inv.countItem(Items.DIAMOND) == 0, "nothing moved");
            h.assertTrue(Bank.pieces(server, a.getUUID()) == 1000 && Bank.pieces(server, b.getUUID()) == 1000, "no pieces moved");
            h.assertTrue(s.chat().stream().anyMatch(l -> l.name().isEmpty() && l.text().contains("1") && l.text().contains("3")), "the note names the room: " + s.chat());
        } finally {
            Trades.useClock(null);
            leaveAll(a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_cancel_gives_the_items_back_and_drops_what_does_not_fit(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        account(server, a.getUUID(), "Ann", 1000);
        account(server, b.getUUID(), "Ben", 1000);
        try {
            TradeSession s = start(h, a, b);
            TradeSession.Side sa = s.side(a.getUUID()), sb = s.side(b.getUUID());
            String mark = "Trade gametest " + UUID.randomUUID();
            ItemStack marked = new ItemStack(Items.APPLE, 3);
            marked.setHoverName(net.minecraft.network.chat.Component.literal(mark));
            sa.offer.setItem(0, new ItemStack(Items.DIAMOND, 7));
            sa.offer.setItem(1, marked);
            sb.offer.setItem(0, new ItemStack(Items.EMERALD, 2));
            // A's inventory fills up meanwhile but for one slot
            var inv = a.getInventory();
            for (int i = 1; i < inv.items.size(); i++) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
            inv.items.set(0, ItemStack.EMPTY);
            Trades.leave(a, "left");
            h.assertTrue(s.state() == TradeSession.State.CANCELLED && !Trades.busy(b.getUUID()), "A leaving cancelled the trade");
            h.assertTrue(b.getInventory().countItem(Items.EMERALD) == 2, "Ben's Emeralds are back");
            h.assertTrue(inv.items.get(0).is(Items.DIAMOND) && inv.items.get(0).getCount() == 7, "the Diamonds went into the free slot");
            h.assertTrue(!h.getLevel().getEntities(net.minecraft.world.entity.EntityType.ITEM, e -> e.getItem().getHoverName().getString().equals(mark)).isEmpty(),
                    "the apples that did not fit dropped at Ann's feet");
            inv.items.set(0, ItemStack.EMPTY);
            // walking away: a Trade Post far off makes the menu invalid, which cancels the trade
            TradeSession s2 = start(h, a, b);
            Trades.join(a, net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), a.blockPosition().offset(20, 0, 0)));
            TradeMenu ma = new TradeMenu(0, a.getInventory(), s2, s2.side(a.getUUID()));
            h.assertTrue(!ma.stillValid(a), "more than 8 blocks from the post: the screen closes");
            ma.removed(a);
            h.assertTrue(s2.state() == TradeSession.State.CANCELLED, "closing it cancelled the trade");
        } finally {
            leaveAll(a, b);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ live trades across servers (task 4)

    /** Two or more hubs as servers: messages wait in a queue until {@link #pump}; {@code cut} loses everything, {@code drop} loses one kind. */
    private static final class Loop {
        final List<TradeHub> hubs = new ArrayList<>();
        final List<Runnable> queue = new ArrayList<>();
        boolean cut;
        final java.util.Set<String> drop = new java.util.HashSet<>();

        TradeLink from(String server) {
            return (to, m) -> {
                if (cut || drop.contains(m.getString("t"))) {
                    return;
                }
                for (TradeHub hub : hubs) {
                    if (!hub.serverId().equals(server) && (to.equals("*") || to.equals(hub.serverId()))) {
                        CompoundTag copy = m.copy();
                        queue.add(() -> hub.onMessage(server, copy));
                    }
                }
            };
        }

        void pump() {
            for (int i = 0; i < 500 && !queue.isEmpty(); i++) {
                queue.remove(0).run();
            }
        }
    }

    /**
     * An in-memory network database for trades: balances, outcome rows, escrow (and which escrows ever went in) and pending
     * items. {@code down} fails every call before it changes anything; {@code loseEscrow} / {@code loseSettle} make the next
     * escrow / settle go in and then lose its answer; {@code failEscrow} makes the next escrow fail at its commit without going
     * in (the caller cannot tell the two apart); {@code downAfter} takes the database down at that moment too.
     */
    private static final class FakeTradeDb implements TradeDb {
        boolean down, loseEscrow, loseSettle, failEscrow, downAfter;
        final java.util.Set<String> escrowed = new java.util.HashSet<>();

        private void check() throws java.sql.SQLException {
            if (down) {
                throw new com.mrgregles.bsp_core.market.MarketDb.Untouched("down (gametest)");
            }
        }

        private java.sql.SQLException lost(String what) {
            down = downAfter;
            downAfter = false;
            return new java.sql.SQLException(what + " (gametest)");
        }

        @Override
        public Recovered recoverEscrow(String attempt, UUID owner) throws Exception {
            check();
            String outcome = decideAbort(attempt);
            boolean was = escrowed.contains(attempt + "/" + owner);
            if (!outcome.equals("aborted")) {
                return new Recovered(outcome, was, List.of());
            }
            return new Recovered("aborted", was, takeEscrow(attempt, owner));
        }

        final Map<UUID, Long> pieces = new HashMap<>();
        final Map<String, String> outcomes = new HashMap<>();
        final Map<String, List<ItemStack>> escrow = new LinkedHashMap<>();
        final Map<String, Long> escrowMs = new HashMap<>();
        final Map<UUID, List<ItemStack>> pending = new HashMap<>();
        final long[] clock;

        FakeTradeDb(long[] clock) {
            this.clock = clock;
        }

        long bal(UUID id) {
            return pieces.getOrDefault(id, 0L);
        }

        int pendingFor(UUID id) {
            return pending.getOrDefault(id, List.of()).size();
        }

        @Override
        public Settled settle(String attempt, UUID a, long aPieces, UUID b, long bPieces, List<ItemStack> hostItems, UUID hostItemsTo, UUID escrowOwner, UUID escrowTo) throws Exception {
            check();
            Settled s = settleNow(attempt, a, aPieces, b, bPieces, hostItems, hostItemsTo, escrowOwner, escrowTo);
            if (loseSettle && s.ok()) {
                loseSettle = false;
                throw lost("settle answer lost");
            }
            return s;
        }

        private Settled settleNow(String attempt, UUID a, long aPieces, UUID b, long bPieces, List<ItemStack> hostItems, UUID hostItemsTo, UUID escrowOwner, UUID escrowTo) {
            if (outcomes.containsKey(attempt)) {
                return new Settled("aborted", -1, -1);
            }
            if (bal(a) < aPieces) {
                return new Settled("short_a", -1, -1);
            }
            if (bal(b) < bPieces) {
                return new Settled("short_b", -1, -1);
            }
            outcomes.put(attempt, "done");
            pieces.put(a, bal(a) - aPieces + bPieces);
            pieces.put(b, bal(b) - bPieces + aPieces);
            if (hostItemsTo != null) {
                hostItems.forEach(x -> pending.computeIfAbsent(hostItemsTo, k -> new ArrayList<>()).add(x.copy()));
            }
            if (escrowOwner != null && escrowTo != null) {
                List<ItemStack> e = escrow.remove(attempt + "/" + escrowOwner);
                if (e != null) {
                    pending.computeIfAbsent(escrowTo, k -> new ArrayList<>()).addAll(e);
                }
            }
            return new Settled("ok", bal(a), bal(b));
        }

        @Override
        public void escrow(String attempt, UUID owner, List<ItemStack> items) throws Exception {
            check();
            if (failEscrow) {
                failEscrow = false;
                throw lost("escrow commit failed");
            }
            escrow.computeIfAbsent(attempt + "/" + owner, k -> new ArrayList<>()).addAll(items);
            escrowMs.put(attempt + "/" + owner, clock[0]);
            escrowed.add(attempt + "/" + owner);
            if (loseEscrow) {
                loseEscrow = false;
                throw lost("escrow answer lost");
            }
        }

        @Override
        public String decideAbort(String attempt) throws Exception {
            check();
            return outcomes.computeIfAbsent(attempt, k -> "aborted");
        }

        @Override
        public List<ItemStack> takeEscrow(String attempt, UUID owner) throws Exception {
            check();
            if (!"aborted".equals(outcomes.get(attempt))) {
                return List.of();
            }
            List<ItemStack> e = escrow.remove(attempt + "/" + owner);
            return e == null ? List.of() : e;
        }

        @Override
        public List<ItemStack> takePending(UUID player) throws Exception {
            check();
            List<ItemStack> p = pending.remove(player);
            return p == null ? List.of() : p;
        }

        @Override
        public int sweep(long beforeMs) throws Exception {
            check();
            int n = 0;
            for (String key : new ArrayList<>(escrow.keySet())) {
                if (escrowMs.getOrDefault(key, 0L) >= beforeMs) {
                    continue;
                }
                String attempt = key.substring(0, key.indexOf('/'));
                if (!decideAbort(attempt).equals("aborted")) {
                    continue;
                }
                UUID owner = UUID.fromString(key.substring(key.indexOf('/') + 1));
                List<ItemStack> e = escrow.remove(key);
                pending.computeIfAbsent(owner, k -> new ArrayList<>()).addAll(e);
                n += e.size();
            }
            return n;
        }
    }

    /** Two servers s1 and s2 on one loop and one database, with Ann on s1 and Ben on s2 at Trade Posts, each holding 1,000 pieces. */
    private record Two(Loop loop, FakeTradeDb db, TradeHub h1, TradeHub h2, Player ann, Player ben, long[] now) {
        void tick() {
            h1.tick();
            h2.tick();
            loop.pump();
        }

        void at(long ms) {
            now[0] = ms;
            tick();
        }
    }

    private static Two two(GameTestHelper h) {
        var server = server(h);
        long[] now = {1_800_000_000_000L};
        Loop loop = new Loop();
        FakeTradeDb db = new FakeTradeDb(now);
        TradeHub h1 = new TradeHub("s1"), h2 = new TradeHub("s2");
        loop.hubs.add(h1);
        loop.hubs.add(h2);
        h1.connect("s1", loop.from("s1"), db, Runnable::run, Runnable::run);
        h2.connect("s2", loop.from("s2"), db, Runnable::run, Runnable::run);
        h1.useClock(() -> now[0]);
        h2.useClock(() -> now[0]);
        Player ann = h.makeMockPlayer(), ben = h.makeMockPlayer();
        account(server, ann.getUUID(), "Ann", 1000);
        account(server, ben.getUUID(), "Ben", 1000);
        db.pieces.put(ann.getUUID(), 1000L);
        db.pieces.put(ben.getUUID(), 1000L);
        h1.join(ann, null);
        h2.join(ben, null);
        Two t = new Two(loop, db, h1, h2, ann, ben, now);
        t.tick();
        return t;
    }

    /** Ann asks Ben across the servers and Ben accepts: the trade runs on s1, Ben's side on s2. */
    private static TradeSession startAcross(GameTestHelper h, Two t) {
        h.assertTrue(t.h1().traders(t.ann().getUUID()).stream().anyMatch(x -> x.id().equals(t.ben().getUUID()) && x.server().equals("s2")), "Ann sees Ben on s2");
        h.assertTrue(t.h1().ask(t.ann(), t.ben().getUUID()).equals("ok"), "Ann asks");
        t.loop().pump();
        h.assertTrue(t.h2().requestsTo(t.ben().getUUID()).size() == 1, "the request reached s2");
        h.assertTrue(t.h2().answer(t.ben(), t.ann().getUUID(), true).equals("ok"), "Ben accepts");
        t.loop().pump();
        h.assertTrue(t.h2().trade(t.ben().getUUID()).orElse(null) instanceof RemoteTrade, "s2 holds Ben's side");
        return t.h1().session(t.ann().getUUID()).orElseThrow();
    }

    /** Ann offers 64 Diamonds, Ben 3 Emeralds and 200 pieces; both press READY; the countdown runs. */
    private static Trade fillAndReady(GameTestHelper h, Two t, TradeSession s) {
        Trade rt = t.h2().trade(t.ben().getUUID()).orElseThrow();
        s.side(t.ann().getUUID()).offer.setItem(0, new ItemStack(Items.DIAMOND, 64));
        rt.mine(t.ben().getUUID()).setItem(0, new ItemStack(Items.EMERALD, 3));
        rt.setPieces(t.ben().getUUID(), 200);
        t.loop().pump();
        h.assertTrue(s.side(t.ben().getUUID()).items().size() == 1 && s.side(t.ben().getUUID()).pieces() == 200, "s1 sees Ben's offer");
        h.assertTrue(s.ready(t.ann().getUUID()).equals("ok") && rt.ready(t.ben().getUUID()).equals("ok"), "both ready");
        t.tick(); // s1 hears Ben's READY and starts the countdown
        t.tick(); // s2 hears the countdown
        h.assertTrue(s.state() == TradeSession.State.COUNTDOWN && rt.locked(), "the countdown runs and locks Ben's side too: " + s.state() + " ann " + s.side(t.ann().getUUID()).ready() + " ben " + s.side(t.ben().getUUID()).ready() + " locked " + rt.locked() + " chat " + s.chat());
        return rt;
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_across_servers_hands_over_once(GameTestHelper h) {
        Two t = two(h);
        UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            // a change on Ben's server during the countdown would stop it; none here
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            h.assertTrue(s.state() == TradeSession.State.DONE && rt.finished(), "the trade is done on both servers");
            h.assertTrue(t.ann().getInventory().countItem(Items.EMERALD) == 3 && t.ben().getInventory().countItem(Items.DIAMOND) == 64, "the items crossed over, once");
            h.assertTrue(t.ann().getInventory().countItem(Items.DIAMOND) == 0 && t.ben().getInventory().countItem(Items.EMERALD) == 0, "and nobody kept their own");
            h.assertTrue(t.db().bal(ann) == 1200 && t.db().bal(ben) == 800 && Bank.pieces(server(h), ann) == 1200, "Ben paid Ann 200 in one transaction");
            h.assertTrue(t.db().escrow.isEmpty() && t.db().pending.isEmpty() && t.db().outcomes.containsValue("done"), "no escrow or pending left, one done row");
            h.assertTrue(!t.h1().busy(ann) && !t.h2().busy(ben), "both are free again");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_across_servers_checks_room_on_the_other_server(GameTestHelper h) {
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            var inv = t.ben().getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
            Trade rt = fillAndReady(h, t, s);
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            h.assertTrue(s.state() == TradeSession.State.OPEN && !s.side(t.ann().getUUID()).ready(), "Ben has no room: back to open, the lights off");
            h.assertTrue(t.db().escrow.isEmpty() && t.db().outcomes.isEmpty(), "nothing went into escrow");
            h.assertTrue(s.side(t.ann().getUUID()).items().size() == 1 && rt.mine(t.ben().getUUID()).getItem(0).is(Items.EMERALD), "both offers are where they were");
            h.assertTrue(s.chat().stream().anyMatch(l -> l.name().isEmpty() && l.text().contains("0") && l.text().contains("1")), "the note names Ben's room: " + s.chat());
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_across_servers_gives_everything_back_when_a_server_goes_quiet(GameTestHelper h) {
        Two t = two(h);
        UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            // Ben's server puts his offer in escrow, then the link goes down before s1 hears
            t.loop().drop.add("escrowed");
            long end = t.now()[0] + TradeSession.COUNTDOWN_MS;
            t.at(end);
            h.assertTrue(s.state() == TradeSession.State.SETTLING && t.db().escrow.size() == 1, "Ben's Emeralds are in escrow, s1 waits");
            t.loop().cut = true;
            t.at(end + TradeHub.REPLY_MS);
            h.assertTrue(t.db().outcomes.containsValue("aborted"), "s1 gave the attempt up in the database");
            t.at(end + TradeHub.REPLY_MS + TradeHub.SILENCE_MS + 1);
            h.assertTrue(s.state() == TradeSession.State.CANCELLED && t.ann().getInventory().countItem(Items.DIAMOND) == 64, "s1 heard nothing more: Ann has her Diamonds back");
            t.at(end + TradeHub.OUTCOME_MS);
            h.assertTrue(rt.finished() && t.ben().getInventory().countItem(Items.EMERALD) == 3, "s2 read the outcome itself: Ben has his Emeralds back");
            h.assertTrue(t.db().escrow.isEmpty() && t.db().pending.isEmpty() && t.db().bal(ann) == 1000 && t.db().bal(ben) == 1000, "nothing doubled, nothing lost, no pieces moved");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_across_servers_finishes_when_the_done_message_is_lost(GameTestHelper h) {
        Two t = two(h);
        UUID ben = t.ben().getUUID();
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            t.loop().drop.add("outcome");
            long end = t.now()[0] + TradeSession.COUNTDOWN_MS;
            t.at(end);
            h.assertTrue(s.state() == TradeSession.State.DONE && t.ann().getInventory().countItem(Items.EMERALD) == 3, "s1 finished and gave Ann the Emeralds");
            h.assertTrue(!rt.finished() && rt.locked() && t.db().pendingFor(ben) == 1, "s2 never heard: Ben's Diamonds wait for him in the database");
            t.at(end + TradeHub.OUTCOME_MS);
            h.assertTrue(rt.finished() && t.ben().getInventory().countItem(Items.DIAMOND) == 64 && t.db().pending.isEmpty(), "s2 read 'done' itself and delivered, once");
            // escrow an unfinished attempt left behind long ago goes back to its owner
            try {
                t.db().escrow("old-attempt", ben, List.of(new ItemStack(Items.GOLD_INGOT, 5)));
            } catch (Exception e) {
                h.fail("the fake database failed: " + e);
            }
            t.at(end + TradeHub.OUTCOME_MS + TradeHub.ESCROW_AGE_MS + TradeHub.SWEEP_EVERY_MS + 1);
            h.assertTrue(t.db().escrow.isEmpty() && t.db().pendingFor(ben) == 1 && t.db().outcomes.get("old-attempt").equals("aborted"), "the sweep sent the old escrow home");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ audit fix task 2b: trades when a database answer is lost

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_escrow_whose_answer_is_lost_comes_back_once(GameTestHelper h) {
        // 1. the escrow went in, its answer was lost: the database is asked, the attempt aborted, the escrow comes back into Ben's offer
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            UUID ben = t.ben().getUUID();
            t.db().loseEscrow = true;
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            h.assertTrue(t.db().escrow.isEmpty() && t.db().outcomes.containsValue("aborted") && !t.db().outcomes.containsValue("done"), "the attempt was aborted and its escrow taken out");
            h.assertTrue(countIn(rt.mine(ben), Items.EMERALD) == 3 && t.ben().getInventory().countItem(Items.EMERALD) == 0 && t.db().pending.isEmpty(), "the Emeralds are back in Ben's offer, once");
            h.assertTrue(s.state() == TradeSession.State.OPEN && countIn(s.side(t.ann().getUUID()).offer, Items.DIAMOND) == 64, "the trade is open again and Ann still has her Diamonds in her offer");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        // 2. the escrow failed at its commit (did not go in) and the database could not be asked: the offer waits; when it can, it comes back from memory
        t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            UUID ben = t.ben().getUUID();
            t.db().failEscrow = true;
            t.db().downAfter = true;
            long at = t.now()[0] + TradeSession.COUNTDOWN_MS;
            t.at(at);
            h.assertTrue(countIn(rt.mine(ben), Items.EMERALD) == 0 && t.ben().getInventory().countItem(Items.EMERALD) == 0 && t.db().escrow.isEmpty(), "while the database cannot say, the Emeralds are held aside");
            t.db().down = false;
            t.at(at + 5_001);
            h.assertTrue(countIn(rt.mine(ben), Items.EMERALD) + t.ben().getInventory().countItem(Items.EMERALD) == 3 && t.db().escrow.isEmpty() && t.db().pending.isEmpty(),
                    "never escrowed: the Emeralds came back from memory, once");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        // 3. the escrow went in, its answer was lost, the database went down, and Ben's side ended meanwhile: still settled once, into his inventory
        t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            UUID ben = t.ben().getUUID();
            t.db().loseEscrow = true;
            t.db().downAfter = true;
            long at = t.now()[0] + TradeSession.COUNTDOWN_MS;
            t.at(at);
            rt.leave(ben, "closed", "");
            h.assertTrue(rt.finished() && t.ben().getInventory().countItem(Items.EMERALD) == 0 && t.db().escrow.size() == 1, "Ben left; the Emeralds are in escrow, not yet back");
            t.db().down = false;
            t.at(at + 5_001);
            h.assertTrue(t.ben().getInventory().countItem(Items.EMERALD) == 3 && t.db().escrow.isEmpty() && t.db().pending.isEmpty(), "the ended trade still settled: the Emeralds came home, once");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_hand_over_whose_answer_is_lost_finishes_once(GameTestHelper h) {
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
            t.db().loseSettle = true;
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            t.tick();
            h.assertTrue(s.state() == TradeSession.State.DONE, "the database said done: the trade finished: " + s.state());
            h.assertTrue(t.ann().getInventory().countItem(Items.EMERALD) == 3 && t.ann().getInventory().countItem(Items.DIAMOND) == 0 && countIn(s.side(ann).offer, Items.DIAMOND) == 0,
                    "Ann got the Emeralds, and her Diamonds did not come back to her");
            t.at(t.now()[0] + TradeHub.OUTCOME_MS);
            h.assertTrue(t.ben().getInventory().countItem(Items.DIAMOND) == 64 && rt.finished() && t.db().pending.isEmpty() && t.db().bal(ann) == 1200 && t.db().bal(ben) == 800,
                    "Ben got the Diamonds once and the pieces moved once");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_one_server_pay_whose_answer_is_lost_pays_once(GameTestHelper h) {
        var server = server(h);
        long[] now = {1_800_000_000_000L};
        FakeTradeDb db = new FakeTradeDb(now);
        TradeHub hub = new TradeHub("s1");
        hub.connect("s1", null, db, Runnable::run, Runnable::run);
        hub.useClock(() -> now[0]);
        Player ann = h.makeMockPlayer(), ben = h.makeMockPlayer();
        account(server, ann.getUUID(), "Ann", 1000);
        account(server, ben.getUUID(), "Ben", 1000);
        db.pieces.put(ann.getUUID(), 1000L);
        db.pieces.put(ben.getUUID(), 1000L);
        try {
            hub.join(ann, null);
            hub.join(ben, null);
            h.assertTrue(hub.ask(ann, ben.getUUID()).equals("ok") && hub.answer(ben, ann.getUUID(), true).equals("ok"), "Ann asks, Ben accepts");
            TradeSession s = hub.session(ann.getUUID()).orElseThrow();
            s.side(ann.getUUID()).offer.setItem(0, new ItemStack(Items.DIAMOND, 4));
            h.assertTrue(s.setPieces(ben.getUUID(), 300).equals("ok"), "Ben offers 300 pieces");
            // the payment goes in, its answer is lost and the database goes down: the trade cannot finish yet and goes back to open
            db.loseSettle = true;
            db.downAfter = true;
            s.ready(ann.getUUID());
            s.ready(ben.getUUID());
            now[0] += TradeSession.COUNTDOWN_MS;
            hub.tick();
            h.assertTrue(s.state() == TradeSession.State.OPEN && db.bal(ben.getUUID()) == 700 && ben.getInventory().countItem(Items.DIAMOND) == 0,
                    "paid in the database but not finished here; open again: " + s.state());
            // the database is back; both ready again: the old payment is found made, nothing is paid twice, the items cross
            db.down = false;
            s.ready(ann.getUUID());
            s.ready(ben.getUUID());
            now[0] += TradeSession.COUNTDOWN_MS;
            hub.tick();
            h.assertTrue(s.state() == TradeSession.State.DONE && ben.getInventory().countItem(Items.DIAMOND) == 4, "done: Ben has the Diamonds: " + s.state());
            h.assertTrue(db.bal(ben.getUUID()) == 700 && db.bal(ann.getUUID()) == 1300 && db.outcomes.values().stream().filter("done"::equals).count() == 1, "the 300 pieces moved once");
        } finally {
            hub.stopAll();
        }
        h.succeed();
    }

    private static int countIn(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ audit fix task 4: items safe in menus and trades

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_lot_places_are_safe_at_logout_death_and_late_answers(GameTestHelper h) {
        var server = server(h);
        MarketStore old = Market.useStore(new LocalMarket(new MarketLedger()));
        try {
            // logout: the places go back before the player is saved
            var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            TradePostMenu m = new TradePostMenu(0, p.getInventory(), null, TradePostBlock.Tab.SELL);
            p.containerMenu = m;
            m.lotSlots().setItem(0, new ItemStack(Items.EMERALD, 6));
            TradePostMenu.emptyLotPlaces(p);
            h.assertTrue(p.getInventory().countItem(Items.EMERALD) == 6 && m.lotSlots().isEmpty(), "at logout the Emeralds are back in the inventory that is about to be saved");
            // the screen torn down for a player object already gone (an old one after a respawn): into the Collect box, not the void
            var q = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            account(server, q.getUUID(), "Quin", 0);
            TradePostMenu mq = new TradePostMenu(0, q.getInventory(), null, TradePostBlock.Tab.SELL);
            q.containerMenu = mq;
            mq.lotSlots().setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
            q.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            h.assertTrue(Market.collectBox(server, q.getUUID()).stream().anyMatch(x -> x.is(Items.GOLD_INGOT) && x.getCount() == 3), "a removed player's places went to the Collect box");
            // a dead player: the same
            Player dead = h.makeMockPlayer();
            dead.setHealth(0);
            TradePostMenu.giveBack(dead, List.of(new ItemStack(Items.IRON_INGOT, 2)));
            h.assertTrue(dead.getInventory().countItem(Items.IRON_INGOT) == 0 && Market.collectBox(server, dead.getUUID()).stream().anyMatch(x -> x.is(Items.IRON_INGOT)),
                    "a dead player's go to the box, not into an inventory already dropped");
        } finally {
            Market.useStore(old);
        }
        // a listing refused after the screen closed (the network answered late): into the inventory, not the closed places
        Player r = h.makeMockPlayer();
        UUID rid = account(server, r.getUUID(), "Rae", 1000);
        FakeDb db = new FakeDb();
        db.pieces.put(rid, 1000L);
        db.down = true;
        List<Runnable> queue = new ArrayList<>();
        MarketStore old2 = Market.useStore(new NetworkMarket(db, queue::add, Runnable::run, () -> {}, s -> new MarketLedger()));
        try {
            TradePostMenu mr = new TradePostMenu(0, r.getInventory(), null, TradePostBlock.Tab.SELL);
            r.containerMenu = mr;
            mr.lotSlots().setItem(0, new ItemStack(Items.DIAMOND, 5));
            MarketResult[] got = new MarketResult[1];
            mr.list(server, r, 100, x -> got[0] = x);
            r.containerMenu = r.inventoryMenu;
            mr.removed(r);
            drain(queue);
            h.assertTrue(got[0] != null && got[0].key().equals("unreachable") && r.getInventory().countItem(Items.DIAMOND) == 5, "the refused Diamonds came back to the inventory: " + got[0]);
        } finally {
            Market.useStore(old2);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_items_go_to_the_player_as_they_are_now(GameTestHelper h) {
        var server = server(h);
        MarketStore old = Market.useStore(new LocalMarket(new MarketLedger()));
        try {
            Player live = h.makeMockPlayer();
            Trades.give(server, live, live.getUUID(), List.of(new ItemStack(Items.DIAMOND, 4)));
            h.assertTrue(live.getInventory().countItem(Items.DIAMOND) == 4, "a player here gets them in the inventory");
            Player dead = h.makeMockPlayer();
            dead.setHealth(0);
            Trades.give(server, dead, dead.getUUID(), List.of(new ItemStack(Items.DIAMOND, 4)));
            h.assertTrue(dead.getInventory().countItem(Items.DIAMOND) == 0 && Market.collectBox(server, dead.getUUID()).size() == 1, "a dead player's go to the Collect box");
            var gone = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            gone.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            Trades.give(server, gone, gone.getUUID(), List.of(new ItemStack(Items.EMERALD, 2)));
            h.assertTrue(gone.getInventory().countItem(Items.EMERALD) == 0 && Market.collectBox(server, gone.getUUID()).size() == 1,
                    "an old player object with no current one here: the Collect box");
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_closing_while_starting_and_a_broken_post_leave_nothing_behind(GameTestHelper h) {
        // an accepted request still waiting for the other server is not a trade: closing the screen then takes the player off the list
        Two t = two(h);
        try {
            UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
            t.loop().drop.add("start"); // the start never arrives on its own
            h.assertTrue(t.h1().ask(t.ann(), ben).equals("ok"), "Ann asks");
            t.loop().pump();
            h.assertTrue(t.h2().answer(t.ben(), ann, true).equals("ok"), "Ben accepts");
            t.loop().pump();
            h.assertTrue(t.h2().busy(ben) && !t.h2().inTrade(ben), "Ben is busy starting, but not in a trade");
            t.h2().leave(t.ben(), "closed");
            h.assertTrue(!t.h2().busy(ben) && t.h2().present(ben).isEmpty(), "closing took him off the list");
            t.at(t.now()[0] + TradeHub.SILENCE_MS + 1);
            h.assertTrue(t.h1().session(ann).map(TradeSession::finished).orElse(true) && !t.h1().busy(ann), "Ann's side gave up on the start that never came, and she is free");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        // a broken post closes the screens of the players at it, and their lot places come back
        var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        net.minecraft.core.GlobalPos post = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
        TradePostMenu m = new TradePostMenu(0, p.getInventory(), post, TradePostBlock.Tab.SELL);
        p.containerMenu = m;
        Trades.join(p, post);
        try {
            m.lotSlots().setItem(0, new ItemStack(Items.EMERALD, 2));
            Trades.hub().postBroken(post);
            h.assertTrue(p.containerMenu != m && p.getInventory().countItem(Items.EMERALD) == 2 && Trades.hub().present(p.getUUID()).isEmpty(),
                    "the screen closed, the Emeralds came back, the player is off the list");
        } finally {
            leaveAll(p);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_offer_put_back_is_told_to_the_running_server(GameTestHelper h) {
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = t.h2().trade(t.ben().getUUID()).orElseThrow();
            UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
            s.side(ann).offer.setItem(0, new ItemStack(Items.EMERALD, 1));
            rt.mine(ben).setItem(0, new ItemStack(Items.DIRT, 32));
            rt.mine(ben).setItem(1, new ItemStack(Items.DIRT, 32));
            t.loop().pump();
            // the first hand-over's escrow answer is lost: Ben's dirt comes back merged, 64 in one place
            t.db().loseEscrow = true;
            s.ready(ann);
            rt.ready(ben);
            t.tick();
            t.tick();
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            h.assertTrue(countIn(rt.mine(ben), Items.DIRT) == 64 && s.state() == TradeSession.State.OPEN, "back in Ben's offer, the trade open again");
            h.assertTrue(s.side(ben).items().size() == 1 && s.side(ben).items().get(0).getCount() == 64, "the running server knows what Ben's offer holds now: " + s.side(ben).items());
            // the second hand-over goes through: the copy matches the escrow
            t.tick();
            String ra = s.ready(ann), rb = rt.ready(ben);
            h.assertTrue(ra.equals("ok") && rb.equals("ok"), "both ready again: " + ra + " / " + rb);
            t.tick();
            t.tick();
            t.at(t.now()[0] + TradeSession.COUNTDOWN_MS);
            t.at(t.now()[0] + TradeHub.OUTCOME_MS);
            h.assertTrue(s.state() == TradeSession.State.DONE && t.ann().getInventory().countItem(Items.DIRT) == 64 && t.ben().getInventory().countItem(Items.EMERALD) == 1,
                    "the next try went through, once: " + s.state() + " chat " + s.chat());
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ audit fix task 7: client and leftovers

    @GameTest(template = EMPTY, batch = "trade_post")
    public void board_lines_are_read_once_a_second_unless_the_data_changes(GameTestHelper h) {
        var id = new net.minecraft.resources.ResourceLocation("bsp_test", "counted");
        int[] asked = {0};
        com.mrgregles.bsp_core.api.BspBoards.register(id, net.minecraft.network.chat.Component.literal("Counted"), () -> {
            asked[0]++;
            return List.of(com.mrgregles.bsp_core.api.BspBoards.BoardLine.of(net.minecraft.network.chat.Component.literal("line " + asked[0]), null));
        });
        try {
            var first = com.mrgregles.bsp_core.api.BspBoards.lines(id);
            for (int i = 0; i < 50; i++) {
                com.mrgregles.bsp_core.api.BspBoards.lines(id);
            }
            h.assertTrue(asked[0] == 1 && first.get(0).text().getString().equals("line 1"), "fifty frames, one read: " + asked[0]);
            com.mrgregles.bsp_core.api.BspBoards.invalidate(id);
            h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.lines(id).get(0).text().getString().equals("line 2") && asked[0] == 2, "new data: read at once");
        } finally {
            com.mrgregles.bsp_core.api.BspBoards.unregister(id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_chat_and_pieces_are_limited_and_long_names_never_break_a_packet(GameTestHelper h) {
        // chat: four lines at once, then two a second
        com.mrgregles.bsp_core.trade.RateLimit chat = new com.mrgregles.bsp_core.trade.RateLimit(4, 2);
        int sent = 0;
        for (int i = 0; i < 10; i++) {
            sent += chat.allow(1_000) ? 1 : 0;
        }
        h.assertTrue(sent == 4 && !chat.allow(1_200) && chat.allow(1_500), "four at once, then one every half second");
        // the same pieces again from the other server: not sent, so nobody's READY light goes off for nothing
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = t.h2().trade(t.ben().getUUID()).orElseThrow();
            UUID ann = t.ann().getUUID(), ben = t.ben().getUUID();
            s.side(ann).offer.setItem(0, new ItemStack(Items.DIAMOND, 1));
            rt.setPieces(ben, 200);
            t.loop().pump();
            h.assertTrue(s.ready(ann).equals("ok"), "Ann is ready");
            rt.setPieces(ben, 200);
            h.assertTrue(t.loop().queue.isEmpty(), "the same 200 again sends nothing");
            t.loop().pump();
            h.assertTrue(s.side(ann).ready() && s.side(ben).pieces() == 200, "Ann is still ready");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        // a server name past what the packet field holds is cut, never thrown while encoding (which in a tick would stop the server)
        var v = new TradePostMenu.View(0, 0, 5, 9, 10, 7, 0, 0, List.of(), 0, 1, 0, List.of(), 0,
                List.of(new TradePostMenu.TraderView(UUID.randomUUID(), "N".repeat(40), "s".repeat(100), false)), List.of(), "", 0, "", List.of());
        int size = com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(new com.mrgregles.bsp_core.network.TradePostDataPacket(v), com.mrgregles.bsp_core.network.TradePostDataPacket::encode);
        h.assertTrue(size > 0 && size < 400, "encoded with the names cut: " + size + " bytes");
        h.succeed();
    }

    // ------------------------------------------------------------------ the Trade Post block (task 5)

    /** A post facing {@code facing} with its controller at {@code rel}; returns the controller's absolute position. */
    private static net.minecraft.core.BlockPos post(GameTestHelper h, net.minecraft.core.BlockPos rel, net.minecraft.core.Direction facing) {
        var block = com.mrgregles.bsp_core.registry.ModBlocks.TRADE_POST.get();
        h.setBlock(rel, block.defaultBlockState().setValue(TradePostBlock.FACING, facing));
        net.minecraft.core.BlockPos c = h.absolutePos(rel);
        TradePostBlock.placeParts(h.getLevel(), c, facing);
        return c;
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_stands_as_four_parts_facing_the_placer(GameTestHelper h) {
        var level = h.getLevel();
        var east = net.minecraft.core.Direction.EAST;
        net.minecraft.core.BlockPos c = post(h, new net.minecraft.core.BlockPos(4, 1, 4), east);
        var parts = TradePostBlock.parts(c, east);
        h.assertTrue(parts.get(2).equals(c.south()) && parts.get(1).equals(c.above()) && parts.get(3).equals(c.south().above()), "facing east, the wing is to the south");
        for (var p : parts) {
            var st = level.getBlockState(p);
            h.assertTrue(st.getBlock() instanceof TradePostBlock && st.getValue(TradePostBlock.FACING) == east, "part at " + p + " stands, facing east");
            h.assertTrue(TradePostBlock.controller(st, p).equals(c), "every part finds the controller");
        }
        h.assertTrue(level.getBlockEntity(c) instanceof TradePostBlockEntity && parts.stream().skip(1).allMatch(p -> level.getBlockEntity(p) == null), "only the controller has a block entity");
        h.assertTrue(level.getBlockState(c).getLightEmission(level, c) == 7, "it gives off light like the Bank");
        // a post needs its whole 2 by 2: a block in the way of the wing's upper part refuses the place
        net.minecraft.core.BlockPos free = h.absolutePos(new net.minecraft.core.BlockPos(10, 1, 10));
        h.assertTrue(TradePostBlock.roomFor(level, free, net.minecraft.core.Direction.NORTH), "room in open air");
        h.setBlock(new net.minecraft.core.BlockPos(11, 2, 10), net.minecraft.world.level.block.Blocks.STONE);
        h.assertTrue(!TradePostBlock.roomFor(level, free, net.minecraft.core.Direction.NORTH), "a stone where the wing's upper part goes: no room");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_falls_together_and_lets_its_traders_go(GameTestHelper h) {
        var level = h.getLevel();
        var north = net.minecraft.core.Direction.NORTH;
        // a player who is not an admin cannot break it, even in reach
        net.minecraft.core.BlockPos c = post(h, new net.minecraft.core.BlockPos(4, 1, 4), north);
        Player p = h.makeMockPlayer();
        var st = level.getBlockState(c);
        h.assertTrue(!st.onDestroyedByPlayer(level, c, p, false, level.getFluidState(c)) && level.getBlockState(c).getBlock() instanceof TradePostBlock, "a player cannot break the post");
        // one part gone (a world edit, a command): the others go with it
        h.setBlock(new net.minecraft.core.BlockPos(5, 2, 4), net.minecraft.world.level.block.Blocks.AIR);
        h.assertTrue(TradePostBlock.parts(c, north).stream().noneMatch(x -> level.getBlockState(x).getBlock() instanceof TradePostBlock), "all four parts went together");
        // broken while two players trade at it: their trade ends and their items come back
        net.minecraft.core.BlockPos c2 = post(h, new net.minecraft.core.BlockPos(12, 1, 12), north);
        var where = net.minecraft.core.GlobalPos.of(level.dimension(), c2);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        try {
            Trades.join(a, where);
            Trades.join(b, where);
            h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok") && Trades.answer(b, a.getUUID(), true).equals("ok"), "they trade at the post");
            TradeSession s = Trades.session(a.getUUID()).orElseThrow();
            s.side(a.getUUID()).offer.setItem(0, new ItemStack(Items.DIAMOND, 9));
            TradePostBlock.removePost(level, c2, north, null);
            h.assertTrue(s.state() == TradeSession.State.CANCELLED && a.getInventory().countItem(Items.DIAMOND) == 9, "the trade ended and Ann has her Diamonds back");
            h.assertTrue(Trades.present(a.getUUID()).isEmpty() && Trades.present(b.getUUID()).isEmpty(), "nobody is at the broken post any more");
            h.assertTrue(TradePostBlock.parts(c2, north).stream().noneMatch(x -> level.getBlockState(x).getBlock() instanceof TradePostBlock), "the post is gone");
        } finally {
            leaveAll(a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_keys_open_their_page_whichever_way_it_faces(GameTestHelper h) {
        net.minecraft.core.BlockPos c = new net.minecraft.core.BlockPos(100, 64, 100);
        for (var facing : List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST)) {
            for (TradePostBlock.Tab tab : TradePostBlock.Tab.values()) {
                double mx = (tab.x0 + tab.x1) / 2, my = 10, mz = 2.5;
                h.assertTrue(TradePostBlock.tabAt(facing, c, world(c, facing, mx, my, mz)) == tab, tab + " key, facing " + facing);
            }
            h.assertTrue(TradePostBlock.tabAt(facing, c, world(c, facing, 16, 24, 11)) == TradePostBlock.Tab.MARKET, "the screen opens MARKET, facing " + facing);
            h.assertTrue(TradePostBlock.tabAt(facing, c, world(c, facing, 16, 5, 0)) == TradePostBlock.Tab.MARKET, "the drawer bay opens MARKET, facing " + facing);
        }
        h.succeed();
    }

    /** A point given in the post's model pixels (front north), in world coordinates for a post facing {@code facing}. */
    private static net.minecraft.world.phys.Vec3 world(net.minecraft.core.BlockPos c, net.minecraft.core.Direction facing, double mx, double my, double mz) {
        double wx, wz;
        switch (facing) {
            case EAST -> {
                wx = 16 - mz;
                wz = mx;
            }
            case SOUTH -> {
                wx = 16 - mx;
                wz = 16 - mz;
            }
            case WEST -> {
                wx = mz;
                wz = 16 - mx;
            }
            default -> {
                wx = mx;
                wz = mz;
            }
        }
        return new net.minecraft.world.phys.Vec3(c.getX() + wx / 16, c.getY() + my / 16, c.getZ() + wz / 16);
    }

    // ------------------------------------------------------------------ what the post shows (task 6)

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_shows_who_uses_it_and_who_trades(GameTestHelper h) {
        var level = h.getLevel();
        net.minecraft.core.BlockPos c = post(h, new net.minecraft.core.BlockPos(4, 1, 4), net.minecraft.core.Direction.NORTH);
        var where = net.minecraft.core.GlobalPos.of(level.dimension(), c);
        TradePostBlockEntity be = entity(h, c);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        long[] now = {1_800_000_000_000L};
        Trades.useClock(() -> now[0]);
        try {
            h.assertTrue(be.users() == 0 && !be.trading(), "an empty post shows nobody");
            Trades.join(a, where);
            Trades.join(b, where);
            Trades.hub().showPosts();
            h.assertTrue(be.users() == 2 && !be.trading(), "two players at it: " + be.users());
            h.assertTrue(Trades.ask(a, b.getUUID()).equals("ok") && Trades.answer(b, a.getUUID(), true).equals("ok"), "they trade");
            Trades.hub().showPosts();
            String an = a.getGameProfile().getName(), bn = b.getGameProfile().getName();
            h.assertTrue(be.trading() && be.first().equals(an) && be.second().equals(bn), "the post shows the trade: " + be.first() + " / " + be.second());
            var tag = be.getUpdateTag();
            TradePostBlockEntity copy = new TradePostBlockEntity(c, level.getBlockState(c));
            copy.load(tag);
            h.assertTrue(copy.trading() && copy.users() == 2, "what the clients get carries it");
            leaveAll(a, b);
            Trades.hub().showPosts();
            h.assertTrue(be.users() == 0 && !be.trading(), "everyone left: the post is clear again");
        } finally {
            Trades.useClock(null);
            leaveAll(a, b);
        }
        h.succeed();
    }

    private static TradePostBlockEntity entity(GameTestHelper h, net.minecraft.core.BlockPos abs) {
        if (!(h.getLevel().getBlockEntity(abs) instanceof TradePostBlockEntity be)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("no Trade Post controller at " + abs);
        }
        return be;
    }

    // ------------------------------------------------------------------ the Trade Post screen's server side (task 7)

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_menu_lists_from_its_slots_and_gives_back_on_close(GameTestHelper h) {
        var server = server(h);
        Player p = h.makeMockPlayer();
        account(server, p.getUUID(), "Sella", 1000);
        MarketLedger ledger = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(ledger));
        try {
            TradePostMenu m = new TradePostMenu(0, p.getInventory(), null, TradePostBlock.Tab.SELL);
            p.containerMenu = m; // as in game: the screen is open while listing
            p.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 32));
            p.getInventory().setItem(10, new ItemStack(ModItems.TOTEM_CORE.get()));
            m.quickMoveStack(p, TradePostMenu.INV);
            m.quickMoveStack(p, TradePostMenu.INV + 1);
            h.assertTrue(m.lotSlots().getItem(0).is(Items.DIAMOND) && p.getInventory().countItem(Items.DIAMOND) == 0, "the Diamonds went into the lot");
            h.assertTrue(p.getInventory().countItem(ModItems.TOTEM_CORE.get()) == 1 && !m.getSlot(TradePostMenu.SELL + 3).mayPlace(new ItemStack(ModItems.TOTEM_CORE.get())), "the Totem Core stays out");
            MarketResult[] r = new MarketResult[1];
            m.list(server, p, 0, x -> r[0] = x);
            h.assertTrue(!r[0].ok() && r[0].key().equals("price") && m.lotSlots().getItem(0).is(Items.DIAMOND), "no price: refused, the Diamonds are back in the lot slots");
            m.list(server, p, 400, x -> r[0] = x);
            h.assertTrue(r[0].ok() && ledger.open().size() == 1 && m.lotSlots().isEmpty(), "listed: the market holds them, the slots are empty");
            // closing with a lot half filled gives the stacks back
            p.getInventory().setItem(11, new ItemStack(Items.EMERALD, 5));
            m.quickMoveStack(p, TradePostMenu.INV + 2);
            m.removed(p);
            h.assertTrue(p.getInventory().countItem(Items.EMERALD) == 5 && m.lotSlots().isEmpty(), "closing gave the Emeralds back");
        } finally {
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_view_searches_sorts_and_shows_traders(GameTestHelper h) {
        var server = server(h);
        Player viewer = h.makeMockPlayer(), other = h.makeMockPlayer();
        UUID seller = account(server, UUID.randomUUID(), "Sella", 10_000);
        account(server, viewer.getUUID(), "Vic", 777);
        MarketStore old = Market.useStore(new LocalMarket(new MarketLedger()));
        try {
            list(server, seller, "Sella", 900, new ItemStack(Items.DIAMOND, 4));
            list(server, seller, "Sella", 100, new ItemStack(Items.OAK_LOG, 64));
            list(server, seller, "Sella", 500, new ItemStack(Items.EMERALD, 9));
            var all = TradePostMenu.view(server, viewer.getUUID(), "", 0, "", List.of());
            h.assertTrue(all.lots().size() == 3 && all.totalLots() == 3 && all.balance() == 777, "three lots, Vic's balance");
            h.assertTrue(all.lots().get(0).items().get(0).is(Items.EMERALD), "newest first");
            var cheap = TradePostMenu.view(server, viewer.getUUID(), "", 1, "", List.of());
            var dear = TradePostMenu.view(server, viewer.getUUID(), "", 2, "", List.of());
            h.assertTrue(cheap.lots().get(0).price() == 100 && dear.lots().get(0).price() == 900, "cheapest and dearest first");
            var found = TradePostMenu.view(server, viewer.getUUID(), "diam", 0, "", List.of());
            h.assertTrue(found.lots().size() == 1 && found.lots().get(0).price() == 900, "the search finds the Diamonds");
            h.assertTrue(TradePostMenu.view(server, viewer.getUUID(), "sella", 0, "", List.of()).lots().size() == 3, "and searches the seller's name");
            Trades.join(viewer, null);
            Trades.join(other, null);
            var v = TradePostMenu.view(server, viewer.getUUID(), "", 0, "", List.of());
            h.assertTrue(v.traders().size() == 1 && v.traders().get(0).id().equals(other.getUUID()), "Vic sees the other player at a Trade Post");
            h.assertTrue(Trades.ask(other, viewer.getUUID()).equals("ok"), "they ask Vic");
            v = TradePostMenu.view(server, viewer.getUUID(), "", 0, "", List.of());
            h.assertTrue(v.toMe().size() == 1 && v.toMe().get(0).seconds() > 0, "Vic's view has the request with its time left");
        } finally {
            Market.useStore(old);
            leaveAll(viewer, other);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ audit fix task 5: the view and the summary stay small

    /** A shulker box of 27 written books of 20 long pages: about 140 KB of item data. */
    private static ItemStack heavyShulker() {
        ListTag pages = new ListTag();
        String page = net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal("x".repeat(250)));
        for (int i = 0; i < 20; i++) {
            pages.add(net.minecraft.nbt.StringTag.valueOf(page));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.getOrCreateTag().putString("title", "Heavy");
        book.getOrCreateTag().putString("author", "Gametest");
        book.getOrCreateTag().put("pages", pages);
        ListTag items = new ListTag();
        for (int slot = 0; slot < 27; slot++) {
            CompoundTag it = book.save(new CompoundTag());
            it.putByte("Slot", (byte) slot);
            items.add(it);
        }
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        CompoundTag be = new CompoundTag();
        be.put("Items", items);
        box.getOrCreateTag().put("BlockEntityTag", be);
        box.setHoverName(net.minecraft.network.chat.Component.literal("Library"));
        return box;
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_view_and_board_stay_small_with_heavy_lots(GameTestHelper h) {
        var server = server(h);
        UUID viewer = account(server, UUID.randomUUID(), "Vic", 0);
        long[] now = {1_800_000_000_000L};
        MarketStore old = Market.useStore(new LocalMarket(new MarketLedger()));
        Market.useClock(() -> now[0]);
        try {
            ItemStack heavy = heavyShulker();
            ItemStack[] nine = new ItemStack[9];
            java.util.Arrays.fill(nine, heavy);
            String heavyId = null;
            for (int i = 0; i < 12; i++) {
                now[0] += 1000;
                UUID s = account(server, UUID.randomUUID(), "Heavy" + i, 1000);
                MarketResult r = list(server, s, "Heavy" + i, 100 + i, nine);
                h.assertTrue(r.ok(), "a lot of nine libraries is listed: " + r);
                heavyId = r.lot().id;
            }
            for (int i = 0; i < 40; i++) {
                now[0] += 1000;
                UUID s = account(server, UUID.randomUUID(), "Light" + (i / 10), 1000);
                h.assertTrue(list(server, s, "Light" + i, 10, new ItemStack(Items.DIRT, 1 + i)).ok(), "a light lot");
            }
            MarketLot one = Market.find(server, heavyId).orElseThrow();
            int full = 0;
            for (ItemStack x : one.items()) {
                full += com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(x, (st, buf) -> buf.writeItem(st));
            }
            h.assertTrue(full > com.mrgregles.bsp_core.network.BSPNetwork.MAX_PAYLOAD, "one such lot in full is over what a packet carries: " + full + " bytes");
            // the view: pictures, a page of 36, small
            var v = TradePostMenu.view(server, viewer, "", 0, 0, "", List.of(), true);
            int size = com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(new com.mrgregles.bsp_core.network.TradePostDataPacket(v), com.mrgregles.bsp_core.network.TradePostDataPacket::encode);
            h.assertTrue(v.lots().size() == TradePostMenu.PAGE && v.pages() == 2 && v.matched() == 52 && size < 60_000, "page 1 of 2, 36 lots, " + size + " bytes");
            var v2 = TradePostMenu.view(server, viewer, "", 0, 5, "", List.of(), true);
            h.assertTrue(v2.page() == 1 && v2.lots().size() == 16, "a page past the end shows the last: " + v2.page() + " " + v2.lots().size());
            var lib = TradePostMenu.view(server, viewer, "library", 0, 0, "", List.of(), true);
            h.assertTrue(lib.matched() == 12 && lib.lots().get(0).items().get(0).getHoverName().getString().equals("Library"), "the search still finds the libraries by name, the pictures keep it");
            h.assertTrue(!lib.lots().get(0).items().get(0).getOrCreateTag().contains("BlockEntityTag"), "a picture carries no contents");
            // the Market Board's summary: small too
            int board = com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(new com.mrgregles.bsp_core.network.MarketSummaryPacket(now[0], com.mrgregles.bsp_core.market.MarketSummary.build(server)),
                    com.mrgregles.bsp_core.network.MarketSummaryPacket::encode);
            h.assertTrue(board < 20_000, "the summary is " + board + " bytes");
            // the open lot's stacks: in full as far as the budget goes, pictures after; under the limit
            var detail = com.mrgregles.bsp_core.network.TradePostLotPacket.of(heavyId, one);
            int ds = com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(detail, com.mrgregles.bsp_core.network.TradePostLotPacket::encode);
            h.assertTrue(detail.found() && detail.items().size() == 9 && ds < com.mrgregles.bsp_core.network.BSPNetwork.MAX_PAYLOAD, "the lot page's stacks fit: " + ds + " bytes");
            h.assertTrue(detail.items().get(0).getOrCreateTag().contains("BlockEntityTag") && !detail.items().get(8).getOrCreateTag().contains("BlockEntityTag"),
                    "the first stacks go in full, the last as a picture");
            h.assertTrue(!com.mrgregles.bsp_core.network.TradePostLotPacket.of("gone", null).found(), "a gone lot says so");
            // a lot past its end leaves the view before the sweep
            now[0] = Market.find(server, heavyId).orElseThrow().endsMs + 1;
            h.assertTrue(TradePostMenu.view(server, viewer, "library", 0, 0, "", List.of(), true).matched() == 0, "ended lots are left out");
        } finally {
            Market.useClock(null);
            Market.useStore(old);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_post_view_is_sent_on_change_only_and_actions_are_limited(GameTestHelper h) {
        var server = server(h);
        var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        UUID id = account(server, p.getUUID(), "Vic", 100);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 1000);
        MarketStore old = Market.useStore(new LocalMarket(new MarketLedger()));
        Bank.setRemoteMarket(server, id, true);
        try {
            TradePostMenu m = new TradePostMenu(0, p.getInventory(), null, TradePostBlock.Tab.MARKET);
            p.containerMenu = m;
            h.assertTrue(TradePostMenu.sync(p, "", List.of()), "the first view goes");
            h.assertTrue(!TradePostMenu.sync(p, "", List.of()), "nothing changed: not sent again");
            list(server, seller, "Sella", 50, new ItemStack(Items.DIAMOND, 2));
            list(server, seller, "Sella", 60, new ItemStack(Items.DIRT, 2));
            h.assertTrue(TradePostMenu.sync(p, "", List.of()) && !TradePostMenu.sync(p, "", List.of()), "a listing changes it once");
            h.assertTrue(TradePostMenu.sync(p, "message.bsp_core.market.gone", List.of()), "a message always goes");
            // a few actions at once, then about eight a second
            int allowed = 0;
            for (int i = 0; i < 20; i++) {
                allowed += m.allow(10_000) ? 1 : 0;
            }
            h.assertTrue(allowed == 8, "eight at once: " + allowed);
            int later = 0;
            for (int i = 0; i < 20; i++) {
                later += m.allow(10_500) ? 1 : 0;
            }
            h.assertTrue(later == 4, "half a second later, four more: " + later);
            // a search sent while over the limit is not lost: the next refresh shows it
            for (int i = 0; i < 20; i++) {
                m.allow(System.currentTimeMillis());
            }
            com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, m, new com.mrgregles.bsp_core.network.TradePostActionPacket(
                    com.mrgregles.bsp_core.network.TradePostActionPacket.Action.VIEW, "diam", 0, false));
            h.assertTrue(TradePostMenu.sync(p, "", List.of()), "the limited search took effect at the next refresh");
            h.assertTrue(TradePostMenu.view(server, id, "diam", 0, 0, "", List.of(), false).lots().size() == 1, "and finds the one lot");
        } finally {
            p.closeContainer();
            Market.useStore(old);
            Bank.setRemoteMarket(server, id, false);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the trade screen's server side (task 8)

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_screen_state_follows_the_trade(GameTestHelper h) {
        var server = server(h);
        Player a = h.makeMockPlayer(), b = h.makeMockPlayer();
        account(server, a.getUUID(), "Ann", 1000);
        account(server, b.getUUID(), "Ben", 600);
        long[] now = {1_800_000_000_000L};
        Trades.useClock(() -> now[0]);
        try {
            TradeSession s = start(h, a, b);
            s.side(a.getUUID()).offer.setItem(0, new ItemStack(Items.DIAMOND, 3));
            h.assertTrue(s.setPieces(b.getUUID(), 250).equals("ok") && s.ready(a.getUUID()).equals("ok"), "Ben offers 250, Ann is ready");
            TradeMenu.State sa = s.state(a.getUUID()), sb = s.state(b.getUUID());
            h.assertTrue(sa.myReady() && !sa.theirReady() && sa.theirPieces() == 250 && sa.myPieces() == 0 && sa.stage().equals("OPEN") && !sa.locked(), "Ann's view: " + sa);
            h.assertTrue(!sb.myReady() && sb.theirReady() && sb.myPieces() == 250 && sb.balance() == 600, "Ben's view mirrors it: " + sb);
            s.ready(b.getUUID());
            sa = s.state(a.getUUID());
            h.assertTrue(sa.stage().equals("COUNTDOWN") && sa.countdownMs() == TradeSession.COUNTDOWN_MS && sa.locked(), "the countdown shows, 5 s, locked");
            s.say(b.getUUID(), "deal");
            h.assertTrue(s.state(a.getUUID()).chat().stream().anyMatch(l -> l.text().equals("deal")), "the chat line reaches Ann's view");
            TradeMenu ma = new TradeMenu(0, a.getInventory(), (com.mrgregles.bsp_core.trade.Trade) s, a.getUUID());
            h.assertTrue(ma.locked() && !ma.getSlot(TradeMenu.MINE).mayPickup(a), "Ann's slots are locked in her menu");
        } finally {
            Trades.useClock(null);
            leaveAll(a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void trade_screen_works_on_the_other_server_too(GameTestHelper h) {
        Two t = two(h);
        try {
            TradeSession s = startAcross(h, t);
            Trade rt = fillAndReady(h, t, s);
            TradeMenu mb = new TradeMenu(0, t.ben().getInventory(), rt, t.ben().getUUID());
            h.assertTrue(mb.getSlot(TradeMenu.THEIRS).getItem().is(Items.DIAMOND), "Ben's screen shows Ann's Diamonds");
            TradeMenu.State sb = rt.state(t.ben().getUUID());
            h.assertTrue(sb.theirServer().equals("s1") && sb.stage().equals("COUNTDOWN") && sb.myPieces() == 200 && sb.theirReady() && mb.locked(), "Ben's view, from s1: " + sb);
            h.assertTrue(!mb.getSlot(TradeMenu.MINE).mayPickup(t.ben()), "Ben's slots are locked during the countdown");
        } finally {
            t.h1().stopAll();
            t.h2().stopAll();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the Portable Banker's market (task 9)

    @GameTest(template = EMPTY, batch = "trade_post")
    public void portable_market_unlock_is_bought_once_with_coins_and_cleared_at_season_end(GameTestHelper h) {
        var server = server(h);
        Player p = h.makeMockPlayer();
        UUID id = account(server, p.getUUID(), "Mira", 0);
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[1];
        Bank.unlockMarket(server, p, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("coins_market") && !Bank.account(server, id).remoteMarket, "no coins, no market: " + r[0]);
        int cost = Bank.marketUnlockCoins();
        var tier = Bank.marketUnlockTier();
        h.assertTrue(cost == 12 && tier == com.mrgregles.bsp_core.coin.CoinTier.NETHERITE, "the default price is 12 Netherite coins");
        p.getInventory().add(new ItemStack(com.mrgregles.bsp_core.coin.CoinTier.ILLYRIUM.coin(), 64));
        Bank.unlockMarket(server, p, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("coins_market"), "other tiers do not pay for it, whatever their value");
        p.getInventory().add(new ItemStack(tier.coin(), cost + 3));
        Bank.unlockMarket(server, p, x -> r[0] = x);
        h.assertTrue(r[0].ok() && Bank.account(server, id).remoteMarket, "unlocked with the coins");
        h.assertTrue(p.getInventory().countItem(tier.coin()) == 3, "the coins were taken once: " + p.getInventory().countItem(tier.coin()) + " left");
        h.assertTrue(!Bank.account(server, id).remoteShops, "the market unlock is its own: the Shopping Network stays locked");
        Bank.unlockMarket(server, p, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("market_already") && p.getInventory().countItem(tier.coin()) == 3, "cannot buy it twice, nothing more is taken");
        // the account keeps it through a save and a copy
        com.mrgregles.bsp_core.bank.BankAccount saved = com.mrgregles.bsp_core.bank.BankAccount.load(Bank.account(server, id).save());
        com.mrgregles.bsp_core.bank.BankAccount copy = new com.mrgregles.bsp_core.bank.BankAccount();
        copy.copyFrom(saved);
        h.assertTrue(saved.remoteMarket && copy.remoteMarket, "saved and copied with the account");
        // the season reset clears it with the Shopping Network
        Bank.setRemoteShops(server, id, true);
        int n = Bank.resetRemoteAll(server);
        h.assertTrue(n >= 1 && !Bank.account(server, id).remoteMarket && !Bank.account(server, id).remoteShops, "the season reset clears both");
        Bank.setRemoteMarket(server, id, true);
        Bank.setRemoteShops(server, id, false);
        h.assertTrue(Bank.resetRemoteAll(server) >= 1 && !Bank.account(server, id).remoteMarket, "an account with only the market is counted and cleared too");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void portable_market_opens_only_with_the_unlock_and_keeps_out_of_the_trader_list(GameTestHelper h) {
        var server = server(h);
        var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        Player other = h.makeMockPlayer();
        UUID id = account(server, p.getUUID(), "Mira", 100);
        try {
            h.assertTrue(!TradePostMenu.openPortable(p) && p.containerMenu == p.inventoryMenu, "locked: nothing opens");
            Bank.setRemoteMarket(server, id, true);
            h.assertTrue(TradePostMenu.openPortable(p) && p.containerMenu instanceof TradePostMenu m && m.portable() && m.post == null
                    && m.openedOn == TradePostBlock.Tab.MARKET, "unlocked: the market opens with no post");
            Trades.join(other, null);
            h.assertTrue(Trades.traders(other.getUUID()).stream().noneMatch(t -> t.id().equals(id)), "a Portable Banker does not put its player in the trader list");
            var atPost = TradePostMenu.view(server, id, "", 0, "", List.of(), true);
            var portable = TradePostMenu.view(server, id, "", 0, "", List.of(), false);
            h.assertTrue(!atPost.traders().isEmpty() && portable.traders().isEmpty() && portable.toMe().isEmpty() && portable.requestTo().isEmpty(),
                    "the portable view leaves the traders and requests out");
            String asked = Trades.ask(other, id);
            h.assertTrue(!asked.equals("ok") && Trades.requestFrom(other.getUUID()).isEmpty(), "nobody can ask a player who is only at a Portable Banker: " + asked);
        } finally {
            p.closeContainer();
            leaveAll(other);
            Bank.setRemoteMarket(server, id, false);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void portable_market_keys_are_refused_without_the_unlock_and_work_with_it(GameTestHelper h) {
        var server = server(h);
        var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        UUID id = account(server, p.getUUID(), "Mira", 5000);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 1000);
        MarketLedger ledger = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(ledger));
        try {
            MarketResult lot = list(server, seller, "Sella", 400, new ItemStack(Items.DIAMOND, 8));
            h.assertTrue(lot.ok(), "a lot to buy: " + lot);
            // a menu whose account has no unlock (an admin reset while it was open): any key closes it, nothing is bought
            TradePostMenu m = new TradePostMenu(0, p.getInventory(), null, TradePostBlock.Tab.MARKET);
            p.containerMenu = m;
            m.lotSlots().setItem(0, new ItemStack(Items.EMERALD, 7));
            com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, m, new com.mrgregles.bsp_core.network.TradePostActionPacket(
                    com.mrgregles.bsp_core.network.TradePostActionPacket.Action.BUY, lot.lot().id, 0, false));
            h.assertTrue(ledger.get(lot.lot().id) != null && Bank.pieces(server, id) == 5000, "locked: the lot is still open and no pieces moved");
            h.assertTrue(p.containerMenu != m && p.getInventory().countItem(Items.EMERALD) == 7, "the screen closed and the lot slots went back");
            for (var action : List.of(com.mrgregles.bsp_core.network.TradePostActionPacket.Action.LIST, com.mrgregles.bsp_core.network.TradePostActionPacket.Action.COLLECT,
                    com.mrgregles.bsp_core.network.TradePostActionPacket.Action.CANCEL)) {
                TradePostMenu again = new TradePostMenu(0, p.getInventory(), null, TradePostBlock.Tab.MARKET);
                p.containerMenu = again;
                com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, again, new com.mrgregles.bsp_core.network.TradePostActionPacket(action, lot.lot().id, 100, false));
                h.assertTrue(p.containerMenu != again && ledger.get(lot.lot().id) != null && Market.lotsOf(server, id) == 0, action + " is refused while locked");
            }
            // unlocked: the same key buys the lot
            Bank.setRemoteMarket(server, id, true);
            h.assertTrue(TradePostMenu.openPortable(p) && p.containerMenu instanceof TradePostMenu, "the market opens");
            TradePostMenu open = (TradePostMenu) p.containerMenu;
            com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, open, new com.mrgregles.bsp_core.network.TradePostActionPacket(
                    com.mrgregles.bsp_core.network.TradePostActionPacket.Action.BUY, lot.lot().id, 0, false));
            long paid = 400 + Market.tax(400);
            h.assertTrue(ledger.get(lot.lot().id) == null && Bank.pieces(server, id) == 5000 - paid, "unlocked: bought for " + paid + ", left " + Bank.pieces(server, id));
            // listing from the Portable Banker
            open.lotSlots().setItem(0, new ItemStack(Items.EMERALD, 7));
            p.getInventory().clearContent();
            com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, open, new com.mrgregles.bsp_core.network.TradePostActionPacket(
                    com.mrgregles.bsp_core.network.TradePostActionPacket.Action.LIST, "", 300, false));
            h.assertTrue(Market.lotsOf(server, id) == 1 && open.lotSlots().isEmpty() && p.containerMenu == open, "unlocked: listed from the device, the screen stays");
            // the trade keys need a Trade Post, unlock or not
            Player other = h.makeMockPlayer();
            Trades.join(other, null);
            try {
                com.mrgregles.bsp_core.network.TradePostActionPacket.run(p, open, new com.mrgregles.bsp_core.network.TradePostActionPacket(
                        com.mrgregles.bsp_core.network.TradePostActionPacket.Action.ASK, other.getUUID().toString(), 0, false));
                h.assertTrue(Trades.requestFrom(id).isEmpty() && p.containerMenu == open, "ASK from a Portable Banker is refused");
            } finally {
                leaveAll(other);
            }
        } finally {
            Market.useStore(old);
            p.closeContainer();
            Bank.setRemoteMarket(server, id, false);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the Market Board (task 10)

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_summary_follows_listings_sales_cancels_and_expiry(GameTestHelper h) {
        var server = server(h);
        UUID seller = account(server, UUID.randomUUID(), "Sella", 100_000);
        UUID buyer = account(server, UUID.randomUUID(), "Bea", 100_000);
        long[] now = {1_800_000_000_000L};
        MarketLedger ledger = new MarketLedger();
        MarketStore old = Market.useStore(new LocalMarket(ledger));
        Market.useClock(() -> now[0]);
        try {
            h.assertTrue(com.mrgregles.bsp_core.market.MarketSummary.build(server).isEmpty(), "an empty market, an empty summary");
            com.mrgregles.bsp_core.market.MarketSummary.tick(server);
            h.assertTrue(!com.mrgregles.bsp_core.market.MarketSummary.tick(server), "nothing changed: nothing is sent again");
            MarketResult a = list(server, seller, "Sella", 100, new ItemStack(Items.DIAMOND, 4));
            now[0] += 60_000;
            MarketResult b = list(server, seller, "Sella", 200, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.BIRCH_LOG, 64), new ItemStack(Items.SPRUCE_LOG, 64));
            now[0] += 60_000;
            MarketResult c = list(server, seller, "Sella", 300, new ItemStack(Items.EMERALD, 9));
            h.assertTrue(com.mrgregles.bsp_core.market.MarketSummary.tick(server), "a listing changes the summary and it is sent");
            var sum = com.mrgregles.bsp_core.market.MarketSummary.build(server);
            h.assertTrue(sum.size() == 3 && sum.get(0).price() == 300 && sum.get(1).price() == 200 && sum.get(2).price() == 100, "newest first: " + sum);
            h.assertTrue(sum.get(1).first().is(Items.OAK_LOG) && sum.get(1).first().getCount() == 64 && sum.get(1).stacks() == 3 && sum.get(1).seller().equals("Sella"),
                    "a lot's first stack, its stack count and seller");
            h.assertTrue(sum.get(2).listedMs() == now[0] - 120_000, "the listing time");
            // a sale, a cancel, an expiry: each takes its lot off
            h.assertTrue(buy(server, buyer, "Bea", c.lot().id).ok(), "Bea buys the Emeralds");
            h.assertTrue(com.mrgregles.bsp_core.market.MarketSummary.tick(server) && com.mrgregles.bsp_core.market.MarketSummary.build(server).size() == 2, "a sale takes it off");
            MarketResult[] r = new MarketResult[1];
            Market.cancel(server, seller, b.lot().id, false, x -> r[0] = x);
            h.assertTrue(r[0].ok() && com.mrgregles.bsp_core.market.MarketSummary.tick(server), "a cancel changes it");
            var left = com.mrgregles.bsp_core.market.MarketSummary.build(server);
            h.assertTrue(left.size() == 1 && left.get(0).price() == 100, "only the Diamonds are left");
            now[0] = a.lot().endsMs + 1;
            h.assertTrue(com.mrgregles.bsp_core.market.MarketSummary.build(server).isEmpty() && com.mrgregles.bsp_core.market.MarketSummary.tick(server),
                    "a lot past its end leaves the summary even before the sweep");
            Market.expireDue(server);
            h.assertTrue(com.mrgregles.bsp_core.market.MarketSummary.build(server).isEmpty() && !com.mrgregles.bsp_core.market.MarketSummary.tick(server), "and the sweep changes nothing more");
            // capped, newest first
            for (int i = 0; i < com.mrgregles.bsp_core.market.MarketSummary.CAP + 5; i++) {
                now[0] += 1000;
                UUID s = account(server, UUID.randomUUID(), "S" + i, 1000);
                h.assertTrue(list(server, s, "S" + i, 10 + i, new ItemStack(Items.DIRT)).ok(), "lot " + i);
            }
            var capped = com.mrgregles.bsp_core.market.MarketSummary.build(server);
            h.assertTrue(capped.size() == com.mrgregles.bsp_core.market.MarketSummary.CAP && capped.get(0).price() == 10 + com.mrgregles.bsp_core.market.MarketSummary.CAP + 4,
                    "capped at " + com.mrgregles.bsp_core.market.MarketSummary.CAP + ", the newest first: " + capped.size());
        } finally {
            Market.useClock(null);
            Market.useStore(old);
            com.mrgregles.bsp_core.market.MarketSummary.forget();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "trade_post")
    public void market_board_lines_and_the_board_line_note(GameTestHelper h) {
        // the lines other mods make are unchanged: no note
        var plain = com.mrgregles.bsp_core.api.BspBoards.BoardLine.of(net.minecraft.network.chat.Component.literal("a"), null);
        var four = new com.mrgregles.bsp_core.api.BspBoards.BoardLine(net.minecraft.network.chat.Component.literal("b"), null, ItemStack.EMPTY, 0xFF0000);
        h.assertTrue(plain.note() == null && four.note() == null && four.colour() == 0xFF0000, "the old ways of making a line have no note");
        var noted = four.withNote(net.minecraft.network.chat.Component.literal("3 h ago"));
        h.assertTrue(noted.note().getString().equals("3 h ago") && noted.text().getString().equals("b") && noted.colour() == 0xFF0000, "withNote keeps the rest");
        // the Market Board's lines from a summary
        long now = 1_800_000_000_000L;
        List<com.mrgregles.bsp_core.market.MarketSummary.Entry> entries = List.of(
                new com.mrgregles.bsp_core.market.MarketSummary.Entry(new ItemStack(Items.OAK_LOG, 64), 3, 1500, now - 3 * 3_600_000L - 60_000, "Sella"),
                new com.mrgregles.bsp_core.market.MarketSummary.Entry(new ItemStack(Items.DIAMOND), 1, 90, now - 10_000, "Sella"));
        List<com.mrgregles.bsp_core.market.MarketSummary.Entry> keep = List.of();
        try {
            com.mrgregles.bsp_core.client.ClientMarket.accept(now, entries);
            var lines = com.mrgregles.bsp_core.client.ClientMarket.lines();
            h.assertTrue(lines.size() == 2 && lines.get(0).icon().is(Items.OAK_LOG), "one line a lot, the first item as its icon");
            String text = lines.get(0).text().getString();
            h.assertTrue(text.contains("64") && text.contains("+2"), "the count, the name and +2 more: " + text);
            h.assertTrue(lines.get(0).value().getString().equals(Bank.format(1500)), "the price as the value: " + lines.get(0).value().getString());
            h.assertTrue(lines.get(0).note() != null && lines.get(1).note() != null && !lines.get(0).note().getString().equals(lines.get(1).note().getString()),
                    "each line notes its age: " + lines.get(0).note().getString() + " / " + lines.get(1).note().getString());
            h.assertTrue(!lines.get(1).text().getString().contains("+"), "a one-stack lot has no +more");
        } finally {
            com.mrgregles.bsp_core.client.ClientMarket.accept(System.currentTimeMillis(), keep);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ audit fix task 1: the connection settings out of the synced config

    @GameTest(template = EMPTY, batch = "trade_post")
    public void network_settings_move_out_of_the_server_config_and_passwords_never_reach_the_log(GameTestHelper h) {
        String oldDbPw = com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PASSWORD.get(), oldRedisPw = com.mrgregles.bsp_core.BSPConfig.OLD_REDIS_PASSWORD.get();
        String oldHost = com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_HOST.get();
        int oldPort = com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PORT.get();
        String dbPw = com.mrgregles.bsp_core.storage.NetworkConfig.DB_PASSWORD.get(), redisPw = com.mrgregles.bsp_core.storage.NetworkConfig.REDIS_PASSWORD.get();
        String host = com.mrgregles.bsp_core.storage.NetworkConfig.DB_HOST.get();
        int port = com.mrgregles.bsp_core.storage.NetworkConfig.DB_PORT.get();
        try {
            // a server config from before: everything in it, nothing in the network file
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PASSWORD.set("pw-db-test");
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_HOST.set("db.test");
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PORT.set(3307);
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_PASSWORD.set("");
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_HOST.set("localhost");
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_PORT.set(3306);
            // and a Redis password already set in the network file: that one wins
            com.mrgregles.bsp_core.BSPConfig.OLD_REDIS_PASSWORD.set("pw-redis-old");
            com.mrgregles.bsp_core.storage.NetworkConfig.REDIS_PASSWORD.set("pw-redis-new");
            List<String> moved = com.mrgregles.bsp_core.storage.NetworkConfig.moveFromServerConfig();
            h.assertTrue(com.mrgregles.bsp_core.storage.NetworkConfig.DB_PASSWORD.get().equals("pw-db-test") && com.mrgregles.bsp_core.storage.NetworkConfig.DB_HOST.get().equals("db.test")
                    && com.mrgregles.bsp_core.storage.NetworkConfig.DB_PORT.get() == 3307, "the database settings moved to the network file");
            h.assertTrue(com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PASSWORD.get().isEmpty() && com.mrgregles.bsp_core.BSPConfig.OLD_REDIS_PASSWORD.get().isEmpty()
                    && com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_HOST.get().equals("localhost") && com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PORT.get() == 3306,
                    "the synced server config holds no password and no address any more");
            h.assertTrue(com.mrgregles.bsp_core.storage.NetworkConfig.REDIS_PASSWORD.get().equals("pw-redis-new"), "a value already in the network file wins");
            String said = String.join("; ", moved);
            h.assertTrue(said.contains("database password moved") && said.contains("Redis password already set here") && said.contains("db.test"), "the log line says what moved: " + said);
            h.assertTrue(!said.contains("pw-db-test") && !said.contains("pw-redis"), "and never a password");
            h.assertTrue(com.mrgregles.bsp_core.storage.NetworkConfig.moveFromServerConfig().isEmpty(), "a second start finds nothing to move");
            // BSP-InventorySync and BSP-Server Utilities read the old field names by reflection: they still answer, from the network file
            for (String field : List.of("STORAGE_SERVER_ID", "STORAGE_HOST", "STORAGE_PORT", "STORAGE_DATABASE", "STORAGE_USER", "STORAGE_PASSWORD", "STORAGE_SSL",
                    "STORAGE_TABLE_PREFIX", "REDIS_ENABLED", "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "REDIS_SSL", "REDIS_DB", "REDIS_CHANNEL")) {
                try {
                    Object v = com.mrgregles.bsp_core.BSPConfig.class.getField(field).get(null);
                    h.assertTrue(v instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>, field + " is still a config value");
                } catch (ReflectiveOperationException e) {
                    h.fail(field + " is missing: BSP-InventorySync or BSP-Server Utilities could not find BSP-Core's database or Redis");
                }
            }
            h.assertTrue(com.mrgregles.bsp_core.BSPConfig.STORAGE_PASSWORD.get().equals("pw-db-test") && com.mrgregles.bsp_core.BSPConfig.STORAGE_HOST.get().equals("db.test")
                    && com.mrgregles.bsp_core.BSPConfig.REDIS_PASSWORD.get().equals("pw-redis-new"), "and they read the moved values");
        } finally {
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PASSWORD.set(oldDbPw);
            com.mrgregles.bsp_core.BSPConfig.OLD_REDIS_PASSWORD.set(oldRedisPw);
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_HOST.set(oldHost);
            com.mrgregles.bsp_core.BSPConfig.OLD_STORAGE_PORT.set(oldPort);
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_PASSWORD.set(dbPw);
            com.mrgregles.bsp_core.storage.NetworkConfig.REDIS_PASSWORD.set(redisPw);
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_HOST.set(host);
            com.mrgregles.bsp_core.storage.NetworkConfig.DB_PORT.set(port);
            com.mrgregles.bsp_core.storage.NetworkConfig.SPEC.save();
            com.mrgregles.bsp_core.BSPConfig.SPEC.save();
        }
        h.succeed();
    }
}
