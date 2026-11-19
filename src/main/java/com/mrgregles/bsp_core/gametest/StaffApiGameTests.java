package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBounties;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.BankLedger;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.bank.Refunds;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.LocalOrders;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketResult;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.market.OrderLedger;
import com.mrgregles.bsp_core.market.OrderStore;
import com.mrgregles.bsp_core.storage.LaterMessages;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GameTests for Server Utilities' request SU-BC3 on the staff apis: the bounty rules in the api, the target refusal on each player
 * of {@code BspBounties.players()}, staff calls that answer "ok" even when telling a player fails, and the extend lines' times.
 * Batch {@code bounty} (a batch of its own moved the chamber_spawn batch onto a jigsaw structure whose spawn override is empty).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class StaffApiGameTests {
    private static final String EMPTY = "empty", BATCH = "bounty";
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS, M = 60_000L;

    private interface Body {
        void run(MinecraftServer server);
    }

    /** A fresh bounty and market store, a fixed clock, nobody staff or protected; everything put back after. */
    private static void run(GameTestHelper h, long[] now, Body body) {
        BountyStore oldB = Bounties.useStore(new LocalBounties(new BountyLedger()));
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        OrderStore oldO = BuyOrders.useStore(new LocalOrders(new OrderLedger()));
        Bounties.useClock(() -> now[0]);
        Bounties.staffCheck = (id, name) -> false;
        Bounties.protectedCheck = id -> false;
        try {
            body.run(h.getLevel().getServer());
        } finally {
            Bounties.useStore(oldB);
            Market.useStore(oldM);
            BuyOrders.useStore(oldO);
            Bounties.useClock(null);
            Bounties.staffCheck = null;
            Bounties.protectedCheck = null;
            NetworkStorage.tellWatch = null;
        }
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h, long pieces) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        Bank.ensure(server, p.getUUID(), p.getGameProfile().getName(), g -> {});
        Bank.set(server, p.getUUID(), pieces, "gametest", r -> {});
        return p;
    }

    private static UUID account(MinecraftServer server, String name, long pieces) {
        UUID id = UUID.randomUUID();
        BankAccount a = BankLedger.get(server).account(id);
        a.name = name;
        a.pieces = pieces;
        BankLedger.get(server).setDirty();
        return id;
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    // ------------------------------------------------------------------ (1) the rules

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_rules_in_the_api_follow_the_config_and_match_what_a_staff_bounty_may_be(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        int minBefore = BSPConfig.BOUNTY_MIN_PIECES.get(), daysBefore = BSPConfig.BOUNTY_MAX_DAYS.get(), notesBefore = BSPConfig.BOUNTY_NOTES_MAX.get();
        int openBefore = BSPConfig.BOUNTY_MAX_OPEN.get(), taxBefore = BSPConfig.MARKET_TAX_PERCENT.get();
        try {
            run(h, new long[]{start}, server -> {
                BSPConfig.BOUNTY_MIN_PIECES.set(2_500);
                BSPConfig.BOUNTY_MAX_DAYS.set(45);
                BSPConfig.BOUNTY_NOTES_MAX.set(12);
                BSPConfig.BOUNTY_MAX_OPEN.set(3);
                BSPConfig.MARKET_TAX_PERCENT.set(7);
                BspBounties.Rules r = BspBounties.rules(server);
                h.assertTrue(r.minPieces() == 2_500 && r.maxDays() == 45 && r.notesMax() == 12 && r.maxOpen() == 3 && r.taxPercent() == 7,
                        "the rules are the config as it is now: " + r);
                h.assertTrue(r.maxDurationMs() == 45 * D && r.minDurationMs() == H && r.maxPieces() == Bounties.MAX_PIECES && r.maxCoins() == Bounties.MAX_COINS
                        && r.maxAccepted() == Bounties.maxAccepted() && r.piecesPerCopper() == Bounties.piecesPerCopper(), "and the fixed limits: " + r);
                for (long v : new long[]{0, 1, 99, 100, 2_500, 12_345, 1_000_000}) {
                    h.assertTrue(r.fee(v) == Bounties.fee(v), "the fee is the one a player pays, for " + v + ": " + r.fee(v) + " vs " + Bounties.fee(v));
                }
                UUID target = UUID.randomUUID();
                BspBounties.Result[] out = new BspBounties.Result[1];
                BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, r.minPieces() - 1, 0, 0, D, "", x -> out[0] = x);
                h.assertTrue(out[0].key().equals("price"), "under minPieces is refused: " + out[0].key());
                BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, r.minPieces(), 0, 0, r.maxDurationMs() + 1, "", x -> out[0] = x);
                h.assertTrue(out[0].key().equals("time"), "past maxDays is refused: " + out[0].key());
                BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, r.minPieces(), 0, 0, r.minDurationMs() - 1, "", x -> out[0] = x);
                h.assertTrue(out[0].key().equals("time"), "under an hour is refused: " + out[0].key());
                BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, r.minPieces(), 0, 0, r.maxDurationMs(), repeat('n', 30), x -> out[0] = x);
                h.assertTrue(out[0].ok() && out[0].bounty().endsMs() == start + 45 * D, "minPieces for maxDays is taken (45 days, more than the default 30): " + out[0]);
                h.assertTrue(out[0].bounty().notes().length() == 12, "notes are cut to notesMax: " + out[0].bounty().notes());
                BspBounties.add(server, "Mod (panel)", target, "Wanted", 0, 1, 0, 0, r.minDurationMs(), "", x -> out[0] = x);
                h.assertTrue(out[0].ok(), "one coin for an hour is taken: " + out[0].key());
            });
        } finally {
            BSPConfig.BOUNTY_MIN_PIECES.set(minBefore);
            BSPConfig.BOUNTY_MAX_DAYS.set(daysBefore);
            BSPConfig.BOUNTY_NOTES_MAX.set(notesBefore);
            BSPConfig.BOUNTY_MAX_OPEN.set(openBefore);
            BSPConfig.MARKET_TAX_PERCENT.set(taxBefore);
        }
    }

    // ------------------------------------------------------------------ (2) the refusal on each player

    @GameTest(template = EMPTY, batch = BATCH)
    public void each_player_from_the_search_says_whether_a_bounty_may_be_placed_on_them(GameTestHelper h) {
        run(h, new long[]{1_800_000_000_000L}, server -> {
            String tag = "Sa" + Long.toString(System.nanoTime() % 100_000_000L, 36);
            UUID plain = account(server, tag + "Plain", 0), staff = account(server, tag + "Staff", 0), young = account(server, tag + "Young", 0);
            Bounties.staffCheck = (id, name) -> id.equals(staff);
            Bounties.protectedCheck = id -> id.equals(young);
            List<BspBounties.Player>[] got = new List[1];
            BspBounties.players(server, tag.toLowerCase(Locale.ROOT), x -> got[0] = x);
            h.assertTrue(got[0] != null && got[0].size() == 3, "the three players: " + got[0]);
            for (BspBounties.Player p : got[0]) {
                String want = p.id().equals(staff) ? "staff" : p.id().equals(young) ? "protected" : null;
                h.assertTrue(java.util.Objects.equals(p.refusal(), want), p.name() + " should carry " + want + ": " + p.refusal());
                h.assertTrue(java.util.Objects.equals(p.refusal(), BspBounties.targetRefusal(server, p.id(), p.name())), "the same answer as targetRefusal for " + p.name());
            }
            h.assertTrue(got[0].stream().anyMatch(p -> p.id().equals(plain) && p.refusal() == null), "a fair target has none");
        });
    }

    // ------------------------------------------------------------------ (3) answering when telling fails

    @GameTest(template = EMPTY, batch = BATCH)
    public void staff_calls_answer_ok_when_telling_the_player_fails(GameTestHelper h) {
        MinecraftServer srv = h.getLevel().getServer();
        if (NetworkStorage.enabled()) {
            h.succeed();
            return;
        }
        long start = 1_800_000_000_000L;
        run(h, new long[]{start}, server -> {
            String tag = Long.toString(System.nanoTime() % 100_000_000L, 36);
            int[] tried = {0};
            NetworkStorage.tellWatch = (id, line) -> {
                tried[0]++;
                throw new IllegalStateException("gametest: telling fails");
            };
            // a refund: the money moves and the caller hears ok
            UUID wren = account(server, "W" + tag, 1_000);
            BankTransaction buy = BankLedger.get(server).record(wren, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.BOUGHT, "Spawn Shop", "Oak Log x64",
                    -320, -1, 0, "local"));
            Refunds.Result[] rr = {null};
            Refunds.refund(server, new Refunds.Ask(wren, buy.id(), 320, 0, false, "Tester (panel)"), x -> rr[0] = x);
            h.assertTrue(rr[0] != null && rr[0].ok() && Bank.pieces(server, wren) == 1_320, "the refund answers ok with the money moved: " + (rr[0] == null ? null : rr[0].key()));
            h.assertTrue(tried[0] == 1 && LaterMessages.get(server).waiting(wren) == 0, "telling was tried once and failed: " + tried[0]);
            // a payment refunded on both players: both lines fail, still ok
            UUID payer = account(server, "Pr" + tag, 1_000), payee = account(server, "Pe" + tag, 1_000);
            BankTransaction paid = BankLedger.get(server).record(payer, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.PAID, "Pe" + tag, "", -200, -1, 0, "local"));
            BankLedger.get(server).record(payee, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.RECEIVED, "Pr" + tag, "", 200, -1, 0, "local"));
            tried[0] = 0;
            Refunds.refund(server, new Refunds.Ask(payer, paid.id(), 200, 0, true, "Tester (panel)"), x -> rr[0] = x);
            h.assertTrue(rr[0] != null && rr[0].ok() && Bank.pieces(server, payer) == 1_200 && Bank.pieces(server, payee) == 800 && tried[0] == 2,
                    "a payment refunded on both answers ok: " + (rr[0] == null ? null : rr[0].key()) + ", tried " + tried[0]);
            // bounties: add (the target is told), extend and remove with a refund (the placer is told)
            ServerPlayer placer = player(h, 100_000);
            UUID target = UUID.randomUUID();
            Bounties.Result[] pr = new Bounties.Result[1];
            tried[0] = 0;
            Bounties.place(server, placer, new Bounties.Ask(target, "Wanted", Bounty.PIECES, 10_000, 0, 0, D, ""), x -> pr[0] = x);
            h.assertTrue(pr[0] != null && pr[0].key().equals("ok") && tried[0] == 1, "a player's bounty answers ok: " + (pr[0] == null ? null : pr[0].key()));
            BspBounties.Result[] r = new BspBounties.Result[1];
            BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, 5_000, 0, 0, D, "", x -> r[0] = x);
            h.assertTrue(r[0] != null && r[0].ok() && tried[0] == 2, "a staff bounty answers ok: " + r[0]);
            BspBounties.extend(server, pr[0].bounty().id, H, "Mod (panel)", x -> r[0] = x);
            h.assertTrue(r[0] != null && r[0].ok() && r[0].bounty().endsMs() == start + D + H && tried[0] == 3, "an extension answers ok: " + r[0]);
            long bank = Bank.pieces(server, placer.getUUID());
            BspBounties.remove(server, pr[0].bounty().id, true, "Mod (panel)", x -> r[0] = x);
            h.assertTrue(r[0] != null && r[0].ok() && Bank.pieces(server, placer.getUUID()) == bank + 10_000 && tried[0] == 5,
                    "a removal with a refund answers ok with the reward back (placer and target told): " + r[0] + ", tried " + tried[0]);
            // the market: a lot and a request cancelled by staff
            UUID sella = UUID.randomUUID();
            Bank.ensure(server, sella, "S" + tag, g -> {});
            Bank.set(server, sella, 50_000, "gametest", x -> {});
            MarketResult[] lot = new MarketResult[1];
            Market.list(server, sella, "S" + tag, List.of(new ItemStack(Items.IRON_INGOT, 4)), 1_000, x -> lot[0] = x);
            OrderStore.Answer[] order = new OrderStore.Answer[1];
            BuyOrders.place(server, sella, "S" + tag, Items.DIAMOND, 4, 100, x -> order[0] = x);
            h.assertTrue(lot[0] != null && lot[0].ok() && order[0] != null && order[0].ok(), "a lot and a request");
            tried[0] = 0;
            MarketResult[] c = new MarketResult[1];
            Market.cancelByStaff(server, lot[0].lot().id, "Mod (panel)", x -> c[0] = x);
            h.assertTrue(c[0] != null && c[0].ok() && tried[0] == 1, "a lot cancelled by staff answers ok: " + (c[0] == null ? null : c[0].key()));
            OrderStore.Answer[] oc = new OrderStore.Answer[1];
            long before = Bank.pieces(server, sella);
            BuyOrders.cancelByStaff(server, order[0].order().id, "Mod (panel)", x -> oc[0] = x);
            h.assertTrue(oc[0] != null && oc[0].ok() && Bank.pieces(server, sella) == before + 400 && tried[0] == 2, "a request cancelled by staff answers ok with the 400 back");
            LaterMessages.get(srv).take(sella);
        });
    }

    // ------------------------------------------------------------------ (4) the extend lines

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_extension_says_the_time_left_to_the_nearest_hour_and_logs_it_to_the_minute(GameTestHelper h) {
        h.assertTrue(Bounties.spanExact(2 * D + 55 * M).equals("2 d 0 h 55 m"), "exact: " + Bounties.spanExact(2 * D + 55 * M));
        h.assertTrue(Bounties.spanExact(5 * H + 20 * M).equals("5 h 20 m") && Bounties.spanExact(12 * M).equals("12 m"), "under a day as span writes it");
        h.assertTrue(Bounties.spanNearest(2 * D + 55 * M).equals("2 d 1 h"), "nearest: " + Bounties.spanNearest(2 * D + 55 * M));
        h.assertTrue(Bounties.spanNearest(2 * D + 29 * M).equals("2 d 0 h") && Bounties.spanNearest(D + 23 * H + 40 * M).equals("2 d 0 h"), "rounded both ways");
        h.assertTrue(Bounties.spanNearest(23 * H + 59 * M).equals("23 h 59 m"), "under a day keeps its minutes");
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, now, server -> {
            ServerPlayer placer = player(h, 100_000);
            UUID target = UUID.randomUUID();
            Bounties.Result[] pr = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(target, "Wanted", Bounty.PIECES, 10_000, 0, 0, 2 * D, ""), x -> pr[0] = x);
            now[0] = start + 5 * M; // five minutes on, +1 hour: 2 d 0 h 55 m left
            List<Component> told = new ArrayList<>();
            NetworkStorage.tellWatch = (id, line) -> {
                if (id.equals(placer.getUUID())) {
                    told.add(line);
                }
            };
            BspBounties.Result[] r = new BspBounties.Result[1];
            BspBounties.extend(server, pr[0].bounty().id, H, "Mod (panel)", x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].bounty().endsMs() == start + 2 * D + H, "extended by an hour");
            h.assertTrue(told.size() == 1 && told.get(0).getContents() instanceof TranslatableContents t && t.getKey().equals("message.bsp_core.bounty.extended")
                    && String.valueOf(t.getArgs()[1]).equals("2 d 1 h"), "the placer reads 2 d 1 h: " + told);
            String note = Bounties.extendedNote(H, start + 2 * D + H, now[0]);
            h.assertTrue(note.startsWith("by 1 h 0 m, to end ") && note.endsWith("(in 2 d 0 h 55 m)")
                    && note.contains(Refunds.when(start + 2 * D + H)), "the log line says what was added, the end and the time left: " + note);
        });
    }
}
