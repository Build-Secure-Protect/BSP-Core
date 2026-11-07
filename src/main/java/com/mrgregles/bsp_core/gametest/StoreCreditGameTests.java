package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.credit.CreditPlace;
import com.mrgregles.bsp_core.credit.CreditSpend;
import com.mrgregles.bsp_core.credit.CreditTicket;
import com.mrgregles.bsp_core.credit.StoreCredit;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.shop.ShopItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * GameTests for Store Credit. Task 1: kinds and their rules, minting a ticket the first time it is seen in a player's inventory,
 * copies sharing one balance, binding when given, bundles (kits, shop cards) keeping tickets blank so each taker's ticket is
 * minted fresh, giving to a player who is away, and voided tickets disappearing. Task 2: paying credit first and the bank the
 * rest, all or nothing, the places a kind covers, the order tickets pay in, the clock and binding on the first purchase, the
 * warnings and the end of the time limit, and refunds. Task 3 (with an in-memory stand-in for the database): kinds, tickets, voids
 * and warnings written to the network, a spend refused by the network's record, a ticket from another server read on sight,
 * nothing changing while the database is away, the catch-up, and refunds. Task 4: shops (a ticket for a shop pays in its
 * sub-shops, not elsewhere, not with the switch off, counts in a test buy; a ticket sold on a card is minted fresh). Task 5: the Land
 * Office through BSP-Core's bank API as Region Guard calls it (balance, charge, a paid-back purchase, the claim's share of a refund
 * taken back), with Region Guard's calls made by the test. Task 6: the teleport API ({@code BspStoreCredit}). Task 7: what the ticket shows
 * (colour, mark, places, time) and the time wording. Task 8: what the shop and Land Office screens are told.
 * Task 9: the stamp when a clock starts and when a ticket runs out. Task 10: the CREDIT tab's server side. The test world keeps its data between runs, so every kind made here is
 * deleted in a finally, and the clock hook is put back.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class StoreCreditGameTests {
    private static final String EMPTY = "empty", BATCH = "store_credit";

    private static CreditKind kind(MinecraftServer server, String id, long value, boolean bind) {
        CreditKind k = new CreditKind(id);
        k.name = "Test " + id;
        k.value = value;
        k.bind = bind;
        k.shops.add("test_shop");
        String problem = StoreCredit.saveKind(server, k);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return StoreCredit.kind(server, id);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_kinds_keep_to_their_rules(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            CreditKind k = new CreditKind("test_rules");
            k.name = "Donor 10,000";
            k.value = 10_000;
            h.assertTrue(k.problem() == null, "a named kind with a value may be saved");
            h.assertTrue(new CreditKind("Bad Id").problem() != null && new CreditKind("").problem() != null, "ids are a-z, 0-9, _ and -");
            CreditKind bad = k.copy();
            bad.name = " ";
            h.assertTrue(bad.problem() != null, "a kind needs a name");
            bad = k.copy();
            bad.value = 0;
            h.assertTrue(bad.problem() != null, "a kind is worth at least a piece");
            bad.value = CreditKind.VALUE_MAX + 1;
            h.assertTrue(bad.problem() != null, "and at most a billion");
            bad = k.copy();
            bad.colour = "chartreuse";
            h.assertTrue(bad.problem() != null, "only the 16 dye colours");
            bad = k.copy();
            bad.mark = "X";
            h.assertTrue(bad.problem() != null, "the mark is C, D or none");
            h.assertTrue("C".equals(CreditKind.mark("c")) && "".equals(CreditKind.mark("none")) && CreditKind.mark("x") == null, "marks are read loosely");
            bad = k.copy();
            bad.durationMs = -1;
            h.assertTrue(bad.problem() != null, "no negative time limit");

            k.plots.addAll(List.of("Sanctuary", " sanctuary ", "Harbour Row", ""));
            k.shops.addAll(List.of("spawn_market", "spawn_market"));
            k.tidy();
            h.assertTrue(k.plots.equals(List.of("Sanctuary", "Harbour Row")) && k.shops.equals(List.of("spawn_market")), "repeats and blanks are dropped (plot names without case): " + k.plots + " " + k.shops);
            k.durationMs = 30L * 24 * 3_600_000L;
            k.mark = "D";
            k.colour = "light_blue";
            k.teleports = true;
            k.anyPlot = true;
            CreditKind back = CreditKind.load(k.save());
            h.assertTrue(back.save().equals(k.save()), "a kind survives saving and loading");

            h.assertTrue(StoreCredit.saveKind(server, bad) != null && StoreCredit.kind(server, "test_rules") == null, "a bad kind is refused and not saved");
            h.assertTrue(StoreCredit.saveKind(server, k) == null && StoreCredit.kind(server, "test_rules") != null, "a good kind is saved");
            h.assertTrue(StoreCredit.kind(server, "test_rules") != k, "the ledger keeps its own copy, not the editor's object");
            h.assertTrue(StoreCredit.deleteKind(server, "test_rules") && StoreCredit.kind(server, "test_rules") == null, "a kind can be deleted");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_rules");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_ticket_is_minted_once_and_copies_share_it(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            kind(server, "test_mint", 2_500, false);
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            ItemStack ticket = StoreCredit.fresh("test_mint");
            h.assertTrue(StoreCredit.serialOf(ticket).isEmpty(), "/give makes a ticket with no serial");
            p.getInventory().setItem(0, ticket);
            StoreCredit.scan(p);
            ItemStack held = p.getInventory().getItem(0);
            String serial = StoreCredit.serialOf(held);
            h.assertTrue(serial.matches("[2-9A-Z]{4}-[2-9A-Z]{4}"), "minted with a serial: " + serial);
            CreditTicket t = StoreCredit.ticket(server, serial);
            h.assertTrue(t != null && t.left == 2_500 && t.firstUseMs == 0 && t.owner == null && "test_mint".equals(t.kind), "the record holds the kind's value, unused and unbound");
            h.assertTrue(held.getTag().getCompound(StoreCredit.SHOWN).getLong("Left") == 2_500, "the tooltip's copy shows the credit");

            StoreCredit.scan(p);
            h.assertTrue(serial.equals(StoreCredit.serialOf(p.getInventory().getItem(0))), "a second look mints nothing new");
            p.getInventory().setItem(1, held.copy());
            StoreCredit.scan(p);
            h.assertTrue(serial.equals(StoreCredit.serialOf(p.getInventory().getItem(1))) && StoreCredit.ticket(server, held) == t, "a copy keeps the serial, so both spend from one record");

            t.left = 1_000; // as a spend would (task 2): every copy shows it at the next look
            StoreCredit.scan(p);
            h.assertTrue(p.getInventory().getItem(1).getTag().getCompound(StoreCredit.SHOWN).getLong("Left") == 1_000, "copies show what is left on the one record");

            ServerPlayer q = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            q.getInventory().setItem(0, StoreCredit.fresh("test_mint"));
            StoreCredit.scan(q);
            h.assertTrue(!serial.equals(StoreCredit.serialOf(q.getInventory().getItem(0))), "a second ticket of the kind gets its own serial");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_mint");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_ticket_binds_when_given_if_the_kind_says_so(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            kind(server, "test_bound", 500, true);
            kind(server, "test_free", 500, false);
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL), q = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            ItemStack bound = StoreCredit.fresh("test_bound"), free = StoreCredit.fresh("test_free");
            p.getInventory().setItem(0, bound);
            p.getInventory().setItem(1, free);
            StoreCredit.scan(p);
            CreditTicket b = StoreCredit.ticket(server, p.getInventory().getItem(0)), f = StoreCredit.ticket(server, p.getInventory().getItem(1));
            h.assertTrue(b != null && p.getUUID().equals(b.owner) && p.getGameProfile().getName().equals(b.ownerName), "a binding kind binds to the player it reaches");
            h.assertTrue(b.usableBy(p.getUUID()) && !b.usableBy(q.getUUID()), "only that player may spend it");
            h.assertTrue(f != null && f.owner == null && f.usableBy(q.getUUID()), "a free kind stays unbound until its first purchase");
            h.assertTrue(p.getGameProfile().getName().equals(p.getInventory().getItem(0).getTag().getCompound(StoreCredit.SHOWN).getString("Owner")), "the tooltip says who it belongs to");

            // handed over: the bound ticket stays theirs, and the new holder's look changes nothing
            q.getInventory().setItem(0, p.getInventory().getItem(0).copy());
            StoreCredit.scan(q);
            h.assertTrue(p.getUUID().equals(StoreCredit.ticket(server, q.getInventory().getItem(0)).owner), "a bound ticket handed on still belongs to its owner");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_bound");
            StoreCredit.deleteKind(server, "test_free");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_blank_unknown_and_withdrawn_tickets(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            p.getInventory().setItem(0, new ItemStack(com.mrgregles.bsp_core.registry.ModItems.STORE_CREDIT.get()));
            p.getInventory().setItem(1, StoreCredit.fresh("test_no_such_kind"));
            StoreCredit.scan(p);
            h.assertTrue(StoreCredit.serialOf(p.getInventory().getItem(0)).isEmpty() && StoreCredit.serialOf(p.getInventory().getItem(1)).isEmpty(),
                    "a blank ticket and a ticket of an unknown kind are left unminted");

            kind(server, "test_withdrawn", 300, false);
            p.getInventory().setItem(2, StoreCredit.fresh("test_withdrawn"));
            StoreCredit.scan(p);
            CreditTicket t = StoreCredit.ticket(server, p.getInventory().getItem(2));
            h.assertTrue(t != null, "minted while the kind exists");
            StoreCredit.deleteKind(server, "test_withdrawn");
            StoreCredit.scan(p);
            ItemStack after = p.getInventory().getItem(2);
            h.assertTrue(!after.isEmpty() && after.getTag().getCompound(StoreCredit.SHOWN).getBoolean("Withdrawn"), "a deleted kind's ticket stays and shows Withdrawn");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_withdrawn");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_tickets_in_kits_and_shop_cards_are_minted_fresh(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            kind(server, "test_bundle", 1_000, true);
            ServerPlayer admin = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            admin.getInventory().setItem(0, StoreCredit.fresh("test_bundle"));
            StoreCredit.scan(admin);
            ItemStack minted = admin.getInventory().getItem(0).copy();
            h.assertTrue(StoreCredit.minted(minted), "the admin's own ticket is minted");

            Kit kit = new Kit("test_credit_kit", "Credit kit", 0);
            kit.items.add(minted.copy());
            kit.items.add(new ItemStack(Items.BREAD, 4));
            h.assertTrue(kit.blankTotems(), "a minted ticket in a kit is blanked");
            h.assertTrue(StoreCredit.serialOf(kit.items.get(0)).isEmpty() && "test_bundle".equals(StoreCredit.kindOf(kit.items.get(0))) && !StoreCredit.minted(kit.items.get(0)),
                    "the kit keeps the kind and drops the serial");
            h.assertTrue(kit.items.get(1).is(Items.BREAD) && kit.items.get(1).getCount() == 4, "other items are left alone");

            ServerPlayer a = MockPlayers.serverPlayer(h, GameType.SURVIVAL), b = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            a.getInventory().setItem(0, kit.copies().get(0));
            b.getInventory().setItem(0, kit.copies().get(0));
            StoreCredit.scan(a);
            StoreCredit.scan(b);
            CreditTicket ta = StoreCredit.ticket(server, a.getInventory().getItem(0)), tb = StoreCredit.ticket(server, b.getInventory().getItem(0));
            h.assertTrue(ta != null && tb != null && ta != tb && ta.left == 1_000 && tb.left == 1_000, "each taker gets their own ticket with the full value");
            h.assertTrue(a.getUUID().equals(ta.owner) && b.getUUID().equals(tb.owner), "each bound to its taker");

            ShopItem card = new ShopItem();
            card.stack = minted.copy();
            card.buy = 50;
            ShopItem saved = ShopItem.load(card.save());
            h.assertTrue(!StoreCredit.minted(saved.stack) && "test_bundle".equals(StoreCredit.kindOf(saved.stack)), "a shop card keeps its ticket blank");
            h.assertTrue(StoreCredit.blank(new ItemStack(Items.DIAMOND)).is(Items.DIAMOND), "blank leaves other items alone");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_bundle");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_given_to_a_player_who_is_away_waits_for_them(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID away = UUID.randomUUID();
        try {
            kind(server, "test_give", 750, true);
            h.assertTrue(StoreCredit.give(server, away, "test_no_such_kind", 1) == -1, "an unknown kind gives nothing");
            if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
                h.succeed(); // on a network the delivery waits in the database; this checks the single-server path
                return;
            }
            h.assertTrue(StoreCredit.give(server, away, "test_give", 3) == 3, "three given");
            List<ItemStack> waiting = SeasonData.get(server).pendingItems.get(away);
            h.assertTrue(waiting != null && waiting.size() == 3, "they wait for the next login: " + waiting);
            for (ItemStack s : waiting) {
                h.assertTrue("test_give".equals(StoreCredit.kindOf(s)) && StoreCredit.serialOf(s).isEmpty(), "unminted until they reach the player");
            }
            h.assertTrue(StoreCredit.give(server, away, "test_give", 999) == StoreCredit.GIVE_MAX, "at most 64 at once");
            h.succeed();
        } finally {
            SeasonData.get(server).pendingItems.remove(away);
            SeasonData.get(server).setDirty();
            StoreCredit.deleteKind(server, "test_give");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void voided_credit_ticket_disappears_when_seen(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            kind(server, "test_void", 400, false);
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            p.getInventory().setItem(0, StoreCredit.fresh("test_void"));
            StoreCredit.scan(p);
            ItemStack ticket = p.getInventory().getItem(0);
            String serial = StoreCredit.serialOf(ticket);
            p.getEnderChestInventory().setItem(5, ticket.copy());
            p.getInventory().setItem(3, new ItemStack(Items.APPLE));
            h.assertTrue(StoreCredit.voidTicket(server, serial), "voided");
            h.assertTrue(!StoreCredit.voidTicket(server, "NONE-SUCH"), "an unknown serial is not voided");
            h.assertTrue(StoreCredit.scan(p) == 2, "both copies are taken away");
            h.assertTrue(p.getInventory().getItem(0).isEmpty() && p.getEnderChestInventory().getItem(5).isEmpty(), "from the inventory and the ender chest");
            h.assertTrue(p.getInventory().getItem(3).is(Items.APPLE), "nothing else is touched");
            h.assertTrue(StoreCredit.ticket(server, serial).voided, "the record stays, marked voided");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_void");
        }
    }

    // ------------------------------------------------------------------ task 2: spending

    private static CreditKind spendKind(MinecraftServer server, String id, long value, long durationMs, java.util.function.Consumer<CreditKind> places) {
        CreditKind k = new CreditKind(id);
        k.name = "Test " + id;
        k.value = value;
        k.durationMs = durationMs;
        places.accept(k);
        String problem = StoreCredit.saveKind(server, k);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return StoreCredit.kind(server, id);
    }

    /** A minted ticket of {@code kind} in {@code slot} of the player's inventory; returns its record. */
    private static CreditTicket hand(ServerPlayer p, String kind, int slot) {
        p.getInventory().setItem(slot, StoreCredit.fresh(kind));
        StoreCredit.scan(p);
        return StoreCredit.ticket(p.server, p.getInventory().getItem(slot));
    }

    private static void fund(MinecraftServer server, ServerPlayer p, long pieces) {
        Bank.give(server, p.getUUID(), pieces, BankTransaction.Type.ADMIN, "test", "", r -> {});
    }

    private static CreditSpend.Result pay(ServerPlayer p, long price, CreditPlace place) {
        CreditSpend.Result[] out = new CreditSpend.Result[1];
        CreditSpend.pay(p, price, place, "Test Shop", "a test purchase", r -> out[0] = r);
        if (out[0] == null) {
            throw new IllegalStateException("a single-server payment answers at once");
        }
        return out[0];
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_pays_first_and_the_bank_pays_the_rest(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        try {
            spendKind(server, "test_pay", 2_000, 0, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            fund(server, p, 10_000);
            CreditTicket t = hand(p, "test_pay", 0);
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 2_000, "the ticket can pay 2,000 here");

            CreditSpend.Result r = pay(p, 2_500, CreditPlace.shop("test_shop"));
            h.assertTrue(r.ok() && r.receipt().credit() == 2_000 && r.receipt().bank() == 500, "2,000 from the ticket and 500 from the bank: " + r);
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 9_500, "the bank paid only its part: " + Bank.pieces(server, p.getUUID()));
            h.assertTrue(t.left == 0 && r.receipt().ticketSpent() && p.getInventory().getItem(0).isEmpty(), "the used-up ticket is gone from the inventory");
            h.assertTrue(r.receipt().parts().size() == 1 && r.receipt().parts().get(0).serial().equals(t.serial), "the receipt names the ticket");

            CreditSpend.Result bankOnly = pay(p, 300, CreditPlace.shop("test_shop"));
            h.assertTrue(bankOnly.ok() && bankOnly.receipt().credit() == 0 && bankOnly.receipt().bank() == 300 && Bank.pieces(server, p.getUUID()) == 9_200, "with no credit left the bank pays it all");

            CreditTicket whole = hand(p, "test_pay", 1);
            CreditSpend.Result inFull = pay(p, 1_500, CreditPlace.shop("test_shop"));
            h.assertTrue(inFull.ok() && inFull.receipt().bank() == 0 && whole.left == 500 && Bank.pieces(server, p.getUUID()) == 9_200, "a ticket that covers the price leaves the bank alone");
            h.assertTrue(p.getInventory().getItem(1).getTag().getCompound(StoreCredit.SHOWN).getLong("Left") == 500, "the ticket shows what is left");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_pay");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_is_all_or_nothing(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        try {
            spendKind(server, "test_short", 2_000, 7 * 24 * 3_600_000L, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            fund(server, p, 100);
            CreditTicket t = hand(p, "test_short", 0);
            CreditSpend.Result r = pay(p, 2_500, CreditPlace.shop("test_shop"));
            h.assertTrue(!r.ok() && "short".equals(r.failKey()), "the bank cannot pay its 500, so nothing is bought: " + r);
            h.assertTrue(t.left == 2_000 && t.firstUseMs == 0 && t.owner == null, "no credit was taken, and the clock has not started");
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 100, "and the bank is untouched");
            h.succeed();
        } finally {
            StoreCredit.deleteKind(server, "test_short");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_pays_only_where_its_kind_says(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        try {
            CreditKind shop = spendKind(server, "test_where_shop", 100, 0, k -> k.shops.add("test_shop"));
            CreditKind plots = spendKind(server, "test_where_plots", 100, 0, k -> k.plots.add("Sanctuary"));
            CreditKind any = spendKind(server, "test_where_any", 100, 0, k -> k.anyPlot = true);
            CreditKind tp = spendKind(server, "test_where_tp", 100, 0, k -> k.teleports = true);
            h.assertTrue(CreditPlace.shop("test_shop").coveredBy(shop) && CreditPlace.shop("test_sub", "test_shop").coveredBy(shop), "the shop and its sub-shops");
            h.assertTrue(!CreditPlace.shop("other_shop").coveredBy(shop) && !CreditPlace.shop("test_shop_2").coveredBy(shop), "not other shops");
            h.assertTrue(CreditPlace.plot("Sanctuary").coveredBy(plots) && CreditPlace.plot("sanctuary").coveredBy(plots) && !CreditPlace.plot("Sanctuary 4").coveredBy(plots),
                    "plot names match whole, without case");
            h.assertTrue(!CreditPlace.ground().coveredBy(plots) && CreditPlace.ground().coveredBy(any) && CreditPlace.plot("Anything").coveredBy(any), "open ground and enlarging need \"any plot\"");
            h.assertTrue(CreditPlace.teleport().coveredBy(tp) && !CreditPlace.teleport().coveredBy(any) && !CreditPlace.shop("test_shop").coveredBy(tp), "teleport credit pays teleports only");

            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            hand(p, "test_where_shop", 0);
            hand(p, "test_where_tp", 1);
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 100 && CreditSpend.available(p, CreditPlace.teleport()) == 100
                    && CreditSpend.available(p, CreditPlace.plot("Sanctuary")) == 0, "each ticket counts only where it spends");
            p.getInventory().setItem(2, p.getInventory().getItem(0).copy());
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 100, "two copies of one ticket count once");
            h.succeed();
        } finally {
            for (String id : List.of("test_where_shop", "test_where_plots", "test_where_any", "test_where_tp")) {
                StoreCredit.deleteKind(server, id);
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_pays_in_order_and_binds_on_the_first_purchase(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long t0 = 1_800_000_000_000L;
        try {
            StoreCredit.useClock(() -> t0);
            spendKind(server, "test_order_long", 1_000, 0, k -> k.shops.add("test_shop"));
            spendKind(server, "test_order_week", 1_000, 7 * 24 * 3_600_000L, k -> k.shops.add("test_shop"));
            spendKind(server, "test_order_day", 1_000, 24 * 3_600_000L, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL), q = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket longer = hand(p, "test_order_long", 0), week = hand(p, "test_order_week", 1), day = hand(p, "test_order_day", 2);
            List<CreditSpend.Usable> order = CreditSpend.usable(p, CreditPlace.shop("test_shop"));
            h.assertTrue(order.size() == 3 && order.get(0).ticket() == day && order.get(1).ticket() == week && order.get(2).ticket() == longer,
                    "the ticket that would run out soonest pays first, and one with no time limit last");

            CreditSpend.Result r = pay(p, 300, CreditPlace.shop("test_shop"));
            h.assertTrue(r.ok() && day.left == 700 && week.left == 1_000 && longer.left == 1_000, "the day ticket paid");
            h.assertTrue(day.firstUseMs == t0 && day.endsMs(StoreCredit.kind(server, "test_order_day")) == t0 + 24 * 3_600_000L, "its clock started at the purchase");
            h.assertTrue(p.getUUID().equals(day.owner) && week.owner == null, "the first purchase binds the ticket to the player");
            q.getInventory().setItem(0, p.getInventory().getItem(2).copy());
            h.assertTrue(CreditSpend.available(q, CreditPlace.shop("test_shop")) == 0, "a bound ticket spends nothing for anyone else");

            CreditSpend.Result spill = pay(p, 1_200, CreditPlace.shop("test_shop"));
            h.assertTrue(spill.ok() && spill.receipt().parts().size() == 2 && day.left == 0 && week.left == 500 && spill.receipt().bank() == 0,
                    "a price larger than one ticket takes the rest from the next: " + spill.receipt().parts());

            CreditTicket small = hand(p, "test_order_long", 3);
            small.left = 50;
            order = CreditSpend.usable(p, CreditPlace.shop("test_shop"));
            h.assertTrue(order.get(order.size() - 2).ticket() == small && order.get(order.size() - 1).ticket() == longer, "among tickets that end together, least left pays first");
            h.succeed();
        } finally {
            StoreCredit.useClock(null);
            for (String id : List.of("test_order_long", "test_order_week", "test_order_day")) {
                StoreCredit.deleteKind(server, id);
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_warns_and_then_runs_out(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long[] now = {1_800_000_000_000L};
        long hour = 3_600_000L;
        try {
            StoreCredit.useClock(() -> now[0]);
            spendKind(server, "test_expiry", 1_000, 30 * hour, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket t = hand(p, "test_expiry", 0);
            p.getEnderChestInventory().setItem(0, p.getInventory().getItem(0).copy());
            now[0] += 40 * hour;
            StoreCredit.scan(p);
            h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "an unused ticket never runs out");

            h.assertTrue(pay(p, 100, CreditPlace.shop("test_shop")).ok(), "the first purchase");
            long start = now[0];
            now[0] = start + 5 * hour;
            StoreCredit.scan(p);
            h.assertTrue(t.warned == 0, "no warning with 25 hours left");
            now[0] = start + 7 * hour;
            StoreCredit.scan(p);
            h.assertTrue(t.warned == CreditTicket.WARNED_DAY, "a warning with less than a day left");
            StoreCredit.scan(p);
            h.assertTrue(t.warned == CreditTicket.WARNED_DAY, "once");
            now[0] = start + 29 * hour + 30 * 60_000L;
            StoreCredit.scan(p);
            h.assertTrue(t.warned == (CreditTicket.WARNED_DAY | CreditTicket.WARNED_HOUR), "and with less than an hour left");
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 900, "still spendable");

            now[0] = start + 30 * hour;
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 0, "at the end it pays nothing, even before the next look");
            h.assertTrue(StoreCredit.scan(p) == 2 && p.getInventory().getItem(0).isEmpty() && p.getEnderChestInventory().getItem(0).isEmpty(),
                    "and the next look takes it from the inventory and the ender chest");
            h.assertTrue(t.left == 900, "the record keeps what was unspent");

            ServerPlayer later = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            later.getInventory().setItem(4, StoreCredit.itemOf(t, StoreCredit.kind(server, "test_expiry")));
            h.assertTrue(StoreCredit.scan(later) == 1 && later.getInventory().getItem(4).isEmpty(), "a copy found later (a chest) disappears when it lands in an inventory");
            h.succeed();
        } finally {
            StoreCredit.useClock(null);
            StoreCredit.deleteKind(server, "test_expiry");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_refund_puts_it_back(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            spendKind(server, "test_refund", 2_000, 0, k -> k.teleports = true);
            fund(server, p, 1_000);
            CreditTicket t = hand(p, "test_refund", 0);
            CreditSpend.Result r = pay(p, 2_300, CreditPlace.teleport());
            h.assertTrue(r.ok() && t.left == 0 && Bank.pieces(server, p.getUUID()) == 700 && p.getInventory().getItem(0).isEmpty(), "paid, ticket used up");

            CreditSpend.refund(server, r.receipt(), "the move failed");
            h.assertTrue(t.left == 2_000 && Bank.pieces(server, p.getUUID()) == 1_000, "the credit is back on the ticket and the bank's part in the bank");
            List<ItemStack> back = SeasonData.get(server).pendingItems.get(p.getUUID()); // a test player is never in the player list, so it is delivered
            h.assertTrue(back != null && back.size() == 1 && t.serial.equals(StoreCredit.serialOf(back.get(0))), "the used-up ticket's item comes back with its serial");

            CreditSpend.Result again = pay(p, 0, CreditPlace.teleport());
            h.assertTrue(again.ok() && again.receipt().total() == 0, "nothing to pay is paid");
            StoreCredit.voidTicket(server, t.serial);
            CreditSpend.refund(server, new CreditSpend.Receipt(p.getUUID(), 500, 0, List.of(new CreditSpend.Part(t.serial, 500)), "", 0, false), "test");
            h.assertTrue(t.left == 2_000, "a refund to a voided ticket is lost");
            h.succeed();
        } finally {
            SeasonData.get(server).pendingItems.remove(p.getUUID());
            StoreCredit.deleteKind(server, "test_refund");
        }
    }

    // ------------------------------------------------------------------ task 3: the network

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_changes_reach_the_network(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long[] now = {1_800_000_000_000L};
        MemoryCreditStore db = new MemoryCreditStore(() -> now[0]);
        try {
            StoreCredit.useClock(() -> now[0]);
            com.mrgregles.bsp_core.credit.CreditSync.useStore(db);
            spendKind(server, "test_net", 1_000, 24 * 3_600_000L, k -> k.shops.add("test_shop"));
            h.assertTrue(db.kinds.containsKey("test_net") && db.kinds.get("test_net").value == 1_000, "a saved kind is written to the network");
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket t = hand(p, "test_net", 0);
            h.assertTrue(db.tickets.containsKey(t.serial) && db.tickets.get(t.serial).left == 1_000, "a minted ticket is written to the network");

            h.assertTrue(pay(p, 300, CreditPlace.shop("test_shop")).ok(), "paid on the network");
            CreditTicket row = db.tickets.get(t.serial);
            h.assertTrue(row.left == 700 && row.firstUseMs == now[0] && p.getUUID().equals(row.owner), "the network's record took the spend, the clock and the binding");
            h.assertTrue(t.left == 700 && t.firstUseMs == now[0], "and this server's copy follows it");

            now[0] += 23 * 3_600_000L + 1;
            StoreCredit.scan(p);
            h.assertTrue((db.tickets.get(t.serial).warned & CreditTicket.WARNED_DAY) != 0, "the day warning is noted on the network, so another server does not send it again");
            h.assertTrue(StoreCredit.voidTicket(server, t.serial) && db.tickets.get(t.serial).voided, "a void is written to the network");
            h.assertTrue(StoreCredit.deleteKind(server, "test_net") && !db.kinds.containsKey("test_net"), "and so is a deleted kind");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(null);
            StoreCredit.useClock(null);
            StoreCredit.deleteKind(server, "test_net");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_spend_is_checked_against_the_network(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long[] now = {1_800_000_000_000L};
        MemoryCreditStore db = new MemoryCreditStore(() -> now[0]);
        try {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(db);
            spendKind(server, "test_net_race", 1_000, 0, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket t = hand(p, "test_net_race", 0);
            // another server spends 800 of it; this server has not heard yet
            h.assertTrue(db.spend(List.of(new com.mrgregles.bsp_core.credit.CreditStore.Claim(t.serial, 800, 0)), p.getUUID(), "elsewhere") > 0, "spent elsewhere");
            h.assertTrue(t.left == 1_000 && CreditSpend.available(p, CreditPlace.shop("test_shop")) == 1_000, "this server still thinks 1,000");
            CreditSpend.Result r = pay(p, 500, CreditPlace.shop("test_shop"));
            h.assertTrue(!r.ok() && "changed".equals(r.failKey()), "the network refuses: only 200 is left: " + r);
            h.assertTrue(t.left == 200 && db.tickets.get(t.serial).left == 200, "nothing more was taken, and this server's copy is corrected");
            h.assertTrue(pay(p, 150, CreditPlace.shop("test_shop")).ok() && db.tickets.get(t.serial).left == 50, "a spend within what is left goes through");

            CreditSpend.Result whole = pay(p, 50, CreditPlace.shop("test_shop"));
            h.assertTrue(whole.ok() && db.tickets.get(t.serial).left == 0 && p.getInventory().getItem(0).isEmpty(), "used up on the network, gone here");
            CreditSpend.refund(server, whole.receipt(), "test");
            h.assertTrue(db.tickets.get(t.serial).left == 50 && t.left == 50, "a refund goes back on the network's record");
            List<ItemStack> back = SeasonData.get(server).pendingItems.remove(p.getUUID());
            h.assertTrue(back != null && back.size() == 1 && t.serial.equals(StoreCredit.serialOf(back.get(0))), "and the used-up item comes back");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(null);
            StoreCredit.deleteKind(server, "test_net_race");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_ticket_from_another_server_is_read_on_sight(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long[] now = {1_800_000_000_000L};
        MemoryCreditStore db = new MemoryCreditStore(() -> now[0]);
        try {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(db);
            CreditKind k = spendKind(server, "test_net_far", 900, 0, x -> x.teleports = true);
            String serial = ("F" + UUID.randomUUID().toString().replace("-", "").substring(0, 7)).toUpperCase(java.util.Locale.ROOT); // the test world keeps every serial it has seen
            serial = serial.substring(0, 4) + "-" + serial.substring(4);
            CreditTicket far = new CreditTicket(serial, "test_net_far");
            far.left = 640;
            db.insertTicket(far);
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            ItemStack carried = StoreCredit.fresh("test_net_far");
            carried.getOrCreateTag().putString(StoreCredit.SERIAL, serial);
            p.getInventory().setItem(0, carried);
            StoreCredit.scan(p);
            CreditTicket here = StoreCredit.ticket(server, serial);
            h.assertTrue(here != null && here.left == 640, "the network's record is read when the ticket turns up");
            h.assertTrue(CreditSpend.available(p, CreditPlace.teleport()) == 640, "and it pays here");
            StoreCredit.scan(p);
            h.assertTrue(p.getInventory().getItem(0).getTag().getCompound(StoreCredit.SHOWN).getLong("Left") == 640, "its tooltip shows the network's credit");

            // the catch-up: a kind and a change made elsewhere
            CreditKind other = new CreditKind("test_net_other");
            other.name = "Other";
            other.value = 5;
            other.shops.add("x");
            db.writeKind(other);
            now[0] += 1_000;
            db.tickets.get(serial).left = 100;
            db.tickets.get(serial).updatedMs = now[0];
            com.mrgregles.bsp_core.credit.CreditSync.catchUp(server);
            h.assertTrue(StoreCredit.kind(server, "test_net_other") != null && here.left == 100, "the catch-up brings both");
            h.assertTrue(StoreCredit.kind(server, "test_net_far") != null, "kinds the network has stay");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(null);
            StoreCredit.deleteKind(server, "test_net_far");
            StoreCredit.deleteKind(server, "test_net_other");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_waits_while_the_network_is_away(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        MemoryCreditStore db = new MemoryCreditStore(() -> 1_800_000_000_000L);
        try {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(db);
            spendKind(server, "test_net_down", 400, 0, k -> k.shops.add("test_shop"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket t = hand(p, "test_net_down", 0);
            com.mrgregles.bsp_core.credit.CreditSync.useDown(true);
            CreditKind edit = StoreCredit.kind(server, "test_net_down").copy();
            edit.value = 999;
            h.assertTrue(StoreCredit.DOWN.equals(StoreCredit.saveKind(server, edit)) && StoreCredit.kind(server, "test_net_down").value == 400, "kinds cannot change");
            p.getInventory().setItem(1, StoreCredit.fresh("test_net_down"));
            StoreCredit.scan(p);
            h.assertTrue(StoreCredit.serialOf(p.getInventory().getItem(1)).isEmpty(), "a new ticket stays unminted");
            CreditSpend.Result r = pay(p, 100, CreditPlace.shop("test_shop"));
            h.assertTrue(!r.ok() && "unreachable".equals(r.failKey()) && t.left == 400, "credit cannot be spent: " + r);
            h.assertTrue(!StoreCredit.voidTicket(server, t.serial) && !t.voided, "nor voided");
            com.mrgregles.bsp_core.credit.CreditSync.useDown(false);
            StoreCredit.scan(p);
            h.assertTrue(!StoreCredit.serialOf(p.getInventory().getItem(1)).isEmpty(), "back again, the ticket is minted at the next look");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.credit.CreditSync.useStore(null);
            StoreCredit.deleteKind(server, "test_net_down");
        }
    }

    // ------------------------------------------------------------------ task 4: shops

    private static com.mrgregles.bsp_core.shop.Shops.Outcome buy(MinecraftServer server, ServerPlayer p, com.mrgregles.bsp_core.shop.Shop shop, int index, boolean test) {
        com.mrgregles.bsp_core.shop.Shops.Outcome[] o = new com.mrgregles.bsp_core.shop.Shops.Outcome[1];
        com.mrgregles.bsp_core.shop.Shops.buy(server, p, shop, index, 1, test, x -> o[0] = x);
        return o[0];
    }

    private static int card(MinecraftServer server, com.mrgregles.bsp_core.shop.Shop shop, ItemStack stack, long price) {
        ShopItem item = new ShopItem();
        item.stack = stack;
        item.buy = price;
        int index = shop.add(item);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        return index;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_pays_in_the_shops_its_kind_names(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        String tag = Long.toHexString(System.nanoTime());
        var market = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_market_" + tag, "Credit Market", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        var blocks = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_blocks_" + tag, "Credit Blocks", com.mrgregles.bsp_core.shop.Shop.Kind.SUB);
        var other = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_other_" + tag, "Other Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            blocks.parent = market.id;
            com.mrgregles.bsp_core.shop.Shops.save(server, blocks);
            spendKind(server, "test_shop_credit", 500, 0, k -> k.shops.add(market.id));
            int bricks = card(server, blocks, new ItemStack(Items.STONE_BRICKS, 64), 600);
            int elsewhere = card(server, other, new ItemStack(Items.OAK_LOG, 16), 100);
            fund(server, p, 1_000);
            CreditTicket t = hand(p, "test_shop_credit", 0);
            long pot = com.mrgregles.bsp_core.market.Market.pot(server);

            var o = buy(server, p, blocks, bricks, false);
            h.assertTrue(o.ok() && p.getInventory().countItem(Items.STONE_BRICKS) == 64, "bought in the sub-shop of the shop the kind names: " + o);
            h.assertTrue(t.left == 0 && Bank.pieces(server, p.getUUID()) == 900, "500 from the ticket, 100 from the bank: " + t.left + " " + Bank.pieces(server, p.getUUID()));
            h.assertTrue(com.mrgregles.bsp_core.market.Market.pot(server) - pot == com.mrgregles.bsp_core.shop.Shops.lotteryShare(600), "the lottery share is on the whole price");
            long rows = com.mrgregles.bsp_core.bank.BankLedger.get(server).log(p.getUUID()).stream().filter(tx -> tx.shopId().equals(blocks.id) && com.mrgregles.bsp_core.shop.ShopReport.leadingCount(tx.detail()) == 64).count();
            h.assertTrue(rows == 1, "the shop report counts the 64 bricks once: " + rows);

            CreditTicket t2 = hand(p, "test_shop_credit", 1);
            var o2 = buy(server, p, other, elsewhere, false);
            h.assertTrue(o2.ok() && t2.left == 500 && Bank.pieces(server, p.getUUID()) == 800, "a shop the kind does not name: the bank pays");
            CreditSpend.setWants(p.getUUID(), false);
            var o3 = buy(server, p, blocks, bricks, false);
            h.assertTrue(o3.ok() && t2.left == 500 && Bank.pieces(server, p.getUUID()) == 200, "with PAY WITH CREDIT off the bank pays it all");
            CreditSpend.setWants(p.getUUID(), true);

            var test = buy(server, p, blocks, bricks, true);
            h.assertTrue(test.ok() && "test_bought".equals(test.key()), "a test buy counts the credit: 500 credit + 200 bank covers 600: " + test);
            h.succeed();
        } finally {
            CreditSpend.setWants(p.getUUID(), true);
            for (var shop : List.of(market, blocks, other)) {
                com.mrgregles.bsp_core.shop.Shops.delete(server, shop.id);
            }
            StoreCredit.deleteKind(server, "test_shop_credit");
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_ticket_bought_from_a_shop_is_minted_fresh(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        var donors = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_donor_" + Long.toHexString(System.nanoTime()), "Donor Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        try {
            spendKind(server, "test_shop_ticket", 3_000, 0, k -> k.teleports = true);
            ServerPlayer admin = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket sample = hand(admin, "test_shop_ticket", 0);
            int index = card(server, donors, admin.getInventory().getItem(0).copy(), 250);
            ServerPlayer a = MockPlayers.serverPlayer(h, GameType.SURVIVAL), b = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            fund(server, a, 1_000);
            fund(server, b, 1_000);
            h.assertTrue(buy(server, a, donors, index, false).ok() && buy(server, b, donors, index, false).ok(), "both bought one");
            ItemStack ta = ItemStack.EMPTY, tb = ItemStack.EMPTY;
            for (int i = 0; i < 36; i++) {
                if (StoreCredit.isTicket(a.getInventory().getItem(i))) ta = a.getInventory().getItem(i);
                if (StoreCredit.isTicket(b.getInventory().getItem(i))) tb = b.getInventory().getItem(i);
            }
            h.assertTrue(StoreCredit.serialOf(ta).isEmpty() && StoreCredit.serialOf(tb).isEmpty(), "handed out blank, never the card's serial");
            StoreCredit.scan(a);
            StoreCredit.scan(b);
            CreditTicket ca = StoreCredit.ticket(server, ta), cb = StoreCredit.ticket(server, tb);
            h.assertTrue(ca != null && cb != null && ca != cb && ca != sample && ca.left == 3_000 && cb.left == 3_000, "each buyer has a full ticket of their own");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.shop.Shops.delete(server, donors.id);
            StoreCredit.deleteKind(server, "test_shop_ticket");
        }
    }

    // ------------------------------------------------------------------ task 5: the Land Office (Region Guard's calls made here, as it makes them)

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_pays_at_the_land_office(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL), admin = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        String claim = "test_claim_" + Long.toHexString(System.nanoTime()), dim = h.getLevel().dimension().location().toString();
        try {
            com.mrgregles.bsp_core.credit.CreditLand.usePlayers(id -> id.equals(p.getUUID()) ? p : id.equals(admin.getUUID()) ? admin : null);
            spendKind(server, "test_land", 6_000, 0, k -> k.plots.add("Sanctuary"));
            fund(server, p, 1_000);
            CreditTicket t = hand(p, "test_land", 0);
            h.assertTrue(com.mrgregles.bsp_core.api.BspBank.balance(server, p.getUUID()) == 1_000, "outside the Land Office the balance is the bank's");

            var quote = com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.plot("Harbour Row"), false);
            h.assertTrue(com.mrgregles.bsp_core.api.BspBank.balance(server, p.getUUID()) == 1_000, "a plot the kind does not name: no credit");
            com.mrgregles.bsp_core.credit.CreditLand.end(p, quote, false, null);

            var buy = com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.plot("sanctuary"), true);
            h.assertTrue(buy != null && com.mrgregles.bsp_core.api.BspBank.balance(server, p.getUUID()) == 7_000, "Region Guard's affordability check sees bank plus credit");
            h.assertTrue(com.mrgregles.bsp_core.credit.CreditLand.busy(p) && com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.plot("sanctuary"), true) == null
                    && com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.ground(), false) == null, "a purchase in flight is never taken over by another purchase or a quote");
            boolean[] other = {false};
            com.mrgregles.bsp_core.api.BspBank.charge(server, p.getUUID(), 100, "BSP Teleport", "warp", ok -> other[0] = ok);
            h.assertTrue(other[0] && t.left == 6_000 && Bank.pieces(server, p.getUUID()) == 900, "another mod's charge meanwhile is the bank's alone");
            fund(server, p, 100);
            boolean[] paid = {false};
            com.mrgregles.bsp_core.api.BspBank.charge(server, p.getUUID(), 6_500, "Region Guard", "plot Sanctuary 4", ok -> paid[0] = ok);
            h.assertTrue(paid[0] && t.left == 0 && Bank.pieces(server, p.getUUID()) == 500, "6,000 from the ticket, 500 from the bank");

            // Region Guard could not finish the claim and pays it back: once, as it was paid
            boolean[] back = {false};
            com.mrgregles.bsp_core.api.BspBank.credit(server, p.getUUID(), 6_500, "Region Guard", "refund, plot Sanctuary 4", ok -> back[0] = ok);
            h.assertTrue(back[0] && t.left == 6_000 && Bank.pieces(server, p.getUUID()) == 1_000, "the credit went back on the ticket, not into the bank");
            List<ItemStack> returned = SeasonData.get(server).pendingItems.remove(p.getUUID()); // the used-up ticket's item (a test player is never online)
            h.assertTrue(returned != null && returned.size() == 1 && t.serial.equals(StoreCredit.serialOf(returned.get(0))), "its item comes back");
            p.getInventory().setItem(0, returned.get(0));
            com.mrgregles.bsp_core.credit.CreditLand.end(p, quote, true, claim); // a stale mark from earlier: it must not close the live one
            h.assertTrue(com.mrgregles.bsp_core.credit.CreditLand.busy(p), "only the purchase's own mark ends it");
            com.mrgregles.bsp_core.credit.CreditLand.end(p, buy, false, null);

            var again = com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.plot("Sanctuary"), true);
            com.mrgregles.bsp_core.api.BspBank.charge(server, p.getUUID(), 6_500, "Region Guard", "plot Sanctuary 4", ok -> paid[0] = ok);
            com.mrgregles.bsp_core.credit.CreditLand.end(p, again, true, claim);
            h.assertTrue(paid[0] && Bank.pieces(server, p.getUUID()) == 500 && !com.mrgregles.bsp_core.credit.CreditLand.busy(p), "bought again");

            // an admin gives it up: Region Guard refunds the owner half (3,250); the credit's half (3,000) comes back out of the owner's bank, not the admin's
            fund(server, admin, 50);
            Bank.give(server, p.getUUID(), 3_250, BankTransaction.Type.SOLD, "Region Guard", "refund", r -> {});
            com.mrgregles.bsp_core.credit.CreditLand.abandoned(server, admin.getUUID(), "minecraft:the_nether", claim, 3_250);
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 3_750, "the same claim id in another dimension is another claim");
            com.mrgregles.bsp_core.credit.CreditLand.abandoned(server, admin.getUUID(), dim, claim, 3_250);
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 750 && Bank.pieces(server, admin.getUUID()) == 50, "the credit's share came from the payer: " + Bank.pieces(server, p.getUUID()));
            com.mrgregles.bsp_core.credit.CreditLand.abandoned(server, admin.getUUID(), dim, claim, 3_250);
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 750, "once only");

            // a claim bought from the bank (9,000) and enlarged with credit (1,000): only the credit's half (500) of the 5,000 refund is taken back
            String grown = claim + "_grown";
            CreditTicket small = hand(p, "test_land", 1);
            small.left = 1_000;
            var enlarge = com.mrgregles.bsp_core.credit.CreditLand.begin(p, CreditPlace.plot("Sanctuary"), true);
            com.mrgregles.bsp_core.api.BspBank.charge(server, p.getUUID(), 1_000, "Region Guard", "enlarge", ok -> paid[0] = ok);
            com.mrgregles.bsp_core.credit.CreditLand.end(p, enlarge, true, grown);
            Bank.give(server, p.getUUID(), 5_000, BankTransaction.Type.SOLD, "Region Guard", "refund", r -> {});
            long before = Bank.pieces(server, p.getUUID());
            com.mrgregles.bsp_core.credit.CreditLand.abandoned(server, p.getUUID(), dim, grown, 5_000);
            h.assertTrue(paid[0] && before - Bank.pieces(server, p.getUUID()) == 500, "the credit's share of the refund, not the whole refund: " + (before - Bank.pieces(server, p.getUUID())));
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.credit.CreditLand.usePlayers(null);
            com.mrgregles.bsp_core.credit.CreditLand.clear();
            SeasonData.get(server).pendingItems.remove(p.getUUID());
            StoreCredit.deleteKind(server, "test_land");
        }
    }

    // ------------------------------------------------------------------ task 6: the teleport API

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_api_pays_teleports(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        var TP = com.mrgregles.bsp_core.api.BspStoreCredit.Use.TELEPORT;
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            fund(server, p, 100);
            h.assertTrue(com.mrgregles.bsp_core.api.BspStoreCredit.credit(p, TP) == 0 && com.mrgregles.bsp_core.api.BspStoreCredit.spendable(p, TP) == 100, "no ticket: the bank alone");
            ItemStack ticket = com.mrgregles.bsp_core.api.BspStoreCredit.testTicket(p, 400, TP);
            h.assertTrue(com.mrgregles.bsp_core.api.BspStoreCredit.credit(p, TP) == 400 && com.mrgregles.bsp_core.api.BspStoreCredit.spendable(p, TP) == 500, "bank plus credit");
            h.assertTrue(CreditSpend.available(p, CreditPlace.shop("test_shop")) == 0, "a teleport ticket pays teleports only");

            com.mrgregles.bsp_core.api.BspStoreCredit.Receipt[] r = new com.mrgregles.bsp_core.api.BspStoreCredit.Receipt[1];
            com.mrgregles.bsp_core.api.BspStoreCredit.charge(p, 450, TP, "BSP Teleport", "Warp Mines", x -> r[0] = x);
            h.assertTrue(r[0] != null && r[0].credit() == 400 && r[0].bank() == 50 && r[0].ticketSpent() && r[0].creditLeft() == 0, "credit first, the bank the rest: " + r[0]);
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 50, "the bank paid 50");

            com.mrgregles.bsp_core.api.BspStoreCredit.refund(server, r[0], "Warp Mines (not moved)");
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 100, "the bank's part back");
            com.mrgregles.bsp_core.api.BspStoreCredit.refund(server, r[0], "Warp Mines (not moved) again");
            h.assertTrue(Bank.pieces(server, p.getUUID()) == 100, "a receipt is refunded once only");
            CreditTicket t = StoreCredit.ticket(server, StoreCredit.serialOf(ticket));
            h.assertTrue(t != null && t.left == 400, "the credit back on its ticket");
            SeasonData.get(server).pendingItems.remove(p.getUUID());

            com.mrgregles.bsp_core.api.BspStoreCredit.Receipt[] none = new com.mrgregles.bsp_core.api.BspStoreCredit.Receipt[]{r[0]};
            com.mrgregles.bsp_core.api.BspStoreCredit.charge(p, 10_000, TP, "BSP Teleport", "Warp Far", x -> none[0] = x);
            h.assertTrue(none[0] == null && Bank.pieces(server, p.getUUID()) == 100, "short: null and nothing taken");
            com.mrgregles.bsp_core.api.BspStoreCredit.charge(p, 0, TP, "BSP Teleport", "free", x -> none[0] = x);
            h.assertTrue(none[0] != null && none[0].credit() == 0 && none[0].bank() == 0, "nothing to pay: an empty receipt");
            com.mrgregles.bsp_core.api.BspStoreCredit.refund(server, null, "nothing");
            h.succeed();
        } finally {
            SeasonData.get(server).pendingItems.remove(p.getUUID());
        }
    }

    // ------------------------------------------------------------------ task 7: what the ticket shows

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_ticket_shows_its_kind(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long now = 1_800_000_000_000L;
        try {
            StoreCredit.useClock(() -> now);
            CreditKind k = spendKind(server, "test_look", 10_000, 30L * 24 * 3_600_000L, x -> {
                x.colour = "lime";
                x.mark = "D";
                x.shops.add("no_such_shop");
                x.anyPlot = true;
                x.plots.add("Sanctuary");
                x.teleports = true;
            });
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CreditTicket t = hand(p, "test_look", 0);
            var shown = p.getInventory().getItem(0).getTag().getCompound(StoreCredit.SHOWN);
            h.assertTrue("lime".equals(shown.getString("Colour")) && "D".equals(shown.getString("Mark")), "the look is written for the client");
            h.assertTrue(com.mrgregles.bsp_core.credit.StoreCreditItem.mark(p.getInventory().getItem(0)) == 1f, "D picks the D overlay");
            List<String> places = new java.util.ArrayList<>();
            shown.getList("Places", net.minecraft.nbt.Tag.TAG_STRING).forEach(x -> places.add(x.getAsString()));
            h.assertTrue(places.equals(List.of("no_such_shop", "any Land Office plot", "Sanctuary plots", "teleports")), "places as players read them: " + places);
            h.assertTrue(shown.getLong("Duration") == k.durationMs && !shown.contains("Ends"), "an unused ticket shows its time limit, no end");
            int tint = com.mrgregles.bsp_core.credit.StoreCreditItem.tint(p.getInventory().getItem(0));
            h.assertTrue((tint & 0xFFFFFF) != 0 && tint != com.mrgregles.bsp_core.credit.StoreCreditItem.tint(StoreCredit.fresh("test_look")), "the tint follows the colour (an unminted ticket is purple)");

            t.firstUseMs = now;
            k = StoreCredit.kind(server, "test_look").copy();
            k.mark = "C";
            StoreCredit.saveKind(server, k);
            StoreCredit.scan(p);
            shown = p.getInventory().getItem(0).getTag().getCompound(StoreCredit.SHOWN);
            h.assertTrue(shown.getLong("Ends") == now + k.durationMs && "C".equals(shown.getString("Mark")), "a started ticket shows its end, and follows the kind's look");

            String span = com.mrgregles.bsp_core.credit.StoreCreditItem.span(29L * 24 * 3_600_000L + 4 * 3_600_000L + 5 * 60_000L);
            h.assertTrue("29 days 4 hours".equals(span), span);
            h.assertTrue("1 hour 1 minute".equals(com.mrgregles.bsp_core.credit.StoreCreditItem.span(61 * 60_000L)) && "less than a minute".equals(com.mrgregles.bsp_core.credit.StoreCreditItem.span(30_000L))
                    && "30 days".equals(com.mrgregles.bsp_core.credit.StoreCreditItem.span(30L * 24 * 3_600_000L)), "time wording");
            h.succeed();
        } finally {
            StoreCredit.useClock(null);
            StoreCredit.deleteKind(server, "test_look");
        }
    }

    // ------------------------------------------------------------------ task 8: what the screens are told

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_strip_tells_the_screens(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_strip_" + Long.toHexString(System.nanoTime()), "Strip Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            spendKind(server, "test_strip_week", 700, 7L * 24 * 3_600_000L, k -> {
                k.shops.add(shop.id);
                k.colour = "orange";
                k.mark = "C";
            });
            spendKind(server, "test_strip_land", 300, 0, k -> {
                k.plots.add("Sanctuary");
                k.anyPlot = false;
            });
            var none = com.mrgregles.bsp_core.credit.CreditStrip.forShop(p, shop);
            h.assertTrue(none.places().isEmpty() && none.wants(), "no ticket: nothing to show");
            hand(p, "test_strip_week", 0);
            hand(p, "test_strip_week", 1);
            hand(p, "test_strip_land", 2);
            var packet = com.mrgregles.bsp_core.credit.CreditStrip.forShop(p, shop);
            h.assertTrue(packet.places().size() == 1, "one summary for the shop");
            var sum = packet.places().get(0);
            h.assertTrue("shop".equals(sum.key()) && sum.total() == 1_400 && "orange".equals(sum.colour()) && "C".equals(sum.mark()) && sum.durationMs() == 7L * 24 * 3_600_000L && sum.endsMs() == 0,
                    "both tickets' credit, the first ticket's look and time: " + sum);

            CreditSpend.setWants(p.getUUID(), false);
            h.assertTrue(!com.mrgregles.bsp_core.credit.CreditStrip.forShop(p, shop).wants(), "the switch travels");
            CreditSpend.setWants(p.getUUID(), true);

            var plots = List.of(new com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot("p4", "Sanctuary", 0, 0, 15, 15, 6_000, 0, 0, 0, com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.FOR_SALE, ""),
                    new com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot("p9", "Harbour Row", 20, 0, 35, 15, 2_000, 0, 0, 0, com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.FOR_SALE, ""));
            var office = com.mrgregles.bsp_core.credit.CreditStrip.forOffice(p, plots);
            h.assertTrue(office.places().size() == 1 && office.places().get(0).key().equals(com.mrgregles.bsp_core.credit.CreditStrip.plotKey("SANCTUARY ")) && office.places().get(0).total() == 300,
                    "the office is told about Sanctuary only (no open-ground ticket, none for Harbour Row): " + office.places());

            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.CreditStripPacket.encode(packet, buf);
            var back = com.mrgregles.bsp_core.network.CreditStripPacket.decode(buf);
            h.assertTrue(back.equals(packet), "the packet survives the trip");
            h.succeed();
        } finally {
            CreditSpend.setWants(p.getUUID(), true);
            com.mrgregles.bsp_core.shop.Shops.delete(server, shop.id);
            StoreCredit.deleteKind(server, "test_strip_week");
            StoreCredit.deleteKind(server, "test_strip_land");
        }
    }

    // ------------------------------------------------------------------ task 9: the stamp

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_stamp_on_the_clock_and_the_end(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        long[] now = {1_800_000_000_000L};
        List<com.mrgregles.bsp_core.network.CreditNoticePacket> sent = new java.util.ArrayList<>();
        try {
            StoreCredit.useClock(() -> now[0]);
            StoreCredit.listenNotices((pl, n) -> sent.add(n));
            spendKind(server, "test_stamp", 1_000, 10 * 3_600_000L, k -> {
                k.shops.add("test_shop");
                k.colour = "red";
                k.mark = "D";
            });
            spendKind(server, "test_stamp_free", 1_000, 0, k -> k.shops.add("test_shop_free"));
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            hand(p, "test_stamp_free", 1);
            h.assertTrue(pay(p, 10, CreditPlace.shop("test_shop_free")).ok() && sent.isEmpty(), "a ticket with no time limit starts no clock, so no stamp");
            hand(p, "test_stamp", 0);
            h.assertTrue(pay(p, 200, CreditPlace.shop("test_shop")).ok(), "first purchase");
            h.assertTrue(sent.size() == 1 && !sent.get(0).end() && sent.get(0).pieces() == 800 && "red".equals(sent.get(0).colour()) && "D".equals(sent.get(0).mark())
                    && sent.get(0).endsMs() == now[0] + 10 * 3_600_000L, "the clock stamp: " + sent);
            h.assertTrue(pay(p, 100, CreditPlace.shop("test_shop")).ok() && sent.size() == 1, "only the first purchase stamps");
            now[0] += 10 * 3_600_000L;
            StoreCredit.scan(p);
            h.assertTrue(sent.size() == 2 && sent.get(1).end() && sent.get(1).pieces() == 700, "the end stamp, with what went unspent: " + sent);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.CreditNoticePacket.encode(sent.get(1), buf);
            h.assertTrue(com.mrgregles.bsp_core.network.CreditNoticePacket.decode(buf).equals(sent.get(1)), "the packet survives the trip");
            h.succeed();
        } finally {
            StoreCredit.listenNotices(null);
            StoreCredit.useClock(null);
            StoreCredit.deleteKind(server, "test_stamp");
            StoreCredit.deleteKind(server, "test_stamp_free");
        }
    }

    // ------------------------------------------------------------------ task 10: the CREDIT tab

    @GameTest(template = EMPTY, batch = BATCH)
    public void credit_admin_tab_answers_admins_only(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL), admin = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "credit_tab_" + Long.toHexString(System.nanoTime()), "Tab Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        String id = "test_tab_" + Long.toHexString(System.nanoTime()); // the test world keeps tickets of kinds deleted in earlier runs
        try {
            CreditKind k = new CreditKind(id);
            k.name = "Tab Kind";
            k.value = 250;
            k.plots.add("Nowhere Plot");
            com.mrgregles.bsp_core.credit.CreditAdmin.act(player, new com.mrgregles.bsp_core.network.CreditAdminActionPacket(com.mrgregles.bsp_core.network.CreditAdminActionPacket.Action.SAVE, id, k.save()));
            h.assertTrue(StoreCredit.kind(server, id) == null, "a player who is not an admin changes nothing");

            h.assertTrue(!com.mrgregles.bsp_core.credit.CreditAdmin.allowed(player), "a test player is not an admin");
            com.mrgregles.bsp_core.credit.CreditAdmin.act(admin, new com.mrgregles.bsp_core.network.CreditAdminActionPacket(com.mrgregles.bsp_core.network.CreditAdminActionPacket.Action.SAVE, id, k.save()), true);
            h.assertTrue(StoreCredit.kind(server, id) != null && StoreCredit.kind(server, id).value == 250, "an admin's save goes through the same rules");
            CreditKind bad = k.copy();
            bad.value = -5;
            com.mrgregles.bsp_core.credit.CreditAdmin.act(admin, new com.mrgregles.bsp_core.network.CreditAdminActionPacket(com.mrgregles.bsp_core.network.CreditAdminActionPacket.Action.SAVE, id, bad.save()), true);
            h.assertTrue(StoreCredit.kind(server, id).value == 250, "a bad value is refused");

            hand(player, id, 0);
            hand(player, id, 1);
            var data = com.mrgregles.bsp_core.credit.CreditAdmin.data(admin, "hello", true);
            int at = -1;
            for (int n = 0; n < data.kinds().size(); n++) {
                if (id.equals(data.kinds().get(n).getString("Id"))) at = n;
            }
            h.assertTrue(at >= 0 && data.given().get(at) == 2, "the tab lists the kind with its 2 tickets");
            h.assertTrue(data.shops().stream().anyMatch(x -> x.id().equals(shop.id) && x.name().equals("Tab Shop")), "and the shops");
            h.assertTrue(data.plots().stream().anyMatch(x -> x.name().equals("Nowhere Plot") && x.count() == 0), "and a saved plot name this server does not sell, with no count");

            var list = com.mrgregles.bsp_core.credit.CreditAdmin.tickets(admin, id);
            h.assertTrue(list.total() == 2 && list.rows().size() == 2 && list.rows().stream().allMatch(r -> r.left() == 250 && !r.voided()), "the ticket list: " + list.rows());
            String serial = list.rows().get(0).serial();
            com.mrgregles.bsp_core.credit.CreditAdmin.act(admin, new com.mrgregles.bsp_core.network.CreditAdminActionPacket(com.mrgregles.bsp_core.network.CreditAdminActionPacket.Action.VOID, serial, new net.minecraft.nbt.CompoundTag()), true);
            h.assertTrue(StoreCredit.ticket(server, serial).voided, "VOID voids it");

            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.CreditAdminPacket.encode(data, buf);
            var back = com.mrgregles.bsp_core.network.CreditAdminPacket.decode(buf);
            int shopsSent = Math.min(com.mrgregles.bsp_core.network.CreditAdminPacket.MAX_SHOPS, data.shops().size()); // the test world keeps every test's shops
            h.assertTrue(back.kinds().size() == Math.min(com.mrgregles.bsp_core.network.CreditAdminPacket.MAX_KINDS, data.kinds().size()) && back.shops().equals(data.shops().subList(0, shopsSent))
                    && back.plots().equals(data.plots().subList(0, Math.min(com.mrgregles.bsp_core.network.CreditAdminPacket.MAX_PLOTS, data.plots().size()))) && back.message().equals("hello"), "the tab's packet survives the trip (up to its caps)");
            buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.CreditTicketsPacket.encode(list, buf);
            h.assertTrue(com.mrgregles.bsp_core.network.CreditTicketsPacket.decode(buf).equals(list), "and the ticket list's");
            h.succeed();
        } finally {
            com.mrgregles.bsp_core.shop.Shops.delete(server, shop.id);
            StoreCredit.deleteKind(server, id);
        }
    }
}
