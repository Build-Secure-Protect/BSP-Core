package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.network.AdminDataPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.score.ScoreService;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.vault.VaultLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the admin panel shows and does. On a single server everything comes from this world's
 * records; with network storage on, {@link SeasonSync} shares seasons, prizes and vault totals
 * between the servers.
 * <ul>
 *   <li><b>The player list</b>: everyone who has been granted a totem, owns one, has a Coin Vault
 *       or is online, with their totems (tier and where they stand), score and vault coins.</li>
 *   <li><b>Reset totems</b>: every totem of one player goes back to Tier I with no upgrades.</li>
 *   <li><b>End season</b>: a full reset. The standings are noted, every totem in the world is
 *       removed, and every player who has had a totem gets exactly one fresh Tier I totem, now if
 *       online and at their next login if not (see {@link #seasonCheck}).</li>
 *   <li><b>Season prizes</b>: an admin fills ten slots each for first, second and third place
 *       ({@link SeasonRewardsMenu}). Ending a season hands each row to the player in that place
 *       and empties the rows.</li>
 *   <li><b>Full reset</b>: End season, and also every Shatter Coin this server can reach is
 *       removed: every Coin Vault and factory coin tray is emptied, interest and recovered coins
 *       are cleared, and every player's inventory and ender chest is stripped of coins, now if
 *       online and at their next login if not. Coins in chests and other storage in the world
 *       are not reached; a full reset is meant to go with a new map.</li>
 *   <li><b>Holder reward</b>: everyone who holds a totem gets, for each totem held, a copy of the
 *       items in the holder row of {@link SeasonRewardsMenu}, now if online and at their next login if
 *       not. An admin can pay it by hand, or set it to repeat every so many hours.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class AdminService {
    private static final int MAX_ROWS = 200, MAX_TOTEMS = 16;
    private static volatile boolean prizesDirty;

    /** The prize rows or the season number changed: every client is sent the new ones on the next tick. */
    public static void prizesChanged() {
        prizesDirty = true;
        if (!SeasonSync.applying()) {
            prizesEditedHere = true; // changed on this server, not copied in from the database
        }
    }

    private static volatile boolean prizesEditedHere;

    private static com.mrgregles.bsp_core.network.PrizeSyncPacket prizePacket(MinecraftServer server) {
        SeasonData season = SeasonData.get(server);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < season.rewards.getSlots(); i++) {
            stacks.add(season.rewards.getStackInSlot(i).copy());
        }
        return new com.mrgregles.bsp_core.network.PrizeSyncPacket(season.number, stacks);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer().getTickCount() % 600 == 0) {
            SeasonData season = SeasonData.get(event.getServer());
            if (season.holderHours > 0 && minutesToHolderReward(season) == 0) {
                MinecraftServer server = event.getServer();
                if (NetworkStorage.enabled()) {
                    // only the server that moves the shared "last paid" time forward pays
                    long was = season.lastHolderMs, now = System.currentTimeMillis();
                    season.lastHolderMs = now; // do not ask again while the answer is on its way
                    NetworkStorage.casState(SeasonSync.K_HOLDER_LAST, Long.toString(was), Long.toString(now), won -> {
                        if (won) {
                            rewardHolders(server, "timer");
                        }
                    });
                } else {
                    rewardHolders(server, "timer");
                }
            }
        }
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && prizesDirty) {
            prizesDirty = false;
            if (prizesEditedHere) {
                prizesEditedHere = false;
                SeasonSync.prizesEdited(event.getServer());
            }
            var packet = prizePacket(event.getServer());
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                BSPNetwork.sendTo(player, packet);
            }
        }
    }

    /** Removes every Shatter Coin from a player's inventory, cursor and ender chest. */
    static void stripCoins(ServerPlayer player, int epoch) {
        player.getInventory().clearOrCountMatchingItems(s -> s.getItem() instanceof com.mrgregles.bsp_core.coin.ShatterCoinItem, -1, player.inventoryMenu.getCraftSlots());
        var ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (ender.getItem(i).getItem() instanceof com.mrgregles.bsp_core.coin.ShatterCoinItem) {
                ender.setItem(i, ItemStack.EMPTY);
            }
        }
        player.inventoryMenu.sendAllDataToRemote();
        PlayerPersistent.setCoinEpoch(player, epoch);
    }

    private AdminService() {}

    // ------------------------------------------------------------------ the panel's data

    public static AdminDataPacket snapshot(MinecraftServer server, ServerPlayer viewer, boolean open) {
        TotemLedger ledger = TotemLedger.get(server);
        VaultLedger vaults = VaultLedger.get(server);
        Map<UUID, List<AdminDataPacket.Totem>> totems = new LinkedHashMap<>();
        Set<UUID> ids = new HashSet<>(ledger.allGranted());
        ledger.allPlaced().forEach((owner, positions) -> {
            List<GlobalPos> sorted = new ArrayList<>(positions);
            sorted.sort(Comparator.comparing((GlobalPos p) -> p.dimension().location().toString()).thenComparing(GlobalPos::pos));
            for (GlobalPos pos : sorted) {
                totems.computeIfAbsent(owner, k -> new ArrayList<>()).add(new AdminDataPacket.Totem(ledger.tierAt(pos), false, pos.dimension().location().toString(), pos.pos()));
            }
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ids.add(player.getUUID());
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (TotemInventories.isTotem(stack)) {
                    totems.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                            .add(new AdminDataPacket.Totem(TotemUpgrades.getTier(stack), true, player.level().dimension().location().toString(), player.blockPosition()));
                }
            }
        }
        ids.addAll(totems.keySet());
        vaults.all().forEach((id, account) -> {
            if (account.vaultCount() > 0) {
                ids.add(id);
            }
        });
        List<AdminDataPacket.Row> rows = new ArrayList<>();
        for (UUID id : ids) {
            List<AdminDataPacket.Totem> list = totems.getOrDefault(id, List.of());
            int points = 0;
            for (AdminDataPacket.Totem t : list) {
                points += ScoreService.tierPoints(t.tier());
            }
            VaultLedger.Account account = vaults.all().get(id);
            // this server's vaults plus, on a network, the player's vaults on the other servers
            int[] coins = account == null ? new int[CoinTier.values().length] : account.coins(), elsewhere = NetworkStorage.vaultCoinsElsewhere(id);
            int value = 0;
            for (CoinTier tier : CoinTier.values()) {
                coins[tier.ordinal()] += elsewhere[tier.ordinal()];
                value += coins[tier.ordinal()] * tier.value();
            }
            rows.add(new AdminDataPacket.Row(id, ScoreService.name(server, id), server.getPlayerList().getPlayer(id) != null, points, list.size(),
                    list.subList(0, Math.min(MAX_TOTEMS, list.size())), coins, value,
                    (account == null ? 0 : account.vaultCount()) + NetworkStorage.vaultBlocksElsewhere(id)));
        }
        rows.sort(Comparator.comparingInt(AdminDataPacket.Row::points).reversed().thenComparing(AdminDataPacket.Row::name, String.CASE_INSENSITIVE_ORDER));
        SeasonData season = SeasonData.get(server);
        return new AdminDataPacket(open, Admins.isAdmin(viewer), season.number, season.day(), season.lastWinner, season.lastWinnerPoints, season.holderHours, minutesToHolderReward(season), decoyRanges(),
                rows.subList(0, Math.min(MAX_ROWS, rows.size())));
    }

    /** The four Decoy Totem compass ranges (0 to 3 Range Coils), from the config. */
    public static int[] decoyRanges() {
        List<? extends Integer> list = com.mrgregles.bsp_core.BSPConfig.DECOY_RANGES.get();
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = com.mrgregles.bsp_core.BSPConfig.levelValue(list, i + 1, 24);
        }
        return out;
    }

    /** Changes one of those ranges and writes it to the server config file. */
    public static void setDecoyRange(int index, int blocks) {
        int[] now = decoyRanges();
        if (index < 0 || index >= now.length) {
            return;
        }
        now[index] = Math.max(1, Math.min(512, blocks));
        List<Integer> list = new ArrayList<>();
        for (int v : now) {
            list.add(v);
        }
        com.mrgregles.bsp_core.BSPConfig.DECOY_RANGES.set(list);
        com.mrgregles.bsp_core.BSPConfig.DECOY_RANGES.save();
    }

    /** Opens the panel for a moderator or admin; tells anyone else they may not. */
    public static void open(ServerPlayer player) {
        if (!Admins.isModerator(player)) {
            player.displayClientMessage(Component.translatable("message.bsp_core.admin.denied").withStyle(ChatFormatting.RED), true);
            return;
        }
        BSPNetwork.sendTo(player, snapshot(player.server, player, true));
    }

    // ------------------------------------------------------------------ actions

    /** Puts every totem of {@code target} back to Tier I with no upgrades. Returns how many were reset. */
    public static int resetTotems(MinecraftServer server, UUID target) {
        int n = 0;
        for (GlobalPos pos : new ArrayList<>(TotemLedger.get(server).placedFor(target))) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level != null && level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity totem) {
                totem.resetUpgrades();
                totem.setTier(0);
                n++;
            }
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            for (int i = 0; i < online.getInventory().getContainerSize(); i++) {
                ItemStack stack = online.getInventory().getItem(i);
                if (TotemInventories.isTotem(stack)) {
                    stack.getOrCreateTag().put(TotemUpgrades.TAG_UPGRADES, TotemUpgrades.stamp(new CompoundTag()));
                    n++;
                }
            }
            online.inventoryMenu.sendAllDataToRemote();
        }
        ScoreService.markDirty();
        return n;
    }

    private static void giveFreshTotem(ServerPlayer player) {
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        new TotemOwner(player.getUUID(), player.getGameProfile().getName()).applyTo(totem);
        TotemInventories.giveBack(player, totem);
        player.displayClientMessage(Component.translatable("message.bsp_core.season.fresh_totem").withStyle(ChatFormatting.GOLD), false);
        ScoreService.markDirty();
    }

    /**
     * Brings a player up to the current season. A player who last played in an earlier season has
     * any totem they still carry removed and is given their one fresh Tier I totem. On a network
     * the database decides, so they get it once, on whichever server they join first.
     */
    public static void seasonCheck(ServerPlayer player) {
        SeasonData season = SeasonData.get(player.server);
        int number = season.number;
        if (PlayerPersistent.seasonSeen(player) >= number) {
            return;
        }
        if (number <= 1) {
            PlayerPersistent.setSeasonSeen(player, number);
            return;
        }
        if (NetworkStorage.enabled()) {
            UUID id = player.getUUID();
            NetworkStorage.claimSeason(number, id, result -> {
                if (result == NetworkStorage.CLAIM_FAILED || player.hasDisconnected() || SeasonData.get(player.server).number != number) {
                    return; // database unreachable, or things moved on: the next login tries again
                }
                if (PlayerPersistent.seasonSeen(player) >= number) {
                    return; // their first-join totem arrived in the meantime, and it is this season's
                }
                TotemInventories.removeAll(player);
                PlayerPersistent.setSeasonSeen(player, number);
                if (result == NetworkStorage.CLAIM_OK) {
                    giveFreshTotem(player);
                }
            });
            return;
        }
        boolean granted = TotemLedger.get(player.server).hasBeenGranted(player.getUUID()) || PlayerPersistent.isTotemGranted(player);
        PlayerPersistent.setSeasonSeen(player, number);
        if (granted) { // a player who has never had a totem gets their first one from the first-join grant instead
            TotemInventories.removeAll(player);
            giveFreshTotem(player);
        }
    }

    /** Removes every totem on this server: placed, lying on the ground in loaded chunks, and carried. */
    public static void wipeTotems(MinecraftServer server) {
        TotemLedger ledger = TotemLedger.get(server);
        Map<UUID, Set<GlobalPos>> placed = new HashMap<>();
        ledger.allPlaced().forEach((owner, positions) -> placed.put(owner, new HashSet<>(positions)));
        for (Set<GlobalPos> positions : placed.values()) {
            for (GlobalPos pos : positions) {
                ServerLevel level = server.getLevel(pos.dimension());
                if (level != null && level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity) {
                    level.removeBlock(pos.pos(), false);
                }
                ledger.recordRemoved(pos);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> drops = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ShatterTotemItemEntity) {
                    drops.add(e);
                }
            }
            drops.forEach(Entity::discard);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TotemInventories.removeAll(player);
        }
        ScoreService.markDirty();
    }

    /** Gives items to a player wherever they are: now if they are on this server, otherwise when they next log in (on any server of a network). */
    public static void deliver(MinecraftServer server, UUID id, List<ItemStack> items, String messageKey) {
        if (items.isEmpty()) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            giveItems(online, items, messageKey);
        } else if (NetworkStorage.enabled()) {
            List<String> encoded = new ArrayList<>();
            items.forEach(stack -> encoded.add(stack.save(new CompoundTag()).toString()));
            NetworkStorage.addPending(id, encoded);
        } else {
            SeasonData season = SeasonData.get(server);
            List<ItemStack> owed = season.pendingItems.computeIfAbsent(id, k -> new ArrayList<>());
            items.forEach(stack -> owed.add(stack.copy()));
            season.setDirty();
        }
    }

    public static void giveItems(ServerPlayer player, List<ItemStack> items, String messageKey) {
        for (ItemStack stack : items) {
            ItemStack out = stack.copy();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    /** Tells everyone a season is over, and brings every online player into the new one. */
    public static void announceSeason(MinecraftServer server, int ended, String winner, int points) {
        Component note = winner.isEmpty() ? Component.translatable("message.bsp_core.season.ended", ended)
                : Component.translatable("message.bsp_core.season.ended_winner", ended, winner, points);
        server.getPlayerList().broadcastSystemMessage(note.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            seasonCheck(player);
        }
    }

    /** Ends the season: see the class comment. Returns how many players are online to get their fresh totem at once. */
    public static int endSeason(MinecraftServer server, String by) {
        SeasonData season = SeasonData.get(server);
        // on a network the standings are the whole network's board
        List<ScoreService.Entry> standings = NetworkStorage.enabled() ? ScoreService.board() : ScoreService.local(server);
        season.lastWinner = standings.isEmpty() ? "" : standings.get(0).name();
        season.lastWinnerPoints = standings.isEmpty() ? 0 : standings.get(0).points();

        // prizes: each of the top three gets their row, and the rows are emptied for the next season
        for (int place = 0; place < SeasonData.PLACES && place < standings.size(); place++) {
            List<ItemStack> prize = new ArrayList<>();
            for (int i = 0; i < SeasonData.PER_PLACE; i++) {
                ItemStack stack = season.rewards.getStackInSlot(place * SeasonData.PER_PLACE + i);
                if (!stack.isEmpty()) {
                    prize.add(stack.copy());
                }
            }
            deliver(server, standings.get(place).id(), prize, "message.bsp_core.season.prize");
            BSPCore.LOGGER.info("Season prize for place {}: {} stacks to {}", place + 1, prize.size(), standings.get(place).name());
        }
        for (int i = 0; i < Math.min(standings.size(), SeasonData.PLACES) * SeasonData.PER_PLACE; i++) {
            season.rewards.setStackInSlot(i, ItemStack.EMPTY); // rows of places nobody reached stay for next season
        }

        wipeTotems(server);
        int ended = season.number;
        season.number++;
        season.startMs = System.currentTimeMillis();
        season.setDirty();
        prizesChanged();
        SeasonSync.publish(server); // the other servers of a network pick the new season up and wipe their own totems
        announceSeason(server, ended, season.lastWinner, season.lastWinnerPoints);
        BSPCore.LOGGER.info("Season {} ended by {}. Winner: {} ({} points).", ended, by, season.lastWinner, season.lastWinnerPoints);
        return server.getPlayerList().getPlayerCount();
    }

    /** Removes every Shatter Coin this server can reach: vaults, interest, factory trays and online players. Offline players lose theirs at login. */
    public static void wipeCoins(MinecraftServer server, int epoch) {
        VaultLedger vaults = VaultLedger.get(server);
        int blocks = 0, trays = 0;
        for (VaultLedger.Account account : vaults.all().values()) {
            for (GlobalPos pos : account.positions()) {
                ServerLevel level = server.getLevel(pos.dimension());
                if (level != null && level.getBlockEntity(pos.pos()) instanceof com.mrgregles.bsp_core.vault.CoinVaultBlockEntity vault) {
                    for (int i = 0; i < vault.getItems().getSlots(); i++) {
                        vault.getItems().setStackInSlot(i, ItemStack.EMPTY);
                    }
                    blocks++;
                }
            }
        }
        vaults.clearEarnings();
        for (Set<GlobalPos> positions : com.mrgregles.bsp_core.data.FactoryLedger.get(server).all().values()) {
            for (GlobalPos pos : new ArrayList<>(positions)) {
                ServerLevel level = server.getLevel(pos.dimension());
                if (level != null && level.getBlockEntity(pos.pos()) instanceof com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity factory) {
                    factory.getItems().setStackInSlot(com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    trays++;
                }
            }
        }
        SeasonData season = SeasonData.get(server);
        season.coinEpoch = epoch;
        season.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            stripCoins(player, epoch);
        }
        BSPCore.LOGGER.info("Coin wipe (epoch {}): emptied {} vault blocks and {} factory trays; coins removed from players", epoch, blocks, trays);
    }

    /** End season, plus every Shatter Coin removed. Returns how many players are online to get their fresh totem at once. */
    public static int fullReset(MinecraftServer server, String by) {
        BSPCore.LOGGER.info("FULL RESET by {}", by);
        wipeCoins(server, SeasonData.get(server).coinEpoch + 1);
        return endSeason(server, by);
    }

    /** Opens the season prize rows: admins can change them, moderators can only look. */
    public static void openRewards(ServerPlayer player) {
        SeasonData season = SeasonData.get(player.server);
        boolean edit = Admins.isAdmin(player);
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new SeasonRewardsMenu(id, inv, season.rewards, edit), Component.translatable("gui.bsp_core.rewards.title", season.number)),
                buf -> buf.writeBoolean(edit));
    }

    /**
     * Pays everyone who holds a totem now one copy of the holder row for each totem they hold.
     * Returns how many players were paid, or -1 if the row is empty.
     */
    public static int rewardHolders(MinecraftServer server, String by) {
        SeasonData season = SeasonData.get(server);
        List<ItemStack> set = new ArrayList<>();
        for (int i = 0; i < SeasonData.PER_PLACE; i++) {
            ItemStack stack = season.rewards.getStackInSlot(SeasonData.HOLDER_ROW * SeasonData.PER_PLACE + i);
            if (!stack.isEmpty()) {
                set.add(stack.copy());
            }
        }
        season.lastHolderMs = System.currentTimeMillis();
        season.setDirty();
        SeasonSync.publish(server);
        if (set.isEmpty()) {
            return -1;
        }
        int paid = 0;
        for (ScoreService.Entry e : NetworkStorage.enabled() ? ScoreService.board() : ScoreService.local(server)) {
            if (e.totems() <= 0) {
                continue;
            }
            paid++;
            // one copy of the row for every totem held, so holding more pays more
            List<ItemStack> share = new ArrayList<>();
            for (int n = 0; n < e.totems(); n++) {
                set.forEach(s -> share.add(s.copy()));
            }
            deliver(server, e.id(), share, "message.bsp_core.season.reward");
        }
        BSPCore.LOGGER.info("Holder reward ({}): {} players paid", by, paid);
        return paid;
    }

    public static void setHolderHours(MinecraftServer server, int hours) {
        SeasonData season = SeasonData.get(server);
        season.holderHours = Math.max(0, Math.min(999, hours));
        season.lastHolderMs = System.currentTimeMillis(); // the first automatic payout is one full interval from now
        season.settingsRev = System.currentTimeMillis();
        season.setDirty();
        SeasonSync.publish(server);
    }

    /** Minutes until the next automatic holder reward, or -1 when it is off. */
    public static int minutesToHolderReward(SeasonData season) {
        if (season.holderHours <= 0) {
            return -1;
        }
        return (int) Math.max(0, (season.lastHolderMs + season.holderHours * 3_600_000L - System.currentTimeMillis()) / 60_000L);
    }

    /** Hands over whatever a player missed while offline. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SeasonData season = SeasonData.get(player.server);
        BSPNetwork.sendTo(player, prizePacket(player.server));
        if (PlayerPersistent.coinEpoch(player) < season.coinEpoch) {
            stripCoins(player, season.coinEpoch); // a full reset happened while they were away
        }
        seasonCheck(player);
        List<ItemStack> owed = season.pendingItems.remove(player.getUUID());
        if (owed != null && !owed.isEmpty()) {
            giveItems(player, owed, "message.bsp_core.season.delivery");
            season.setDirty();
        }
        SeasonSync.fetchPending(player.server);
    }
}
