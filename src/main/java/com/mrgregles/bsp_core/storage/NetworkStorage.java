package com.mrgregles.bsp_core.storage;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Optional network-wide storage. BSP-Core always keeps its records in the world (the ledgers), so
 * a single server needs nothing more. With {@code storage.mode = "mysql"} the same records are also
 * written to a MySQL database shared by every server of the network, which makes these things
 * network-wide: one first-join totem per player, the factory slice limit, the list of placed
 * totems, the leaderboard, Coin Vault totals and the vault block limit, seasons and full resets,
 * the season prizes and holder reward, and the delivery of items owed to a player.
 *
 * <p>All database work happens on one background thread. Nothing here blocks the server thread;
 * answers come back through callbacks that run on the server thread. If the database cannot be
 * reached the server keeps working from its local records and logs the problem.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class NetworkStorage {
    /** Result of asking the network whether this server may hand out a first-join totem. */
    public static final int CLAIM_GRANTED_ELSEWHERE = 0, CLAIM_OK = 1, CLAIM_FAILED = -1;

    @Nullable
    private static volatile MySqlStore store;
    private static volatile RedisLink redis;
    @Nullable
    private static ExecutorService worker;
    @Nullable
    private static MinecraftServer server;
    private static final Map<UUID, Integer> slicesElsewhere = new ConcurrentHashMap<>();
    /** What each player keeps in Coin Vaults on the other servers, as last read: five coin counts, blocks, and the Illyrium-interest flag. */
    private static volatile Map<UUID, int[]> vaultsElsewhere = Map.of();
    private static final int[] NO_VAULTS = new int[7];
    /** The steals running on the other servers, as last read. */
    private static volatile List<RemoteSteal> stealsElsewhere = List.of();

    /** A steal running on another server of the network: where, whose totem, who is stealing, and when it started and is expected to end (wall-clock ms). */
    public record RemoteSteal(String serverId, String dimension, int x, int y, int z, @Nullable UUID owner, UUID thief, String thiefName, long startedMs, long endsMs) {}

    private NetworkStorage() {}

    private interface Work {
        void run(MySqlStore s) throws SQLException;
    }

    private static synchronized ExecutorService worker() {
        if (worker == null || worker.isShutdown()) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BSP-Core storage");
                t.setDaemon(true);
                return t;
            });
        }
        return worker;
    }

    private static MySqlStore fromConfig() {
        return new MySqlStore(BSPConfig.STORAGE_HOST.get(), BSPConfig.STORAGE_PORT.get(), BSPConfig.STORAGE_DATABASE.get(), BSPConfig.STORAGE_USER.get(),
                BSPConfig.STORAGE_PASSWORD.get(), BSPConfig.STORAGE_TABLE_PREFIX.get(), BSPConfig.STORAGE_SSL.get(), BSPConfig.STORAGE_SERVER_ID.get());
    }

    /** True while this server is writing to and reading from the network database. */
    public static boolean enabled() {
        return store != null;
    }

    private static void onServer(Runnable r) {
        MinecraftServer s = server;
        if (s != null) {
            s.execute(r);
        }
    }

    /** Runs database work in the background if network storage is on; problems are logged, never thrown. */
    private static void async(String what, Work work) {
        MySqlStore s = store;
        if (s == null) {
            return;
        }
        worker().execute(() -> {
            try {
                work.run(s);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not {}: {}", what, e.toString());
            }
        });
    }

    /** Tells the other servers something changed, over Redis if it is on; harmless without it. Runs on the storage thread. */
    private static void publish(String kind, String payload) {
        RedisLink r = redis;
        if (r != null) {
            worker().execute(() -> r.publish(kind, payload));
        }
    }

    /** A message from another server (Redis thread): refresh what it names, on the server thread. */
    private static void onRedisMessage(String[] msg) {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        String kind = msg[0], payload = msg[2];
        srv.execute(() -> {
            switch (kind) {
                case "bank", "box" -> {
                    try {
                        UUID id = UUID.fromString(payload);
                        if (srv.getPlayerList().getPlayer(id) != null) {
                            com.mrgregles.bsp_core.bank.Bank.refresh(srv, id);
                            if (kind.equals("box") && srv.getPlayerList().getPlayer(id).containerMenu instanceof com.mrgregles.bsp_core.bank.DepositBoxMenu) {
                                srv.getPlayerList().getPlayer(id).closeContainer(); // the box changed elsewhere: reopen to see it
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        // not a player id
                    }
                }
                case "notice" -> collectNotices(srv);
                case "steal" -> refreshSteals();
                case "shop" -> {
                    if (payload.startsWith("-")) {
                        com.mrgregles.bsp_core.shop.ShopLedger.get(srv).remove(payload.substring(1));
                    } else {
                        shopReload(payload);
                    }
                }
                default -> { } // shops and later kinds
            }
        });
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        if (!"mysql".equalsIgnoreCase(BSPConfig.STORAGE_MODE.get())) {
            return;
        }
        worker().execute(() -> {
            MySqlStore s = null;
            try {
                s = fromConfig();
                s.ensureSchema();
                boolean migrated = s.hasMigrated();
                store = s;
                BSPCore.LOGGER.info("BSP-Core storage: connected to MySQL {}", s.describe());
                if (BSPConfig.REDIS_ENABLED.get()) {
                    RedisLink r = new RedisLink(BSPConfig.REDIS_HOST.get(), BSPConfig.REDIS_PORT.get(), BSPConfig.REDIS_PASSWORD.get(), BSPConfig.REDIS_SSL.get(),
                            BSPConfig.REDIS_DB.get(), BSPConfig.REDIS_CHANNEL.get(), BSPConfig.STORAGE_SERVER_ID.get(), NetworkStorage::onRedisMessage);
                    redis = r;
                    r.start();
                }
                if (!migrated) {
                    BSPCore.LOGGER.warn("BSP-Core storage: this server's local records are not in the database yet. Run /bsp storage migrate.");
                }
                shopReload(null);
                s.clearSteals();
                stealsElsewhere = List.copyOf(s.readSteals(System.currentTimeMillis()));
            } catch (SQLException | RuntimeException e) {
                if (s != null) {
                    s.close();
                }
                BSPCore.LOGGER.error("BSP-Core storage: storage.mode is \"mysql\" but the database could not be used ({}). "
                        + "Running on local records only; network-wide limits are NOT enforced until this is fixed and the server restarted.", e.toString());
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RedisLink r = redis;
        redis = null;
        if (r != null) {
            r.close();
        }
        MySqlStore s = store;
        store = null;
        ExecutorService w = worker;
        if (w != null) {
            if (s != null) {
                w.execute(s::close);
            }
            w.shutdown();
        }
        slicesElsewhere.clear();
        vaultsElsewhere = Map.of();
        stealsElsewhere = List.of();
        server = null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshSlices(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        slicesElsewhere.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ first-join grants

    /**
     * Asks the network whether {@code player} may be given a first-join totem here. {@code then}
     * runs on the server thread with {@link #CLAIM_OK} (recorded; hand it out), {@link
     * #CLAIM_GRANTED_ELSEWHERE} or {@link #CLAIM_FAILED} (database unreachable; try again next join).
     */
    public static void claimGrant(UUID player, IntConsumer then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(CLAIM_OK);
            return;
        }
        worker().execute(() -> {
            int result;
            try {
                result = s.claimGrant(player) ? CLAIM_OK : CLAIM_GRANTED_ELSEWHERE;
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not check the first-join grant for {}: {}", player, e.toString());
                result = CLAIM_FAILED;
            }
            int r = result;
            onServer(() -> then.accept(r));
        });
    }

    public static void clearGrant(UUID player) {
        async("clear a grant", s -> s.clearGrant(player));
    }

    // ------------------------------------------------------------------ mirrors of the local ledgers

    public static void totemPlaced(UUID owner, GlobalPos pos) {
        async("record a placed totem", s -> s.totemPlaced(owner, pos));
    }

    public static void totemRemoved(GlobalPos pos) {
        async("remove a placed totem", s -> s.totemRemoved(pos));
    }

    public static void sliceAdded(UUID owner, GlobalPos pos) {
        async("record a factory slice", s -> s.sliceAdded(owner, pos));
    }

    public static void sliceRemoved(GlobalPos pos) {
        async("remove a factory slice", s -> s.sliceRemoved(pos));
    }

    /** Factory slices the player owns on other servers, as last read from the database (0 when storage is local). */
    public static int slicesElsewhere(UUID owner) {
        return slicesElsewhere.getOrDefault(owner, 0);
    }

    /** Re-reads how many slices the player owns on other servers. Called on login and can be called again at any time. */
    public static void refreshSlices(UUID owner) {
        async("count factory slices", s -> slicesElsewhere.put(owner, s.slicesElsewhere(owner)));
    }

    /**
     * Reads from the database, now, how many slices the player owns on other servers, and hands the
     * answer to {@code then} on the server thread. Used every time a slice is placed, so the limit
     * holds even for a player who never logs out. Does nothing when storage is local or the
     * database cannot be reached.
     */
    public static void countSlicesElsewhere(UUID owner, IntConsumer then) {
        MySqlStore s = store;
        if (s == null) {
            return;
        }
        worker().execute(() -> {
            try {
                int n = s.slicesElsewhere(owner);
                slicesElsewhere.put(owner, n);
                onServer(() -> then.accept(n));
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not count factory slices for {}: {}", owner, e.toString());
            }
        });
    }

    /**
     * Publishes this server's score rows and hands the whole network's board to {@code then} on the
     * server thread. If the database cannot be reached, {@code then} gets this server's own rows.
     */
    public static void syncScores(List<com.mrgregles.bsp_core.score.ScoreService.Entry> mine, Consumer<List<com.mrgregles.bsp_core.score.ScoreService.Entry>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(mine);
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.score.ScoreService.Entry> result;
            try {
                result = s.syncScores(mine);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not sync the leaderboard: {}", e.toString());
                result = mine;
            }
            List<com.mrgregles.bsp_core.score.ScoreService.Entry> out = result;
            onServer(() -> then.accept(out));
        });
    }

    // ------------------------------------------------------------------ messages that follow a player across servers

    private static int noticeTicks;

    /**
     * Tells a player something wherever they are. If they are on this server they see it at once.
     * If not, and network storage is on, the message is left in the database marked with this
     * server's name, and the server they are on shows it within {@code storage.noticeSeconds}.
     * Used for steal warnings and vault alarms, which matter most when the owner is elsewhere.
     */
    public static void tell(MinecraftServer srv, UUID player, net.minecraft.network.chat.Component message) {
        ServerPlayer online = srv.getPlayerList().getPlayer(player);
        if (online != null) {
            online.displayClientMessage(message, false);
            return;
        }
        if (store == null) {
            return;
        }
        String json = net.minecraft.network.chat.Component.Serializer.toJson(
                net.minecraft.network.chat.Component.translatable("message.bsp_core.network.from", BSPConfig.STORAGE_SERVER_ID.get(), message));
        async("leave a message for a player", s -> {
            s.addNotice(player, json);
            publish("notice", player.toString());
        });
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || store == null
                || ++noticeTicks < Math.max(1, BSPConfig.getOr(BSPConfig.STORAGE_NOTICE_SECONDS, 3)) * 20) {
            return;
        }
        noticeTicks = 0;
        collectNotices(event.getServer());
        refreshSteals();
    }

    // ------------------------------------------------------------------ steals in progress, visible from every server

    /** A steal began here: every server is told, so a teleport mod can keep its raid rule across the network. */
    @SubscribeEvent
    public static void onStealStarted(com.mrgregles.bsp_core.api.event.TotemEvent.StealStarted event) {
        if (store == null) {
            return;
        }
        UUID owner = event.getOwner(), thief = event.getThief();
        String name = event.getThiefName();
        GlobalPos where = event.getWhere();
        long now = System.currentTimeMillis(), ends = now + event.getSeconds() * 1000L;
        async("record a steal", s -> {
            s.stealStarted(owner, thief, name, where, now, ends);
            publish("steal", "");
        });
    }

    @SubscribeEvent
    public static void onStealEnded(com.mrgregles.bsp_core.api.event.TotemEvent.StealEnded event) {
        if (store == null) {
            return;
        }
        GlobalPos where = event.getWhere();
        async("forget a steal", s -> {
            s.stealEnded(where);
            publish("steal", "");
        });
    }

    /** The steals running on the other servers, as last read (every {@code storage.noticeSeconds}, and at once on a Redis notice). Empty without network storage. */
    public static List<RemoteSteal> stealsElsewhere() {
        return stealsElsewhere;
    }

    private static void refreshSteals() {
        async("read the steals in progress", s -> stealsElsewhere = List.copyOf(s.readSteals(System.currentTimeMillis())));
    }

    /** Delivers the messages other servers left for the players online here. Polled every few seconds, and at once on a Redis notice. */
    private static void collectNotices(MinecraftServer srv) {
        List<UUID> online = new java.util.ArrayList<>();
        srv.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
        if (online.isEmpty()) {
            return;
        }
        async("collect messages for players", s -> {
            Map<UUID, List<String>> notices = s.takeNotices(online);
            if (!notices.isEmpty()) {
                onServer(() -> notices.forEach((id, list) -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(id);
                    for (String json : list) {
                        net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.Serializer.fromJson(json);
                        if (p != null && c != null) {
                            p.displayClientMessage(c, false);
                        }
                    }
                }));
            }
        });
    }

    // ------------------------------------------------------------------ coin vaults

    /** Publishes this server's vault totals, refreshes what is known of the other servers, then runs {@code then} on the server thread. */
    public static void syncVaults(Map<UUID, int[]> mine, Runnable then) {
        MySqlStore s = store;
        if (s == null) {
            return;
        }
        worker().execute(() -> {
            try {
                vaultsElsewhere = Map.copyOf(s.syncVaults(mine));
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not sync Coin Vault totals: {}", e.toString());
            }
            onServer(then);
        });
    }

    /**
     * Writes one player's vault totals for this server to the database at once, so the other
     * servers see a vault block the moment it is placed or broken. If {@code then} is given it
     * hears, on the server thread, how many vault blocks the player owns on the other servers.
     */
    public static void putVaultRow(UUID owner, int[] row, @Nullable IntConsumer then) {
        int[] copy = row.clone();
        async("write Coin Vault totals", s -> {
            int elsewhere = s.putVaultRow(owner, copy);
            if (then != null) {
                onServer(() -> then.accept(elsewhere));
            }
        });
    }

    /** Coins of each tier the player keeps in vaults on other servers (zeros when storage is local). */
    public static int[] vaultCoinsElsewhere(UUID owner) {
        return vaultsElsewhere.getOrDefault(owner, NO_VAULTS);
    }

    public static int vaultBlocksElsewhere(UUID owner) {
        return vaultsElsewhere.getOrDefault(owner, NO_VAULTS)[5];
    }

    /** True if another server is the one that pays this player's Illyrium interest, so that it is only paid once on the network. */
    public static boolean illyriumPaidElsewhere(UUID owner) {
        return vaultsElsewhere.getOrDefault(owner, NO_VAULTS)[6] != 0;
    }

    // ------------------------------------------------------------------ seasons

    /**
     * Asks the network whether {@code player} should be given their fresh totem for {@code season}
     * here. {@code then} runs on the server thread with {@link #CLAIM_OK}, {@link
     * #CLAIM_GRANTED_ELSEWHERE} (already given, or the player has never had a totem) or {@link #CLAIM_FAILED}.
     */
    public static void claimSeason(int season, UUID player, IntConsumer then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(CLAIM_FAILED);
            return;
        }
        worker().execute(() -> {
            int result;
            try {
                result = s.claimSeason(season, player) ? CLAIM_OK : CLAIM_GRANTED_ELSEWHERE;
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not check the season totem for {}: {}", player, e.toString());
                result = CLAIM_FAILED;
            }
            int r = result;
            onServer(() -> then.accept(r));
        });
    }

    public static void putState(Map<String, String> values, boolean onlyIfAbsent) {
        Map<String, String> copy = new java.util.LinkedHashMap<>(values);
        async("write the season state", s -> s.putState(copy, onlyIfAbsent));
    }

    /** Reads the shared season state and hands it to {@code then} on the server thread. Nothing happens if the database cannot be reached. */
    public static void readState(Consumer<Map<String, String>> then) {
        async("read the season state", s -> {
            Map<String, String> state = s.readState();
            onServer(() -> then.accept(state));
        });
    }

    /** Changes a state key from {@code expected} to {@code value}; {@code then} hears whether this server was the one to do it. */
    public static void casState(String key, String expected, String value, Consumer<Boolean> then) {
        async("update the season state", s -> {
            boolean won = s.casState(key, expected, value);
            onServer(() -> then.accept(won));
        });
    }

    /** Leaves items (as SNBT) for a player to be handed over on whichever server they are on or next join. */
    public static void addPending(UUID player, List<String> items) {
        List<String> copy = List.copyOf(items);
        async("store items owed to a player", s -> s.addPending(player, copy));
    }

    /** Takes whatever is waiting for the given players and hands it to {@code then} on the server thread. */
    public static void takePending(List<UUID> players, Consumer<Map<UUID, List<String>>> then) {
        List<UUID> copy = List.copyOf(players);
        async("collect items owed to players", s -> {
            Map<UUID, List<String>> owed = s.takePending(copy);
            if (!owed.isEmpty()) {
                onServer(() -> then.accept(owed));
            }
        });
    }

    // ------------------------------------------------------------------ operator tools

    /** Tries to connect with the configured credentials and reports the outcome on the server thread. */
    public static void test(MinecraftServer srv, Consumer<String> report) {
        worker().execute(() -> {
            String line;
            try (MySqlStore s = fromConfig()) {
                s.ensureSchema();
                line = "Connected: " + s.describe() + (s.hasMigrated() ? ". This server has migrated." : ". This server has not migrated yet.");
            } catch (SQLException | RuntimeException e) {
                line = "Could not connect: " + e.getMessage();
            }
            if (BSPConfig.REDIS_ENABLED.get()) {
                try (RedisLink r = new RedisLink(BSPConfig.REDIS_HOST.get(), BSPConfig.REDIS_PORT.get(), BSPConfig.REDIS_PASSWORD.get(), BSPConfig.REDIS_SSL.get(),
                        BSPConfig.REDIS_DB.get(), BSPConfig.REDIS_CHANNEL.get(), BSPConfig.STORAGE_SERVER_ID.get(), m -> { })) {
                    line += " Redis: " + r.ping() + " from " + BSPConfig.REDIS_HOST.get() + ":" + BSPConfig.REDIS_PORT.get() + ".";
                } catch (java.io.IOException | RuntimeException e) {
                    line += " Redis: could not connect: " + e.getMessage() + ".";
                }
            }
            String out = line;
            srv.execute(() -> report.accept(out));
        });
    }

    /**
     * Copies this server's local records into the database and merges them with what is there. Works
     * in either storage mode, so it can be run before switching to "mysql". Refuses to run a second
     * time unless {@code force} is set. The local records are left as they are.
     */
    public static void migrate(MinecraftServer srv, boolean force, Consumer<List<String>> report) {
        TotemLedger totems = TotemLedger.get(srv);
        FactoryLedger factories = FactoryLedger.get(srv);
        Set<UUID> grants = new HashSet<>(totems.allGranted());
        Map<UUID, Set<GlobalPos>> placed = copy(totems.allPlaced()), slices = copy(factories.all());
        worker().execute(() -> {
            List<String> lines;
            try (MySqlStore s = fromConfig()) {
                s.ensureSchema();
                if (s.hasMigrated() && !force) {
                    lines = List.of("This server has already migrated to " + s.describe() + ".", "Nothing was changed. Use /bsp storage migrate force to copy the local records again.");
                } else {
                    int[] n = s.migrate(grants, placed, slices);
                    lines = List.of("Migrated to " + s.describe() + ".",
                            "First-join grants: " + n[0] + " added, " + n[1] + " already in the database.",
                            "Placed totems written: " + n[2] + ". Factory slices written: " + n[3] + ".",
                            "The local records were not changed and stay as a backup.",
                            enabled() ? "Network storage is on." : "Now set storage.mode = \"mysql\" in the server config and restart the server.");
                }
            } catch (SQLException | RuntimeException e) {
                lines = List.of("Migration failed, nothing was written: " + e.getMessage());
            }
            List<String> out = lines;
            srv.execute(() -> report.accept(out));
        });
    }

    // ------------------------------------------------------------------ bank

    /** Opens or refreshes the account; {@code then} gets whether the starting pieces were given now, and the row (null if unreachable). */
    public static void bankEnsure(UUID id, String name, long starting, java.util.function.BiConsumer<Boolean, com.mrgregles.bsp_core.bank.BankAccount> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(false, null);
            return;
        }
        worker().execute(() -> {
            boolean inserted = false;
            com.mrgregles.bsp_core.bank.BankAccount row = null;
            try {
                inserted = s.bankEnsure(id, name, starting);
                row = s.bankRead(id);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not open the bank account of {}: {}", name, e.toString());
            }
            boolean i = inserted;
            com.mrgregles.bsp_core.bank.BankAccount r = row;
            onServer(() -> then.accept(i, r));
        });
    }

    public static void bankRead(UUID id, Consumer<com.mrgregles.bsp_core.bank.BankAccount> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.bank.BankAccount row = null;
            try {
                row = s.bankRead(id);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read a bank account: {}", e.toString());
            }
            com.mrgregles.bsp_core.bank.BankAccount r = row;
            onServer(() -> then.accept(r));
        });
    }

    /** Applies the deltas atomically; {@code then} gets the row after, or null when refused or unreachable. */
    public static void bankAdjust(UUID id, long dPieces, int[] dCoins, Consumer<com.mrgregles.bsp_core.bank.BankAccount> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.bank.BankAccount row = null;
            try {
                row = s.bankAdjust(id, dPieces, dCoins);
                if (row != null) {
                    publish("bank", id.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not change a bank balance: {}", e.toString());
            }
            com.mrgregles.bsp_core.bank.BankAccount r = row;
            onServer(() -> then.accept(r));
        });
    }

    public static void bankSet(UUID id, long pieces, Consumer<com.mrgregles.bsp_core.bank.BankAccount> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.bank.BankAccount row = null;
            try {
                row = s.bankSet(id, pieces);
                publish("bank", id.toString());
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not set a bank balance: {}", e.toString());
            }
            com.mrgregles.bsp_core.bank.BankAccount r = row;
            onServer(() -> then.accept(r));
        });
    }

    public static void bankTransfer(UUID from, UUID to, long amount, Consumer<com.mrgregles.bsp_core.bank.BankResult> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(com.mrgregles.bsp_core.bank.BankResult.fail("unreachable"));
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.bank.BankResult r;
            try {
                r = s.bankTransfer(from, to, amount);
                if (r.ok()) {
                    publish("bank", from.toString());
                    publish("bank", to.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not transfer pieces: {}", e.toString());
                r = com.mrgregles.bsp_core.bank.BankResult.fail("unreachable");
            }
            com.mrgregles.bsp_core.bank.BankResult rr = r;
            onServer(() -> then.accept(rr));
        });
    }

    public static void bankFlags(UUID id, @Nullable Boolean acceptPay, @Nullable Boolean remoteShops, @Nullable Long seenMs) {
        async("update bank account switches", s -> {
            s.bankFlags(id, acceptPay, remoteShops, seenMs);
            if (acceptPay != null || remoteShops != null) {
                publish("bank", id.toString());
            }
        });
    }

    public static void bankLog(UUID id, com.mrgregles.bsp_core.bank.BankTransaction tx) {
        async("write a bank log line", s -> s.bankLog(id, tx));
    }

    /** The newest log entries from the database, or null when unreachable. */
    public static void bankLogRead(UUID id, Consumer<List<com.mrgregles.bsp_core.bank.BankTransaction>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.bank.BankTransaction> list = null;
            try {
                list = s.bankLogRead(id, BSPConfig.getOr(BSPConfig.BANK_LOG_ENTRIES, 500), BSPConfig.getOr(BSPConfig.BANK_LOG_DAYS, 90));
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read a bank log: {}", e.toString());
            }
            List<com.mrgregles.bsp_core.bank.BankTransaction> l = list;
            onServer(() -> then.accept(l));
        });
    }

    /** {@code then} gets {slots, rentUntil, itemsSnbt}, or null when the network has no box or is unreachable. */
    public static void bankBoxRead(UUID id, Consumer<Object[]> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            Object[] row = null;
            try {
                row = s.bankBoxRead(id);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read a deposit box: {}", e.toString());
            }
            Object[] r = row;
            onServer(() -> then.accept(r));
        });
    }

    public static void bankBoxWrite(UUID id, int slots, long until, String items) {
        async("save a deposit box", s -> {
            s.bankBoxWrite(id, slots, until, items);
            publish("box", id.toString());
        });
    }

    // ------------------------------------------------------------------ shops

    public static void shopWrite(String id, String name, String data) {
        async("save a shop", s -> {
            s.shopWrite(id, name, data);
            publish("shop", id);
        });
    }

    public static void shopDelete(String id) {
        async("delete a shop", s -> {
            s.shopDelete(id);
            publish("shop", "-" + id);
        });
    }

    /** Loads one shop (or all with null) from the database into this server's ledger. */
    public static void shopReload(@Nullable String id) {
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null || srv == null) {
            return;
        }
        worker().execute(() -> {
            Map<String, String> rows;
            try {
                rows = s.shopRead(id);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read shops: {}", e.toString());
                return;
            }
            onServer(() -> {
                var ledger = com.mrgregles.bsp_core.shop.ShopLedger.get(srv);
                for (var e : rows.entrySet()) {
                    try {
                        ledger.put(com.mrgregles.bsp_core.shop.Shop.load(net.minecraft.nbt.TagParser.parseTag(e.getValue())));
                    } catch (Exception ex) {
                        BSPCore.LOGGER.error("BSP-Core storage: shop {} is unreadable: {}", e.getKey(), ex.toString());
                    }
                }
                if (id != null && rows.isEmpty()) {
                    ledger.remove(id);
                }
            });
        });
    }

    public static void bankResetRemote() {
        async("reset Shopping Network access", MySqlStore::bankResetRemote);
    }

    public static void bankFind(String name, Consumer<Map.Entry<UUID, String>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            Map.Entry<UUID, String> hit = null;
            try {
                hit = s.bankFind(name);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not look up a player: {}", e.toString());
            }
            Map.Entry<UUID, String> h = hit;
            onServer(() -> then.accept(h));
        });
    }

    private static Map<UUID, Set<GlobalPos>> copy(Map<UUID, Set<GlobalPos>> in) {
        Map<UUID, Set<GlobalPos>> out = new HashMap<>();
        in.forEach((k, v) -> out.put(k, new HashSet<>(v)));
        return out;
    }

    /** One-line state for /bsp storage status. */
    public static String status() {
        MySqlStore s = store;
        if (s != null) {
            RedisLink r = redis;
            return "Network storage: MySQL, " + s.describe() + (r != null ? ". Redis: " + r.describe() : BSPConfig.REDIS_ENABLED.get() ? ". Redis: enabled but not started." : ". Redis: off (polling).");
        }
        return "mysql".equalsIgnoreCase(BSPConfig.STORAGE_MODE.get())
                ? "Network storage: storage.mode is \"mysql\" but the database is not connected. See the server log."
                : "Network storage: off. Records are kept in this world only (storage.mode = \"local\").";
    }
}
