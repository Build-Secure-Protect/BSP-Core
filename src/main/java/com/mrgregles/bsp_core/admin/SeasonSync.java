package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.vault.VaultLedger;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the servers of a network in step, through the shared database. Does nothing when network
 * storage is off.
 *
 * <p>Every {@code scoring.refreshSeconds} each server publishes what its players keep in Coin
 * Vaults here and reads what they keep elsewhere, reads the shared season state, and collects
 * items owed to its online players.
 * <ul>
 *   <li><b>Season number</b>: when the database is ahead, a season was ended on another server:
 *       this server removes its own totems and its players are brought into the new season.</li>
 *   <li><b>Coin epoch</b>: when the database is ahead, a full reset was run on another server:
 *       this server removes its coins too.</li>
 *   <li><b>Prizes and the holder reward interval</b>: the newest change wins.</li>
 * </ul>
 * The database is the authority once it holds a season state. A server that joins a network with
 * a lower season number is treated as one that missed a season end.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class SeasonSync {
    public static final String K_SEASON = "season", K_START = "season_start", K_WINNER = "winner", K_WINNER_POINTS = "winner_points", K_EPOCH = "coin_epoch",
            K_HOLDER_HOURS = "holder_hours", K_HOLDER_LAST = "holder_last", K_PRIZES = "prizes", K_REV = "settings_rev";
    private static int ticks;
    /** True while state read from the database is being applied, so that it is not written straight back. */
    private static boolean applying;

    private SeasonSync() {}

    static boolean applying() {
        return applying;
    }

    private static Map<String, String> state(MinecraftServer server) {
        SeasonData s = SeasonData.get(server);
        Map<String, String> out = new LinkedHashMap<>();
        out.put(K_SEASON, Integer.toString(s.number));
        out.put(K_START, Long.toString(s.startMs));
        out.put(K_WINNER, s.lastWinner);
        out.put(K_WINNER_POINTS, Integer.toString(s.lastWinnerPoints));
        out.put(K_EPOCH, Integer.toString(s.coinEpoch));
        out.put(K_HOLDER_HOURS, Integer.toString(s.holderHours));
        out.put(K_HOLDER_LAST, Long.toString(s.lastHolderMs));
        out.put(K_PRIZES, s.rewards.serializeNBT().toString());
        out.put(K_REV, Long.toString(s.settingsRev));
        return out;
    }

    /** Writes this server's season state to the database. Called after an admin changes something here. */
    public static void publish(MinecraftServer server) {
        if (NetworkStorage.enabled() && !applying) {
            NetworkStorage.putState(state(server), false);
        }
    }

    /** The prize rows changed on this server. */
    static void prizesEdited(MinecraftServer server) {
        if (NetworkStorage.enabled() && !applying) {
            SeasonData s = SeasonData.get(server);
            s.settingsRev = System.currentTimeMillis();
            s.setDirty();
            publish(server);
        }
    }

    private static int num(Map<String, String> db, String key, int fallback) {
        try {
            return Integer.parseInt(db.get(key));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long big(Map<String, String> db, String key, long fallback) {
        try {
            return Long.parseLong(db.get(key));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static void apply(MinecraftServer server, Map<String, String> db) {
        SeasonData s = SeasonData.get(server);
        if (!db.containsKey(K_SEASON)) {
            NetworkStorage.putState(state(server), true); // first server on the network: its state becomes the shared one
            return;
        }
        applying = true;
        try {
            int epoch = num(db, K_EPOCH, s.coinEpoch);
            if (epoch > s.coinEpoch) {
                BSPCore.LOGGER.info("A full reset was run on another server: removing coins here");
                AdminService.wipeCoins(server, epoch);
            }
            s.coinEpoch = epoch;
            int number = num(db, K_SEASON, s.number), ended = s.number;
            s.startMs = big(db, K_START, s.startMs);
            s.lastWinner = db.getOrDefault(K_WINNER, s.lastWinner);
            s.lastWinnerPoints = num(db, K_WINNER_POINTS, s.lastWinnerPoints);
            boolean newSeason = number > s.number;
            s.number = number;
            s.lastHolderMs = big(db, K_HOLDER_LAST, s.lastHolderMs); // exactly the shared value: the automatic payout is claimed by changing it
            long rev = big(db, K_REV, 0);
            boolean prizes = false;
            if (rev > s.settingsRev || newSeason) {
                s.settingsRev = Math.max(rev, s.settingsRev);
                s.holderHours = num(db, K_HOLDER_HOURS, s.holderHours);
                String blob = db.get(K_PRIZES);
                if (blob != null && !blob.equals(s.rewards.serializeNBT().toString())) {
                    try {
                        net.minecraftforge.items.ItemStackHandler read = new net.minecraftforge.items.ItemStackHandler();
                        read.deserializeNBT(TagParser.parseTag(blob));
                        for (int i = 0; i < s.rewards.getSlots(); i++) {
                            s.rewards.setStackInSlot(i, i < read.getSlots() ? read.getStackInSlot(i) : ItemStack.EMPTY);
                        }
                        prizes = true;
                    } catch (Exception e) {
                        BSPCore.LOGGER.error("BSP-Core storage: could not read the shared prize rows: {}", e.toString());
                    }
                }
            }
            s.setDirty();
            if (newSeason) {
                BSPCore.LOGGER.info("Season {} was ended on another server: removing totems here", ended);
                AdminService.wipeTotems(server);
                AdminService.announceSeason(server, ended, s.lastWinner, s.lastWinnerPoints);
            }
            if (prizes || newSeason) {
                AdminService.prizesChanged(); // resend to this server's clients
            }
        } finally {
            applying = false;
        }
    }

    private static void deliver(MinecraftServer server, Map<UUID, List<String>> owed) {
        SeasonData season = SeasonData.get(server);
        owed.forEach((id, encoded) -> {
            List<ItemStack> items = new ArrayList<>();
            for (String snbt : encoded) {
                try {
                    ItemStack stack = ItemStack.of(TagParser.parseTag(snbt));
                    if (!stack.isEmpty()) {
                        items.add(stack);
                    }
                } catch (Exception e) {
                    BSPCore.LOGGER.error("BSP-Core storage: could not read an item owed to {}: {}", id, snbt);
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                AdminService.giveItems(player, items, "message.bsp_core.season.delivery");
            } else if (!items.isEmpty()) {
                season.pendingItems.computeIfAbsent(id, k -> new ArrayList<>()).addAll(items); // they left in the meantime: keep it here for their next login
                season.setDirty();
            }
        });
    }

    /** Collects, now, anything the database holds for the players online here. */
    public static void fetchPending(MinecraftServer server) {
        if (NetworkStorage.enabled()) {
            List<UUID> online = new ArrayList<>();
            server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
            NetworkStorage.takePending(online, owed -> deliver(server, owed));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !NetworkStorage.enabled()) {
            return;
        }
        if (++ticks < Math.max(5, BSPConfig.getOr(BSPConfig.SCORE_REFRESH_SECONDS, 30)) * 20) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        Map<UUID, int[]> mine = new HashMap<>();
        VaultLedger.get(server).all().forEach((id, account) -> {
            if (account.vaultCount() > 0) {
                int[] coins = account.coins(), row = new int[coins.length + 1];
                System.arraycopy(coins, 0, row, 0, coins.length);
                row[coins.length] = account.vaultCount();
                mine.put(id, row);
            }
        });
        NetworkStorage.syncVaults(mine, () -> NetworkStorage.readState(db -> {
            apply(server, db);
            fetchPending(server);
        }));
    }
}
