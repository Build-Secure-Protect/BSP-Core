package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.BankLedger;
import com.mrgregles.bsp_core.bank.Refunds;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.storage.LaterMessages;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for the Bank & Market admin page's bookkeeping (task 1): every log row has an id of its own that survives a save, rows
 * saved before ids are numbered once, a row remembers how much of it was refunded, and a line for an offline player waits for their
 * next login on a server without network storage. Batch {@code bank_refund}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BankRefundGameTests {
    private static final String EMPTY = "empty", BATCH = "bank_refund";

    @GameTest(template = EMPTY, batch = BATCH)
    public void every_log_row_has_an_id_of_its_own_that_survives_a_save(GameTestHelper h) {
        BankLedger ledger = new BankLedger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ledger.account(a);
        ledger.account(b); // only players with an account are saved
        long now = System.currentTimeMillis(); // rows older than bank.logDays are trimmed as they are kept
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            ids.add(ledger.record(a, new BankTransaction(now + i, BankTransaction.Type.BOUGHT, "Spawn Shop", "Oak Log x64", -320, -1, 0, "local")).id());
            ids.add(ledger.record(b, new BankTransaction(now + i, BankTransaction.Type.PAID, "Kestrel", "", -10, -1, 0, "local")).id());
        }
        h.assertTrue(ids.size() == 10 && !ids.contains(0L), "ten rows, ten different ids, none 0: " + ids);
        long first = ledger.log(a).get(4).id();
        BankLedger back = BankLedger.load(ledger.save(new CompoundTag()));
        h.assertTrue(back.find(a, first) != null && back.find(a, first).pieces() == -320, "found by id after a save");
        long next = back.record(a, new BankTransaction(now + 10, BankTransaction.Type.FEE, "Shatter Bank", "", 0, 0, -1, "local")).id();
        h.assertFalse(ids.contains(next), "a row after the save never takes an old number");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void rows_saved_before_ids_are_numbered_once_on_load(GameTestHelper h) {
        BankLedger old = new BankLedger();
        UUID a = UUID.randomUUID();
        old.account(a);
        long now = System.currentTimeMillis();
        old.record(a, new BankTransaction(now, BankTransaction.Type.GRANT, "Shatter Bank", "welcome", 1000, -1, 0, "local"));
        old.record(a, new BankTransaction(now + 1, BankTransaction.Type.BOUGHT, "Spawn Shop", "", -50, -1, 0, "local"));
        CompoundTag tag = old.save(new CompoundTag());
        tag.remove("NextId");
        for (var acc : tag.getList("Accounts", 10)) { // as an older BSP-Core wrote them: no ids
            for (var row : ((CompoundTag) acc).getList("Log", 10)) {
                ((CompoundTag) row).remove("Id");
            }
        }
        BankLedger back = BankLedger.load(tag);
        var log = back.log(a);
        h.assertTrue(log.size() == 2 && log.get(0).id() != 0 && log.get(1).id() != 0 && log.get(0).id() != log.get(1).id(), "both rows numbered");
        h.assertTrue(log.get(1).id() < log.get(0).id(), "oldest first, so the numbers run with time");
        long[] firstIds = {log.get(0).id(), log.get(1).id()};
        BankLedger again = BankLedger.load(back.save(new CompoundTag()));
        h.assertTrue(again.log(a).get(0).id() == firstIds[0] && again.log(a).get(1).id() == firstIds[1], "numbered once: the same ids after another load");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_row_remembers_how_much_of_it_was_refunded(GameTestHelper h) {
        BankLedger ledger = new BankLedger();
        UUID a = UUID.randomUUID();
        ledger.account(a);
        long now = System.currentTimeMillis();
        BankTransaction conv = ledger.record(a, new BankTransaction(now, BankTransaction.Type.CONVERT, "Shatter Bank", "converted 2 Gold coins", 2000, 1, -2, "local"));
        h.assertTrue(conv.piecesLeft() == 2000 && conv.coinsLeft() == 2, "nothing refunded yet");
        h.assertTrue(ledger.replace(a, conv.refunded(500, 1)), "the row is in the log");
        BankTransaction after = BankLedger.load(ledger.save(new CompoundTag())).find(a, conv.id());
        h.assertTrue(after != null && after.refundedPieces() == 500 && after.refundedCoins() == 1 && after.piecesLeft() == 1500 && after.coinsLeft() == 1,
                "500 pieces and 1 coin refunded, kept through a save");
        BankTransaction refund = new BankTransaction(now + 1, BankTransaction.Type.REFUND, "admin", "refund", -500, 1, 1, "local").refundOf(conv.id());
        BankTransaction kept = BankLedger.load(ledger.save(new CompoundTag())).record(a, refund);
        h.assertTrue(kept.refundOf() == conv.id() && kept.type() == BankTransaction.Type.REFUND, "a REFUND row points at its row");
        h.assertFalse(ledger.replace(UUID.randomUUID(), conv), "no such player: nothing replaced");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_line_for_an_offline_player_waits_for_their_next_login(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID away = UUID.randomUUID();
        if (NetworkStorage.enabled()) {
            h.succeed(); // on a network the database keeps it; this test is for a single server
            return;
        }
        NetworkStorage.tellOrKeep(server, away, Component.literal("An admin refunded your Oak Log purchase from 18 Nov: +320 pieces."));
        LaterMessages kept = LaterMessages.get(server);
        h.assertTrue(kept.waiting(away) == 1, "kept for the next login");
        var lines = kept.take(away);
        h.assertTrue(lines.size() == 1 && lines.get(0).getString().contains("+320 pieces") && kept.waiting(away) == 0, "shown once, then gone");
        for (int i = 0; i < LaterMessages.MAX + 5; i++) {
            kept.add(away, Component.literal("line " + i));
        }
        h.assertTrue(kept.waiting(away) == LaterMessages.MAX, "at most " + LaterMessages.MAX + " kept");
        LaterMessages back = LaterMessages.load(kept.save(new CompoundTag()));
        var all = back.take(away);
        h.assertTrue(all.size() == LaterMessages.MAX && all.get(0).getString().equals("line 5"), "saved, oldest dropped first: " + all.get(0).getString());
        kept.take(away);
        h.succeed();
    }

    // ------------------------------------------------------------------ task 2: the refund rules (single server)

    private static UUID account(MinecraftServer server, String name, long pieces) {
        UUID id = UUID.randomUUID();
        BankAccount a = BankLedger.get(server).account(id);
        a.name = name;
        a.pieces = pieces;
        BankLedger.get(server).setDirty();
        return id;
    }

    private static BankTransaction row(MinecraftServer server, UUID id, BankTransaction.Type type, String with, String detail, long pieces, int tier, int coins) {
        return BankLedger.get(server).record(id, new BankTransaction(System.currentTimeMillis(), type, with, detail, pieces, tier, coins, "local"));
    }

    private static Refunds.Result refund(MinecraftServer server, UUID id, long rowId, long pieces, int coins, boolean other) {
        Refunds.Result[] out = {null};
        Refunds.refund(server, new Refunds.Ask(id, rowId, pieces, coins, other, "Tester (test)"), r -> out[0] = r);
        return out[0]; // a single server answers at once
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_purchase_refunds_in_full_or_in_parts_but_never_twice(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        UUID wren = account(server, "Wren_t1", 1_000);
        BankTransaction buy = row(server, wren, BankTransaction.Type.BOUGHT, "Spawn Shop", "Oak Log x64", -320, -1, 0);
        Refunds.Result part = refund(server, wren, buy.id(), 100, 0, false);
        h.assertTrue(part != null && part.ok() && Bank.pieces(server, wren) == 1_100, "part of it: +100 pieces");
        h.assertTrue(part.refundRow().type() == BankTransaction.Type.REFUND && part.refundRow().refundOf() == buy.id() && part.refundRow().pieces() == 100, "a REFUND row naming the row");
        h.assertTrue(refund(server, wren, buy.id(), 221, 0, false).key().equals("too_much"), "only 220 is left");
        h.assertTrue(refund(server, wren, buy.id(), 220, 0, false).ok() && Bank.pieces(server, wren) == 1_320, "the rest of it");
        h.assertTrue(refund(server, wren, buy.id(), 1, 0, false).key().equals("too_much"), "never more than the row");
        h.assertTrue(BankLedger.get(server).find(wren, buy.id()).piecesLeft() == 0, "the row shows it all refunded");
        h.assertTrue(LaterMessages.get(server).waiting(wren) == 2, "an offline player gets each line at their next login");
        h.assertTrue(refund(server, wren, 999_999_999L, 1, 0, false).key().equals("no_row"), "no such row");
        h.assertTrue(refund(server, wren, buy.id(), 0, 0, false).key().equals("nothing"), "nothing asked");
        BankTransaction fresh = row(server, wren, BankTransaction.Type.BOUGHT, "Spawn Shop", "Iron Ingot x16", -640, -1, 0);
        h.assertTrue(refund(server, wren, fresh.id(), 0, 1, false).key().equals("no_coins"), "a purchase moved no coins");
        h.assertTrue(refund(server, wren, fresh.id(), 1, 0, true).key().equals("not_payment"), "only a payment has another player");
        LaterMessages.get(server).take(wren);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_gain_is_taken_back_only_while_the_player_has_it(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        UUID wren = account(server, "Wren_t2", 50);
        BankTransaction sale = row(server, wren, BankTransaction.Type.SOLD, "Spawn Shop", "Wheat x32", 96, -1, 0);
        h.assertTrue(refund(server, wren, sale.id(), 96, 0, false).key().equals("short") && Bank.pieces(server, wren) == 50, "50 pieces cannot give 96 back: nothing changes");
        h.assertTrue(BankLedger.get(server).find(wren, sale.id()).piecesLeft() == 96, "the row is untouched");
        BankLedger.get(server).account(wren).pieces = 200;
        Refunds.Result r = refund(server, wren, sale.id(), 96, 0, false);
        h.assertTrue(r.ok() && Bank.pieces(server, wren) == 104 && r.refundRow().pieces() == -96, "taken back: 200 - 96");
        LaterMessages.get(server).take(wren);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_conversion_gives_coins_back_and_takes_pieces_back_as_staff_choose(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        UUID wren = account(server, "Wren_t3", 2_500);
        int gold = CoinTier.values().length > 1 ? 1 : 0;
        BankTransaction conv = row(server, wren, BankTransaction.Type.CONVERT, "Shatter Bank", "converted 2 Gold coins", 2_000, gold, -2);
        Refunds.Result coinsOnly = refund(server, wren, conv.id(), 0, 2, false);
        h.assertTrue(coinsOnly.ok() && BankLedger.get(server).account(wren).coins[gold] == 2 && Bank.pieces(server, wren) == 2_500, "the coins come back, the pieces stay");
        Refunds.Result piecesOnly = refund(server, wren, conv.id(), 2_000, 0, false);
        h.assertTrue(piecesOnly.ok() && Bank.pieces(server, wren) == 500, "and later the pieces it made are taken back");
        BankTransaction dep = row(server, wren, BankTransaction.Type.DEPOSIT, "Shatter Bank", "banked 5 Copper coins", 0, 0, 5);
        h.assertTrue(refund(server, wren, dep.id(), 0, 5, false).key().equals("own_coins"), "a deposit only moved her own coins: nothing to refund");
        BankTransaction wd = row(server, wren, BankTransaction.Type.WITHDRAW, "Shatter Bank", "took out 1 Copper coin", 0, 0, -1);
        h.assertTrue(refund(server, wren, wd.id(), 0, 1, false).key().equals("own_coins"), "nor a withdrawal: she still has the coin, so it never comes back twice");
        BankTransaction gift = row(server, wren, BankTransaction.Type.ADMIN, "admin", "5 Copper coins", 0, 0, 5);
        h.assertTrue(refund(server, wren, gift.id(), 0, 5, false).key().equals("short"), "no Copper coins in the bank: an admin's gift of coins cannot be taken back");
        BankLedger.get(server).account(wren).coins[0] = 7;
        h.assertTrue(refund(server, wren, gift.id(), 0, 5, false).ok() && BankLedger.get(server).account(wren).coins[0] == 2, "with them, five go");
        LaterMessages.get(server).take(wren);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_payment_can_be_reversed_on_both_players(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        String tag = UUID.randomUUID().toString().substring(0, 8); // the test world keeps its bank between runs: names new each run
        UUID wren = account(server, "W" + tag, 2_000), kes = account(server, "K" + tag, 0);
        BankResultHolder paid = new BankResultHolder();
        Bank.transfer(server, wren, kes, 1_500, r -> paid.ok = r.ok());
        h.assertTrue(paid.ok && Bank.pieces(server, kes) == 1_500, "the payment went through");
        BankTransaction out = BankLedger.get(server).log(wren).get(0), in = BankLedger.get(server).log(kes).get(0);
        h.assertTrue(out.type() == BankTransaction.Type.PAID && in.type() == BankTransaction.Type.RECEIVED, "both rows");
        BankLedger.get(server).account(kes).pieces = 1_000;
        h.assertTrue(refund(server, wren, out.id(), 1_500, 0, true).key().equals("other_short") && Bank.pieces(server, wren) == 500, "Kestrel spent some: nothing changes");
        BankLedger.get(server).account(kes).pieces = 1_500;
        h.assertTrue(refund(server, wren, out.id(), 1_500, 0, true).ok(), "reversed on both");
        h.assertTrue(Bank.pieces(server, wren) == 2_000 && Bank.pieces(server, kes) == 0, "back where they were");
        h.assertTrue(BankLedger.get(server).find(kes, in.id()).piecesLeft() == 0, "Kestrel's row is marked too, so it cannot be reversed again from their side");
        h.assertTrue(BankLedger.get(server).log(kes).get(0).type() == BankTransaction.Type.REFUND && LaterMessages.get(server).waiting(kes) >= 1, "Kestrel has a REFUND row and a line waiting");
        // a second payment, refunded first on the receiver's side alone: reversing it later from the payer's side cannot charge the receiver again
        BankLedger.get(server).account(wren).pieces = 2_000;
        Bank.transfer(server, wren, kes, 100, r -> paid.ok = r.ok());
        BankTransaction out2 = BankLedger.get(server).log(wren).get(0), in2 = BankLedger.get(server).log(kes).get(0);
        h.assertTrue(refund(server, kes, in2.id(), 100, 0, false).ok() && Bank.pieces(server, kes) == 0, "Kestrel's side refunded alone");
        h.assertTrue(refund(server, wren, out2.id(), 100, 0, true).key().equals("other_row") && Bank.pieces(server, wren) == 1_900, "her row's other side is used up: nothing changes");
        BankTransaction lone = row(server, wren, BankTransaction.Type.PAID, "N" + tag, "", -10, -1, 0);
        String key = refund(server, wren, lone.id(), 10, 0, true).key(); // an offline-mode server makes up a profile for any name
        h.assertTrue(key.equals("other_unknown") || key.equals("other_row"), "a player with no row of that payment is never charged: " + key);
        h.assertTrue(refund(server, wren, lone.id(), 10, 0, false).ok(), "but the payer alone can still be refunded");
        LaterMessages.get(server).take(wren);
        LaterMessages.get(server).take(kes);
        h.succeed();
    }

    private static final class BankResultHolder {
        boolean ok;
    }

    // ------------------------------------------------------------------ task 3: staff cancels on the market

    @GameTest(template = EMPTY, batch = "market_staff")
    public void staff_take_a_players_lots_and_requests_off_the_market_and_the_player_hears_of_it(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        com.mrgregles.bsp_core.market.MarketLedger lots = new com.mrgregles.bsp_core.market.MarketLedger();
        com.mrgregles.bsp_core.market.OrderLedger orders = new com.mrgregles.bsp_core.market.OrderLedger();
        var oldMarket = com.mrgregles.bsp_core.market.Market.useStore(new com.mrgregles.bsp_core.market.LocalMarket(lots));
        var oldOrders = com.mrgregles.bsp_core.market.BuyOrders.useStore(new com.mrgregles.bsp_core.market.LocalOrders(orders));
        try {
            String tag = UUID.randomUUID().toString().substring(0, 8);
            UUID sella = UUID.randomUUID(), other = UUID.randomUUID();
            Bank.ensure(server, sella, "S" + tag, g -> {});
            Bank.set(server, sella, 50_000, "gametest", r -> {}); // the lot's listing tax and the request's 9,600 held
            Bank.ensure(server, other, "O" + tag, g -> {});
            Bank.set(server, other, 10_000, "gametest", r -> {});
            var ingots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 4);
            com.mrgregles.bsp_core.market.MarketResult[] r = new com.mrgregles.bsp_core.market.MarketResult[1];
            com.mrgregles.bsp_core.market.Market.list(server, sella, "S" + tag, java.util.List.of(ingots.copy()), 12_000, x -> r[0] = x);
            com.mrgregles.bsp_core.market.Market.list(server, other, "O" + tag, java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 3)), 10, x -> {});
            h.assertTrue(r[0] != null && r[0].ok(), "listed: " + r[0]);
            com.mrgregles.bsp_core.market.OrderStore.Answer[] a = new com.mrgregles.bsp_core.market.OrderStore.Answer[1];
            com.mrgregles.bsp_core.market.BuyOrders.place(server, sella, "S" + tag, net.minecraft.world.item.Items.DIAMOND, 64, 150, x -> a[0] = x);
            h.assertTrue(a[0] != null && a[0].ok(), "ordered: " + a[0]);
            long afterOrder = Bank.pieces(server, sella);
            h.assertTrue(com.mrgregles.bsp_core.market.Market.lotsBy(server, sella).size() == 1 && com.mrgregles.bsp_core.market.BuyOrders.ordersBy(server, sella).size() == 1,
                    "one lot and one request of hers; the other player's lot is not hers");

            com.mrgregles.bsp_core.market.MarketResult[] c = new com.mrgregles.bsp_core.market.MarketResult[1];
            com.mrgregles.bsp_core.market.Market.cancelByStaff(server, r[0].lot().id, "Tester (panel)", x -> c[0] = x);
            h.assertTrue(c[0] != null && c[0].ok() && com.mrgregles.bsp_core.market.Market.lotsBy(server, sella).isEmpty(), "the lot is off the market");
            var box = com.mrgregles.bsp_core.market.Market.collectBox(server, sella);
            h.assertTrue(box.size() == 1 && net.minecraft.world.item.ItemStack.matches(box.get(0), ingots), "the items wait in her Collect box: " + box);

            com.mrgregles.bsp_core.market.OrderStore.Answer[] oc = new com.mrgregles.bsp_core.market.OrderStore.Answer[1];
            com.mrgregles.bsp_core.market.BuyOrders.cancelByStaff(server, a[0].order().id, "Tester (panel)", x -> oc[0] = x);
            h.assertTrue(oc[0] != null && oc[0].ok() && Bank.pieces(server, sella) == afterOrder + 64 * 150L, "the held 9,600 pieces are back in her bank");
            h.assertTrue(LaterMessages.get(server).waiting(sella) == 2, "she is told of both at her next login");
            LaterMessages.get(server).take(sella);
            com.mrgregles.bsp_core.market.MarketResult[] gone = new com.mrgregles.bsp_core.market.MarketResult[1];
            com.mrgregles.bsp_core.market.Market.cancelByStaff(server, r[0].lot().id, "Tester (panel)", x -> gone[0] = x);
            h.assertTrue(gone[0] != null && gone[0].key().equals("gone"), "a lot already off the market: gone");
        } finally {
            com.mrgregles.bsp_core.market.Market.useStore(oldMarket);
            com.mrgregles.bsp_core.market.BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ task 4: the same through the public api, as the staff web panel calls it

    @GameTest(template = EMPTY, batch = "market_staff")
    public void the_staff_api_reads_an_account_refunds_a_row_and_cancels_market_entries(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        var lots = new com.mrgregles.bsp_core.market.MarketLedger();
        var orders = new com.mrgregles.bsp_core.market.OrderLedger();
        var oldMarket = com.mrgregles.bsp_core.market.Market.useStore(new com.mrgregles.bsp_core.market.LocalMarket(lots));
        var oldOrders = com.mrgregles.bsp_core.market.BuyOrders.useStore(new com.mrgregles.bsp_core.market.LocalOrders(orders));
        try {
            String name = "A" + UUID.randomUUID().toString().substring(0, 8);
            UUID id = UUID.randomUUID();
            Bank.ensure(server, id, name, g -> {});
            Bank.set(server, id, 30_000, "gametest", r -> {});
            BankLedger.get(server).account(id).coins[0] = 4;
            com.mrgregles.bsp_core.api.BspBank.Named[] who = {null};
            com.mrgregles.bsp_core.api.BspBank.findPlayer(server, name, n -> who[0] = n);
            h.assertTrue(who[0] != null && who[0].id().equals(id) && who[0].name().equals(name), "found by name");
            com.mrgregles.bsp_core.api.BspBank.Account[] acc = {null};
            com.mrgregles.bsp_core.api.BspBank.account(server, id, a -> acc[0] = a);
            h.assertTrue(acc[0] != null && acc[0].known() && acc[0].pieces() == 30_000 && acc[0].coins().get(0) == 4
                    && acc[0].coins().size() == com.mrgregles.bsp_core.api.BspCoins.tiers().size(), "pieces and the bank's coins by tier: " + acc[0]);

            BankTransaction buy = BankLedger.get(server).record(id, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.BOUGHT, "Spawn Shop", "Oak Log x64", -320, -1, 0, "local"));
            List<com.mrgregles.bsp_core.api.BspBank.Row> rows = new java.util.ArrayList<>();
            com.mrgregles.bsp_core.api.BspBank.log(server, id, rows::addAll);
            var top = rows.get(0);
            h.assertTrue(top.id() == buy.id() && top.type().equals("BOUGHT") && top.pieces() == -320 && top.coinTier() == -1 && top.piecesLeft() == 320 && !top.payment(),
                    "the row as the panel sees it: " + top);
            String[] key = {null};
            com.mrgregles.bsp_core.api.BspBank.refund(server, id, buy.id(), 120, 0, false, "Tester (panel)", k -> key[0] = k);
            h.assertTrue("ok".equals(key[0]) && Bank.pieces(server, id) == 30_120, "refunded through the api: " + key[0]);
            rows.clear();
            com.mrgregles.bsp_core.api.BspBank.log(server, id, rows::addAll);
            h.assertTrue(rows.get(0).type().equals("REFUND") && rows.get(0).refundOf() == buy.id() && rows.get(0).with().equals("Tester (panel)")
                    && rows.get(1).piecesLeft() == 200, "the REFUND row names the row and the staff member; 200 left");
            com.mrgregles.bsp_core.api.BspBank.refund(server, id, buy.id(), 201, 0, false, "Tester (panel)", k -> key[0] = k);
            h.assertTrue("too_much".equals(key[0]), "never more than is left: " + key[0]);

            com.mrgregles.bsp_core.market.MarketResult[] r = new com.mrgregles.bsp_core.market.MarketResult[1];
            com.mrgregles.bsp_core.market.Market.list(server, id, name, List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA)), 4_500, x -> r[0] = x);
            com.mrgregles.bsp_core.market.OrderStore.Answer[] a = new com.mrgregles.bsp_core.market.OrderStore.Answer[1];
            com.mrgregles.bsp_core.market.BuyOrders.place(server, id, name, net.minecraft.world.item.Items.ANCIENT_DEBRIS, 16, 900, x -> a[0] = x);
            h.assertTrue(r[0].ok() && a[0].ok(), "a lot and an order");
            var myLots = com.mrgregles.bsp_core.api.BspMarket.lotsOf(server, id);
            var myOrders = com.mrgregles.bsp_core.api.BspMarket.ordersOf(server, id);
            h.assertTrue(myLots.size() == 1 && myOrders.size() == 1 && myOrders.get(0).price() == 14_400, "hers, as the panel sees them");
            com.mrgregles.bsp_core.api.BspMarket.Cancelled[] c = {null, null};
            com.mrgregles.bsp_core.api.BspMarket.cancelLot(server, myLots.get(0).id(), "Tester (panel)", x -> c[0] = x);
            com.mrgregles.bsp_core.api.BspMarket.cancelOrder(server, myOrders.get(0).id(), "Tester (panel)", x -> c[1] = x);
            h.assertTrue(c[0].key().equals("ok") && c[1].key().equals("ok") && c[1].piecesBack() == 14_400, "both cancelled; the order's 14,400 back: " + c[1]);
            h.assertTrue(com.mrgregles.bsp_core.api.BspMarket.lotsOf(server, id).isEmpty() && com.mrgregles.bsp_core.api.BspMarket.ordersOf(server, id).isEmpty(), "nothing left on the market");
            com.mrgregles.bsp_core.api.BspMarket.cancelLot(server, myLots.get(0).id(), "Tester (panel)", x -> c[0] = x);
            h.assertTrue(c[0].key().equals("gone"), "a second cancel: gone");
            LaterMessages.get(server).take(id);
        } finally {
            com.mrgregles.bsp_core.market.Market.useStore(oldMarket);
            com.mrgregles.bsp_core.market.BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ task 5: who may act on the page, and its packets

    @GameTest(template = EMPTY, batch = "admin_bank")
    public void only_admins_and_permitted_moderators_may_refund_and_cancel(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var admin = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        var mod = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        var nobody = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        var adminsBefore = new java.util.ArrayList<String>(com.mrgregles.bsp_core.BSPConfig.ADMINS.get());
        var modsBefore = new java.util.ArrayList<String>(com.mrgregles.bsp_core.BSPConfig.MODERATORS.get());
        var sourceBefore = com.mrgregles.bsp_core.admin.Admins.bankPermissionSource;
        try {
            com.mrgregles.bsp_core.BSPConfig.ADMINS.set(List.of(admin.getGameProfile().getName()));
            com.mrgregles.bsp_core.BSPConfig.MODERATORS.set(List.of(mod.getGameProfile().getName()));
            com.mrgregles.bsp_core.admin.Admins.bankPermissionSource = p -> false;
            h.assertTrue(com.mrgregles.bsp_core.admin.Admins.canBank(admin), "an admin may");
            h.assertFalse(com.mrgregles.bsp_core.admin.Admins.canBank(mod), "a moderator without " + com.mrgregles.bsp_core.admin.Admins.BANK_PERMISSION + " may not");
            h.assertFalse(com.mrgregles.bsp_core.admin.Admins.canBank(nobody), "a player who is no moderator may not");
            com.mrgregles.bsp_core.admin.Admins.bankPermissionSource = p -> true;
            h.assertTrue(com.mrgregles.bsp_core.admin.Admins.canBank(mod), "a moderator holding it may");
            h.assertFalse(com.mrgregles.bsp_core.admin.Admins.canBank(nobody), "the permission alone is not enough: moderators only");

            if (!NetworkStorage.enabled()) {
                UUID wren = account(server, "Wren_t5", 1_000);
                BankTransaction buy = row(server, wren, BankTransaction.Type.BOUGHT, "Spawn Shop", "Oak Log x64", -320, -1, 0);
                var ask = new com.mrgregles.bsp_core.network.AdminBankAskPacket(com.mrgregles.bsp_core.network.AdminBankAskPacket.Action.REFUND, wren, buy.id(), 320, 0, false, "");
                String[] key = {null};
                com.mrgregles.bsp_core.admin.AdminBank.perform(server, "Mod", false, ask, k -> key[0] = k);
                h.assertTrue("no_permission".equals(key[0]) && Bank.pieces(server, wren) == 1_000, "refused: nothing changes");
                com.mrgregles.bsp_core.admin.AdminBank.perform(server, "Admin", true, ask, k -> key[0] = k);
                h.assertTrue("ok".equals(key[0]) && Bank.pieces(server, wren) == 1_320, "allowed: refunded");
                var cancel = new com.mrgregles.bsp_core.network.AdminBankAskPacket(com.mrgregles.bsp_core.network.AdminBankAskPacket.Action.CANCEL_LOT, wren, 0, 0, 0, false, "nolot");
                com.mrgregles.bsp_core.admin.AdminBank.perform(server, "Admin", true, cancel, k -> key[0] = k);
                h.assertTrue("gone".equals(key[0]), "a lot that is not this player's: gone");

                com.mrgregles.bsp_core.network.AdminBankDataPacket[] page = {null};
                com.mrgregles.bsp_core.admin.AdminBank.view(server, wren, true, "ok", p -> page[0] = p);
                var p = page[0];
                h.assertTrue(p != null && p.reached() && p.canEdit() && p.pieces() == 1_320 && p.rows().size() >= 2 && p.rows().get(0).type().equals("REFUND") && p.result().equals("ok"),
                        "the page: pieces, the log with the REFUND on top, the answer");
                var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                com.mrgregles.bsp_core.network.AdminBankDataPacket.encode(p, buf);
                var back = com.mrgregles.bsp_core.network.AdminBankDataPacket.decode(buf);
                h.assertTrue(back.player().equals(wren) && back.pieces() == 1_320 && back.rows().size() == p.rows().size() && back.rows().get(1).id() == buy.id()
                        && back.rows().get(1).piecesLeft() == 0 && back.rows().get(0).refundOf() == buy.id() && back.coins().length == p.coins().length, "it crosses the network intact");
                var askBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                com.mrgregles.bsp_core.network.AdminBankAskPacket.encode(ask, askBuf);
                h.assertTrue(com.mrgregles.bsp_core.network.AdminBankAskPacket.decode(askBuf).equals(ask), "the ask too");
                LaterMessages.get(server).take(wren);
            }
        } finally {
            com.mrgregles.bsp_core.BSPConfig.ADMINS.set(adminsBefore);
            com.mrgregles.bsp_core.BSPConfig.MODERATORS.set(modsBefore);
            com.mrgregles.bsp_core.admin.Admins.bankPermissionSource = sourceBefore;
        }
        h.succeed();
    }
}
