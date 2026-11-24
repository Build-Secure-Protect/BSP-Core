package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.admin.SeasonReset;
import com.mrgregles.bsp_core.api.event.SeasonEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.PrizeBox;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.kit.KitLedger;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /bsp season reset} (vault note "Season Reset - Brief"): on one server (no database in a GameTest) the season ends with the
 * prizes in the winners' prize compartments, the bank, kits and other records start over, the Safety Deposit Box stays, everyone is
 * sent off, the sister mods hear it, and each player is wiped once at their next settle, keeping this season's totem. In the
 * {@code player_data} batch; every test finishes within its own call, and the reset number is put back, so nothing else in the batch
 * sees a player due a wipe.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class SeasonResetGameTests {
    private static final String BATCH = "player_data";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    /** A player who has played this season: items, ender chest, XP, an effect, a granted totem flag. */
    private static ServerPlayer veteran(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 12));
        p.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        p.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
        p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
        p.setExperienceLevels(30);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000));
        PlayerPersistent.setTotemGranted(p, true);
        PlayerPersistent.setSeasonSeen(p, SeasonData.get(p.server).number);
        PlayerPersistent.get(p).putString("othermod:kept", "yes");
        return p;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_confirm_wants_the_code_shown_to_the_same_admin(GameTestHelper h) {
        String code = SeasonReset.ask("test admin");
        h.assertTrue(code.matches("\\d{4}"), "a four-digit code: " + code);
        h.assertTrue(!SeasonReset.confirmed("someone else", code), "another admin cannot use it");
        h.assertTrue(SeasonReset.confirmed("test admin", code), "the other admin's try left the asker's code as it was");
        h.assertTrue(!SeasonReset.confirmed("test admin", code), "a code counts once");
        code = SeasonReset.ask("test admin");
        String wrong = code.equals("1000") ? "1001" : "1000";
        h.assertTrue(!SeasonReset.confirmed("test admin", wrong), "a wrong code does not count");
        h.assertTrue(!SeasonReset.confirmed("test admin", code), "and it used the ask up");
        code = SeasonReset.ask("test admin");
        h.assertTrue(SeasonReset.confirmed("test admin", " " + code + " "), "the right code within the minute counts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_preview_changes_nothing(GameTestHelper h) {
        MinecraftServer server = server(h);
        SeasonData season = SeasonData.get(server);
        int number = season.number, epoch = season.resetEpoch;
        List<List<String>> got = new ArrayList<>();
        SeasonReset.preview(server, got::add);
        h.assertTrue(got.size() == 1 && got.get(0).get(0).startsWith("SEASON RESET"), "the preview answers at once on one server");
        h.assertTrue(got.get(0).stream().anyMatch(l -> l.contains("Kept: Safety Deposit Boxes")), "it says what is kept");
        h.assertTrue(season.number == number && season.resetEpoch == epoch, "and changes nothing");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public void season_reset_starts_everyone_over_and_keeps_the_deposit_box_and_prizes(GameTestHelper h) throws Exception {
        MinecraftServer server = server(h);
        SeasonData season = SeasonData.get(server);
        int number = season.number, epoch = season.resetEpoch, winnerPoints = season.lastWinnerPoints, coinEpoch = season.coinEpoch;
        long startMs = season.startMs;
        String lastWinner = season.lastWinner;
        String[] groups = season.prizeGroups.clone();
        // the test world is kept between runs and other tests count on its season and lottery drum: both are put back after
        List<com.mrgregles.bsp_core.lottery.LotteryPlayer> drum = com.mrgregles.bsp_core.lottery.LotteryLedger.get(server).since(0);
        ServerPlayer p = veteran(h);
        UUID id = p.getUUID();
        // the bank: pieces, banked coins, an unlock, and a deposit box with an item in it
        Bank.set(server, id, 123_456, "test", r -> {});
        BankAccount account = Bank.account(server, id);
        account.coins[0] = 7;
        account.remoteShops = true;
        account.boxSlots = 6;
        account.boxUntilMs = System.currentTimeMillis() + 86_400_000L;
        account.box.setStackInSlot(0, new ItemStack(Items.NETHER_STAR));
        // kits: one taken (once), one owed and not delivered yet
        KitLedger kits = KitLedger.get(server);
        kits.allRedeems().put(id, new ArrayList<>(List.of(KitLedger.Redeem.of("bsp_test_once", "", 1, System.currentTimeMillis(), 0),
                KitLedger.Redeem.of("bsp_test_owed", "", 2, System.currentTimeMillis(), 1))));
        com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).paid(id, 20_000);
        com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).start(id, System.currentTimeMillis() + 3_600_000L);
        // the winner: far ahead by a score set by hand, with a prize in the first row and a group (no LuckPerms here: logged)
        UUID winner = UUID.randomUUID();
        com.mrgregles.bsp_core.score.ScoreAdjust.set(server, winner, 1_000_000, "test", r -> {});
        season.rewards.setStackInSlot(0, new ItemStack(Items.DIAMOND_BLOCK, 3));
        season.prizeGroups[0] = "season_champion";
        List<ServerPlayer> sentOff = new ArrayList<>();
        Consumer<ServerPlayer> kicker = SeasonReset.kicker;
        SeasonReset.kicker = sentOff::add;
        List<SeasonEvent> events = new ArrayList<>();
        Consumer<SeasonEvent> listener = events::add;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, SeasonEvent.class, listener);
        SeasonReset.Report[] report = new SeasonReset.Report[1];
        try {
            SeasonReset.run(server, "test admin", r -> report[0] = r);
            h.assertTrue(report[0] != null && report[0].ok(), "the reset is done at once on one server: " + (report[0] == null ? "no answer" : report[0].failure()));
            h.assertTrue(!SeasonReset.running(), "and is not running any more");
            h.assertTrue(season.number == number + 1 && season.resetEpoch == epoch + 1 && report[0].endedSeason() == number, "the season moved on and the reset number went up");
            h.assertTrue(report[0].backups() > 0, "this world's data files were copied first");
            // the bank: the starting pieces, nothing banked, no unlock, the box as it was
            long starting = BSPConfig.getOr(BSPConfig.BANK_STARTING_PIECES, 500);
            h.assertTrue(Bank.pieces(server, id) == starting && account.coins[0] == 0 && !account.remoteShops, "the account starts over: " + Bank.pieces(server, id));
            h.assertTrue(account.box.getStackInSlot(0).is(Items.NETHER_STAR) && account.boxSlots == 6, "the Safety Deposit Box keeps its item and slots");
            // kits
            h.assertTrue(kits.redeem(id, "bsp_test_once", "") == null, "a kit taken can be taken again");
            KitLedger.Redeem still = kits.redeem(id, "bsp_test_owed", "");
            h.assertTrue(still != null && still.owed == 1 && still.count == 0 && still.lastMs == 0, "a kit still owed stays owed, with its count cleared");
            h.assertTrue(com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).last(id) != 20_000, "the daily reward starts over");
            h.assertTrue(com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).until(id) == 0, "the compass wait is gone");
            // the winner's prize: in their compartment, not handed to anyone's inventory; the row emptied
            List<List<PrizeBox.Entry>> box = new ArrayList<>();
            PrizeBox.load(server, winner, box::add);
            h.assertTrue(box.size() == 1 && box.get(0).stream().anyMatch(e -> e.stack().is(Items.DIAMOND_BLOCK) && e.stack().getCount() == 3 && e.place() == 1 && e.season() == number),
                    "the prize waits in the winner's prize compartment");
            h.assertTrue(season.rewards.getStackInSlot(0).isEmpty(), "the prize row is emptied for the next season");
            // the sister mods
            h.assertTrue(events.stream().anyMatch(e -> e instanceof SeasonEvent.Ended en && en.isSeasonReset() && en.isOrigin() && en.isFullReset() && en.getEndedNumber() == number),
                    "the sister mods hear the season reset, as the origin");
            h.assertTrue(events.stream().filter(e -> e instanceof SeasonEvent.Ended).count() == 1, "once");
            h.assertTrue(events.stream().anyMatch(e -> e instanceof SeasonEvent.Ended en && en.getResetMs() == season.startMs && en.getResetMs() > startMs),
                    "with the time of the reset, the new season's start");
            // the player (a mock player is not in the player list, so nobody was sent off here): wiped at their next settle
            h.assertTrue(SeasonReset.due(p), "the wipe is due at their next settle");
            h.assertTrue(SeasonReset.onSettle(p), "the settle wipes them");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0 && p.getInventory().armor.get(3).isEmpty() && p.getInventory().offhand.get(0).isEmpty(), "inventory, armour and offhand are empty");
            h.assertTrue(p.getEnderChestInventory().isEmpty() && p.experienceLevel == 0 && p.getActiveEffects().isEmpty(), "ender chest, XP and effects too");
            h.assertTrue("yes".equals(PlayerPersistent.get(p).getString("othermod:kept")), "another mod's key stays");
            h.assertTrue(events.stream().anyMatch(e -> e instanceof SeasonEvent.Wiped w && w.getPlayer() == p && !w.isNewPlayer() && w.getEndedNumber() == number),
                    "the sister mods hear the wipe");
            p.getInventory().add(new ItemStack(Items.BREAD, 3)); // what the login hands them
            h.assertTrue(!SeasonReset.due(p) && !SeasonReset.onSettle(p) && p.getInventory().countItem(Items.BREAD) == 3, "a later settle wipes nothing");
            // sent off: through the kicker, every player in the list (mock players are not in it: this only checks the count is reported)
            h.assertTrue(report[0].here().kicked() == sentOff.size() && sentOff.size() == server.getPlayerList().getPlayerCount(), "everyone in the player list was sent off");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            SeasonReset.kicker = kicker;
            season.resetEpoch = epoch; // the rest of the test run must not find its players due a wipe
            season.number = number; // nor its totems a season old
            season.coinEpoch = coinEpoch;
            season.startMs = startMs;
            season.lastWinner = lastWinner;
            season.lastWinnerPoints = winnerPoints;
            drum.forEach(e -> com.mrgregles.bsp_core.lottery.LotteryLedger.get(server).seen(e));
            System.arraycopy(groups, 0, season.prizeGroups, 0, groups.length);
            season.setDirty();
            com.mrgregles.bsp_core.score.ScoreAdjust.clear(server, winner, "test", r -> {});
            deleteBackups(server);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_wipe_keeps_this_seasons_totem_and_retires_an_old_one(GameTestHelper h) {
        MinecraftServer server = server(h);
        SeasonData season = SeasonData.get(server);
        int epoch = season.resetEpoch;
        ServerPlayer p = veteran(h);
        ItemStack fresh = com.mrgregles.bsp_core.totem.TotemMint.fresh(p);
        ItemStack old = com.mrgregles.bsp_core.totem.TotemMint.fresh(p);
        TotemIdentity.stampSeason(old, season.number - 1);
        TotemInventories.giveBack(p, fresh);
        TotemInventories.giveBack(p, old);
        season.resetEpoch = epoch + 1;
        try {
            h.assertTrue(com.mrgregles.bsp_core.admin.AdminService.owedUntilWiped(p), "while the wipe is due, items given to them wait as owed (store credit refunds in the reset event)");
            h.assertTrue(SeasonReset.onSettle(p), "the settle wipes them");
            h.assertTrue(!com.mrgregles.bsp_core.admin.AdminService.owedUntilWiped(p), "after it, items are handed over at once again");
            List<ItemStack> totems = new ArrayList<>();
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (TotemInventories.isTotem(p.getInventory().getItem(i))) {
                    totems.add(p.getInventory().getItem(i));
                }
            }
            h.assertTrue(totems.size() == 1 && TotemIdentity.season(totems.get(0)) == season.number, "only this season's totem is kept: " + totems.size());
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0, "the rest is gone");
        } finally {
            season.resetEpoch = epoch;
            season.setDirty();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_only_marks_a_player_who_never_played(GameTestHelper h) {
        MinecraftServer server = server(h);
        SeasonData season = SeasonData.get(server);
        int epoch = season.resetEpoch;
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.getInventory().add(new ItemStack(Items.APPLE, 2));
        season.resetEpoch = epoch + 1;
        List<SeasonEvent.Wiped> heard = new ArrayList<>();
        Consumer<SeasonEvent.Wiped> listener = heard::add;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, SeasonEvent.Wiped.class, listener);
        try {
            h.assertTrue(SeasonReset.due(p) && !SeasonReset.onSettle(p), "a new player is not wiped");
            h.assertTrue(!SeasonReset.due(p) && p.getInventory().countItem(Items.APPLE) == 2, "only marked as through the reset");
            h.assertTrue(heard.stream().filter(w -> w.getPlayer() == p).count() == 1
                    && heard.stream().anyMatch(w -> w.getPlayer() == p && w.isNewPlayer() && w.getEndedNumber() == season.number - 1),
                    "the sister mods still hear of them, as a new player, once");
            SeasonReset.onSettle(p);
            h.assertTrue(heard.stream().filter(w -> w.getPlayer() == p).count() == 1, "and not again at a later settle");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            season.resetEpoch = epoch;
            season.setDirty();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_notice_carries_the_reset_and_season_numbers(GameTestHelper h) {
        String payload = com.mrgregles.bsp_core.storage.NetworkStorage.seasonResetPayload(7, 12);
        h.assertTrue(payload.equals("7|12"), "the reset number, then the new season: " + payload);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void prize_compartment_hands_out_and_never_takes_in(GameTestHelper h) {
        MinecraftServer server = server(h);
        UUID winner = UUID.randomUUID();
        PrizeBox.add(server, winner, List.of(new ItemStack(Items.GOLD_BLOCK, 10), new ItemStack(Items.ELYTRA), ItemStack.EMPTY), 4, 2);
        List<List<PrizeBox.Entry>> got = new ArrayList<>();
        PrizeBox.load(server, winner, got::add);
        h.assertTrue(got.size() == 1 && got.get(0).size() == 2, "two stacks wait (the empty one is not kept)");
        PrizeBox.Entry gold = got.get(0).stream().filter(e -> e.stack().is(Items.GOLD_BLOCK)).findFirst().orElseThrow();
        PrizeBox.Entry elytra = got.get(0).stream().filter(e -> e.stack().is(Items.ELYTRA)).findFirst().orElseThrow();
        h.assertTrue(gold.season() == 4 && gold.place() == 2, "with the season and place won");
        PrizeBox.taken(server, winner, Map.of(gold.id(), new ItemStack(Items.GOLD_BLOCK, 4), elytra.id(), ItemStack.EMPTY));
        got.clear();
        PrizeBox.load(server, winner, got::add);
        h.assertTrue(got.get(0).size() == 1 && got.get(0).get(0).stack().getCount() == 4, "what was taken is gone, what is left stays");
        PrizeBox.taken(server, winner, Map.of(gold.id(), ItemStack.EMPTY));
        got.clear();
        PrizeBox.load(server, winner, got::add);
        h.assertTrue(got.get(0).isEmpty() && PrizeBox.Ledger.get(server).of(winner).isEmpty(), "an emptied compartment holds nothing");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void prize_drawer_shows_under_a_box_not_rented_and_only_gives(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID id = p.getUUID();
        PrizeBox.add(server, id, List.of(new ItemStack(Items.DIAMOND_BLOCK, 3), new ItemStack(Items.ELYTRA)), 5, 1);
        PrizeBox.add(server, id, List.of(new ItemStack(Items.GOLD_INGOT, 9)), 5, 3);
        List<List<PrizeBox.Entry>> got = new ArrayList<>();
        PrizeBox.load(server, id, got::add);
        com.mrgregles.bsp_core.bank.DepositBoxMenu.Prizes prizes = com.mrgregles.bsp_core.bank.DepositBoxMenu.Prizes.of(got.get(0));
        h.assertTrue(prizes.count() == 3 && prizes.more() == 0 && prizes.places()[2] == 3 && prizes.seasons()[0] == 5, "three prizes with their place and season");
        // a box never rented: no box slots, but the inventory and the drawer
        var menu = new com.mrgregles.bsp_core.bank.DepositBoxMenu(0, p.getInventory(), net.minecraft.core.BlockPos.ZERO, false, 0,
                new net.minecraftforge.items.ItemStackHandler(com.mrgregles.bsp_core.bank.DepositBox.SLOTS_MAX), prizes);
        h.assertTrue(menu.prizeStart == 36 && menu.slots.size() == 39, "36 inventory slots, then 3 drawer slots: " + menu.slots.size());
        h.assertTrue(menu.drawerRows == 1 && menu.height > com.mrgregles.bsp_core.bank.DepositBoxMenu.LOCKED_HEIGHT && menu.invY > menu.drawerY, "the drawer sits under the screen, the inventory under it");
        var first = menu.slots.get(menu.prizeStart);
        h.assertTrue(!first.mayPlace(new ItemStack(Items.DIRT)), "nothing can be put in the drawer");
        // shift-click the first prize out
        menu.quickMoveStack(p, menu.prizeStart);
        h.assertTrue(p.getInventory().countItem(Items.DIAMOND_BLOCK) == 3 && !first.hasItem(), "a prize shift-clicked lands in the inventory");
        got.clear();
        PrizeBox.load(server, id, got::add);
        h.assertTrue(got.get(0).size() == 2 && got.get(0).stream().noneMatch(e -> e.stack().is(Items.DIAMOND_BLOCK)), "and is gone from the compartment at once");
        // an inventory stack shift-clicked goes nowhere: no rented box, and never into the drawer
        int dirtSlot = 0;
        p.getInventory().setItem(9, new ItemStack(Items.DIRT, 5));
        menu.quickMoveStack(p, dirtSlot);
        h.assertTrue(p.getInventory().countItem(Items.DIRT) == 5 && !menu.slots.get(menu.prizeStart).hasItem(), "the inventory never fills the drawer");
        // a partial take: half the gold
        var gold = menu.slots.get(menu.prizeStart + 2);
        gold.remove(4);
        gold.setChanged();
        got.clear();
        PrizeBox.load(server, id, got::add);
        h.assertTrue(got.get(0).stream().anyMatch(e -> e.stack().is(Items.GOLD_INGOT) && e.stack().getCount() == 5), "what is left of a stack is kept");
        menu.removed(p);
        // a full compartment shows 27 and says how many more wait
        UUID many = UUID.randomUUID();
        List<ItemStack> lots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lots.add(new ItemStack(Items.APPLE));
        }
        PrizeBox.add(server, many, lots, 5, 2);
        got.clear();
        PrizeBox.load(server, many, got::add);
        var full = com.mrgregles.bsp_core.bank.DepositBoxMenu.Prizes.of(got.get(0));
        h.assertTrue(full.count() == 27 && full.more() == 3, "27 shown, 3 more wait: " + full.count() + "/" + full.more());
        // clean up the test world's ledger
        PrizeBox.Ledger ledger = PrizeBox.Ledger.get(server);
        for (UUID who : List.of(id, many)) {
            got.clear();
            PrizeBox.load(server, who, got::add);
            Map<Long, ItemStack> empty = new java.util.HashMap<>();
            got.get(0).forEach(e -> empty.put(e.id(), ItemStack.EMPTY));
            PrizeBox.taken(server, who, empty);
            h.assertTrue(ledger.of(who).isEmpty(), "cleaned up");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void prize_group_names_are_checked_and_shown_on_the_prize_screen(GameTestHelper h) {
        MinecraftServer server = server(h);
        SeasonData season = SeasonData.get(server);
        String[] before = season.prizeGroups.clone();
        try {
            h.assertTrue(SeasonData.validGroup("") && SeasonData.validGroup("season_champion") && SeasonData.validGroup("s1.top-3"), "group names and none are accepted");
            h.assertTrue(!SeasonData.validGroup("-") && !SeasonData.validGroup("has space") && !SeasonData.validGroup("x".repeat(SeasonData.GROUP_MAX + 1))
                    && !SeasonData.validGroup("a;lp user"), "anything else is refused");
            SeasonData.setPrizeGroup(server, 1, "runner_up", "test");
            h.assertTrue(season.prizeGroups[1].equals("runner_up"), "a place's group is set");
            ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            var menu = new com.mrgregles.bsp_core.admin.SeasonRewardsMenu(0, p.getInventory(),
                    new net.minecraftforge.items.ItemStackHandler(SeasonData.REWARD_SLOTS), false, season.prizeGroups);
            h.assertTrue(menu.groups[1].equals("runner_up") && !menu.editable(), "the prize screen opens with each place's group, read-only for a moderator");
            season.prizeGroups[1] = "changed";
            h.assertTrue(menu.groups[1].equals("runner_up"), "the screen keeps its own copy");
            h.assertTrue(com.mrgregles.bsp_core.admin.SeasonRewardsMenu.GROUP_X + com.mrgregles.bsp_core.admin.SeasonRewardsMenu.GROUP_W
                    <= com.mrgregles.bsp_core.admin.SeasonRewardsMenu.WIDTH - 6, "the group box fits at the end of the row");
        } finally {
            System.arraycopy(before, 0, season.prizeGroups, 0, before.length);
            season.setDirty();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void season_reset_steps_run_as_a_loop_and_a_failing_step_ends_it(GameTestHelper h) {
        // on one server every lot, order and bounty answers at once: thousands of them must not nest thousands of calls
        List<Consumer<Runnable>> steps = new ArrayList<>();
        int[] ran = {0};
        for (int i = 0; i < 20_000; i++) {
            steps.add(next -> {
                ran[0]++;
                next.run();
            });
        }
        int[] done = {0};
        List<SeasonReset.Report> failed = new ArrayList<>();
        SeasonReset.run(steps.iterator(), failed::add, () -> done[0]++);
        h.assertTrue(ran[0] == 20_000 && done[0] == 1 && failed.isEmpty(), "every step ran, then the end once: " + ran[0] + "/" + done[0]);
        // a step that answers later carries on from there
        List<Runnable> later = new ArrayList<>();
        int[] after = {0};
        SeasonReset.run(List.<Consumer<Runnable>>of(later::add, next -> {
            after[0]++;
            next.run();
        }).iterator(), failed::add, () -> after[0] += 10);
        h.assertTrue(after[0] == 0 && later.size() == 1, "nothing after a step still waiting for its answer");
        later.get(0).run();
        h.assertTrue(after[0] == 11, "its answer runs the rest and the end: " + after[0]);
        // a step that throws ends it with a failure, never leaving it running
        int[] end = {0};
        SeasonReset.run(List.<Consumer<Runnable>>of(next -> {
            throw new IllegalStateException("gametest: a step fails");
        }).iterator(), failed::add, () -> end[0]++);
        h.assertTrue(failed.size() == 1 && !failed.get(0).ok() && end[0] == 0, "a step that throws is a failure, and the end does not run");
        h.succeed();
    }

    /** The backups a test reset made in the test world. */
    private static void deleteBackups(MinecraftServer server) throws java.io.IOException {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve(SeasonReset.BACKUP_DIR);
        if (Files.isDirectory(dir)) {
            try (var all = Files.walk(dir)) {
                for (Path f : all.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(f);
                }
            }
        }
    }
}
