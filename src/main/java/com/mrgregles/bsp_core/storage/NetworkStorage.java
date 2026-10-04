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
 * written to a MySQL database shared by every server of the network, which makes three things
 * network-wide: one first-join totem per player, the factory slice limit, and a list of every
 * placed totem.
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
    @Nullable
    private static ExecutorService worker;
    @Nullable
    private static MinecraftServer server;
    private static final Map<UUID, Integer> slicesElsewhere = new ConcurrentHashMap<>();

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
                if (!migrated) {
                    BSPCore.LOGGER.warn("BSP-Core storage: this server's local records are not in the database yet. Run /bsp storage migrate.");
                }
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

    private static Map<UUID, Set<GlobalPos>> copy(Map<UUID, Set<GlobalPos>> in) {
        Map<UUID, Set<GlobalPos>> out = new HashMap<>();
        in.forEach((k, v) -> out.put(k, new HashSet<>(v)));
        return out;
    }

    /** One-line state for /bsp storage status. */
    public static String status() {
        MySqlStore s = store;
        if (s != null) {
            return "Network storage: MySQL, " + s.describe();
        }
        return "mysql".equalsIgnoreCase(BSPConfig.STORAGE_MODE.get())
                ? "Network storage: storage.mode is \"mysql\" but the database is not connected. See the server log."
                : "Network storage: off. Records are kept in this world only (storage.mode = \"local\").";
    }
}
