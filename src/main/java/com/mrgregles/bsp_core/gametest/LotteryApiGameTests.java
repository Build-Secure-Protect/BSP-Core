package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspLottery;
import com.mrgregles.bsp_core.bank.BankLedger;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.lottery.Lottery;
import com.mrgregles.bsp_core.lottery.LotteryDraw;
import com.mrgregles.bsp_core.lottery.LotteryPage;
import com.mrgregles.bsp_core.lottery.LotteryWin;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * GameTests for {@link BspLottery} (BSP-Server Utilities request SU-BC1). Batch {@code lottery_api}, one test: while its draw
 * runs the pot is locked, which no other test may meet.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class LotteryApiGameTests {
    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "lottery_api")
    public void the_api_sums_up_the_lottery_and_draws_it_as_the_command_does(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var ann = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        var realRanks = Lottery.rankSource;
        Lottery.rankSource = id -> Set.of();
        Lottery.seen(ann); // at least one player is in
        Lottery.rankSource = realRanks;
        LotteryPage.invalidate();
        String name = ann.getGameProfile().getName();
        h.assertTrue(!LotteryDraw.busy() && BspLottery.phase() == BspLottery.Phase.IDLE, "no draw is running before the test: " + BspLottery.phase());

        // an empty pot: the api answers as the command's draw does, and nothing starts
        long held = Market.pot(server);
        if (held > 0) {
            Lottery.takeFromPot(server, held, "test", "empty the pot", r -> {});
        }
        String[] viaApi = new String[1], viaCommand = new String[1];
        BspLottery.draw(server, "Staff", k -> viaApi[0] = k);
        LotteryDraw.start(server, "test", k -> viaCommand[0] = k);
        h.assertTrue("empty".equals(viaApi[0]) && viaApi[0].equals(viaCommand[0]) && !LotteryDraw.busy(), "an empty pot is refused alike: " + viaApi[0] + ", " + viaCommand[0]);

        // the summary after an admin's add
        Lottery.addToPot(server, 1_000, "test", "event prize", r -> {});
        BspLottery.Summary[] s = new BspLottery.Summary[1];
        List<String> names = new ArrayList<>();
        BspLottery.summary(server, x -> s[0] = x);
        BspLottery.entrants(server, names::addAll);
        h.assertTrue(s[0] != null && s[0].pot() == 1_000 && s[0].phase() == BspLottery.Phase.IDLE, "the summary shows the pot after the add: " + s[0]);
        h.assertTrue(names.contains(name) && s[0].entrants() == names.size(), "the player seen is in, and the count matches the names: " + s[0].entrants() + ", " + names.size());
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        h.assertTrue(names.equals(sorted), "the names are in alphabetical order");
        List<LotteryWin> before = new ArrayList<>();
        Lottery.wins(server, before::addAll);
        h.assertTrue(s[0].lastWins().size() == before.size(), "the summary has every past win kept: " + s[0].lastWins().size() + " of " + before.size());
        for (int i = 0; i < before.size(); i++) {
            LotteryWin w = before.get(i);
            BspLottery.Win x = s[0].lastWins().get(i);
            h.assertTrue(x.name().equals(w.name()) && x.pieces() == w.pieces() && x.atMs() == w.ms(), "win " + i + " is the same, newest first: " + x);
        }

        // a draw through the api starts, and a second one is busy, through the api and the command alike. The test server ticks
        // without waiting, so the countdown runs with no pause; the pause is put back once the draw ends, or at once on a failure.
        h.assertTrue(BspLottery.connected(), "a single server is always connected");
        LotteryDraw.stepMs = 0;
        try {
            BspLottery.draw(server, "Staff", k -> viaApi[0] = k);
            h.assertTrue("ok".equals(viaApi[0]) && LotteryDraw.busy() && BspLottery.phase() == BspLottery.Phase.COUNTING, "the draw starts: " + viaApi[0] + ", " + BspLottery.phase());
            BspLottery.draw(server, "Staff", k -> viaApi[0] = k);
            LotteryDraw.start(server, "test", k -> viaCommand[0] = k);
            h.assertTrue("busy".equals(viaApi[0]) && "busy".equals(viaCommand[0]), "a second draw is busy either way: " + viaApi[0] + ", " + viaCommand[0]);
            BspLottery.summary(server, x -> s[0] = x);
            h.assertTrue(s[0].phase() == BspLottery.Phase.COUNTING, "the summary shows the countdown: " + s[0].phase());
        } catch (RuntimeException e) {
            LotteryDraw.stepMs = 1000;
            throw e;
        }

        h.succeedWhen(() -> {
            if (!LotteryDraw.busy()) {
                LotteryDraw.stepMs = 1000;
            }
            h.assertTrue(!LotteryDraw.busy() && BspLottery.phase() == BspLottery.Phase.IDLE, "the draw has finished");
            List<LotteryWin> after = new ArrayList<>();
            Lottery.wins(server, after::addAll);
            h.assertTrue(!after.isEmpty() && (before.isEmpty() || !after.get(0).equals(before.get(0))), "the draw is in the list of wins");
            LotteryWin win = after.get(0);
            h.assertTrue(win.pieces() == 1_000 && Market.pot(server) == 0, "the winner took the whole pot: " + win);
            var row = BankLedger.get(server).log(win.id()).get(0);
            h.assertTrue(row.type() == BankTransaction.Type.LOTTERY && row.pieces() == win.pieces(), "the winner's bank log shows the win: " + row);
            BspLottery.Summary[] end = new BspLottery.Summary[1];
            LotteryPage.invalidate();
            BspLottery.summary(server, x -> end[0] = x);
            h.assertTrue(end[0] != null && end[0].pot() == 0 && !end[0].lastWins().isEmpty() && end[0].lastWins().get(0).name().equals(win.name())
                    && end[0].lastWins().get(0).pieces() == 1_000, "the summary's newest win is this draw: " + end[0]);
        });
    }
}
