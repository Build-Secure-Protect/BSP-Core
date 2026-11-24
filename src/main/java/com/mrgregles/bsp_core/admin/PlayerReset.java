package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.PlayerResetEvent;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * An admin's reset of one player to a brand-new player, on the whole network and in every BSP mod ({@code /bsp player reset}; vault
 * note "Player Reset", owner decisions PR1 to PR22).
 *
 * <p>The server it is typed on (the origin) first ends what the player has going with other players, through each feature's own
 * service: their team (the lead passed to the longest-standing member first; a lone leader's team is disbanded), their market lots
 * and buy orders (no refund), the bounties they placed (no refund), on them (refunded to the placers) and hunt; then it empties their
 * Collect box and deletes every database row that is theirs (the bank account among them, so the starting pieces are given again),
 * records the reset in {@code player_resets} and tells every server. Each server, the origin included, then forgets the player in
 * its own records ({@link #apply}): totems, grant, protection, steal-backs, compass wait, Totem Forge, kits, bank copy, daily reward,
 * vault interest and recovered coins, lottery entry, shop limits, looks, BSP-RankUp's progress (its own reset, on every server, since
 * its copies on other servers would bring the old rank back), and on the origin the LuckPerms groups of RankUp's awards; posts
 * {@link PlayerResetEvent.Forgotten} for the sister mods; and wipes what the player carries ({@link #clearCarried}) when they are in
 * here, or else moves this server's own files of the player (player data, statistics, advancements) aside into
 * {@code bsp_core_player_resets/} in the world folder, so they arrive here as new.
 *
 * <p>What they carry is wiped at once when they are online and settled, otherwise at their next login, after an inventory sync mod's
 * restore (whose snapshot would bring it back) and before anything is handed to them: a {@code bsp_core:ResetAt} older than the
 * reset known here means it is still to do. Kept: store credit, the Discord link, placed blocks, the coins in their Coin Vaults and
 * every moderation record.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class PlayerReset {
    private PlayerReset() {}

    /** The player's persistent key: the reset (ms) whose wipe of what they carry is done. */
    public static final String TAG_RESET_AT = PlayerPersistent.KEY_PREFIX + "ResetAt";
    /** A reset reaching a server more than this after it was made is late: RankUp is left alone there. */
    public static final long LATE_MS = 10 * 60_000L;
    /** How long an admin has to confirm. */
    public static final long CONFIRM_MS = 30_000L;
    /** The folder of the world where an offline player's own files are moved by a reset. */
    public static final String BACKUP_DIR = "bsp_core_player_resets";
    private static final String SYNC_API = "com.mrgregles.bsp_inventorysync.api.InventorySyncApi";
    private static final String SYNC_RESTORED = "com.mrgregles.bsp_inventorysync.api.event.InventoryRestoredEvent";

    /** Resets asked for and waiting for their confirm, by who asked. */
    private static final Map<String, Asked> ASKED = new HashMap<>();
    /** Players wiped at a settle after the reset landed mid-login or while dead: the account and kit grants of a first join follow. */
    private static final java.util.Set<UUID> HANDOUTS = new java.util.HashSet<>();
    private static boolean hooked;

    private record Asked(UUID id, String name, long untilMs) {}

    /** What the origin did, for the admin's eyes. */
    public record Report(boolean ok, String failure, int lots, int orders, int bounties, String team, Applied here) {
        static Report fail(String why) {
            return new Report(false, why, 0, 0, 0, "", Applied.NONE);
        }
    }

    /** What one server forgot. */
    public record Applied(int totems, boolean carriedCleared, int filesMoved, boolean rankUp, int awardsRemoved) {
        static final Applied NONE = new Applied(0, false, 0, false, 0);
    }

    // ------------------------------------------------------------------ the confirm step

    /** Remembers that {@code who} asked to reset the player; their confirm counts until {@link #CONFIRM_MS} from now. */
    public static void ask(String who, UUID id, String name) {
        ASKED.put(who, new Asked(id, name, System.currentTimeMillis() + CONFIRM_MS));
    }

    /** Whether {@code who} asked to reset this player within the last {@link #CONFIRM_MS}; the ask is used up. */
    public static boolean confirmed(String who, UUID id) {
        Asked a = ASKED.remove(who);
        return a != null && a.id.equals(id) && System.currentTimeMillis() <= a.untilMs;
    }

    /** What the reset of the player will take, as this server sees it (the lines the admin reads before confirming). */
    public static List<String> preview(MinecraftServer server, UUID id, String name) {
        List<String> out = new ArrayList<>();
        out.add("Resetting " + name + " makes them a new player on every server:");
        int placed = TotemLedger.get(server).placedFor(id).size();
        out.add("- Shatter Totems: carried and placed (" + placed + " placed on this server) removed; a fresh first-join totem at their next login.");
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, id);
        if (team != null) {
            out.add("- Team " + team.name + ": " + (!team.isLeader(id) ? "they leave it." : team.size() == 1 ? "disbanded (they are alone in it)." : "the lead passes to the longest-standing member, then they leave."));
        }
        int lots = com.mrgregles.bsp_core.market.Market.lotsBy(server, id).size(), orders = com.mrgregles.bsp_core.market.BuyOrders.ordersBy(server, id).size();
        int placedB = com.mrgregles.bsp_core.bounty.Bounties.placedBy(server, id).size(), onB = com.mrgregles.bsp_core.bounty.Bounties.on(server, id).size();
        out.add("- Market: " + lots + " lot(s) and " + orders + " buy order(s) cancelled with no refund; Collect box emptied.");
        out.add("- Bounties: " + placedB + " they placed removed with no refund; " + onB + " on them refunded to the placers; their hunts dropped.");
        out.add("- Bank: the whole account (pieces, coins, stamps, log, deposit box, Shopping Network and market unlocks, daily reward); starting pieces again.");
        out.add("- Inventory, ender chest, Curios, XP, effects, advancements and statistics wiped; kits, looks, compass wait, protection, lottery entry, shop limits, vault interest forgotten.");
        boolean rankUp = ModList.get().isLoaded("bsp_rankup");
        out.add("- Other BSP mods: " + (rankUp ? "RankUp progress" + (ModList.get().isLoaded("luckperms") ? " and its LuckPerms award groups" : "") + "; " : "")
                + "Teleport, InventorySync, Region Guard, Server Utilities and the proxy forget them through the reset event.");
        out.add("- Kept: store credit, the Discord link, placed blocks and the coins in their Coin Vaults, staff ranks and bought ranks, moderation records.");
        if (rankUp && !ModList.get().isLoaded("luckperms")) {
            out.add("- LuckPerms is not on this server: RankUp's award groups stay. Run the reset on a server with LuckPerms to remove them.");
        }
        return out;
    }

    // ------------------------------------------------------------------ the origin

    /**
     * Resets the player from this server (the origin), as the command's confirm does. {@code online} is the player when they are in
     * here (tests pass a mock player; null looks them up). {@code then} hears the report on the server thread when it is all done.
     */
    public static void reset(MinecraftServer server, UUID id, String name, String by, @Nullable ServerPlayer online, Consumer<Report> then) {
        if (NetworkStorage.down()) {
            then.accept(Report.fail("the network's database is not connected on this server; nothing was changed"));
            return;
        }
        // the team first: the one step that can refuse, so nothing else is done when it does
        String team = leaveTeam(server, id);
        if (team.startsWith("!")) {
            then.accept(Report.fail(team.substring(1)));
            return;
        }
        // the lots and orders read again first: one listed on another server since the last read is cancelled too
        com.mrgregles.bsp_core.api.BspMarket.refresh(server, () -> endAndForget(server, id, name, by, online, team, then));
    }

    private static void endAndForget(MinecraftServer server, UUID id, String name, String by, @Nullable ServerPlayer online, String team, Consumer<Report> then) {
        int[] counts = new int[3];
        List<Consumer<Runnable>> steps = new ArrayList<>();
        for (var lot : com.mrgregles.bsp_core.market.Market.lotsBy(server, id)) {
            steps.add(next -> com.mrgregles.bsp_core.market.Market.cancelByStaff(server, lot.id, by + " (player reset)", r -> {
                counts[0] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var order : com.mrgregles.bsp_core.market.BuyOrders.ordersBy(server, id)) {
            steps.add(next -> com.mrgregles.bsp_core.market.BuyOrders.cancelByStaff(server, order.id, by + " (player reset)", r -> {
                counts[1] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var b : com.mrgregles.bsp_core.bounty.Bounties.on(server, id)) {
            steps.add(next -> com.mrgregles.bsp_core.bounty.Bounties.removeByStaff(server, b.id, true, by + " (player reset)", r -> {
                counts[2] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var b : com.mrgregles.bsp_core.bounty.Bounties.placedBy(server, id)) {
            if (b.target.equals(id)) {
                continue; // on themselves: already ended above
            }
            steps.add(next -> com.mrgregles.bsp_core.bounty.Bounties.removeByStaff(server, b.id, false, by + " (player reset)", r -> {
                counts[2] += r.ok() ? 1 : 0;
                next.run();
            }));
        }
        for (var b : com.mrgregles.bsp_core.bounty.Bounties.huntedBy(server, id)) {
            steps.add(next -> com.mrgregles.bsp_core.bounty.Bounties.abandon(server, id, b.id, r -> next.run()));
        }
        // the Collect box last of the market: the cancelled lots' items went into it
        steps.add(next -> com.mrgregles.bsp_core.market.Market.store(server).takeCollect(server, id, items -> next.run()));
        run(steps.iterator(), () -> NetworkStorage.playerForget(id, ok -> {
            if (!ok) {
                then.accept(new Report(false, "the database rows could not be deleted (see the log); the team, market and bounty steps are done, run the reset again",
                        counts[0], counts[1], counts[2], team, Applied.NONE));
                return;
            }
            PlayerResetRow row = new PlayerResetRow(id, name, System.currentTimeMillis(), by);
            NetworkStorage.playerResetWrite(row, written -> {
                Applied here = apply(server, row, true, online);
                then.accept(new Report(true, written ? "" : "the reset could not be recorded in the database: the other servers hear of it only when it is run again",
                        counts[0], counts[1], counts[2], team, here));
            });
        }));
    }

    /** Runs the steps one after another (each calls the next when its answer is in), then {@code done}. */
    private static void run(Iterator<Consumer<Runnable>> steps, Runnable done) {
        if (!steps.hasNext()) {
            done.run();
            return;
        }
        steps.next().accept(() -> run(steps, done));
    }

    /** Takes the player out of their team (PR9); what was done, "" for no team, or "!" and why it could not be done. */
    private static String leaveTeam(MinecraftServer server, UUID id) {
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, id);
        if (team == null) {
            return "";
        }
        String name = team.name;
        if (team.isLeader(id) && team.size() > 1) {
            UUID next = team.members().entrySet().stream().filter(e -> !e.getKey().equals(id))
                    .min(Comparator.comparingLong(e -> e.getValue().joinedMs)).map(Map.Entry::getKey).orElse(null);
            var lead = next == null ? null : com.mrgregles.bsp_core.team.Teams.adminLead(server, team, next);
            if (lead == null || !lead.ok()) {
                return "!the lead of team " + name + " could not be passed on" + (lead != null && lead.message() != null ? " (" + lead.message().getString() + ")" : "")
                        + "; pass it on with /bsp team lead, then reset again. Nothing was changed";
            }
        }
        boolean alone = team.isLeader(id) && team.size() == 1;
        var out = com.mrgregles.bsp_core.team.Teams.adminKick(server, id);
        if (!out.ok()) {
            return "!they could not be taken out of team " + name + (out.message() != null ? " (" + out.message().getString() + ")" : "") + "; nothing was changed";
        }
        return alone ? "team " + name + " disbanded" : "left team " + name;
    }

    // ------------------------------------------------------------------ every server

    /** A reset made on another server (its Redis notice, or the database's records read at a connect and every few minutes). */
    public static void fromNetwork(MinecraftServer server, PlayerResetRow row) {
        apply(server, row, false, null);
    }

    /**
     * Forgets the player on this server for this reset, once (a repeat, or an older reset, does nothing). A reset older than this
     * server's record ({@link PlayerResetLedger#since}) is only noted apart, and never wipes them at a login. {@code online} as in {@link #reset}.
     */
    public static Applied apply(MinecraftServer server, PlayerResetRow row, boolean origin, @Nullable ServerPlayer online) {
        PlayerResetLedger ledger = PlayerResetLedger.get(server);
        if (!origin && row.resetMs() < ledger.since() - LATE_MS) {
            if (ledger.skip(row)) {
                BSPCore.LOGGER.info("Player reset of {} ({}) by {} is older than this server's record of resets: noted, not applied", row.name(), row.id(), row.by());
            }
            return Applied.NONE;
        }
        if (!ledger.note(row)) {
            return Applied.NONE;
        }
        saveNow(server, ledger); // a crash before the next autosave must not apply this reset a second time
        UUID id = row.id();
        ServerPlayer here = online != null ? online : server.getPlayerList().getPlayer(id);
        // offline here, but their own file here already carries this reset's stamp (they were wiped and played here since, and the
        // reset is reaching this server again): their files and what they placed here since are theirs, and stay
        boolean playedSince = here == null && stampOnDisk(server, id) >= row.resetMs();
        // What they carry here is from before this reset only without its stamp: with an inventory sync mod a player wiped on another
        // server already (this server heard late) carries their new things, which stay, and so do the totems they carry now.
        boolean carries = here != null && PlayerPersistent.get(here).getLong(TAG_RESET_AT) < row.resetMs();
        ServerPlayer mine = carries ? here : null;
        TotemLedger totems = TotemLedger.get(server);
        // RankUp's reset and the protection delete write the network's rows: on the other servers only while the reset is fresh (their
        // RankUp copies would bring the old rank back); a server reached late leaves them alone, as the player may have earned since
        boolean late = !origin && System.currentTimeMillis() - row.resetMs() > LATE_MS;
        // Only the origin touches what the network shares (the database rows it deleted first, the looks record, the Totem Forge row,
        // the kit records): a server reached late (it was down) must not delete what the player has made since. The others forget
        // their own copies, which the network's next read or the player's next login fills again.
        if (origin) {
            // looks first: they come off the totems still standing, which the wipe then removes
            com.mrgregles.bsp_core.looks.LookOwners.get(server).clearPlayer(server, id);
        }
        // the origin's database rows went with the others before the notice (deleting them again now could erase a new first-join
        // grant or kit record another server just wrote), so with network storage every server forgets its own records only
        var wipe = playedSince ? new com.mrgregles.bsp_core.totem.TotemWipe.Result(0, 0, 0, 0, false, false, false)
                : here != null && !carries ? com.mrgregles.bsp_core.totem.TotemWipe.forgetHereKeeping(server, id, carriedTotems(here))
                : origin && !NetworkStorage.enabled() ? com.mrgregles.bsp_core.totem.TotemWipe.forget(server, id, mine)
                : com.mrgregles.bsp_core.totem.TotemWipe.forgetHere(server, id, mine);
        int gone = wipe.carried() + wipe.placed();
        for (ItemStack st : com.mrgregles.bsp_core.api.BspTransfer.forgetPlayer(id)) {
            forgetTotem(totems, st);
            gone++;
        }
        if (mine != null) {
            for (ItemStack st : com.mrgregles.bsp_core.zone.ZoneHandler.takeHeld(mine)) {
                forgetTotem(totems, st);
                gone++;
            }
        }
        com.mrgregles.bsp_core.totem.NewPlayerProtection.get(server).forget(id);
        if (!late) {
            NetworkStorage.protectionForget(id); // after this server's minute of reading RankUp, which could have raised it again
        }
        com.mrgregles.bsp_core.totem.StealBacks.forget(server, id);
        com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).clear(id);
        if (!playedSince) {
            voidForgePour(server, id); // the forge itself would still hand the set totem to its pourer
        }
        if (origin) {
            com.mrgregles.bsp_core.machine.TotemForgeLedger.get(server).clear(id);
        } else {
            com.mrgregles.bsp_core.machine.TotemForgeLedger.get(server).forgetHere(id);
        }
        com.mrgregles.bsp_core.kit.Kits.forgetHere(server, id); // the network's kit rows went with the database rows
        com.mrgregles.bsp_core.bank.BankLedger.get(server).forget(id);
        com.mrgregles.bsp_core.market.MarketLedger.get(server).takeCollect(id); // Collect items held here while the database was out of reach
        com.mrgregles.bsp_core.storage.LaterMessages.get(server).take(id); // lines kept for them (the cancelled lots and orders): theirs no more
        com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).forget(id);
        com.mrgregles.bsp_core.vault.VaultLedger.get(server).forgetEarnings(id);
        com.mrgregles.bsp_core.lottery.LotteryLedger.get(server).forget(id);
        com.mrgregles.bsp_core.shop.ShopLedger.get(server).forgetPlayer(id);
        com.mrgregles.bsp_core.credit.CreditLedger.get(server).forgetPayer(id); // their tickets stay (PR10); claims they paid with credit no longer charge them
        boolean rankUp = !late && rankUpReset(server, id);
        if (late && ModList.get().isLoaded("bsp_rankup")) {
            BSPCore.LOGGER.warn("Player reset of {} reached this server {} min late: BSP-RankUp's copy of them here is left as it is", row.name(), (System.currentTimeMillis() - row.resetMs()) / 60_000L);
        }
        int awards = origin ? removeAwardGroups(server, id) : 0;
        boolean cleared = false;
        int moved = 0;
        if (here == null && !playedSince) {
            moved = moveFiles(server, id, row.resetMs());
        } else if (mine != null && LoginSettle.canReceive(mine)) {
            clearCarried(mine, row);
            cleared = true;
        } else if (mine != null) {
            // logging in here right now (the restore's settle wipes them) or dead (wiped at the respawn's settle); what a first join
            // hands out, which their login did before the account went, follows that wipe
            HANDOUTS.add(id);
            if (mine.isDeadOrDying() && !LoginSettle.isWaiting(id)) {
                LoginSettle.afterRespawn(mine);
            }
        } else if (NetworkStorage.enabled()) {
            com.mrgregles.bsp_core.bank.Bank.refresh(server, id); // wiped elsewhere already and online here: their new account, read again
        }
        post(new PlayerResetEvent.Forgotten(server, id, row.name(), row.resetMs(), origin, row.by()));
        if (cleared && !here.hasDisconnected()) {
            // as a first join: the account and its starting pieces, the first totem and the guide book, the first-join kits
            com.mrgregles.bsp_core.bank.BankEvents.arrive(here);
            com.mrgregles.bsp_core.event.FirstJoinHandler.check(here);
            com.mrgregles.bsp_core.kit.KitGrants.connected(here);
        }
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        BSPCore.LOGGER.info("Player reset of {} ({}) by {}{}: {} totem(s) gone here, {}, {} file(s) moved aside, RankUp {}, {} award group(s) removed",
                row.name(), id, row.by(), origin ? " (typed here)" : "", gone, cleared ? "what they carry wiped" : here != null ? "carried items wiped once they are settled" : "offline here",
                moved, rankUp ? "reset" : "not here", awards);
        return new Applied(gone, cleared, moved, rankUp, awards);
    }

    private static void forgetTotem(TotemLedger ledger, ItemStack st) {
        UUID totemId = TotemIdentity.id(st);
        if (totemId != null) {
            ledger.forget(totemId);
        }
    }

    // ------------------------------------------------------------------ what the player carries

    /**
     * Wipes what the player carries (PR12, PR19): their totems (forgotten in the ledger), the inventory with armour and offhand, the
     * ender chest, Curios slots, XP, effects, every advancement and statistic, health and food full; BSP-Core's keys of their persistent
     * data reset (the season and coin epoch set to the current ones, so no season check or coin strip follows); {@code ResetAt}
     * stamped; an inventory sync mod asked to save it at once; {@link PlayerResetEvent.Cleared} posted.
     */
    public static void clearCarried(ServerPlayer player, PlayerResetRow row) {
        clearCarried(player, row, false);
    }

    /**
     * As {@link #clearCarried(ServerPlayer, PlayerResetRow)}; with {@code keepLiveTotems} the totems they carry are handed back after
     * the wipe, not retired: an inventory sync mod's restore after the login already settled (and handed them their first totem) put
     * the old inventory back, and the totems in it are the live ones BSP-Core handed over (a sync mod never saves one).
     */
    public static void clearCarried(ServerPlayer player, PlayerResetRow row, boolean keepLiveTotems) {
        MinecraftServer server = player.server;
        TotemLedger ledger = TotemLedger.get(server);
        List<ItemStack> live = TotemInventories.removeAll(player);
        if (!keepLiveTotems) {
            live.forEach(st -> forgetTotem(ledger, st));
        }
        player.closeContainer();
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        clearCurios(player);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
        player.experienceProgress = 0;
        player.removeAllEffects();
        player.clearFire();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, null, 0.0F, false, false); // no bed or anchor of the old base
        revokeAdvancements(player);
        resetStats(player);
        resetOwnKeys(player, row.resetMs());
        if (keepLiveTotems) {
            live.forEach(st -> TotemInventories.giveBack(player, st));
        }
        player.inventoryMenu.broadcastChanges();
        saveSynced(player);
        post(new PlayerResetEvent.Cleared(server, player, row.name(), row.resetMs()));
        BSPCore.LOGGER.info("Player reset of {}: what they carry wiped on this server", player.getGameProfile().getName());
    }

    /** Whether what the player carries is still to be wiped here for a reset known here. */
    public static boolean due(ServerPlayer player) {
        PlayerResetRow row = PlayerResetLedger.get(player.server).latest(player.getUUID());
        return row != null && PlayerPersistent.get(player).getLong(TAG_RESET_AT) < row.resetMs();
    }

    /** The settle's first step ({@link AdminService#settle}): wipes what the player carries if a reset is still to be done for them. */
    public static boolean onSettle(ServerPlayer player) {
        if (!due(player) || player.isDeadOrDying()) {
            return false;
        }
        clearCarried(player, PlayerResetLedger.get(player.server).latest(player.getUUID()));
        if (HANDOUTS.remove(player.getUUID())) {
            // the reset landed while they were logging in or dead: their login's account and kit grants came before it (the settle that
            // follows gives the first totem and the guide book)
            com.mrgregles.bsp_core.bank.BankEvents.arrive(player);
            com.mrgregles.bsp_core.kit.KitGrants.connected(player);
        }
        return true;
    }

    /** The ids of the live totems the player carries (inventory and ender chest). */
    private static java.util.Set<UUID> carriedTotems(ServerPlayer player) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (var inv : List.of(player.getInventory(), player.getEnderChestInventory())) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                UUID tid = TotemInventories.isTotem(st) ? TotemIdentity.id(st) : null;
                if (tid != null) {
                    out.add(tid);
                }
            }
        }
        return out;
    }

    /** BSP-Core's keys in the player's persistent data, back to a new player's: only the reset, season and coin epoch marks remain. */
    static void resetOwnKeys(Player player, long resetMs) {
        CompoundTag persisted = PlayerPersistent.get(player);
        for (String key : new ArrayList<>(persisted.getAllKeys())) {
            if (key.startsWith(PlayerPersistent.KEY_PREFIX) || key.equals("RecallReadyAt")) {
                persisted.remove(key);
            }
        }
        CompoundTag root = player.getPersistentData();
        for (String key : new String[] {"SiegeUntil", "SiegeReadyAt", PlayerPersistent.KEY_PREFIX + "SiegeUntil", PlayerPersistent.KEY_PREFIX + "SiegeReadyAt"}) {
            root.remove(key); // where the Siege times were kept before
        }
        SeasonData season = SeasonData.get(player.getServer());
        PlayerPersistent.setSeasonSeen(player, season.number);
        PlayerPersistent.setCoinEpoch(player, season.coinEpoch);
        persisted.putLong(TAG_RESET_AT, resetMs);
        PlayerPersistent.edited(player, TAG_RESET_AT);
    }

    private static void revokeAdvancements(ServerPlayer player) {
        var mine = player.getAdvancements();
        for (var adv : player.server.getAdvancements().getAllAdvancements()) {
            var progress = mine.getOrStartProgress(adv);
            if (progress.hasProgress()) {
                List<String> done = new ArrayList<>();
                progress.getCompletedCriteria().forEach(done::add);
                done.forEach(c -> mine.revoke(adv, c));
            }
        }
    }

    /** Every statistic of the player back to 0 (on this server: statistics are kept per server). */
    @SuppressWarnings("unchecked")
    static void resetStats(ServerPlayer player) {
        var counter = player.getStats();
        try {
            for (java.lang.reflect.Field f : net.minecraft.stats.StatsCounter.class.getDeclaredFields()) {
                if (it.unimi.dsi.fastutil.objects.Object2IntMap.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    var map = (it.unimi.dsi.fastutil.objects.Object2IntMap<Stat<?>>) f.get(counter);
                    for (Stat<?> stat : new ArrayList<>(map.keySet())) {
                        player.resetStat(stat);
                    }
                    return;
                }
            }
            BSPCore.LOGGER.warn("Player reset: the statistics of {} could not be reached; they are kept", player.getGameProfile().getName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            BSPCore.LOGGER.warn("Player reset: the statistics of {} could not be reset: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    /** Empties every Curios slot (and its cosmetic slot) when Curios is installed; read by reflection, as nothing else here needs it. */
    private static void clearCurios(ServerPlayer player) {
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        try {
            Object lazy = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Object handler = ((net.minecraftforge.common.util.LazyOptional<?>) lazy).orElse(null);
            if (handler == null) {
                return;
            }
            Map<?, ?> curios = (Map<?, ?>) handler.getClass().getMethod("getCurios").invoke(handler);
            for (Object stacks : curios.values()) {
                for (String getter : new String[] {"getStacks", "getCosmeticStacks"}) {
                    var items = (net.minecraftforge.items.IItemHandlerModifiable) stacks.getClass().getMethod(getter).invoke(stacks);
                    for (int i = 0; i < items.getSlots(); i++) {
                        items.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.warn("Player reset: the Curios slots of {} could not be emptied: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    /** Asks BSP-InventorySync to save the wiped player now, so a crash before its next save cannot bring the old inventory back. */
    private static void saveSynced(ServerPlayer player) {
        if (!ModList.get().isLoaded("bsp_inventorysync")) {
            return;
        }
        try {
            Class.forName(SYNC_API).getMethod("saveNow", ServerPlayer.class).invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.warn("Player reset: BSP-InventorySync could not be asked to save {}: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    /**
     * Moves this server's own files of an offline player (player data, statistics, advancements) into the world's
     * {@link #BACKUP_DIR}{@code /<uuid>-<ms>/}, so they arrive here as a new player; nothing is deleted. Returns how many moved.
     */
    static int moveFiles(MinecraftServer server, UUID id, long resetMs) {
        Path to = server.getWorldPath(LevelResource.ROOT).resolve(BACKUP_DIR).resolve(id + "-" + resetMs);
        int n = 0;
        Path[] files = {
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat"),
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat_old"),
                server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(id + ".json"),
                server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(id + ".json")};
        String[] names = {"playerdata.dat", "playerdata.dat_old", "stats.json", "advancements.json"};
        for (int i = 0; i < files.length; i++) {
            if (!Files.isRegularFile(files[i])) {
                continue;
            }
            try {
                Files.createDirectories(to);
                Files.move(files[i], to.resolve(names[i]), StandardCopyOption.REPLACE_EXISTING);
                n++;
            } catch (IOException e) {
                BSPCore.LOGGER.error("Player reset: could not move {} aside: {}", files[i], e.toString());
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the sister mods BSP-Core reaches itself

    /** BSP-RankUp's own reset of the player on this server (every count, bonus and rank; back on the first rung). */
    private static boolean rankUpReset(MinecraftServer server, UUID id) {
        if (!ModList.get().isLoaded("bsp_rankup")) {
            return false;
        }
        try {
            Class.forName("com.mrgregles.bsp_rankup.admin.AdminEdits").getMethod("playerReset", MinecraftServer.class, UUID.class).invoke(null, server, id);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.error("Player reset: BSP-RankUp's reset of {} failed: {}", id, e.toString());
            return false;
        }
    }

    /**
     * Takes the player out of the LuckPerms group of every RankUp award (PR15), through LuckPerms' own command so the change is saved,
     * logged and pushed to every server; RankUp's reset moves them back onto the first rung of each ladder itself. Staff, bought ranks
     * and permission nodes stay. Returns how many groups were asked to go.
     */
    private static int removeAwardGroups(MinecraftServer server, UUID id) {
        if (!ModList.get().isLoaded("bsp_rankup") || !ModList.get().isLoaded("luckperms")) {
            return 0;
        }
        List<String> groups = new ArrayList<>();
        try {
            for (Object award : (List<?>) Class.forName("com.mrgregles.bsp_rankup.api.RankUpApi").getMethod("awards", MinecraftServer.class).invoke(null, server)) {
                groups.add((String) award.getClass().getMethod("group").invoke(award));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.error("Player reset: BSP-RankUp's awards could not be read: {}", e.toString());
            return 0;
        }
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (String group : groups) {
            if (group.matches("[A-Za-z0-9_.-]+")) {
                server.getCommands().performPrefixedCommand(source, "lp user " + id + " parent remove " + group);
            }
        }
        return groups.size();
    }

    // ------------------------------------------------------------------ logins

    /**
     * Before anything else hears BSP-InventorySync's restore (kit deliveries among them): a player whose reset is still to be done here
     * is wiped first, so what the login hands them is kept. The settle checks again, for servers without the mod and for a player
     * restored dead (wiped on the respawned entity).
     */
    public static void hookRestore() {
        if (hooked || !ModList.get().isLoaded("bsp_inventorysync")) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) Class.forName(SYNC_RESTORED);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, e -> {
                if (!(e instanceof PlayerEvent pe) || !(pe.getEntity() instanceof ServerPlayer player)) {
                    return;
                }
                if (LoginSettle.isWaiting(player.getUUID())) {
                    onSettle(player); // a login's restore: wiped before anything is handed over
                } else if (due(player) && !player.isDeadOrDying()) {
                    // a restore after the login settled (logins that do not wait for it): the old inventory came back over the wiped
                    // one; wiped again, the live totems kept (their first totem among them). An admin's restore that keeps the
                    // player's ResetAt is not due, so an undo stands.
                    clearCarried(player, PlayerResetLedger.get(player.server).latest(player.getUUID()), true);
                }
            });
            hooked = true;
        } catch (ClassNotFoundException | LinkageError e) {
            BSPCore.LOGGER.warn("BSP-InventorySync is loaded but its restore event was not found; a reset player is wiped at the settle");
        }
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        hookRestore();
    }

    /** The record of resets starts with the server's first start on this version, before any reset can reach it, and is kept. */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        PlayerResetLedger.get(event.getServer()).setDirty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HANDOUTS.remove(event.getEntity().getUUID()); // their next login here settles from the start
    }

    /** Posts an event; a listener that throws is logged, never allowed to stop the reset (Forge stops at the first that throws). */
    private static void post(Event event) {
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException | LinkageError e) {
            BSPCore.LOGGER.error("Player reset: a listener of {} failed: {}", event.getClass().getSimpleName(), e.toString());
        }
    }

    /** Writes the ledger to disk now. */
    private static void saveNow(MinecraftServer server, PlayerResetLedger ledger) {
        try {
            Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
            Files.createDirectories(data);
            ledger.save(data.resolve(BSPCore.MODID + "_player_resets.dat").toFile());
        } catch (IOException | RuntimeException e) {
            BSPCore.LOGGER.warn("Player reset: the record of resets could not be saved at once (the next save writes it): {}", e.toString());
        }
    }

    /** The {@code ResetAt} in this server's own file of an offline player, 0 when none (or no file). */
    static long stampOnDisk(MinecraftServer server, UUID id) {
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try {
            return PlayerPersistent.longInPlayerFile(net.minecraft.nbt.NbtIo.readCompressed(file.toFile()), TAG_RESET_AT);
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** Voids the player's Totem Forge pour where this server has it (the forge's chunk loaded if saved). */
    private static void voidForgePour(MinecraftServer server, UUID id) {
        com.mrgregles.bsp_core.machine.TotemForgeLedger.get(server).pourOf(id).ifPresent(at -> {
            var level = server.getLevel(at.dimension());
            if (level == null) {
                return;
            }
            com.mrgregles.bsp_core.totem.LedgerCheck.loadIfSaved(level, at.pos());
            if (level.getBlockEntity(at.pos()) instanceof com.mrgregles.bsp_core.machine.TotemForgeBlockEntity forge && forge.voidPourOf(id)) {
                BSPCore.LOGGER.info("Player reset: their Totem Forge pour at {} is void", at);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ASKED.clear();
        HANDOUTS.clear();
    }

    /** The command's lines for the admin. */
    public static Component line(String text) {
        return Component.literal(text);
    }
}
