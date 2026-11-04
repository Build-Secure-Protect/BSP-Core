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
import java.util.Optional;
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
    /** While the server stops: answers from the storage thread wait here and are run before the world is saved. */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> late = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static volatile boolean stopping;
    /** Whether this server's local records were copied to the database ({@code /bsp storage migrate}), as read at the start. */
    private static volatile boolean migratedHere = true;
    private static final Map<UUID, Integer> slicesElsewhere = new ConcurrentHashMap<>();
    /** What each player keeps in Coin Vaults on the other servers, as last read: five coin counts, blocks, and the Illyrium-interest flag. */
    private static volatile Map<UUID, int[]> vaultsElsewhere = Map.of();
    private static final int[] NO_VAULTS = new int[7];
    /** The steals running on the other servers, as last read. */
    private static volatile List<RemoteSteal> stealsElsewhere = List.of();
    /** The marketplace while network storage is on (made when the database connects). */
    @Nullable
    private static volatile com.mrgregles.bsp_core.market.NetworkMarket market;
    private static volatile com.mrgregles.bsp_core.market.NetworkOrders orders;

    /** A steal running on another server of the network: where, whose totem, who is stealing, and when it started and is expected to end (wall-clock ms). */
    /**
     * A player online on another server of the network. {@code dim} and the block are where they were at {@code posMs}, written only
     * for players in an alliance ({@code dim} "" when never written).
     */
    public record OnlinePlayer(UUID id, String name, String server, String dim, int x, int y, int z, long posMs) {
        public OnlinePlayer(UUID id, String name, String server) {
            this(id, name, server, "", 0, 0, 0, 0L);
        }
    }

    /** Where a player online here is, for their allies on the other servers. */
    public record Position(UUID id, String dim, int x, int y, int z) {}

    public record RemoteSteal(String serverId, String dimension, int x, int y, int z, @Nullable UUID owner, UUID thief, String thiefName, long startedMs, long endsMs) {}

    /** A placed totem on this server with its upgrade levels in the column's text form ({@link RemoteTotemLevels#encode}). */
    public record TotemLevels(UUID owner, GlobalPos pos, String levels) {}

    private NetworkStorage() {}

    private interface Work {
        void run(MySqlStore s) throws SQLException;
    }

    private static ExecutorService redisWorker;

    /**
     * Redis's own thread: publishing and Redis calls never wait behind the database (or the database behind a slow Redis).
     * At most 1,000 messages wait; past that the oldest are dropped, since the database polling still carries what they said.
     */
    private static synchronized ExecutorService redisWorker() {
        if (redisWorker == null || redisWorker.isShutdown()) {
            redisWorker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(1000), r -> {
                        Thread t = new Thread(r, "BSP-Core redis publish");
                        t.setDaemon(true);
                        return t;
                    }, (r, pool) -> {
                        if (!pool.isShutdown()) {
                            pool.getQueue().poll();
                            pool.execute(r);
                            BSPCore.LOGGER.warn("BSP-Core redis: more than 1,000 messages waiting; the oldest was dropped");
                        }
                    });
        }
        return redisWorker;
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
        return new MySqlStore(NetworkConfig.DB_HOST.get(), NetworkConfig.DB_PORT.get(), NetworkConfig.DB_DATABASE.get(), NetworkConfig.DB_USER.get(),
                NetworkConfig.DB_PASSWORD.get(), NetworkConfig.DB_TABLE_PREFIX.get(), NetworkConfig.DB_SSL.get(), BSPConfig.STORAGE_SERVER_ID.get());
    }

    private static RedisLink redisFromConfig(java.util.function.Consumer<String[]> onMessage) {
        return new RedisLink(NetworkConfig.REDIS_HOST.get(), NetworkConfig.REDIS_PORT.get(), NetworkConfig.REDIS_PASSWORD.get(), NetworkConfig.REDIS_SSL.get(),
                NetworkConfig.REDIS_DB.get(), BSPConfig.REDIS_CHANNEL.get(), BSPConfig.STORAGE_SERVER_ID.get(), onMessage);
    }

    /**
     * True on the server built into a game client: a single-player or LAN world (BSP-InventorySync request R7). The client pack
     * ships the servers' configs as defaults, so such a world would otherwise join the network's database as the server its
     * config names. Not {@code isDedicatedServer()}: the GameTest server answers false to that too, and counts as dedicated here.
     */
    public static boolean singlePlayer() {
        net.minecraftforge.api.distmarker.Dist dist = net.minecraftforge.fml.loading.FMLEnvironment.dist;
        return dist != null && dist.isClient() && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null;
    }

    /** Whether a server joins the network database: {@code storage.mode = "mysql"}, and never in a single-player or LAN world. */
    public static boolean networkWanted(String mode, boolean singlePlayer) {
        return !singlePlayer && "mysql".equalsIgnoreCase(mode);
    }

    private static final String SINGLE_PLAYER = "Network storage stays off in a single-player or LAN world: records are kept in this world only, "
            + "whatever storage.mode and the database settings say.";

    /** True when this server is meant to keep its records on the network (a dedicated server with storage.mode "mysql"), connected or not. */
    public static boolean countsWanted() {
        MinecraftServer s = server;
        return s != null && networkWanted(BSPConfig.STORAGE_MODE.get(), singlePlayer());
    }

    /**
     * True on a network server whose database is not connected: in the seconds after the start before it connects, or for good
     * when it could not. Its local records are only a cache then, so nothing that the network keeps (money, grants, claims) may
     * be decided on them.
     */
    public static boolean down() {
        return countsWanted() && (!enabled() || market == null); // connected only once the network's market is built too
    }

    /** True while this server is writing to and reading from the network database. */
    public static boolean enabled() {
        return store != null;
    }

    private static void onServer(Runnable r) {
        if (stopping) {
            late.add(r);
            return;
        }
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

    /** Tells the other servers something changed, over Redis if it is on; harmless without it. Runs on Redis's own thread. */
    private static void publish(String kind, String payload) {
        RedisLink r = redis;
        if (r != null) {
            redisWorker().execute(() -> r.publish(kind, payload));
        }
    }

    /**
     * Hands a message to a listener outside the game servers (the Discord bot) on its own Redis channel, on the storage thread.
     * False when this server has no Redis link, so nothing can reach the listener.
     */
    public static boolean publishTo(String channel, String message) {
        RedisLink r = redis;
        if (r == null) {
            return false;
        }
        redisWorker().execute(() -> {
            if (!r.publishTo(channel, message)) {
                BSPCore.LOGGER.warn("BSP-Core redis: could not publish on {} ({})", channel, r.lastError());
            }
        });
        return true;
    }

    /** Work on the Redis link, run on the storage thread. */
    public interface RedisWork<T> {
        T run(RedisLink redis) throws java.io.IOException;
    }

    /** Runs {@code work} on the storage thread and hands its answer back on the server thread: null when Redis is off here or the work failed (logged). */
    public static <T> void redis(String what, RedisWork<T> work, Consumer<T> then) {
        RedisLink r = redis;
        if (r == null) {
            then.accept(null);
            return;
        }
        redisWorker().execute(() -> {
            T answer = null;
            try {
                answer = work.run(r);
            } catch (java.io.IOException | RuntimeException e) {
                BSPCore.LOGGER.warn("BSP-Core redis: could not {}: {}", what, e.toString());
            }
            T done = answer;
            onServer(() -> then.accept(done));
        });
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
                case "mbox" -> {
                    // a market Collect box changed on another server (a lot closed into it): a player here sees it at once
                    try {
                        UUID id = UUID.fromString(payload);
                        var m = market();
                        if (m != null && srv.getPlayerList().getPlayer(id) != null) {
                            m.refreshBox(id);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // not a player id
                    }
                }
                case "lottery" -> com.mrgregles.bsp_core.lottery.LotteryDraw.onMessage(srv, payload);
                case "bank", "box" -> {
                    try {
                        UUID id = UUID.fromString(payload);
                        if (id.equals(com.mrgregles.bsp_core.market.Market.POT)) {
                            com.mrgregles.bsp_core.bank.Bank.refresh(srv, id); // the lottery pot is never online, but /bsp market pot reads it here
                        } else if (srv.getPlayerList().getPlayer(id) != null) {
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
                case "totem_return" -> {
                    if (payload.equals(BSPConfig.STORAGE_SERVER_ID.get())) {
                        collectTotemReturns(srv);
                    }
                }
                case "steal" -> refreshSteals();
                case "totem_destroyed" -> {
                    try {
                        com.mrgregles.bsp_core.totem.TotemWipe.destroyedElsewhere(srv, UUID.fromString(payload));
                    } catch (IllegalArgumentException ignored) {
                        // not a totem id
                    }
                }
                case "online" -> refreshOnline();
                case "team_invite" -> {
                    int sp = payload.indexOf(' ');
                    if (sp > 0 && payload.substring(0, sp).equals(BSPConfig.STORAGE_SERVER_ID.get())) {
                        try {
                            com.mrgregles.bsp_core.team.Teams.onRemote(srv, msg[1], net.minecraft.nbt.TagParser.parseTag(payload.substring(sp + 1)));
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                            BSPCore.LOGGER.warn("BSP-Core team: an unreadable invite message from {}: {}", msg[1], e.getMessage());
                        }
                    }
                }
                case "team" -> {
                    try {
                        if (payload.startsWith("-")) {
                            com.mrgregles.bsp_core.team.TeamSync.apply(srv, List.of(), UUID.fromString(payload.substring(1)));
                        } else {
                            teamReload(UUID.fromString(payload));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // not a team id
                    }
                }
                case "alliance" -> {
                    try {
                        if (payload.startsWith("-")) {
                            com.mrgregles.bsp_core.team.AllianceSync.apply(srv, List.of(), UUID.fromString(payload.substring(1)), null, false, false);
                        } else {
                            allianceReload(UUID.fromString(payload));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // not an alliance id
                    }
                }
                case "alliance_invite" -> {
                    int sp = payload.indexOf(' ');
                    if (sp > 0 && payload.substring(0, sp).equals(BSPConfig.STORAGE_SERVER_ID.get())) {
                        try {
                            com.mrgregles.bsp_core.team.Alliances.onRemote(srv, msg[1], net.minecraft.nbt.TagParser.parseTag(payload.substring(sp + 1)));
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                            BSPCore.LOGGER.warn("BSP-Core alliances: an unreadable invite message from {}: {}", msg[1], e.getMessage());
                        }
                    }
                }
                case "alliance_broken" -> {
                    try {
                        com.mrgregles.bsp_core.team.Alliances.brokenCard(srv, net.minecraft.nbt.TagParser.parseTag(payload));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        BSPCore.LOGGER.warn("BSP-Core alliances: an unreadable break message from {}: {}", msg[1], e.getMessage());
                    }
                }
                case "shopcount" -> com.mrgregles.bsp_core.shop.Shops.heard(srv, payload);
                case "trade" -> {
                    int sp = payload.indexOf(' ');
                    if (sp > 0) {
                        String to = payload.substring(0, sp);
                        if (to.equals("*") || to.equals(BSPConfig.STORAGE_SERVER_ID.get())) {
                            try {
                                com.mrgregles.bsp_core.trade.Trades.hub().onMessage(msg[1], net.minecraft.nbt.TagParser.parseTag(payload.substring(sp + 1)));
                            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                BSPCore.LOGGER.warn("BSP-Core trade: an unreadable message from {}: {}", msg[1], e.getMessage());
                            }
                        }
                    }
                }
                case "market" -> {
                    var m = market();
                    if (m != null) {
                        m.refresh(srv);
                    }
                }
                case "orders" -> {
                    var o = orders();
                    if (o != null) {
                        o.refresh(srv);
                    }
                }
                case "shop" -> {
                    if (payload.startsWith("-")) {
                        com.mrgregles.bsp_core.shop.ShopLedger.get(srv).remove(payload.substring(1));
                    } else {
                        shopReload(payload);
                    }
                }
                case "kit" -> {
                    if (payload.startsWith("-")) {
                        com.mrgregles.bsp_core.kit.KitSync.apply(srv, List.of(), payload.substring(1));
                    } else if (payload.startsWith("r:")) {
                        try {
                            UUID id = UUID.fromString(payload.substring(2));
                            if (srv.getPlayerList().getPlayer(id) != null) {
                                kitRedeemsReload(id, null);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // not a player id
                        }
                    } else {
                        kitReload(payload);
                    }
                }
                default -> { } // shops and later kinds
            }
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /** The server config is loaded and nobody can have joined yet: connection settings left in it move to the network file. */
    @SubscribeEvent
    public static void onAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        try {
            NetworkConfig.moveFromServerConfig();
        } catch (RuntimeException e) {
            BSPCore.LOGGER.error("BSP-Core storage: could not move the connection settings out of bsp_core-server.toml: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        String sid = BSPConfig.STORAGE_SERVER_ID.get();
        if (sid.length() > 32) {
            BSPCore.LOGGER.error("BSP-Core storage: storage.serverId \"{}\" is {} characters; keep it to 32 or fewer. Trades between servers name their "
                    + "hand-overs by it and fail with a longer one (the database's column is too short), and screens show it cut.", sid, sid.length());
        }
        boolean single = singlePlayer();
        if (!networkWanted(BSPConfig.STORAGE_MODE.get(), single)) {
            if (single && "mysql".equalsIgnoreCase(BSPConfig.STORAGE_MODE.get())) {
                BSPCore.LOGGER.info("BSP-Core storage: {} (storage.mode \"mysql\", serverId \"{}\" ignored)", SINGLE_PLAYER, BSPConfig.STORAGE_SERVER_ID.get());
            }
            return;
        }
        worker().execute(NetworkStorage::connect);
    }

    /** Ticks between full reloads of teams, kits and shops (five minutes), which catch up on any Redis message missed. */
    private static final int CATCH_UP_TICKS = 5 * 60 * 20;
    private static int catchUpTicks;

    /** When to try connecting again after a failed start (0: no retry due). The server stays "down" meanwhile: nothing networked is decided locally. */
    private static volatile long retryAtMs;
    private static final long RETRY_MS = 30_000L;

    /** Connects to the database (the storage thread): at the start, and again every 30 s while it fails. */
    private static void connect() {
        {
            MySqlStore s = null;
            try {
                s = fromConfig();
                s.ensureSchema();
                boolean migrated = s.hasMigrated();
                migratedHere = migrated;
                store = s;
                BSPCore.LOGGER.info("BSP-Core storage: connected to MySQL {}", s.describe());
                if (BSPConfig.REDIS_ENABLED.get()) {
                    RedisLink r = redisFromConfig(NetworkStorage::onRedisMessage);
                    redis = r;
                    r.start();
                }
                if (!migrated) {
                    BSPCore.LOGGER.warn("BSP-Core storage: this server's local records are not in the database yet. Run /bsp storage migrate.");
                }
                shopReload(null);
                MinecraftServer started = server;
                onServer(() -> com.mrgregles.bsp_core.shop.Shops.moveCountsOnce(started, BSPConfig.STORAGE_SERVER_ID.get()));
                s.teamClearOnline();
                s.onlineClear();
                playersElsewhere = List.copyOf(s.onlineElsewhere(System.currentTimeMillis(), ONLINE_MAX_AGE_MS));
                teamReload(null);
                allianceReload(null);
                kitReload(null);
                s.marketEnsurePot();
                orders = newOrders(s); // before the market: down() turns false with the market, and an order then must find its store
                market = newMarket(s);
                MinecraftServer forTrades = server;
                RedisLink linked = redis;
                com.mrgregles.bsp_core.trade.TradeLink tradeLink = linked == null ? null : (to, m) -> publish("trade", to + " " + m);
                var tradeDb = newTradeDb(s, forTrades);
                onServer(() -> com.mrgregles.bsp_core.trade.Trades.hub().connect(BSPConfig.STORAGE_SERVER_ID.get(), tradeLink, tradeDb, r -> worker().execute(r), NetworkStorage::onServer));
                onServer(() -> {
                    var m = market;
                    if (m != null && server != null) {
                        m.refresh(server);
                    }
                    var o = orders;
                    if (o != null && server != null) {
                        o.refresh(server);
                    }
                });
                s.clearSteals();
                stealsElsewhere = List.copyOf(s.readSteals(System.currentTimeMillis()));
                RemoteTotemLevels.apply(s.readTotemLevels());
                MinecraftServer joined = server;
                onServer(() -> connected(joined));
            } catch (SQLException | RuntimeException e) {
                if (s != null) {
                    if (store == s) {
                        store = null; // never half on: a failure part-way leaves the server on its local records, as the log says
                    }
                    s.close();
                }
                RedisLink r = redis;
                redis = null;
                if (r != null) {
                    r.close();
                }
                market = null;
                orders = null;
                retryAtMs = System.currentTimeMillis() + RETRY_MS;
                BSPCore.LOGGER.error("BSP-Core storage: storage.mode is \"mysql\" but the database could not be used ({}). Trying again in {} s; until it connects, "
                        + "the bank, the market, first-join totems and kits wait (nothing the network keeps is decided on this server's local copy).", e.toString(), RETRY_MS / 1000);
            }
        }
    }

    /**
     * The database connected: what a login decides on the network (the account and starting pieces, the first totem, first-join
     * kits, the online mark) is done now for the players who joined in the seconds before, when it waited.
     */
    private static void connected(MinecraftServer srv) {
        if (srv == null) {
            return;
        }
        List<TotemLevels> levels = TotemLedger.get(srv).levelRows();
        async("write the placed totems' levels", s -> s.totemLevelsAll(levels)); // rows written before the levels were kept get them now
        long now = System.currentTimeMillis();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            String name = p.getGameProfile().getName();
            async("mark a player online", s -> s.onlineHere(id, name, now));
            com.mrgregles.bsp_core.bank.BankEvents.arrive(p);
            if (com.mrgregles.bsp_core.admin.LoginSettle.canReceive(p)) {
                // settled already: the season check and the first totem waited for the database (not settled yet: the settle runs them)
                com.mrgregles.bsp_core.admin.AdminService.seasonCheck(p);
                com.mrgregles.bsp_core.event.FirstJoinHandler.check(p);
            }
            com.mrgregles.bsp_core.kit.KitGrants.connected(p);
        }
        publish("online", "");
    }

    /**
     * The server is stopping, before the players leave and the world is saved: what is queued finishes (a purchase, a Collect box
     * being emptied) and its answers run here, while they can still reach the players and the world. The database stays connected,
     * so the players' logouts that follow still write to it (a totem kept for a player leaving a no-totem server, their deposit box,
     * their online mark); it closes at {@link #onServerStopped}.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stopping = true; // from now answers queue in `late` and run here, not on a server loop that no longer turns
        drain(5);
    }

    /** The server has stopped (players gone, world saved): the logouts' writes finish, then the database and Redis close. */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        MySqlStore s = store;
        if (s != null) {
            async("clear this server's online players", MySqlStore::onlineClear);
            publish("online", "");
        }
        drain(5);
        store = null;
        RedisLink r = redis;
        redis = null;
        if (r != null) {
            r.close();
        }
        ExecutorService w = worker;
        if (w != null && !w.isShutdown()) {
            w.shutdown();
            try {
                if (!w.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    BSPCore.LOGGER.warn("BSP-Core storage: the database work still queued at stop did not finish in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        runLate();
        MinecraftServer stopped = server;
        if (stopped != null) {
            try {
                stopped.overworld().getDataStorage().save(); // what the late answers kept here (items owed to a player) was after the world save
            } catch (RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not save the records changed at stop: {}", e.toString());
            }
        }
        ExecutorService rw = redisWorker;
        if (rw != null) {
            rw.shutdownNow();
        }
        pollWaiting.set(false);
        if (s != null) {
            s.close();
        }
        late.clear();
        stopping = false;
        retryAtMs = 0;
        slicesElsewhere.clear();
        vaultsElsewhere = Map.of();
        stealsElsewhere = List.of();
        RemoteTotemLevels.clear();
        playersElsewhere = List.of();
        market = null;
        orders = null;
        com.mrgregles.bsp_core.trade.Trades.hub().disconnect();
        server = null;
    }

    /**
     * Waits (up to {@code seconds} a round) for the work queued so far to finish and runs the answers it left, a few rounds, since
     * an answer may queue a follow-up (deleting the rows of an emptied box). Never gives up on a round because nothing came back yet.
     */
    private static void drain(int seconds) {
        for (int round = 0; round < 3; round++) {
            ExecutorService w = worker;
            if (w == null || w.isShutdown()) {
                runLate();
                return;
            }
            java.util.concurrent.Future<?> marker = w.submit(() -> { });
            try {
                marker.get(round == 0 ? seconds : 2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                BSPCore.LOGGER.warn("BSP-Core storage: the database work queued at stop is still running");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.util.concurrent.ExecutionException ignored) {
                // a no-op cannot fail
            }
            if (!runLate() && marker.isDone()) {
                return;
            }
        }
    }

    /** Runs the answers queued while stopping; true when there were any. */
    private static boolean runLate() {
        boolean ran = false;
        for (Runnable task; (task = late.poll()) != null; ) {
            ran = true;
            try {
                task.run();
            } catch (RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: an answer at stop failed: {}", e.toString());
            }
        }
        return ran;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshSlices(player.getUUID());
            UUID id = player.getUUID();
            String name = player.getGameProfile().getName();
            long now = System.currentTimeMillis();
            async("mark a player online", s -> {
                s.onlineHere(id, name, now);
                publish("online", "");
            });
            var m = market();
            if (m != null) {
                m.refreshBox(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        slicesElsewhere.remove(event.getEntity().getUUID());
        UUID id = event.getEntity().getUUID();
        async("mark a player gone", s -> {
            s.onlineGone(id);
            publish("online", "");
        });
        var m = market();
        if (m != null) {
            m.forget(event.getEntity().getUUID()); // the copy of their Collect box; read again at their next login
        }
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

    /** The upgrade levels of this server's placed totem at {@code pos} changed (placed, a level bought, a reset, first load). */
    public static void totemLevels(UUID owner, GlobalPos pos, Map<String, Integer> levels) {
        String text = RemoteTotemLevels.encode(levels);
        async("record a placed totem's levels", s -> s.totemLevels(owner, pos, text));
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
    public static void syncScores(List<com.mrgregles.bsp_core.score.ScoreService.Entry> mine, List<com.mrgregles.bsp_core.score.ScoreService.Spot> spots,
                                  java.util.function.BiConsumer<List<com.mrgregles.bsp_core.score.ScoreService.Entry>, Map<UUID, Integer>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(mine, null); // not connected: this server's rows, with the adjustments last read
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.score.ScoreService.Entry> result;
            Map<UUID, Integer> adjust;
            try {
                result = s.syncScores(mine, spots);
                adjust = s.scoreAdjustments();
            } catch (SQLException | RuntimeException e) {
                // the board keeps what it last showed: publishing this server's rows alone would post every other server's points as lost
                BSPCore.LOGGER.error("BSP-Core storage: could not sync the leaderboard: {}", e.toString());
                return;
            }
            List<com.mrgregles.bsp_core.score.ScoreService.Entry> out = result;
            onServer(() -> then.accept(out, adjust));
        });
    }

    /**
     * Sets ({@code add} false: {@code points} is the score wanted) or adds to a player's hand-set score adjustment for the whole
     * network. The answer (the new adjustment, or null when the database could not be reached) arrives on the server thread.
     */
    public static void scoreAdjust(UUID player, int points, boolean add, String by, Consumer<Integer> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            Integer adjust = null;
            try {
                adjust = s.scoreAdjust(player, points, add, by);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not change a score: {}", e.toString());
            }
            Integer r = adjust;
            onServer(() -> then.accept(r));
        });
    }

    /** Removes one player's adjustment, or everyone's with {@code player} null. The answer (false when it failed) arrives on the server thread. */
    public static void scoreAdjustClear(@Nullable UUID player, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(false);
            return;
        }
        worker().execute(() -> {
            boolean ok = false;
            try {
                s.scoreAdjustClear(player);
                ok = true;
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not clear a score: {}", e.toString());
            }
            boolean r = ok;
            onServer(() -> then.accept(r));
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

    /** Ticks to the next sweep of shop counts that are full again: five minutes after the start, then daily. */
    private static int sweepTicks = 5 * 60 * 20;

    /** Ticks to the next check of rotating shops that announce (every minute, network or not). */
    private static int rotationTicks = 20 * 60;

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && store != null && ++catchUpTicks >= CATCH_UP_TICKS) {
            // a Redis message missed (a reconnect gap, a dropped publish) is never re-sent: teams, kits and shops are read whole now and then
            catchUpTicks = 0;
            teamReload(null);
            allianceReload(null);
            kitReload(null, false); // never prunes: a kit written here a moment ago may not be in the read yet
            shopReload(null);
        }
        long due = retryAtMs;
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && due > 0 && store == null && !stopping && System.currentTimeMillis() >= due) {
            retryAtMs = 0;
            worker().execute(NetworkStorage::connect);
        }
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && --rotationTicks <= 0) {
            rotationTicks = 20 * 60;
            com.mrgregles.bsp_core.shop.Shops.tickRotations(event.getServer());
        }
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && store != null && --sweepTicks <= 0) {
            sweepTicks = 24 * 60 * 60 * 20;
            com.mrgregles.bsp_core.shop.Shops.sweep(event.getServer());
        }
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || store == null
                || ++noticeTicks < Math.max(1, BSPConfig.getOr(BSPConfig.STORAGE_NOTICE_SECONDS, 3)) * 20) {
            return;
        }
        noticeTicks = 0;
        // never a second round queued behind one still waiting (a slow or unreachable database would pile them up)
        if (!pollWaiting.compareAndSet(false, true)) {
            return;
        }
        collectNotices(event.getServer());
        collectTotemReturns(event.getServer());
        refreshSteals();
        refreshTotemLevels();
        sendPositions(event.getServer());
        refreshOnline();
        MySqlStore s = store;
        if (s == null) {
            pollWaiting.set(false);
        } else {
            worker().execute(() -> pollWaiting.set(false)); // after the round's own work on the same thread
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean pollWaiting = new java.util.concurrent.atomic.AtomicBoolean();

    // ------------------------------------------------------------------ servers without totem placement

    /** Records the player's latest spot on this server (one that allows totem placement), for a logout on a no-totem server to send a totem back to. */
    public static void saveTotemSpot(UUID player, com.mrgregles.bsp_core.totem.NoTotemServers.Spot spot) {
        long now = System.currentTimeMillis();
        async("record a player's spot for totem returns", s -> s.saveTotemSpot(player, toRow(spot), now));
    }

    /** Reads the player's last spot on a server that allows totem placement; {@code then} gets null when there is none (server thread). */
    public static void readTotemSpot(UUID player, Consumer<com.mrgregles.bsp_core.totem.NoTotemServers.Spot> then) {
        async("read a player's spot for totem returns", s -> {
            MySqlStore.TotemSpot row = s.readTotemSpot(player);
            onServer(() -> then.accept(row == null ? null : fromRow(row)));
        });
    }

    /**
     * Asks the server named in {@code spot} to place the totems waiting for {@code player} at that spot. The totems must already be in
     * {@code pending_items}: requests and items are written by the same storage thread, in order.
     */
    public static void requestTotemReturn(UUID player, com.mrgregles.bsp_core.totem.NoTotemServers.Spot spot) {
        long now = System.currentTimeMillis();
        async("ask a server to place a totem back", s -> {
            s.addTotemReturn(player, toRow(spot), now);
            publish("totem_return", spot.serverId());
        });
    }

    /** Drops the player's recorded spot and any return waiting for them (a forget). */
    public static void clearTotemSpots(UUID player) {
        async("drop a player's totem spot", s -> s.clearTotemSpots(player));
    }

    /** Takes the return requests addressed to this server, with the items waiting for those players, and places the totems (server thread). */
    private static void collectTotemReturns(MinecraftServer srv) {
        async("collect totem return requests", s -> {
            List<MySqlStore.TotemReturn> requests = s.takeTotemReturns();
            if (requests.isEmpty()) {
                return;
            }
            Map<UUID, com.mrgregles.bsp_core.totem.NoTotemServers.Spot> latest = new java.util.LinkedHashMap<>();
            requests.forEach(r -> latest.put(r.player(), fromRow(r.spot()))); // a later request wins
            Map<UUID, List<String>> owed = s.takePending(latest.keySet());
            onServer(() -> latest.forEach((id, spot) -> {
                List<String> left = com.mrgregles.bsp_core.totem.NoTotemServers.placeReturned(srv, id, spot, owed.getOrDefault(id, List.of()));
                if (!left.isEmpty()) {
                    addPending(id, left);
                }
            }));
        });
    }

    private static MySqlStore.TotemSpot toRow(com.mrgregles.bsp_core.totem.NoTotemServers.Spot spot) {
        return new MySqlStore.TotemSpot(spot.serverId(), spot.dimension(), spot.pos().getX(), spot.pos().getY(), spot.pos().getZ());
    }

    private static com.mrgregles.bsp_core.totem.NoTotemServers.Spot fromRow(MySqlStore.TotemSpot row) {
        return new com.mrgregles.bsp_core.totem.NoTotemServers.Spot(row.serverId(), row.dimension(), new net.minecraft.core.BlockPos(row.x(), row.y(), row.z()));
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

    private static volatile List<OnlinePlayer> playersElsewhere = List.of();

    /**
     * The players online on the other servers, as last read (every {@code storage.noticeSeconds}, and at once on a Redis notice when
     * someone joins or leaves a server). Empty without network storage.
     */
    public static List<OnlinePlayer> playersElsewhere() {
        return playersElsewhere;
    }

    /** Where on the other servers the player is online, if they are. */
    public static Optional<OnlinePlayer> onlineElsewhere(UUID player) {
        for (OnlinePlayer p : playersElsewhere) {
            if (p.id().equals(player)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static long positionsSentMs;

    /** Writes where this server's allied players are, every {@code alliance.positionSeconds} (read on the server thread). */
    private static void sendPositions(MinecraftServer srv) {
        long now = System.currentTimeMillis();
        if (now - positionsSentMs < Math.max(1, BSPConfig.getOr(BSPConfig.ALLIANCE_POSITION_SECONDS, 5)) * 1000L || !com.mrgregles.bsp_core.team.Alliances.enabled()) {
            return;
        }
        positionsSentMs = now;
        List<Position> out = new java.util.ArrayList<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (com.mrgregles.bsp_core.team.Alliances.allianceOf(srv, p.getUUID()) != null) {
                net.minecraft.core.BlockPos b = p.blockPosition();
                out.add(new Position(p.getUUID(), p.level().dimension().location().toString(), b.getX(), b.getY(), b.getZ()));
            }
        }
        if (!out.isEmpty()) {
            async("write where allied players are", s -> s.onlinePositions(out, now));
        }
    }

    /** How long a server's online rows count without it confirming them (it confirms every {@code storage.noticeSeconds}). */
    private static final long ONLINE_MAX_AGE_MS = 60_000L;

    private static void refreshOnline() {
        async("read the players online elsewhere", s -> {
            long now = System.currentTimeMillis();
            s.onlineBeat(now);
            playersElsewhere = List.copyOf(s.onlineElsewhere(now, ONLINE_MAX_AGE_MS));
        });
    }

    private static void refreshSteals() {
        async("read the steals in progress", s -> stealsElsewhere = List.copyOf(s.readSteals(System.currentTimeMillis())));
    }

    /** The upgrade levels of the totems placed on the other servers (request R11), read every {@code storage.noticeSeconds}. */
    private static void refreshTotemLevels() {
        async("read the totem levels elsewhere", s -> RemoteTotemLevels.apply(s.readTotemLevels()));
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

    /** The admin panel's view of the network; null when the database could not be read. */
    public static void adminNetwork(Consumer<com.mrgregles.bsp_core.admin.AdminService.Network> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.admin.AdminService.Network n = null;
            try {
                n = s.adminNetwork();
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read the network for the admin panel: {}", e.toString());
            }
            com.mrgregles.bsp_core.admin.AdminService.Network out = n;
            onServer(() -> then.accept(out));
        });
    }

    /** Players who keep Coin Vaults on other servers. */
    public static Set<UUID> vaultOwnersElsewhere() {
        return Set.copyOf(vaultsElsewhere.keySet());
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

    /**
     * Claims one state key for a Totem Forge pour if it is free (missing or empty), in one round trip on the storage thread, and counts the Shatter Totems waiting for {@code player} in the pending
     * items (travelling between servers, owed hand-backs): {@code then} hears, on the server thread, who holds the key afterwards
     * ({@code value} when this server got it) and that count. Nothing happens if the database cannot be reached, so a caller that
     * waits should time out.
     */
    public static void claimPour(String key, String value, UUID player, java.util.function.BiConsumer<String, Integer> then) {
        async("claim a Totem Forge pour", s -> {
            s.putState(Map.of(key, ""), true);
            String holder = s.casState(key, "", value) ? value : s.readStateKey(key);
            int owed = s.countPendingTotems(player);
            String answer = holder == null ? "" : holder;
            onServer(() -> then.accept(answer, owed));
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
    /**
     * Stores items owed to a player for whichever server they are on next. When the database cannot take them (not connected, or
     * the write fails) they are kept in this world's own records instead, handed over at the player's next login here, so items
     * that already left an inventory (a totem leaving for a transfer, a no-totem server's logout) are never lost.
     */
    public static void addPending(UUID player, List<String> items) {
        List<String> copy = List.copyOf(items);
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null) {
            keepLocally(srv, player, copy);
            return;
        }
        worker().execute(() -> {
            try {
                s.addPending(player, copy);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not store {} item(s) owed to {}; kept on this server instead: {}", copy.size(), player, e.toString());
                onServer(() -> keepLocally(srv, player, copy));
            }
        });
    }

    private static void keepLocally(@Nullable MinecraftServer srv, UUID player, List<String> encoded) {
        if (srv == null) {
            BSPCore.LOGGER.error("BSP-Core storage: {} item(s) owed to {} could not be kept anywhere: {}", encoded.size(), player, encoded);
            return;
        }
        var season = com.mrgregles.bsp_core.admin.SeasonData.get(srv);
        var owed = season.pendingItems.computeIfAbsent(player, k -> new java.util.ArrayList<>());
        for (String snbt : encoded) {
            try {
                net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(snbt));
                if (!stack.isEmpty()) {
                    owed.add(stack);
                }
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core storage: an item owed to {} could not be read: {}", player, snbt);
            }
        }
        season.setDirty();
    }

    /** Takes whatever is waiting for the given players and hands it to {@code then} on the server thread. */
    /** Drops every item still owed to a player across the network (an admin wipe). */
    /**
     * A totem destroyed by an admin: its rows in the items owed (pending_items) are deleted, and the other servers are told to
     * retire it. Without Redis the other servers do not hear of it; a copy of it there would still be a husk once it reached this
     * server, but their own ledgers keep their record of it.
     */
    public static void totemDestroyed(UUID totemId) {
        String needle = pendingNeedle(totemId);
        async("drop a destroyed totem from the items owed", s -> s.removePendingContaining(needle));
        publish("totem_destroyed", totemId.toString());
    }

    /** How a totem's id reads inside a stored item (the SNBT of its tag), to find its rows in the items owed. */
    public static String pendingNeedle(UUID totemId) {
        return "TotemId:" + net.minecraft.nbt.NbtUtils.createUUID(totemId);
    }

    public static void clearPending(UUID player) {
        async("drop the items owed to a player", s -> s.clearPending(player));
    }

    public static void takePending(List<UUID> players, Consumer<Map<UUID, List<String>>> then) {
        List<UUID> copy = List.copyOf(players);
        async("collect items owed to players", s -> {
            Map<UUID, List<String>> owed = s.takePending(copy);
            onServer(() -> then.accept(owed)); // also when nothing was owed: a caller may have records to settle either way
        });
    }

    // ------------------------------------------------------------------ operator tools

    /** Tries to connect with the configured credentials and reports the outcome on the server thread. */
    public static void test(MinecraftServer srv, Consumer<String> report) {
        if (singlePlayer()) {
            report.accept(SINGLE_PLAYER);
            return;
        }
        worker().execute(() -> {
            String line;
            try (MySqlStore s = fromConfig()) {
                s.ensureSchema();
                line = "Connected: " + s.describe() + (s.hasMigrated() ? ". This server has migrated." : ". This server has not migrated yet.");
            } catch (SQLException | RuntimeException e) {
                line = "Could not connect: " + e.getMessage();
            }
            if (BSPConfig.REDIS_ENABLED.get()) {
                try (RedisLink r = redisFromConfig(m -> { })) {
                    line += " Redis: " + r.ping() + " from " + NetworkConfig.REDIS_HOST.get() + ":" + NetworkConfig.REDIS_PORT.get() + ".";
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
        if (singlePlayer()) {
            report.accept(List.of(SINGLE_PLAYER, "Nothing was copied."));
            return;
        }
        TotemLedger totems = TotemLedger.get(srv);
        FactoryLedger factories = FactoryLedger.get(srv);
        Set<UUID> grants = new HashSet<>(totems.allGranted());
        Map<UUID, Set<GlobalPos>> placed = copy(totems.allPlaced()), slices = copy(factories.all());
        List<TotemLevels> levels = totems.levelRows();
        List<com.mrgregles.bsp_core.kit.Kit> kits = new java.util.ArrayList<>();
        for (com.mrgregles.bsp_core.kit.Kit k : com.mrgregles.bsp_core.kit.KitLedger.get(srv).all()) {
            kits.add(k.copy());
        }
        Map<UUID, List<com.mrgregles.bsp_core.kit.KitLedger.Redeem>> kitRows = new HashMap<>();
        com.mrgregles.bsp_core.kit.KitLedger.get(srv).allRedeems().forEach((id, rows) -> kitRows.put(id, List.copyOf(rows)));
        worker().execute(() -> {
            List<String> lines;
            try (MySqlStore s = fromConfig()) {
                s.ensureSchema();
                if (s.hasMigrated() && !force) {
                    lines = List.of("This server has already migrated to " + s.describe() + ".", "Nothing was changed. Use /bsp storage migrate force to copy the local records again.");
                } else {
                    int[] n = s.migrate(grants, placed, slices);
                    s.totemLevelsAll(levels);
                    int kitRowCount = 0;
                    for (com.mrgregles.bsp_core.kit.Kit k : kits) {
                        if (!s.kitExists(k.id)) {
                            s.kitWrite(k); // only kits the network does not have: never over a newer copy edited on another server
                        }
                    }
                    for (var e : kitRows.entrySet()) {
                        for (com.mrgregles.bsp_core.kit.KitLedger.Redeem r : e.getValue()) {
                            s.kitRedeemMigrate(e.getKey(), r);
                            kitRowCount++;
                        }
                    }
                    migratedHere = true;
                    lines = List.of("Migrated to " + s.describe() + ".",
                            "First-join grants: " + n[0] + " added, " + n[1] + " already in the database.",
                            "Placed totems written: " + n[2] + ". Factory slices written: " + n[3] + ".",
                            "Kits written: " + kits.size() + ", kit records: " + kitRowCount + ".",
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
    /**
     * Changes a balance and writes its log row in one transaction. {@code then} gets the account after the change, or null when it
     * was refused (it would go below zero, or there is no account), and whether the database was reached: false means it was
     * rolled back (or never sent), so nothing changed.
     */
    public static void bankAdjust(UUID id, long dPieces, int[] dCoins, @Nullable com.mrgregles.bsp_core.bank.BankTransaction log,
                                  java.util.function.BiConsumer<com.mrgregles.bsp_core.bank.BankAccount, Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null, false);
            return;
        }
        worker().execute(() -> {
            com.mrgregles.bsp_core.bank.BankAccount row = null;
            boolean reached = false;
            try {
                row = s.bankAdjust(id, dPieces, dCoins, log);
                reached = true;
                if (row != null) {
                    publish("bank", id.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not change a bank balance: {}", e.toString());
            }
            com.mrgregles.bsp_core.bank.BankAccount r = row;
            boolean ok = reached;
            onServer(() -> then.accept(r, ok));
        });
    }

    /** Turns a one-time switch on ({@code remote_shops}, {@code remote_market}) only if it is off: {@code then} gets true when set now, false when it was on, null when unreachable. */
    public static void bankUnlock(UUID id, String column, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            Boolean set = null;
            try {
                set = s.bankUnlock(id, column);
                if (set) {
                    publish("bank", id.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not unlock {}: {}", column, e.toString());
            }
            Boolean r = set;
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

    public static void bankFlags(UUID id, @Nullable Boolean acceptPay, @Nullable Boolean remoteShops, @Nullable Boolean remoteMarket, @Nullable Long seenMs) {
        async("update bank account switches", s -> {
            s.bankFlags(id, acceptPay, remoteShops, remoteMarket, seenMs);
            if (acceptPay != null || remoteShops != null || remoteMarket != null) {
                publish("bank", id.toString());
            }
        });
    }

    /** One key of the shared state: {@code then} gets its value ("" when missing), or null when the database is unreachable. */
    public static void readStateKey(String key, Consumer<String> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            String value = null;
            try {
                String v = s.readStateKey(key);
                value = v == null ? "" : v;
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read the shared state {}: {}", key, e.toString());
            }
            String answer = value;
            onServer(() -> then.accept(answer));
        });
    }

    /** Writes one key of the shared state; {@code then} hears whether it went in. */
    public static void writeStateKey(String key, String value, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(false);
            return;
        }
        worker().execute(() -> {
            boolean ok = false;
            try {
                s.putState(Map.of(key, value), false);
                ok = true;
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not write the shared state {}: {}", key, e.toString());
            }
            boolean answer = ok;
            onServer(() -> then.accept(answer));
        });
    }

    /** Claims the lottery's draw network-wide: true when this server got it, false when another draw holds it, null when unreachable. */
    public static void lotteryClaim(String draw, long now, long staleMs, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            Boolean got = null;
            try {
                got = s.lotteryClaim(draw, now, staleMs);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not claim the lottery draw: {}", e.toString());
            }
            Boolean answer = got;
            onServer(() -> then.accept(answer));
        });
    }

    public static void lotteryRelease(String draw) {
        async("free the lottery draw", s -> s.lotteryRelease(draw));
    }

    /** What a lottery payout came to: "ok" with the pieces and the winner's balance, "empty", "untouched" (surely not paid) or "unsure". */
    public record Payout(String key, long pieces, long winnerPieces) {}

    public static void lotteryPayout(com.mrgregles.bsp_core.lottery.LotteryWin win, Consumer<Payout> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(new Payout("untouched", 0, -1));
            return;
        }
        worker().execute(() -> {
            Payout p;
            try {
                long[] r = s.lotteryPayout(win);
                p = r[0] > 0 ? new Payout("ok", r[0], r[1]) : new Payout("empty", 0, -1);
            } catch (com.mrgregles.bsp_core.market.MarketDb.Untouched e) {
                BSPCore.LOGGER.error("BSP-Core storage: the lottery payout did not happen: {}", e.toString());
                p = new Payout("untouched", 0, -1);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: the lottery payout's answer was lost (it may have happened): {}", e.toString());
                p = new Payout("unsure", 0, -1);
            }
            Payout answer = p;
            onServer(() -> then.accept(answer));
        });
    }

    /** Tells the other servers about a lottery draw (kind {@code lottery}); harmless without Redis. */
    public static void publishLottery(String payload) {
        publish("lottery", payload);
    }

    /**
     * What a daily reward claim came to: "paid" (with the pieces and the balance after), "already" (the day was paid), and, to
     * be tried again, "no_account", "unreachable", "untouched" (surely nothing happened) or "unsure" (the answer was lost after
     * the commit may have gone in; trying again is safe, the claim is in the same transaction).
     */
    public record Daily(String key, long pieces, long balance) {}

    public static void dailyReward(UUID id, int day, long amount, Consumer<Daily> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(new Daily("unreachable", 0, -1));
            return;
        }
        String here = com.mrgregles.bsp_core.bank.Bank.serverId();
        worker().execute(() -> {
            Daily d;
            try {
                long[] r = s.dailyReward(id, day, amount, here);
                d = r[0] > 0 ? new Daily("paid", r[0], r[1]) : r[0] == 0 ? new Daily("already", 0, -1) : new Daily("no_account", 0, -1);
            } catch (com.mrgregles.bsp_core.market.MarketDb.Untouched e) {
                BSPCore.LOGGER.error("BSP-Core storage: a daily reward could not be paid (will try again): {}", e.toString());
                d = new Daily("untouched", 0, -1);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: a daily reward's answer was lost (will check again): {}", e.toString());
                d = new Daily("unsure", 0, -1);
            }
            Daily answer = d;
            onServer(() -> then.accept(answer));
        });
    }

    public static void lotterySeen(com.mrgregles.bsp_core.lottery.LotteryPlayer p, boolean ranksKnown) {
        async("record a player for the lottery", s -> s.lotterySeen(p, ranksKnown));
    }

    /** Players seen at or after {@code sinceMs}, or null when unreachable. */
    public static void lotteryPlayers(long sinceMs, Consumer<List<com.mrgregles.bsp_core.lottery.LotteryPlayer>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.lottery.LotteryPlayer> list = null;
            try {
                list = s.lotteryPlayers(sinceMs);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read the lottery's players: {}", e.toString());
            }
            List<com.mrgregles.bsp_core.lottery.LotteryPlayer> l = list;
            onServer(() -> then.accept(l));
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
    /** The player's box row (slots, rent until, items), an empty array when the network has none, or null when unreachable. */
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
                if (row == null) {
                    row = new Object[0];
                }
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
            s.countsReset(id, 0, Integer.MAX_VALUE);
            s.dropDraw(id);
            publish("shop", "-" + id);
        });
    }

    /** Every shop trade since {@code since} from the database (every server), for the shop report; null when it could not be read. */
    public static void shopTrades(long since, Consumer<List<com.mrgregles.bsp_core.shop.ShopReport.Row>> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.shop.ShopReport.Row> rows = null;
            try {
                rows = s.shopTrades(since);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read the shop trades: {}", e.toString());
            }
            var done = rows;
            onServer(() -> then.accept(done));
        });
    }

    /** Work on the shop counts in the database (or a GameTest's stand-in), on the storage thread. */
    public interface CountWork<T> {
        T run(com.mrgregles.bsp_core.shop.CountStore counts) throws Exception;
    }

    /**
     * Runs {@code work} on the database's shop counts on the storage thread and hands its answer back
     * on the server thread: null when the database is not connected or the work failed (logged).
     * With {@code notifyShop}, the other servers are told that shop's counts changed.
     */
    public static <T> void shopCounts(String what, CountWork<T> work, @Nullable String notifyShop, Consumer<T> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(null);
            return;
        }
        worker().execute(() -> {
            T answer = null;
            try {
                answer = work.run(new DbCounts(s));
                if (notifyShop != null) {
                    publish("shopcount", notifyShop);
                }
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not {}: {}", what, e.toString());
            }
            T done = answer;
            onServer(() -> then.accept(done));
        });
    }

    /** Tells the other servers a shop's counts or draw changed (Redis kind {@code shopcount}; see {@link com.mrgregles.bsp_core.shop.Shops#countNotice}). */
    public static void tellShopCount(String notice) {
        publish("shopcount", notice);
    }

    /** The database as a {@link com.mrgregles.bsp_core.shop.CountStore}. */
    private record DbCounts(MySqlStore s) implements com.mrgregles.bsp_core.shop.CountStore {
        @Override
        public <T> T locked(String shop, int card, boolean selling, UUID player, java.util.function.BiFunction<com.mrgregles.bsp_core.shop.ShopLedger.Count, com.mrgregles.bsp_core.shop.ShopLedger.Count, T> change) throws SQLException {
            return s.countsLocked(shop, card, selling, player, change);
        }

        @Override
        public Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> read(String shop) throws SQLException {
            return s.countsRead(shop);
        }

        @Override
        public void reset(String shop, int from, int to) throws SQLException {
            s.countsReset(shop, from, to);
        }

        @Override
        public void dropIf(String shop, int card, boolean selling, UUID player, int left, long at) throws SQLException {
            s.countsDropIf(shop, card, selling, player, left, at);
        }

        @Override
        public boolean moveOnce(String serverId, Map<String, Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count>> counts) throws SQLException {
            return s.countsMoveOnce(serverId, counts);
        }

        @Override
        public com.mrgregles.bsp_core.shop.ShopLedger.Draw drawLocked(String shop, java.util.function.UnaryOperator<com.mrgregles.bsp_core.shop.ShopLedger.Draw> change) throws SQLException {
            return s.drawLocked(shop, change);
        }

        @Override
        public void dropDraw(String shop) throws SQLException {
            s.dropDraw(shop);
        }

        @Override
        public Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> readCard(String shop, int card, boolean selling, UUID player) throws SQLException {
            return s.countsReadCard(shop, card, selling, player);
        }

        @Override
        public com.mrgregles.bsp_core.shop.ShopLedger.Draw readDraw(String shop) throws SQLException {
            return s.drawRead(shop);
        }
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

    // ------------------------------------------------------------------ the marketplace

    /** The network's marketplace, or null while network storage is off. */
    @Nullable
    public static com.mrgregles.bsp_core.market.NetworkMarket market() {
        return store != null ? market : null;
    }

    /** The network's buy orders, or null while network storage is off. */
    @Nullable
    public static com.mrgregles.bsp_core.market.NetworkOrders orders() {
        return store != null ? orders : null;
    }

    /** Tells the other servers a bank account changed in the database (their cached copy re-reads it). */
    public static void bankChanged(UUID id) {
        if (store != null) {
            publish("bank", id.toString());
        }
    }

    private static com.mrgregles.bsp_core.trade.TradeDb newTradeDb(MySqlStore s, @Nullable MinecraftServer srv) {
        return new com.mrgregles.bsp_core.trade.TradeDb() {
            @Override
            public Settled settle(String attempt, UUID a, long aPieces, UUID b, long bPieces, List<net.minecraft.world.item.ItemStack> hostItems,
                                  @Nullable UUID hostItemsTo, @Nullable UUID escrowOwner, @Nullable UUID escrowTo) throws Exception {
                return s.tradeSettle(attempt, a, aPieces, b, bPieces, hostItems, hostItemsTo, escrowOwner, escrowTo);
            }

            @Override
            public void escrow(String attempt, UUID owner, List<net.minecraft.world.item.ItemStack> items) throws Exception {
                s.tradeEscrow(attempt, owner, items);
            }

            @Override
            public String decideAbort(String attempt) throws Exception {
                return s.tradeDecideAbort(attempt);
            }

            @Override
            public List<net.minecraft.world.item.ItemStack> takeEscrow(String attempt, UUID owner) throws Exception {
                return s.tradeTakeEscrow(attempt, owner);
            }

            @Override
            public Recovered recoverEscrow(String attempt, UUID owner) throws Exception {
                return s.tradeRecoverEscrow(attempt, owner);
            }

            /** Delivery goes through the usual pending-items path (it knows totems and the inventory sync mods): fetch now, hand nothing over here. */
            @Override
            public List<net.minecraft.world.item.ItemStack> takePending(UUID player) {
                MinecraftServer m = srv;
                if (m != null) {
                    onServer(() -> com.mrgregles.bsp_core.admin.SeasonSync.fetchPending(m));
                }
                return List.of();
            }

            @Override
            public int sweep(long beforeMs) throws Exception {
                return s.tradeSweep(beforeMs);
            }
        };
    }

    private static com.mrgregles.bsp_core.market.NetworkMarket newMarket(MySqlStore s) {
        com.mrgregles.bsp_core.market.MarketDb db = new com.mrgregles.bsp_core.market.MarketDb() {
            @Override
            public Done list(com.mrgregles.bsp_core.market.MarketLot lot, long tax, int maxLots) throws Exception {
                return s.marketList(lot, tax, maxLots);
            }

            @Override
            public boolean committed(String attempt) throws Exception {
                return s.marketCommitted(attempt);
            }

            @Override
            public void flush(String attempt, List<com.mrgregles.bsp_core.market.MarketLot> lots, List<com.mrgregles.bsp_core.market.MarketLot> unsure,
                              Map<UUID, List<net.minecraft.world.item.ItemStack>> boxes) throws Exception {
                s.marketFlush(attempt, lots, unsure, boxes);
            }

            @Override
            public List<String> openIds() throws Exception {
                return s.marketOpenIds();
            }

            @Override
            public List<com.mrgregles.bsp_core.market.MarketLot> lots(List<String> ids) throws Exception {
                return s.marketLots(ids);
            }

            @Override
            public Done buy(String id, UUID buyer, long tax, long now) throws Exception {
                return s.marketBuy(id, buyer, tax, now);
            }

            @Override
            public Done close(String id) throws Exception {
                return s.marketClose(id);
            }

            @Override
            public void addCollect(UUID player, List<net.minecraft.world.item.ItemStack> items) throws Exception {
                s.marketAddCollect(player, items);
            }

            @Override
            public List<net.minecraft.world.item.ItemStack> takeCollect(UUID player, String attempt) throws Exception {
                return s.marketTakeCollect(player, attempt);
            }

            @Override
            public void taken(String attempt) throws Exception {
                s.marketTaken(attempt);
            }

            @Override
            public void untake(String attempt) throws Exception {
                s.marketUntake(attempt);
            }

            @Override
            public void sweep(long now) throws Exception {
                s.marketSweep(now);
            }

            @Override
            public List<net.minecraft.world.item.ItemStack> peekCollect(UUID player) throws Exception {
                return s.marketPeekCollect(player);
            }
        };
        return new com.mrgregles.bsp_core.market.NetworkMarket(db, r -> worker().execute(r), NetworkStorage::onServer, () -> publish("market", ""),
                com.mrgregles.bsp_core.market.MarketLedger::get, id -> publish("mbox", id.toString()));
    }

    private static com.mrgregles.bsp_core.market.NetworkOrders newOrders(MySqlStore s) {
        com.mrgregles.bsp_core.market.OrderDb db = new com.mrgregles.bsp_core.market.OrderDb() {
            @Override
            public Done place(com.mrgregles.bsp_core.market.BuyOrder order, int maxOrders) throws Exception {
                return s.orderPlace(order, maxOrders);
            }

            @Override
            public List<com.mrgregles.bsp_core.market.BuyOrder> orders() throws Exception {
                return s.orderList();
            }

            @Override
            public Done fill(String id, UUID seller, int count, String attempt, long now) throws Exception {
                return s.orderFill(id, seller, count, attempt, now);
            }

            @Override
            public Done close(String id) throws Exception {
                return s.orderClose(id);
            }

            @Override
            public boolean committed(String attempt) throws Exception {
                return s.marketCommitted(attempt);
            }

            @Override
            public void settleFill(String attempt, UUID seller, List<net.minecraft.world.item.ItemStack> items) throws Exception {
                s.orderSettleFill(attempt, seller, items);
            }
        };
        // a fill puts items in Collect boxes in the database: this server's copy of the box is read again, and the others are told
        java.util.function.Consumer<UUID> boxChanged = id -> {
            var m = market();
            if (m != null) {
                m.refreshBox(id);
            }
            publish("mbox", id.toString());
        };
        return new com.mrgregles.bsp_core.market.NetworkOrders(db, r -> worker().execute(r), NetworkStorage::onServer, () -> publish("orders", ""),
                com.mrgregles.bsp_core.market.OrderLedger::get, boxChanged);
    }

    // ------------------------------------------------------------------ teams

    /** The team's own row (name, colour, leader, pin, message count); members are written one by one, never replaced as a set. */
    public static void teamWrite(com.mrgregles.bsp_core.team.Team team) {
        com.mrgregles.bsp_core.team.Team.Note pin = team.pinned();
        MySqlStore.TeamRow row = new MySqlStore.TeamRow(team.id, team.name, team.colour.getName(), team.leader, team.createdMs, team.messageSeq(),
                pin == null ? "" : pin.text(), pin == null ? "" : pin.by().toString(), pin == null ? "" : pin.byName(), pin == null ? 0 : pin.ms());
        async("write a team", s -> {
            s.teamRowWrite(row);
            publish("team", row.id().toString());
        });
    }

    /** Seats a player in a team: {@code then} gets true when seated, false when refused (full, gone, in another team), null when unreachable. Nothing without the database. */
    public static void teamJoin(UUID teamId, UUID player, com.mrgregles.bsp_core.team.Team.Member m, int max, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            return;
        }
        com.mrgregles.bsp_core.team.Team.Member copy = m.copy();
        worker().execute(() -> {
            Boolean seated = null;
            try {
                seated = s.teamJoin(teamId, player, copy, max);
                if (seated) {
                    publish("team", teamId.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not seat a player in a team: {}", e.toString());
            }
            Boolean r = seated;
            onServer(() -> then.accept(r));
        });
    }

    /** Whether this server has a Redis link, the only way a message reaches another server at once (a team invite). */
    public static boolean redisOn() {
        return redis != null;
    }

    public static void teamMember(UUID teamId, UUID player, com.mrgregles.bsp_core.team.Team.Member m) {
        com.mrgregles.bsp_core.team.Team.Member copy = m.copy(); // read here, on the server thread
        async("write a team member", s -> {
            s.teamMemberWrite(teamId, player, copy);
            publish("team", teamId.toString());
        });
    }

    public static void teamLeft(UUID teamId, UUID player) {
        async("remove a team member", s -> {
            s.teamMemberDelete(player);
            publish("team", teamId.toString());
        });
    }

    public static void teamDelete(UUID teamId) {
        async("remove a team", s -> {
            s.teamDelete(teamId);
            publish("team", "-" + teamId);
            String alliance = s.allianceLeaveTeam(teamId); // its seat in an alliance goes with it, whatever this server's copy knew
            if (alliance != null) {
                publish("alliance", alliance);
            }
        });
    }

    public static void teamMessage(UUID teamId, com.mrgregles.bsp_core.team.Team.Message message, int keep) {
        async("write a team message", s -> {
            s.teamMessageWrite(teamId, message, keep);
            publish("team", teamId.toString());
        });
    }

    /** Hands a team invite message to one other server over Redis; false when Redis is off here, so it cannot reach it. */
    public static boolean teamInvite(String toServer, net.minecraft.nbt.CompoundTag message) {
        if (redis == null) {
            return false;
        }
        publish("team_invite", toServer + " " + message);
        return true;
    }

    public static void teamOnline(UUID player, boolean online) {
        // the team is looked up here, on the server thread: the storage thread never reads the team ledger
        com.mrgregles.bsp_core.team.Team team = server == null ? null : com.mrgregles.bsp_core.team.Teams.teamOf(server, player);
        UUID teamId = team == null ? null : team.id;
        String where = online ? BSPConfig.STORAGE_SERVER_ID.get() : "";
        async("mark a team member online", s -> {
            s.teamOnline(player, where);
            if (teamId != null) {
                publish("team", teamId.toString());
            }
        });
    }

    // ------------------------------------------------------------------ alliances

    /**
     * Seats a team in an alliance: {@code then} gets true when seated, false when refused (full, gone, in another alliance, a wait),
     * null when unreachable. With {@code row} the alliance's row is written first (a new alliance). Nothing without the database.
     */
    public static void allianceJoin(@Nullable com.mrgregles.bsp_core.team.Alliance row, UUID allianceId, UUID team, com.mrgregles.bsp_core.team.Alliance.Seat seat, int max,
                                    Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            return;
        }
        MySqlStore.AllianceRow r = row == null ? null : new MySqlStore.AllianceRow(row.id, row.name, row.createdMs, row.messageSeq());
        com.mrgregles.bsp_core.team.Alliance.Seat copy = seat.copy();
        worker().execute(() -> {
            Boolean seated = null;
            try {
                if (r != null) {
                    s.allianceRowWrite(r);
                }
                seated = s.allianceJoin(allianceId, team, copy, max, System.currentTimeMillis());
                if (seated) {
                    publish("alliance", allianceId.toString());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not seat a team in an alliance: {}", e.toString());
            }
            Boolean done = seated;
            onServer(() -> then.accept(done));
        });
    }

    public static void allianceSeat(UUID allianceId, UUID team, com.mrgregles.bsp_core.team.Alliance.Seat seat) {
        int perks = seat.perks;
        boolean share = seat.shareCoords;
        async("write what a team gives its allies", s -> {
            s.allianceSeatWrite(allianceId, team, perks, share);
            publish("alliance", allianceId.toString());
        });
    }

    /** Takes a team out of an alliance; the database ends the alliance when fewer than two teams are left. */
    public static void allianceLeave(UUID allianceId, UUID team) {
        async("take a team out of an alliance", s -> publish("alliance", (s.allianceLeave(allianceId, team) ? "-" : "") + allianceId));
    }

    public static void allianceDelete(UUID allianceId) {
        async("end an alliance", s -> {
            s.allianceDelete(allianceId);
            publish("alliance", "-" + allianceId);
        });
    }

    public static void allianceWaits(List<com.mrgregles.bsp_core.team.AllianceLedger.Wait> waits) {
        if (waits.isEmpty()) {
            return;
        }
        List<com.mrgregles.bsp_core.team.AllianceLedger.Wait> copy = List.copyOf(waits);
        async("record the waits after an alliance broke", s -> s.allianceWaitsWrite(copy));
    }

    public static void allianceFormers(List<com.mrgregles.bsp_core.team.AllianceLedger.Former> formers) {
        if (formers.isEmpty()) {
            return;
        }
        List<com.mrgregles.bsp_core.team.AllianceLedger.Former> copy = List.copyOf(formers);
        async("record the teams that left an alliance", s -> s.allianceFormerWrite(copy));
    }

    public static void allianceFormerSpend(UUID team) {
        async("use up a team's left-alliance records", s -> s.allianceFormerSpend(team));
    }

    public static void allianceMessage(UUID allianceId, com.mrgregles.bsp_core.team.Alliance.Message m, int keep) {
        async("write an alliance message", s -> {
            s.allianceMessageWrite(allianceId, m, keep);
            publish("alliance", allianceId.toString());
        });
    }

    public static void allianceRead(UUID allianceId, UUID player, int seq) {
        async("mark alliance messages read", s -> s.allianceReadWrite(allianceId, player, seq));
    }

    /** Reads one alliance (or every alliance) and the waits from the database and puts them in place of the local copy, on the server thread. */
    public static void allianceReload(@Nullable UUID id) {
        allianceReload(id, false);
    }

    /**
     * As {@link #allianceReload(UUID)}; {@code own} for the read this server queues right after its own write to that alliance, so
     * it shows the write (the storage thread runs one thing at a time, in order).
     */
    public static void allianceReload(@Nullable UUID id, boolean own) {
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null || srv == null) {
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.team.Alliance> read;
            List<com.mrgregles.bsp_core.team.AllianceLedger.Wait> waits;
            List<com.mrgregles.bsp_core.team.AllianceLedger.Former> formers;
            try {
                read = s.allianceRead(id);
                waits = s.allianceWaitsRead(System.currentTimeMillis());
                formers = s.allianceFormerRead(System.currentTimeMillis());
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read alliances: {}", e.toString());
                if (own) {
                    onServer(() -> com.mrgregles.bsp_core.team.AllianceSync.ownReadFailed(id));
                }
                return;
            }
            boolean prune = id == null && migratedHere; // before /bsp storage migrate, alliances only this server has are kept
            onServer(() -> {
                com.mrgregles.bsp_core.team.AllianceSync.formersRead(srv, formers);
                com.mrgregles.bsp_core.team.AllianceSync.apply(srv, read, id, waits, own, prune);
            });
        });
    }

    /** Hands an alliance invite message to one other server over Redis; false when Redis is off here. */
    public static boolean allianceInvite(String toServer, net.minecraft.nbt.CompoundTag message) {
        if (redis == null) {
            return false;
        }
        publish("alliance_invite", toServer + " " + message);
        return true;
    }

    /** Asks every other server to show the break card to the players it names. */
    public static void allianceBroken(net.minecraft.nbt.CompoundTag card) {
        publish("alliance_broken", card.toString());
    }

    // ------------------------------------------------------------------ kits, one record for the network

    private static volatile List<String> kitServers = List.of();

    /** Server ids the network has seen (read with the kits at start); empty without network storage. */
    public static List<String> knownServers() {
        return kitServers;
    }

    public static void kitWrite(com.mrgregles.bsp_core.kit.Kit kit) {
        async("write a kit", s -> {
            s.kitWrite(kit);
            publish("kit", kit.id);
        });
    }

    public static void kitDelete(String id) {
        async("remove a kit", s -> {
            s.kitDelete(id);
            publish("kit", "-" + id);
        });
    }

    public static void kitTaken(String id, String by, long ms) {
        async("count a kit taken", s -> {
            s.kitTaken(id, by, ms);
            publish("kit", id);
        });
    }

    public static void kitRedeem(UUID player, com.mrgregles.bsp_core.kit.KitLedger.Redeem row) {
        async("write a kit record", s -> {
            s.kitRedeemWrite(player, row);
            publish("kit", "r:" + player);
        });
    }

    public static void kitRedeemReset(UUID player, @Nullable String kit) {
        async("forget kit records", s -> {
            s.kitRedeemReset(player, kit);
            publish("kit", "r:" + player);
        });
    }

    /** A first-join claim; {@code then} gets true on the server thread when this server's row landed first. Answers true at once without network storage. */
    public static void kitRedeemClaim(UUID player, com.mrgregles.bsp_core.kit.KitLedger.Redeem row, Consumer<Boolean> then) {
        MySqlStore s = store;
        if (s == null) {
            then.accept(true);
            return;
        }
        worker().execute(() -> {
            boolean won;
            try {
                won = s.kitRedeemClaim(player, row);
                if (won) {
                    publish("kit", "r:" + player);
                }
            } catch (SQLException | RuntimeException e) {
                // not recorded: the grant made here is taken back, so the kit stays unclaimed for the next login rather than owed twice
                BSPCore.LOGGER.error("BSP-Core storage: could not claim a first-join kit: {}", e.toString());
                onServer(() -> then.accept(false));
                return;
            }
            onServer(() -> then.accept(won));
        });
    }

    /** Reads a player's kit rows and puts them in place of the local ones, on the server thread; {@code then} runs after, and at once without network storage. */
    public static void kitRedeemsReload(UUID player, @Nullable Runnable then) {
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null || srv == null) {
            if (then != null) {
                then.run();
            }
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.kit.KitLedger.Redeem> rows;
            try {
                rows = s.kitRedeemsRead(player);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read kit records: {}", e.toString());
                return;
            }
            onServer(() -> {
                com.mrgregles.bsp_core.kit.KitSync.applyRows(srv, player, rows, then == null);
                if (then != null) {
                    then.run();
                }
            });
        });
    }

    /** Reads one kit (or every kit) and the server list from the database and puts them in place of the local copy, on the server thread. */
    public static void kitReload(@Nullable String id) {
        kitReload(id, true);
    }

    /** As {@link #kitReload(String)}; {@code prune} false never drops kits the read did not have (the periodic catch-up). */
    public static void kitReload(@Nullable String id, boolean pruneFull) {
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null || srv == null) {
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.kit.Kit> kits;
            try {
                kits = s.kitRead(id);
                if (id == null) {
                    kitServers = List.copyOf(s.serverIds());
                }
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read kits: {}", e.toString());
                return;
            }
            boolean prune = pruneFull && migratedHere; // before /bsp storage migrate, kits only this server has are kept for it to copy up
            onServer(() -> com.mrgregles.bsp_core.kit.KitSync.apply(srv, kits, id, prune));
        });
    }

    /** Reads one team (or every team) from the database and puts it in place of the local copy, on the server thread. */
    public static void teamReload(@Nullable UUID id) {
        MySqlStore s = store;
        MinecraftServer srv = server;
        if (s == null || srv == null) {
            return;
        }
        worker().execute(() -> {
            List<com.mrgregles.bsp_core.team.Team> teams;
            try {
                teams = s.teamRead(id);
            } catch (SQLException | RuntimeException e) {
                BSPCore.LOGGER.error("BSP-Core storage: could not read teams: {}", e.toString());
                return;
            }
            onServer(() -> com.mrgregles.bsp_core.team.TeamSync.apply(srv, teams, id));
        });
    }

    public static void bankResetRemote() {
        async("reset Shopping Network and market access", MySqlStore::bankResetRemote);
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
        if (singlePlayer()) {
            return "Network storage: off. " + SINGLE_PLAYER;
        }
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
