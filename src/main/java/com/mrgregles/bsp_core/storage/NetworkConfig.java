package com.mrgregles.bsp_core.storage;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the network database and Redis are, and their passwords: {@code config/bsp_core-network.toml}, a COMMON config. Forge
 * sends a server's SERVER config file (bsp_core-server.toml) to every player who joins, so these settings must never be in it.
 * The same keys are still read from the server file once ({@link #moveFromServerConfig}): a value found there is copied here at
 * the start and cleared there.
 */
public final class NetworkConfig {
    public static final String FILE = "bsp_core-network.toml";

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> DB_HOST, DB_DATABASE, DB_USER, DB_PASSWORD, DB_TABLE_PREFIX, REDIS_HOST, REDIS_PASSWORD;
    public static final ForgeConfigSpec.IntValue DB_PORT, REDIS_PORT, REDIS_DB;
    public static final ForgeConfigSpec.BooleanValue DB_SSL, REDIS_SSL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("BSP-Core's network connection: the MySQL or MariaDB database and Redis that every server of a network shares.",
                        "Used only when storage.mode in the server config (bsp_core-server.toml) is \"mysql\".",
                        "This file stays on the server: unlike the server config it is never sent to players. Keep it private,",
                        "and leave it out of any client pack.")
                .push("database");
        DB_HOST = b.comment("Database host name or address.").define("host", "localhost");
        DB_PORT = b.comment("Database port.").defineInRange("port", 3306, 1, 65535);
        DB_DATABASE = b.comment("Name of the existing database (schema) to use.").define("database", "bsp");
        DB_USER = b.comment("Database user. It needs CREATE, SELECT, INSERT, UPDATE and DELETE on that database.").define("user", "bsp");
        DB_PASSWORD = b.comment("Database password.").define("password", "");
        DB_TABLE_PREFIX = b.comment("Prefix for BSP-Core's table names (letters, digits and underscores).").define("tablePrefix", "bsp_");
        DB_SSL = b.comment("Connect with SSL/TLS.").define("useSsl", false);
        b.pop();
        b.comment("Redis, optional (switched on with storage.redis.enabled in the server config).").push("redis");
        REDIS_HOST = b.define("host", "localhost");
        REDIS_PORT = b.defineInRange("port", 6379, 1, 65535);
        REDIS_PASSWORD = b.comment("Password, if Redis has one.").define("password", "");
        REDIS_SSL = b.comment("Connect with TLS.").define("useSsl", false);
        REDIS_DB = b.comment("Redis database index.").defineInRange("db", 0, 0, 15);
        b.pop();
        SPEC = b.build();
    }

    private NetworkConfig() {}

    /** One setting that moved: its old place in the server config and its new one here. */
    private record Moved<T>(String name, ForgeConfigSpec.ConfigValue<T> old, ForgeConfigSpec.ConfigValue<T> now, boolean secret) {
        /** Copies a value set in the old place over, unless one is already set here; then clears the old place. Null when nothing was there. */
        String move() {
            T was = old.get();
            if (was.equals(old.getDefault())) {
                return null;
            }
            String what;
            if (now.get().equals(now.getDefault())) {
                now.set(was);
                what = name + " moved";
            } else {
                what = now.get().equals(was) ? name + " already here" : name + " already set here, the old value was dropped";
            }
            old.set(old.getDefault());
            return secret ? what : what + " (" + was + ")";
        }
    }

    private static List<Moved<?>> moved() {
        return List.of(
                new Moved<>("database host", BSPConfig.OLD_STORAGE_HOST, DB_HOST, false),
                new Moved<>("database port", BSPConfig.OLD_STORAGE_PORT, DB_PORT, false),
                new Moved<>("database name", BSPConfig.OLD_STORAGE_DATABASE, DB_DATABASE, false),
                new Moved<>("database user", BSPConfig.OLD_STORAGE_USER, DB_USER, false),
                new Moved<>("database password", BSPConfig.OLD_STORAGE_PASSWORD, DB_PASSWORD, true),
                new Moved<>("table prefix", BSPConfig.OLD_STORAGE_TABLE_PREFIX, DB_TABLE_PREFIX, false),
                new Moved<>("database SSL", BSPConfig.OLD_STORAGE_SSL, DB_SSL, false),
                new Moved<>("Redis host", BSPConfig.OLD_REDIS_HOST, REDIS_HOST, false),
                new Moved<>("Redis port", BSPConfig.OLD_REDIS_PORT, REDIS_PORT, false),
                new Moved<>("Redis password", BSPConfig.OLD_REDIS_PASSWORD, REDIS_PASSWORD, true),
                new Moved<>("Redis TLS", BSPConfig.OLD_REDIS_SSL, REDIS_SSL, false),
                new Moved<>("Redis database", BSPConfig.OLD_REDIS_DB, REDIS_DB, false));
    }

    /**
     * At every start, before anyone can join: the connection settings still in the server config are copied here (a value
     * already set here wins) and cleared there, and both files are saved. Returns what moved, for the log; passwords are never
     * written to it.
     */
    public static List<String> moveFromServerConfig() {
        List<String> done = new ArrayList<>();
        for (Moved<?> m : moved()) {
            String what = m.move();
            if (what != null) {
                done.add(what);
            }
        }
        if (!done.isEmpty()) {
            SPEC.save();
            BSPConfig.SPEC.save();
            BSPCore.LOGGER.warn("BSP-Core storage: the network connection settings were in bsp_core-server.toml, which is sent to every player who joins. "
                    + "They are now in config/{} and cleared from the server config: {}. If they are also in defaultconfigs/bsp_core-server.toml, clear them there too.",
                    FILE, String.join(", ", done));
        }
        return done;
    }
}
