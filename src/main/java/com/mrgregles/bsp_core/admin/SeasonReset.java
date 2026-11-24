package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.SeasonEvent;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The season reset ({@code /bsp season reset}; vault note "Season Reset - Brief", owner decisions SR1 to SR16): the season ends and
 * every player starts the next one over, on every server and in every BSP mod, while the Safety Deposit Boxes, the prize
 * compartments, store credit and LuckPerms groups other than RankUp's ladders carry over.
 *
 * <p>The server it is typed on (the origin), once the database and Redis answer: copies every shared table it will change
 * ({@code backup_s<N>_*}; on one server the world's BSP-Core data files); ends every market lot and buy order and every bounty one by
 * one with no refund ({@code Market.cancelByStaff}, {@code BuyOrders.cancelByStaff}, {@code Bounties.removeByStaff}); clears what
 * the network shares in one transaction ({@code NetworkStorage.seasonClear}: bank accounts back to the starting pieces, kit records,
 * Collect boxes, open trades and the rest); then ends the season as a full reset does (prizes into the winners' prize compartments,
 * their LuckPerms groups, every totem and Shatter Coin gone) with the reset number raised, and applies it here. Every server, the
 * origin included, then forgets its own copies ({@link #applyHere}), posts {@link SeasonEvent.Ended} with
 * {@link SeasonEvent.Ended#isSeasonReset()} for the sister mods, and sends every player off with a "new season" message (SR2).
 *
 * <p>Each player is wiped at their next login anywhere ({@link #onSettle}), after an inventory sync mod's restore: the inventory,
 * armour, offhand, ender chest, Curios slots, XP and effects go, health and food are filled, the bed spawn is forgotten, and a player
 * who had played before gets the guide book again (the first-join kits follow from their kit records). Live totems of the new
 * season stay. Advancements and statistics stay (SR10).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class SeasonReset {
    private SeasonReset() {}

    /** The player's persistent key: the season reset (its number) whose wipe of what they carry is done. */
    public static final String TAG_RESET = PlayerPersistent.KEY_PREFIX + "SeasonReset";
    /** How long an admin has to confirm with the code. */
    public static final long CONFIRM_MS = 60_000L;
    /** The world folder where one server's data files are copied before a season reset without a database. */
    public static final String BACKUP_DIR = "bsp_core_season_resets";

    private static final Random RANDOM = new Random();
    private static final Map<String, Asked> ASKED = new HashMap<>();
    private static boolean running;

    /** How a player is sent off the network. Tests swap it (a mock player has no connection to close). */
    public static Consumer<ServerPlayer> kicker = p -> p.connection.disconnect(kickMessage(p.server));

    private record Asked(String code, long untilMs) {}

    /** What the origin did, for the admin's eyes. */
    public record Report(boolean ok, String failure, int endedSeason, int lots, int orders, int bounties, int backups, Applied here) {
        static Report fail(String why) {
            return new Report(false, why, 0, 0, 0, 0, 0, Applied.NONE);
        }
    }

    /** What one server cleared. */
    public record Applied(int accounts, int kitRows, int forgePours, int kicked) {
        static final Applied NONE = new Applied(0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ the confirm step

    /** A fresh four-digit code for {@code who}; their confirm counts with it until {@link #CONFIRM_MS} from now. */
    public static String ask(String who) {
        String code = Integer.toString(1000 + RANDOM.nextInt(9000));
        ASKED.put(who, new Asked(code, System.currentTimeMillis() + CONFIRM_MS));
        return code;
    }

    /** Whether {@code who} asked within the last {@link #CONFIRM_MS} and typed the code shown then; the ask is used up either way. */
    public static boolean confirmed(String who, String code) {
        Asked a = ASKED.remove(who);
        return a != null && a.code.equals(code.trim()) && System.currentTimeMillis() <= a.untilMs;
    }

    public static boolean running() {
        return running;
    }

    // ------------------------------------------------------------------ the preview (the dry run)

    /** The lines the admin reads before confirming, with the network's counts when there is a database ({@code then} on the server thread). */
    public static void preview(MinecraftServer server, Consumer<List<String>> then) {
        NetworkStorage.seasonCounts(counts -> then.accept(previewLines(server, counts)));
    }

    static List<String> previewLines(MinecraftServer server, int[] counts) {
        SeasonData season = SeasonData.get(server);
        boolean net = NetworkStorage.enabled();
        int lots = counts != null ? counts[0] : com.mrgregles.bsp_core.market.Market.open(server).size();
        int orders = counts != null ? counts[1] : com.mrgregles.bsp_core.market.BuyOrders.open(server).size();
        int bounties = counts != null ? counts[2] : com.mrgregles.bsp_core.bounty.Bounties.open(server).size();
        int accounts = counts != null ? counts[3] : com.mrgregles.bsp_core.bank.BankLedger.get(server).all().size();
        List<String> out = new ArrayList<>();
        out.add("SEASON RESET: season " + season.number + " ends and season " + (season.number + 1) + " starts over on " + (net ? "every server of the network" : "this server") + " and in every BSP mod.");
        if (net && counts == null) {
            out.add("(The network's database did not answer; the counts below are this server's copies.)");
        }
        List<com.mrgregles.bsp_core.score.ScoreService.Entry> standings = net ? com.mrgregles.bsp_core.score.ScoreService.board() : com.mrgregles.bsp_core.score.ScoreService.local(server);
        for (int place = 0; place < SeasonData.PLACES; place++) {
            int stacks = 0;
            for (int i = 0; i < SeasonData.PER_PLACE; i++) {
                stacks += season.rewards.getStackInSlot(place * SeasonData.PER_PLACE + i).isEmpty() ? 0 : 1;
            }
            String who = place < standings.size() ? standings.get(place).name() + " (" + standings.get(place).points() + " points)" : "nobody";
            String group = season.prizeGroups[place].isBlank() ? "" : ", LuckPerms group " + season.prizeGroups[place];
            out.add("- Place " + (place + 1) + ": " + who + ": " + stacks + " prize stack(s) into their prize compartment" + group + ".");
        }
        out.add("- Shatter Totems: every totem removed on every server (" + TotemLedger.get(server).allPlaced().values().stream().mapToInt(Set::size).sum()
                + " placed here); each player gets one fresh Tier I totem at their next login.");
        out.add("- Bank: " + accounts + " account(s) back to " + BSPConfig.getOr(BSPConfig.BANK_STARTING_PIECES, 500)
                + " pieces; banked coins, stamps, logs, daily streaks and Portable Banker unlocks cleared. Every Shatter Coin wiped (carried, ender chests, Coin Vaults, factory trays).");
        out.add("- Market: " + lots + " lot(s) and " + orders + " buy order(s) cancelled with no refund; Collect boxes emptied; open trades cancelled.");
        out.add("- Bounties: " + bounties + " open bount" + (bounties == 1 ? "y" : "ies") + " removed with no refund.");
        out.add("- Kits: every player's uses and cooldowns cleared (owed kits stay owed). Shop limits, compass waits, lottery entries, steal-back records and Totem Forge pours cleared.");
        out.add("- Players: everyone is sent off the network. At their next login: inventory, armour, offhand, ender chest, Curios, XP and effects wiped; guide book and first-join kits again. Advancements and statistics kept.");
        List<String> mods = new ArrayList<>();
        for (String[] m : new String[][] {{"bsp_rankup", "RankUp (ladders to the bottom)"}, {"bsp_region_guard", "Region Guard (plots back on sale)"},
                {"bsp_teleport", "Teleport (homes and positions)"}, {"bsp_inventorysync", "InventorySync (graves and gives)"}, {"bsp_server_utilities", "Server Utilities"}}) {
            if (ModList.get().isLoaded(m[0])) {
                mods.add(m[1]);
            }
        }
        out.add("- Other BSP mods hear the season reset: " + (mods.isEmpty() ? "none loaded here" : String.join(", ", mods)) + "; the proxy forgets everyone's last server.");
        out.add("- Kept: Safety Deposit Boxes" + (counts != null ? " (" + counts[6] + ")" : "") + " and prize compartments, store credit, the Discord link, teams and alliances, VIP and every LuckPerms group off RankUp's ladders, placed blocks.");
        out.add(net ? "- Backups: every shared table it changes is copied to backup_s" + season.number + "_* in the database first."
                : "- Backups: this world's BSP-Core data files are copied to " + BACKUP_DIR + "/ first.");
        out.add("Back up the worlds before you confirm.");
        return out;
    }

    // ------------------------------------------------------------------ the origin

    /** Runs the season reset from this server (the command's confirm, the panel's button). {@code then} hears the report on the server thread. */
    public static void run(MinecraftServer server, String by, Consumer<Report> then) {
        if (running) {
            then.accept(Report.fail("a season reset is already running"));
            return;
        }
        if (NetworkStorage.down()) {
            then.accept(Report.fail("the network's database is not connected on this server; nothing was changed"));
            return;
        }
        if (NetworkStorage.redisDown()) {
            then.accept(Report.fail("Redis is not connected on this server, so the other servers would hear of it late; nothing was changed"));
            return;
        }
        running = true;
        SeasonData data = SeasonData.get(server);
        int season = data.number, resetEpoch = data.resetEpoch;
        boolean[] answered = {false};
        Consumer<Report> done = r -> {
            if (answered[0]) {
                return;
            }
            answered[0] = true;
            running = false;
            then.accept(r);
        };
        guarded(done, () -> checkSeason(season, resetEpoch, failure -> guarded(done, () -> {
            if (failure != null) {
                done.accept(Report.fail(failure + "; nothing was changed"));
                return;
            }
            backup(server, season, backups -> guarded(done, () -> {
                if (backups < 0) {
                    done.accept(Report.fail("the backups could not be made (see the log); nothing was changed"));
                    return;
                }
                // the lots and orders read again first: one listed on another server since the last read is cancelled too
                com.mrgregles.bsp_core.api.BspMarket.refresh(server, () -> guarded(done, () -> endOpen(server, by, season, resetEpoch, backups, done)));
            }));
        })));
    }

    /** Runs a step of the reset; one that throws ends it with a failure, so the reset never stays "running" with nobody told. */
    private static void guarded(Consumer<Report> done, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | StackOverflowError e) {
            BSPCore.LOGGER.error("Season reset: a step failed", e);
            done.accept(Report.fail("a step failed (" + e + "); see the log, check what was done, then run it again if the season did not end"));
        }
    }

    /** On a network, whether the database is still at this server's season and reset number (null when it is, else why not). */
    private static void checkSeason(int season, int resetEpoch, Consumer<String> then) {
        if (!NetworkStorage.enabled()) {
            then.accept(null);
            return;
        }
        NetworkStorage.seasonState(db -> {
            if (db == null) {
                then.accept("the network's season could not be read");
            } else if (db[0] >= 0 && db[0] != season || db[1] > resetEpoch) {
                then.accept("the network is at season " + db[0] + " (reset number " + Math.max(0, db[1]) + "), this server at season " + season
                        + ": the season was ended elsewhere and this server has not read it yet; wait a few seconds");
            } else {
                then.accept(null);
            }
        });
    }

    /** Ends every open lot, order and bounty, then the shared clear and the season's end. */
    private static void endOpen(MinecraftServer server, String by, int season, int resetEpoch, int backups, Consumer<Report> then) {
        int[] counts = new int[3];
        String who = by + " (season reset)";
        List<Consumer<Runnable>> steps = new ArrayList<>();
        for (var lot : com.mrgregles.bsp_core.market.Market.open(server)) {
            steps.add(next -> com.mrgregles.bsp_core.market.Market.cancelByStaff(server, lot.id, who, r -> {
                counts[0] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var order : com.mrgregles.bsp_core.market.BuyOrders.open(server)) {
            steps.add(next -> com.mrgregles.bsp_core.market.BuyOrders.cancelByStaff(server, order.id, who, r -> {
                counts[1] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var b : com.mrgregles.bsp_core.bounty.Bounties.open(server)) {
            steps.add(next -> com.mrgregles.bsp_core.bounty.Bounties.removeByStaff(server, b.id, false, who, r -> {
                counts[2] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        // the new season as the shared clear writes it, in the same transaction: no server can read the cleared rows with the old season
        long startMs = System.currentTimeMillis();
        SeasonData data = SeasonData.get(server);
        Map<String, String> newState = Map.of(SeasonSync.K_SEASON, Integer.toString(season + 1), SeasonSync.K_RESET_EPOCH, Integer.toString(resetEpoch + 1),
                SeasonSync.K_EPOCH, Integer.toString(data.coinEpoch + 1), SeasonSync.K_START, Long.toString(startMs));
        run(steps.iterator(), then, () -> NetworkStorage.seasonClear(startingPieces(), keptAccounts(), season, resetEpoch, newState, failure -> guarded(then, () -> {
            if (failure != null) {
                then.accept(new Report(false, failure.isEmpty() ? "the shared records could not be cleared (see the log); the lots, orders and bounties are ended, the season is not: run it again"
                        : failure, 0, counts[0], counts[1], counts[2], backups, Applied.NONE));
                return;
            }
            int ended = AdminService.seasonResetEnd(server, by, startMs);
            Applied here = applyHere(server, ended, by, true);
            NetworkStorage.seasonResetPublish(SeasonData.get(server).resetEpoch, SeasonData.get(server).number);
            then.accept(new Report(true, "", ended, counts[0], counts[1], counts[2], backups, here));
        })));
    }

    /**
     * Runs the steps one after another (each calls the next when its answer is in), then {@code done}. A loop, not a call chain: on one
     * server every answer comes at once, and a thousand lots would otherwise be a thousand nested calls.
     */
    public static void run(Iterator<Consumer<Runnable>> steps, Consumer<Report> fail, Runnable done) { // public for the tests
        while (steps.hasNext()) {
            Consumer<Runnable> step = steps.next();
            boolean[] returned = {false}, answered = {false};
            guarded(fail, () -> step.accept(() -> {
                if (returned[0]) {
                    run(steps, fail, done); // answered later (the database): carry on from here
                } else {
                    answered[0] = true;
                }
            }));
            returned[0] = true;
            if (!answered[0]) {
                return;
            }
        }
        guarded(fail, done);
    }

    /** The database's backup tables, or on one server a copy of the world's BSP-Core data files; -1 on a failure. */
    private static void backup(MinecraftServer server, int season, Consumer<Integer> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.seasonBackup(season, then);
            return;
        }
        then.accept(copyDataFiles(server, season));
    }

    /** Copies {@code data/bsp_core*.dat} into {@code <world>/}{@link #BACKUP_DIR}{@code /s<N>-<ms>/}. Returns files copied, -1 on a failure. */
    static int copyDataFiles(MinecraftServer server, int season) {
        try {
            server.overworld().getDataStorage().save(); // what is in memory first
            Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
            Path to = server.getWorldPath(LevelResource.ROOT).resolve(BACKUP_DIR).resolve("s" + season + "-" + System.currentTimeMillis());
            Files.createDirectories(to);
            int n = 0;
            if (Files.isDirectory(data)) {
                try (var files = Files.list(data)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        String name = f.getFileName().toString();
                        if (name.startsWith(BSPCore.MODID) && name.endsWith(".dat") && Files.isRegularFile(f)) {
                            Files.copy(f, to.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                            n++;
                        }
                    }
                }
            }
            BSPCore.LOGGER.info("Season reset: {} BSP-Core data file(s) copied to {}", n, to);
            return n;
        } catch (IOException | RuntimeException e) {
            BSPCore.LOGGER.error("Season reset: the data files could not be copied: {}", e.toString());
            return -1;
        }
    }

    static long startingPieces() {
        return BSPConfig.getOr(BSPConfig.BANK_STARTING_PIECES, 500);
    }

    /** The bank accounts that are not players': the lottery pot and the bounty board's. */
    static Set<UUID> keptAccounts() {
        return Set.of(com.mrgregles.bsp_core.market.Market.POT, com.mrgregles.bsp_core.bounty.Bounties.SERVER);
    }

    // ------------------------------------------------------------------ every server

    /**
     * Clears this server's own records for a season reset (the origin right after the shared clear, the others when they read it),
     * posts {@link SeasonEvent.Ended} as a season reset, and sends every player here off the network. The totems and coins went
     * already (the season's end, or {@link SeasonSync}).
     */
    public static Applied applyHere(MinecraftServer server, int endedSeason, String by, boolean origin) {
        int accounts = com.mrgregles.bsp_core.bank.BankLedger.get(server).seasonReset(startingPieces(), keptAccounts());
        com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).clearAll();
        com.mrgregles.bsp_core.lottery.LotteryLedger.get(server).forgetAll();
        com.mrgregles.bsp_core.shop.ShopLedger.get(server).forgetAllPlayers();
        com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).clearAll();
        com.mrgregles.bsp_core.totem.StealBacks.clearAll(server);
        int kitRows = com.mrgregles.bsp_core.kit.KitLedger.get(server).seasonReset();
        var market = com.mrgregles.bsp_core.market.MarketLedger.get(server);
        for (UUID collector : new ArrayList<>(market.collectors())) {
            market.takeCollect(collector);
        }
        market.setDirty();
        // Totem Forge pours: the totems they would set belong to the season that ended
        var forge = com.mrgregles.bsp_core.machine.TotemForgeLedger.get(server);
        int pours = 0;
        for (UUID player : forge.players()) {
            PlayerReset.voidForgePour(server, player);
            forge.clear(player);
            pours++;
        }
        if (NetworkStorage.enabled()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                com.mrgregles.bsp_core.bank.Bank.refresh(server, p.getUUID()); // their reset account, read again
            }
        }
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        post(new SeasonEvent.Ended(server, endedSeason, true, by, true, origin, SeasonData.get(server).startMs));
        // SR2: everyone off the network; each is wiped when they come back
        int kicked = 0;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            try {
                kicker.accept(p);
                kicked++;
            } catch (RuntimeException e) {
                BSPCore.LOGGER.warn("Season reset: {} could not be sent off: {}", p.getGameProfile().getName(), e.toString());
            }
        }
        BSPCore.LOGGER.info("Season reset (season {} ended by {}{}): {} bank account(s), {} kit record(s), {} Totem Forge pour(s) cleared here; {} player(s) sent off",
                endedSeason, by, origin ? ", typed here" : "", accounts, kitRows, pours, kicked);
        return new Applied(accounts, kitRows, pours, kicked);
    }

    /** The line a player sees as they are sent off. */
    static Component kickMessage(MinecraftServer server) {
        return Component.translatable("message.bsp_core.season_reset.kick", SeasonData.get(server).number).withStyle(ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ logins

    /** Whether the player is still to be wiped here for a season reset this server knows of. */
    public static boolean due(ServerPlayer player) {
        return PlayerPersistent.get(player).getInt(TAG_RESET) < SeasonData.get(player.server).resetEpoch;
    }

    /**
     * The settle's step ({@link AdminService#settle}, and the HIGHEST listener on an inventory sync mod's restore): wipes what the player
     * carries if a season reset is still to do for them. A player who never had a totem (new since) is only marked, and still heard of
     * ({@link SeasonEvent.Wiped#isNewPlayer()}), so the sister mods start them at the season's beginning too. True when wiped.
     */
    public static boolean onSettle(ServerPlayer player) {
        if (!due(player) || player.isDeadOrDying()) {
            return false;
        }
        boolean played = PlayerPersistent.isTotemGranted(player) || TotemLedger.get(player.server).hasBeenGranted(player.getUUID())
                || PlayerPersistent.get(player).contains(PlayerPersistent.KEY_PREFIX + "SeasonSeen");
        if (!played) {
            stamp(player);
            post(new SeasonEvent.Wiped(player.server, player, SeasonData.get(player.server).number - 1, true));
            return false;
        }
        wipe(player);
        return true;
    }

    /**
     * Wipes what the player carries (SR10): totems from an earlier season retired, this season's kept; the inventory with armour and
     * offhand, the ender chest, Curios slots, XP and effects gone; health and food full; the bed spawn forgotten; the guide book given
     * again; the reset stamped; an inventory sync mod asked to save it at once; {@link SeasonEvent.Wiped} posted.
     */
    public static void wipe(ServerPlayer player) {
        MinecraftServer server = player.server;
        int season = SeasonData.get(server).number;
        TotemLedger ledger = TotemLedger.get(server);
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack st : TotemInventories.removeAll(player)) {
            if (TotemIdentity.season(st) >= season) {
                keep.add(st); // the fresh totem of this season (a season check before they were sent off)
            } else {
                UUID id = TotemIdentity.id(st);
                if (id != null) {
                    ledger.forget(id);
                }
            }
        }
        player.closeContainer();
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        PlayerReset.clearCurios(player);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
        player.experienceProgress = 0;
        player.removeAllEffects();
        player.clearFire();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, null, 0.0F, false, false);
        keep.forEach(st -> TotemInventories.giveBack(player, st));
        com.mrgregles.bsp_core.event.FirstJoinHandler.giveGuideBook(player);
        stamp(player);
        player.inventoryMenu.broadcastChanges();
        PlayerReset.saveSynced(player);
        player.displayClientMessage(Component.translatable("message.bsp_core.season_reset.wiped", season).withStyle(ChatFormatting.GOLD), false);
        post(new SeasonEvent.Wiped(server, player, season - 1));
        BSPCore.LOGGER.info("Season reset: what {} carries wiped on this server", player.getGameProfile().getName());
    }

    private static void stamp(ServerPlayer player) {
        PlayerPersistent.get(player).putInt(TAG_RESET, SeasonData.get(player.server).resetEpoch);
        PlayerPersistent.edited(player, TAG_RESET);
    }

    /** Posts an event; a listener that throws is logged, never allowed to stop the reset (Forge stops at the first that throws). */
    static void post(Event event) {
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException | LinkageError e) {
            BSPCore.LOGGER.error("Season: a listener of {} failed: {}", event.getClass().getSimpleName(), e.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ASKED.clear();
        running = false;
    }
}
