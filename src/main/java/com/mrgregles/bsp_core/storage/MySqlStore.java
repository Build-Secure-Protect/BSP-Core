package com.mrgregles.bsp_core.storage;

import net.minecraft.core.GlobalPos;

import java.sql.Connection;
import java.sql.Driver;
import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;

/**
 * BSP-Core's tables in a MySQL (or MariaDB) database that the network's owner provides. The mod
 * never creates the database or the account: it connects with the configured host, database, user
 * and password and only creates and uses its own tables there, all named with the configured prefix.
 *
 * <p>Every method blocks and must run on the storage thread, never on the server thread; see
 * {@link NetworkStorage}. One connection is kept open and reopened if it has gone stale.
 *
 * <p>Tables: {@code grants} (one row per player who has been given a first-join totem, anywhere on
 * the network), {@code totems} (every placed totem, by server), {@code factory_slices} (every
 * factory controller, by server), {@code scores} (each server's leaderboard rows), {@code meta} (one
 * row per server that has migrated), {@code vault_totals} (what each player keeps in Coin Vaults,
 * by server), {@code state} (the shared season state, as keys and values), {@code season_claims}
 * (who has been given their fresh totem for which season) and {@code pending_items} (prize and
 * reward items waiting for a player to log in somewhere) and {@code notices} (chat messages for a
 * player who is on another server, such as a steal warning; dropped if not collected in 15 minutes).
 */
final class MySqlStore implements AutoCloseable {
    private final String host, database, user, password, prefix, serverId;
    private final int port;
    private final boolean ssl;
    private Connection connection;

    MySqlStore(String host, int port, String database, String user, String password, String prefix, boolean ssl, String serverId) {
        if (!prefix.matches("[A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("storage.tablePrefix may only contain letters, digits and underscores");
        }
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
        this.prefix = prefix;
        this.ssl = ssl;
        this.serverId = serverId;
    }

    String describe() {
        return user + "@" + host + ":" + port + "/" + database + " (tables " + prefix + "*, server id \"" + serverId + "\")";
    }

    private String t(String table) {
        return "`" + prefix + table + "`";
    }

    private Connection open() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        SQLException last = null;
        for (JdbcDrivers.Found d : JdbcDrivers.available()) {
            Driver driver = d.driver();
            String url = d.urlPrefix() + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&connectTimeout=5000&socketTimeout=15000";
            try {
                Connection c = driver.connect(url, props);
                if (c != null) {
                    try (Statement st = c.createStatement()) { // a locked shop count waits at most 5 s for another server, well inside the socket timeout
                        st.execute("SET SESSION innodb_lock_wait_timeout = 5");
                    } catch (SQLException e) {
                        com.mrgregles.bsp_core.BSPCore.LOGGER.debug("BSP-Core storage: could not shorten the lock wait: {}", e.toString());
                    }
                    return c;
                }
            } catch (SQLException e) {
                last = e;
            }
        }
        throw last != null ? last : new SQLException("No MySQL or MariaDB JDBC driver is available to BSP-Core");
    }

    /** When the connection was last checked, and until when a failed connect is not tried again. */
    private long checkedMs, downUntilMs;

    /**
     * The connection: checked with a round trip at most every 2 seconds (not before every statement); after a failed connect,
     * calls fail at once for 5 seconds instead of each waiting out a connect timeout behind the others.
     */
    private Connection conn() throws SQLException {
        long now = System.currentTimeMillis();
        if (connection != null && now - checkedMs < 2000) {
            return connection;
        }
        if (connection == null || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // already gone
                }
                connection = null;
            }
            if (now < downUntilMs) {
                throw new SQLException("the database could not be reached a moment ago; trying again shortly");
            }
            try {
                connection = open();
            } catch (SQLException e) {
                downUntilMs = now + 5000;
                throw e;
            }
        }
        checkedMs = now;
        return connection;
    }

    /** Creates BSP-Core's tables if they are not there yet. Safe to call every start. */
    void ensureSchema() throws SQLException {
        String place = "server_id VARCHAR(64) NOT NULL, dimension VARCHAR(128) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("grants") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(64) NOT NULL, "
                    + "granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("totems") + " (" + place + ", owner_uuid CHAR(36) NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (server_id, dimension, x, y, z), INDEX (owner_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("factory_slices") + " (" + place + ", owner_uuid CHAR(36) NOT NULL, "
                    + "PRIMARY KEY (server_id, dimension, x, y, z), INDEX (owner_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("scores") + " (server_id VARCHAR(64) NOT NULL, uuid CHAR(36) NOT NULL, name VARCHAR(32) NOT NULL, "
                    + "totems INT NOT NULL, points INT NOT NULL, best_tier INT NOT NULL, PRIMARY KEY (server_id, uuid), INDEX (uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("meta") + " (server_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "migrated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("vault_totals") + " (server_id VARCHAR(64) NOT NULL, uuid CHAR(36) NOT NULL, "
                    + "c0 INT NOT NULL, c1 INT NOT NULL, c2 INT NOT NULL, c3 INT NOT NULL, c4 INT NOT NULL, blocks INT NOT NULL, "
                    + "PRIMARY KEY (server_id, uuid), INDEX (uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("state") + " (k VARCHAR(64) NOT NULL PRIMARY KEY, v MEDIUMTEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("season_claims") + " (season INT NOT NULL, uuid CHAR(36) NOT NULL, PRIMARY KEY (season, uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("notices") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, "
                    + "message MEDIUMTEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX (uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("pending_items") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, "
                    + "item MEDIUMTEXT NOT NULL, INDEX (uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("totem_spots") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, " + place + ", ms BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("totem_returns") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, " + place
                    + ", ms BIGINT NOT NULL, INDEX (server_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("steals") + " (" + place + ", owner_uuid CHAR(36) NULL, thief_uuid CHAR(36) NOT NULL, "
                    + "thief_name VARCHAR(32) NOT NULL, started_ms BIGINT NOT NULL, ends_ms BIGINT NOT NULL, "
                    + "PRIMARY KEY (server_id, dimension, x, y, z), INDEX (owner_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_accounts") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL DEFAULT '', "
                    + "pieces BIGINT NOT NULL DEFAULT 0, c0 INT NOT NULL DEFAULT 0, c1 INT NOT NULL DEFAULT 0, c2 INT NOT NULL DEFAULT 0, c3 INT NOT NULL DEFAULT 0, c4 INT NOT NULL DEFAULT 0, "
                    + "accept_pay TINYINT NOT NULL DEFAULT 1, remote_shops TINYINT NOT NULL DEFAULT 0, remote_market TINYINT NOT NULL DEFAULT 0, seen_ms BIGINT NOT NULL DEFAULT 0, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX (name))");
            try { // the Portable Banker's market unlock, added to a bank_accounts made before it
                st.executeUpdate("ALTER TABLE " + t("bank_accounts") + " ADD COLUMN remote_market TINYINT NOT NULL DEFAULT 0");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1060) { // 1060: the column is there already
                    throw e;
                }
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_log") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, ms BIGINT NOT NULL, "
                    + "type VARCHAR(16) NOT NULL, with_name VARCHAR(64) NOT NULL, detail VARCHAR(255) NOT NULL, pieces BIGINT NOT NULL, coin_tier INT NOT NULL, coin_count INT NOT NULL, "
                    + "server_id VARCHAR(64) NOT NULL, INDEX (uuid, ms))");
            for (String column : List.of("shop_id VARCHAR(32) NOT NULL DEFAULT ''", "item_id VARCHAR(128) NOT NULL DEFAULT ''")) {
                try { // the shop report's columns, added to a bank_log made before them
                    st.executeUpdate("ALTER TABLE " + t("bank_log") + " ADD COLUMN " + column);
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1060) { // 1060: the column is there already
                        throw e;
                    }
                }
            }
            try { // the shop report reads buy and sell rows by time across every player
                st.executeUpdate("ALTER TABLE " + t("bank_log") + " ADD INDEX bsp_shop_report (type, ms)");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1061) { // 1061: the index is there already
                    throw e;
                }
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shops") + " (id VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL, data MEDIUMTEXT NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shop_counts") + " (shop VARCHAR(32) NOT NULL, card INT NOT NULL, side CHAR(1) NOT NULL, who VARCHAR(36) NOT NULL, "
                    + "left_lots INT NOT NULL, at_ms BIGINT NOT NULL, PRIMARY KEY (shop, card, side, who))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shop_draws") + " (shop VARCHAR(32) NOT NULL PRIMARY KEY, shown VARCHAR(1024) NOT NULL, used VARCHAR(4096) NOT NULL DEFAULT '', "
                    + "drawn_at BIGINT NOT NULL, next_at BIGINT NOT NULL, for_shop BIGINT NOT NULL DEFAULT 0)");
            try { // the record a draw was made for, added to a shop_draws made before it
                st.executeUpdate("ALTER TABLE " + t("shop_draws") + " ADD COLUMN for_shop BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1060) { // 1060: the column is there already
                    throw e;
                }
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_boxes") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, slots INT NOT NULL, rent_until BIGINT NOT NULL, "
                    + "items MEDIUMTEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("teams") + " (id CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, colour VARCHAR(16) NOT NULL, "
                    + "leader CHAR(36) NOT NULL, created_ms BIGINT NOT NULL, seq INT NOT NULL DEFAULT 0, pin_text VARCHAR(255) NOT NULL DEFAULT '', pin_by CHAR(36) NOT NULL DEFAULT '', "
                    + "pin_by_name VARCHAR(16) NOT NULL DEFAULT '', pin_ms BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX (name))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("team_members") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, team CHAR(36) NOT NULL, name VARCHAR(16) NOT NULL, "
                    + "joined_ms BIGINT NOT NULL, read_seq INT NOT NULL DEFAULT 0, seen_ms BIGINT NOT NULL DEFAULT 0, online_server VARCHAR(64) NOT NULL DEFAULT '', INDEX (team))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("team_messages") + " (team CHAR(36) NOT NULL, seq INT NOT NULL, ms BIGINT NOT NULL, uuid CHAR(36) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, text VARCHAR(512) NOT NULL, PRIMARY KEY (team, seq))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("kits") + " (id VARCHAR(24) NOT NULL PRIMARY KEY, name VARCHAR(24) NOT NULL, description VARCHAR(80) NOT NULL DEFAULT '', "
                    + "icon TEXT NOT NULL, in_browser TINYINT NOT NULL DEFAULT 1, once TINYINT NOT NULL DEFAULT 0, cooldown BIGINT NOT NULL DEFAULT 0, price BIGINT NOT NULL DEFAULT 0, "
                    + "ranks VARCHAR(512) NOT NULL DEFAULT '', servers VARCHAR(512) NOT NULL DEFAULT '', grant_kind VARCHAR(16) NOT NULL DEFAULT 'NONE', grant_servers VARCHAR(512) NOT NULL DEFAULT '', "
                    + "enabled TINYINT NOT NULL DEFAULT 1, created_ms BIGINT NOT NULL, taken INT NOT NULL DEFAULT 0, last_by VARCHAR(16) NOT NULL DEFAULT '', last_ms BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("kit_items") + " (kit VARCHAR(24) NOT NULL, slot INT NOT NULL, item VARCHAR(128) NOT NULL, count INT NOT NULL, nbt MEDIUMTEXT NOT NULL, PRIMARY KEY (kit, slot))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("kit_redeems") + " (uuid CHAR(36) NOT NULL, kit VARCHAR(24) NOT NULL, scope VARCHAR(64) NOT NULL DEFAULT '', count INT NOT NULL DEFAULT 0, "
                    + "last_ms BIGINT NOT NULL DEFAULT 0, owed INT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, kit, scope), INDEX (kit))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("market_lots") + " (id VARCHAR(16) NOT NULL PRIMARY KEY, seller CHAR(36) NOT NULL, seller_name VARCHAR(16) NOT NULL, "
                    + "price BIGINT NOT NULL, listed_ms BIGINT NOT NULL, ends_ms BIGINT NOT NULL, server_id VARCHAR(64) NOT NULL, data MEDIUMTEXT NOT NULL, INDEX (seller), INDEX (listed_ms))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("trade_escrow") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, attempt VARCHAR(48) NOT NULL, owner CHAR(36) NOT NULL, "
                    + "item MEDIUMTEXT NOT NULL, ms BIGINT NOT NULL, INDEX (attempt), INDEX (ms))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("trade_outcomes") + " (attempt VARCHAR(48) NOT NULL PRIMARY KEY, outcome VARCHAR(8) NOT NULL, ms BIGINT NOT NULL, INDEX (ms))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("market_collect") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, item MEDIUMTEXT NOT NULL, "
                    + "taking VARCHAR(48) NULL, taking_ms BIGINT NOT NULL DEFAULT 0, INDEX (uuid), INDEX (taking))");
            // a Collect box being emptied: its rows are marked with the attempt first and deleted once the items are delivered
            for (String alter : List.of("ADD COLUMN taking VARCHAR(48) NULL", "ADD COLUMN taking_ms BIGINT NOT NULL DEFAULT 0", "ADD INDEX bsp_taking (taking)")) {
                try {
                    st.executeUpdate("ALTER TABLE " + t("market_collect") + " " + alter);
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1060 && e.getErrorCode() != 1061) { // 1060 the column, 1061 the index, is there already
                        throw e;
                    }
                }
            }
            // what reached the database: listings (by lot id) and held-item writes, so a lost answer can be asked about
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("market_attempts") + " (id VARCHAR(64) NOT NULL PRIMARY KEY, ms BIGINT NOT NULL, INDEX (ms))");
            // the daily login reward: the last day each player was paid (days since 1970 on the shop clock)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("daily_rewards") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, day INT NOT NULL)");
            // the lottery: every player seen on the network, with their ranks and operator status at the time
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("lottery_players") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL, seen_ms BIGINT NOT NULL, "
                    + "ranks VARCHAR(1024) NOT NULL DEFAULT '', op TINYINT NOT NULL DEFAULT 0, server_id VARCHAR(64) NOT NULL DEFAULT '', INDEX (seen_ms))");
        }
    }

    // ------------------------------------------------------------------ grants

    /** Records the grant if no server has recorded one for this player. True means this server should hand out the totem. */
    boolean claimGrant(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("grants") + " (uuid, server_id) VALUES (?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, serverId);
            if (ps.executeUpdate() != 1) {
                return false;
            }
        }
        // their first totem is also their totem for the season in progress
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("season_claims") + " (season, uuid) SELECT CAST(v AS UNSIGNED), ? FROM "
                + t("state") + " WHERE k = 'season'")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
        return true;
    }

    // ------------------------------------------------------------------ seasons

    /**
     * Records that the player has their fresh totem for {@code season}. True means this server should
     * hand it out: the player has had a first-join totem, and no server has given them this season's yet.
     */
    boolean claimSeason(int season, UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("season_claims") + " (season, uuid) SELECT ?, uuid FROM "
                + t("grants") + " WHERE uuid = ?")) {
            ps.setInt(1, season);
            ps.setString(2, player.toString());
            return ps.executeUpdate() == 1;
        }
    }

    Map<String, String> readState() throws SQLException {
        Map<String, String> out = new java.util.HashMap<>();
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("SELECT k, v FROM " + t("state"))) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    /** One key of the shared state, or null when it is missing. */
    @javax.annotation.Nullable
    String readStateKey(String key) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT v FROM " + t("state") + " WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Writes the given keys; with {@code onlyIfAbsent}, keys that already exist are left alone. */
    void putState(Map<String, String> values, boolean onlyIfAbsent) throws SQLException {
        String sql = onlyIfAbsent ? "INSERT IGNORE INTO " + t("state") + " (k, v) VALUES (?, ?)"
                : "INSERT INTO " + t("state") + " (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (var e : values.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Changes a key from {@code expected} to {@code value}. True only for the one server that gets there first. */
    boolean casState(String key, String expected, String value) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("state") + " SET v = ? WHERE k = ? AND v = ?")) {
            ps.setString(1, value);
            ps.setString(2, key);
            ps.setString(3, expected);
            return ps.executeUpdate() == 1;
        }
    }

    // ------------------------------------------------------------------ items owed to players

    void addPending(UUID player, java.util.List<String> items) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("pending_items") + " (uuid, item) VALUES (?, ?)")) {
            for (String item : items) {
                ps.setString(1, player.toString());
                ps.setString(2, item);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** How many Shatter Totems wait for the player in the pending items (a transfer, a hand-back, a delivery), without taking them. */
    int countPendingTotems(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT COUNT(*) FROM " + t("pending_items") + " WHERE uuid = ? AND item LIKE ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, "%id:\"bsp_core:shatter_totem\"%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Takes out, and returns, everything waiting for the given players. */
    void clearPending(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("pending_items") + " WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
    }

    Map<UUID, java.util.List<String>> takePending(Collection<UUID> players) throws SQLException {
        Map<UUID, java.util.List<String>> out = new java.util.HashMap<>();
        if (players.isEmpty()) {
            return out;
        }
        String marks = String.join(",", java.util.Collections.nCopies(players.size(), "?"));
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, uuid, item FROM " + t("pending_items") + " WHERE uuid IN (" + marks + ") ORDER BY id FOR UPDATE")) {
                int i = 1;
                for (UUID p : players) {
                    ps.setString(i++, p.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                        out.computeIfAbsent(UUID.fromString(rs.getString(2)), k -> new java.util.ArrayList<>()).add(rs.getString(3));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("pending_items") + " WHERE id = ?")) {
                for (long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ------------------------------------------------------------------ servers without totem placement

    /** A player's last spot on a server that allows totem placement: the server, the world and the block. */
    record TotemSpot(String serverId, String dimension, int x, int y, int z) {}

    /** A request to the server named in {@code spot}: place the totems waiting for {@code player} at that spot. */
    record TotemReturn(UUID player, TotemSpot spot) {}

    void saveTotemSpot(UUID player, TotemSpot spot, long ms) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("REPLACE INTO " + t("totem_spots") + " (uuid, server_id, dimension, x, y, z, ms) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.dimension());
            ps.setInt(4, spot.x());
            ps.setInt(5, spot.y());
            ps.setInt(6, spot.z());
            ps.setLong(7, ms);
            ps.executeUpdate();
        }
    }

    @javax.annotation.Nullable
    TotemSpot readTotemSpot(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT server_id, dimension, x, y, z FROM " + t("totem_spots") + " WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new TotemSpot(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)) : null;
            }
        }
    }

    void addTotemReturn(UUID player, TotemSpot spot, long ms) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("totem_returns") + " (uuid, server_id, dimension, x, y, z, ms) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.dimension());
            ps.setInt(4, spot.x());
            ps.setInt(5, spot.y());
            ps.setInt(6, spot.z());
            ps.setLong(7, ms);
            ps.executeUpdate();
        }
    }

    /** Takes out, and returns, the return requests addressed to this server. */
    java.util.List<TotemReturn> takeTotemReturns() throws SQLException {
        java.util.List<TotemReturn> out = new java.util.ArrayList<>();
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, uuid, server_id, dimension, x, y, z FROM " + t("totem_returns") + " WHERE server_id = ? ORDER BY id FOR UPDATE")) {
                ps.setString(1, serverId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                        out.add(new TotemReturn(UUID.fromString(rs.getString(2)), new TotemSpot(rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7))));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("totem_returns") + " WHERE id = ?")) {
                for (long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ------------------------------------------------------------------ steals in progress, visible from every server

    void stealStarted(UUID owner, UUID thief, String thiefName, GlobalPos pos, long startedMs, long endsMs) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("REPLACE INTO " + t("steals")
                + " (server_id, dimension, x, y, z, owner_uuid, thief_uuid, thief_name, started_ms, ends_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            place(ps, pos);
            ps.setString(6, owner == null ? null : owner.toString());
            ps.setString(7, thief.toString());
            ps.setString(8, thiefName);
            ps.setLong(9, startedMs);
            ps.setLong(10, endsMs);
            ps.executeUpdate();
        }
    }

    void stealEnded(GlobalPos pos) throws SQLException {
        delete("steals", pos);
    }

    /** Forgets every steal this server recorded: run at start, since a crash can leave rows behind. */
    void clearSteals() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("steals") + " WHERE server_id = ?")) {
            ps.setString(1, serverId);
            ps.executeUpdate();
        }
    }

    /** The steals running on the other servers. Rows an hour past their expected end are dropped as stale (a server that died mid-steal). */
    java.util.List<NetworkStorage.RemoteSteal> readSteals(long now) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("steals") + " WHERE ends_ms < ?")) {
            ps.setLong(1, now - 3_600_000L);
            ps.executeUpdate();
        }
        java.util.List<NetworkStorage.RemoteSteal> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT server_id, dimension, x, y, z, owner_uuid, thief_uuid, thief_name, started_ms, ends_ms FROM "
                + t("steals") + " WHERE server_id <> ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString(6);
                    out.add(new NetworkStorage.RemoteSteal(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                            owner == null ? null : UUID.fromString(owner), UUID.fromString(rs.getString(7)), rs.getString(8), rs.getLong(9), rs.getLong(10)));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ messages for players on other servers

    void addNotice(UUID player, String messageJson) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("notices") + " (uuid, message) VALUES (?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, messageJson);
            ps.executeUpdate();
        }
    }

    /** Takes out, and returns, the messages waiting for the given players, and drops messages nobody collected in 15 minutes. */
    Map<UUID, java.util.List<String>> takeNotices(Collection<UUID> players) throws SQLException {
        Map<UUID, java.util.List<String>> out = new java.util.HashMap<>();
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("DELETE FROM " + t("notices") + " WHERE created_at < NOW() - INTERVAL 15 MINUTE");
        }
        if (players.isEmpty()) {
            return out;
        }
        String marks = String.join(",", java.util.Collections.nCopies(players.size(), "?"));
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, uuid, message FROM " + t("notices") + " WHERE uuid IN (" + marks + ") ORDER BY id FOR UPDATE")) {
                int i = 1;
                for (UUID p : players) {
                    ps.setString(i++, p.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                        out.computeIfAbsent(UUID.fromString(rs.getString(2)), k -> new java.util.ArrayList<>()).add(rs.getString(3));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("notices") + " WHERE id = ?")) {
                for (long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ------------------------------------------------------------------ coin vaults

    /** Writes one player's vault row for this server now (removing it when they have no blocks left) and returns their block count on the other servers. */
    int putVaultRow(UUID owner, int[] row) throws SQLException {
        if (row[5] <= 0) {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("vault_totals") + " WHERE server_id = ? AND uuid = ?")) {
                ps.setString(1, serverId);
                ps.setString(2, owner.toString());
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("vault_totals") + " (server_id, uuid, c0, c1, c2, c3, c4, blocks) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE c0 = VALUES(c0), c1 = VALUES(c1), c2 = VALUES(c2), c3 = VALUES(c3), c4 = VALUES(c4), blocks = VALUES(blocks)")) {
                ps.setString(1, serverId);
                ps.setString(2, owner.toString());
                for (int i = 0; i < 6; i++) {
                    ps.setInt(3 + i, row[i]);
                }
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn().prepareStatement("SELECT COALESCE(SUM(blocks), 0) FROM " + t("vault_totals") + " WHERE uuid = ? AND server_id <> ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Replaces this server's vault rows with {@code mine} (five coin counts, then the block count, per
     * player) and returns what every player keeps on the other servers: five coin counts, the block
     * count, and 1 if a server that sorts before this one holds a Netherite or Illyrium coin of theirs.
     */
    Map<UUID, int[]> syncVaults(Map<UUID, int[]> mine) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("vault_totals") + " WHERE server_id = ?")) {
                ps.setString(1, serverId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("vault_totals") + " (server_id, uuid, c0, c1, c2, c3, c4, blocks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (var e : mine.entrySet()) {
                    ps.setString(1, serverId);
                    ps.setString(2, e.getKey().toString());
                    for (int i = 0; i < 6; i++) {
                        ps.setInt(3 + i, e.getValue()[i]);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        Map<UUID, int[]> out = new java.util.HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT server_id, uuid, c0, c1, c2, c3, c4, blocks FROM " + t("vault_totals") + " WHERE server_id <> ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int[] row = out.computeIfAbsent(UUID.fromString(rs.getString(2)), k -> new int[7]);
                    for (int i = 0; i < 6; i++) {
                        row[i] += rs.getInt(3 + i);
                    }
                    if (rs.getString(1).compareTo(serverId) < 0 && rs.getInt(6) + rs.getInt(7) > 0) {
                        row[6] = 1;
                    }
                }
            }
        }
        return out;
    }


    /** Drops the player's spot and any totem return addressed for them (a forget). */
    void clearTotemSpots(UUID player) throws SQLException {
        for (String table : new String[]{"totem_spots", "totem_returns"}) {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t(table) + " WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
        }
    }

    void clearGrant(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("grants") + " WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ placed totems and factory slices

    private void upsert(String table, UUID owner, GlobalPos pos) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t(table) + " (server_id, dimension, x, y, z, owner_uuid) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE owner_uuid = VALUES(owner_uuid)")) {
            place(ps, pos);
            ps.setString(6, owner.toString());
            ps.executeUpdate();
        }
    }

    private void delete(String table, GlobalPos pos) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t(table) + " WHERE server_id = ? AND dimension = ? AND x = ? AND y = ? AND z = ?")) {
            place(ps, pos);
            ps.executeUpdate();
        }
    }

    private void place(PreparedStatement ps, GlobalPos pos) throws SQLException {
        ps.setString(1, serverId);
        ps.setString(2, pos.dimension().location().toString());
        ps.setInt(3, pos.pos().getX());
        ps.setInt(4, pos.pos().getY());
        ps.setInt(5, pos.pos().getZ());
    }

    void totemPlaced(UUID owner, GlobalPos pos) throws SQLException {
        upsert("totems", owner, pos);
    }

    void totemRemoved(GlobalPos pos) throws SQLException {
        delete("totems", pos);
    }

    void sliceAdded(UUID owner, GlobalPos pos) throws SQLException {
        upsert("factory_slices", owner, pos);
    }

    void sliceRemoved(GlobalPos pos) throws SQLException {
        delete("factory_slices", pos);
    }

    /** Factory slices this player owns on every other server of the network. */
    int slicesElsewhere(UUID owner) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT COUNT(*) FROM " + t("factory_slices") + " WHERE owner_uuid = ? AND server_id <> ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------------ leaderboard

    /**
     * Replaces this server's score rows with {@code mine} and returns the board of the whole network:
     * each player's totems and points added up across servers.
     */
    java.util.List<com.mrgregles.bsp_core.score.ScoreService.Entry> syncScores(java.util.List<com.mrgregles.bsp_core.score.ScoreService.Entry> mine) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("scores") + " WHERE server_id = ?")) {
                ps.setString(1, serverId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("scores") + " (server_id, uuid, name, totems, points, best_tier) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (var e : mine) {
                    ps.setString(1, serverId);
                    ps.setString(2, e.id().toString());
                    ps.setString(3, e.name());
                    ps.setInt(4, e.totems());
                    ps.setInt(5, e.points());
                    ps.setInt(6, e.bestTier());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        java.util.List<com.mrgregles.bsp_core.score.ScoreService.Entry> all = new java.util.ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, MAX(name), SUM(totems), SUM(points), MAX(best_tier) FROM " + t("scores") + " GROUP BY uuid")) {
            while (rs.next()) {
                all.add(new com.mrgregles.bsp_core.score.ScoreService.Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ migration

    boolean hasMigrated() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT 1 FROM " + t("meta") + " WHERE server_id = ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Copies this server's local records into the database, merging with what other servers put
     * there. A player who already has a grant in the database is skipped. This server's own totem
     * and slice rows are replaced by the local ones. The local data is not touched.
     *
     * @return {grants added, grants already there, totems written, slices written}
     */
    int[] migrate(Collection<UUID> grants, Map<UUID, Set<GlobalPos>> totems, Map<UUID, Set<GlobalPos>> slices) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int added = 0, skipped = 0, t = 0, s = 0;
            for (UUID player : grants) {
                if (claimGrant(player)) {
                    added++;
                } else {
                    skipped++;
                }
            }
            for (String table : new String[]{"totems", "factory_slices"}) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t(table) + " WHERE server_id = ?")) {
                    ps.setString(1, serverId);
                    ps.executeUpdate();
                }
            }
            for (var e : totems.entrySet()) {
                for (GlobalPos pos : e.getValue()) {
                    totemPlaced(e.getKey(), pos);
                    t++;
                }
            }
            for (var e : slices.entrySet()) {
                for (GlobalPos pos : e.getValue()) {
                    sliceAdded(e.getKey(), pos);
                    s++;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("meta") + " (server_id) VALUES (?) ON DUPLICATE KEY UPDATE migrated_at = CURRENT_TIMESTAMP")) {
                ps.setString(1, serverId);
                ps.executeUpdate();
            }
            c.commit();
            return new int[]{added, skipped, t, s};
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ------------------------------------------------------------------ bank

    private BankAccount bankRow(ResultSet rs) throws SQLException {
        BankAccount a = new BankAccount();
        a.name = rs.getString("name");
        a.pieces = rs.getLong("pieces");
        for (int i = 0; i < 5; i++) {
            a.coins[i] = rs.getInt("c" + i);
        }
        a.granted = true;
        a.acceptPay = rs.getInt("accept_pay") != 0;
        a.remoteShops = rs.getInt("remote_shops") != 0;
        a.remoteMarket = rs.getInt("remote_market") != 0;
        a.seenMs = rs.getLong("seen_ms");
        return a;
    }

    /** The account, or null if the network has none for this player. */
    BankAccount bankRead(UUID id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM " + t("bank_accounts") + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? bankRow(rs) : null;
            }
        }
    }

    /** Opens the account with the starting pieces if no server has yet; records the name. True when opened just now. */
    boolean bankEnsure(UUID id, String name, long starting) throws SQLException {
        boolean inserted;
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("bank_accounts") + " (uuid, name, pieces) VALUES (?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.setLong(3, starting);
            inserted = ps.executeUpdate() == 1;
        }
        if (!inserted) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET name = ? WHERE uuid = ?")) {
                ps.setString(1, name);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
        return inserted;
    }

    /** Applies signed deltas as one atomic change; null if the account is missing or anything would go below zero. */
    BankAccount bankAdjust(UUID id, long dPieces, int[] dCoins) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = pieces + ?, c0 = c0 + ?, c1 = c1 + ?, c2 = c2 + ?, c3 = c3 + ?, c4 = c4 + ? "
                + "WHERE uuid = ? AND pieces + ? >= 0 AND c0 + ? >= 0 AND c1 + ? >= 0 AND c2 + ? >= 0 AND c3 + ? >= 0 AND c4 + ? >= 0")) {
            ps.setLong(1, dPieces);
            for (int i = 0; i < 5; i++) {
                ps.setInt(2 + i, dCoins[i]);
            }
            ps.setString(7, id.toString());
            ps.setLong(8, dPieces);
            for (int i = 0; i < 5; i++) {
                ps.setInt(9 + i, dCoins[i]);
            }
            if (ps.executeUpdate() != 1) {
                return null;
            }
        }
        return bankRead(id);
    }

    BankAccount bankSet(UUID id, long pieces) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = ? WHERE uuid = ?")) {
            ps.setLong(1, pieces);
            ps.setString(2, id.toString());
            if (ps.executeUpdate() != 1) {
                return null;
            }
        }
        return bankRead(id);
    }

    /** Moves pieces between two accounts in one transaction. */
    BankResult bankTransfer(UUID from, UUID to, long amount) throws SQLException {
        return SqlRetry.once(() -> bankTransferOnce(from, to, amount));
    }

    private BankResult bankTransferOnce(UUID from, UUID to, long amount) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            lockAccounts(c, from, to); // both rows, in UUID order, before anything else
            try (PreparedStatement ps = c.prepareStatement("SELECT accept_pay FROM " + t("bank_accounts") + " WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, to.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return BankResult.fail("no_account");
                    }
                    if (rs.getInt(1) == 0) {
                        c.rollback();
                        return BankResult.fail("refused");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = pieces - ? WHERE uuid = ? AND pieces >= ?")) {
                ps.setLong(1, amount);
                ps.setString(2, from.toString());
                ps.setLong(3, amount);
                if (ps.executeUpdate() != 1) {
                    c.rollback();
                    return BankResult.fail("short");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = pieces + ? WHERE uuid = ?")) {
                ps.setLong(1, amount);
                ps.setString(2, to.toString());
                ps.executeUpdate();
            }
            long left;
            try (PreparedStatement ps = c.prepareStatement("SELECT pieces FROM " + t("bank_accounts") + " WHERE uuid = ?")) {
                ps.setString(1, from.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    left = rs.next() ? rs.getLong(1) : 0;
                }
            }
            c.commit();
            return BankResult.ok(left);
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    void bankFlags(UUID id, Boolean acceptPay, Boolean remoteShops, Boolean remoteMarket, Long seenMs) throws SQLException {
        if (acceptPay != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET accept_pay = ? WHERE uuid = ?")) {
                ps.setInt(1, acceptPay ? 1 : 0);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
        if (remoteShops != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET remote_shops = ? WHERE uuid = ?")) {
                ps.setInt(1, remoteShops ? 1 : 0);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
        if (remoteMarket != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET remote_market = ? WHERE uuid = ?")) {
                ps.setInt(1, remoteMarket ? 1 : 0);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
        if (seenMs != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET seen_ms = GREATEST(seen_ms, ?) WHERE uuid = ?")) {
                ps.setLong(1, seenMs);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
    }

    void bankLog(UUID id, BankTransaction tx) throws SQLException {
        bankLog(conn(), id, tx);
    }

    /** A log row on the given connection (inside a transaction, it goes in or not with the rest). */
    private void bankLog(Connection c, UUID id, BankTransaction tx) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("bank_log") + " (uuid, ms, type, with_name, detail, pieces, coin_tier, coin_count, server_id, shop_id, item_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setLong(2, tx.ms());
            ps.setString(3, tx.type().name());
            ps.setString(4, tx.with().length() > 64 ? tx.with().substring(0, 64) : tx.with());
            ps.setString(5, tx.detail().length() > 255 ? tx.detail().substring(0, 255) : tx.detail());
            ps.setLong(6, tx.pieces());
            ps.setInt(7, tx.coinTier());
            ps.setInt(8, tx.coinCount());
            ps.setString(9, tx.server());
            ps.setString(10, tx.shopId());
            ps.setString(11, tx.itemId().length() > 128 ? tx.itemId().substring(0, 128) : tx.itemId());
            ps.executeUpdate();
        }
    }

    /** The newest {@code limit} entries within {@code days}, newest first; older rows are trimmed now and then. */
    List<BankTransaction> bankLogRead(UUID id, int limit, int days) throws SQLException {
        long oldest = System.currentTimeMillis() - days * 86_400_000L;
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("bank_log") + " WHERE uuid = ? AND ms < ?")) {
            ps.setString(1, id.toString());
            ps.setLong(2, oldest);
            ps.executeUpdate();
        }
        List<BankTransaction> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT ms, type, with_name, detail, pieces, coin_tier, coin_count, server_id, shop_id, item_id FROM " + t("bank_log")
                + " WHERE uuid = ? ORDER BY ms DESC, id DESC LIMIT ?")) {
            ps.setString(1, id.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BankTransaction.Type type;
                    try {
                        type = BankTransaction.Type.valueOf(rs.getString(2));
                    } catch (IllegalArgumentException e) {
                        type = BankTransaction.Type.ADMIN;
                    }
                    out.add(new BankTransaction(rs.getLong(1), type, rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10)));
                }
            }
        }
        return out;
    }

    /** Every shop trade (BOUGHT and SOLD rows) since {@code since}, with the player's name, for the shop report. */
    List<com.mrgregles.bsp_core.shop.ShopReport.Row> shopTrades(long since) throws SQLException {
        List<com.mrgregles.bsp_core.shop.ShopReport.Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT l.uuid, COALESCE(a.name, ''), l.ms, l.type, l.with_name, l.detail, l.pieces, l.coin_tier, l.coin_count, l.server_id, l.shop_id, l.item_id FROM "
                + t("bank_log") + " l LEFT JOIN " + t("bank_accounts") + " a ON a.uuid = l.uuid WHERE l.type IN ('BOUGHT', 'SOLD') AND l.ms >= ?")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new com.mrgregles.bsp_core.shop.ShopReport.Row(UUID.fromString(rs.getString(1)), rs.getString(2), new BankTransaction(rs.getLong(3),
                            BankTransaction.Type.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getInt(8), rs.getInt(9), rs.getString(10), rs.getString(11), rs.getString(12))));
                }
            }
        }
        return out;
    }

    void bankResetRemote() throws SQLException {
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("UPDATE " + t("bank_accounts") + " SET remote_shops = 0, remote_market = 0");
        }
    }

    /** The player's safety deposit box: slots, rent end and the items as SNBT; null if none. */
    Object[] bankBoxRead(UUID id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT slots, rent_until, items FROM " + t("bank_boxes") + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Object[]{rs.getInt(1), rs.getLong(2), rs.getString(3)} : null;
            }
        }
    }

    void bankBoxWrite(UUID id, int slots, long until, String items) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("bank_boxes") + " (uuid, slots, rent_until, items) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE slots = VALUES(slots), rent_until = VALUES(rent_until), items = VALUES(items)")) {
            ps.setString(1, id.toString());
            ps.setInt(2, slots);
            ps.setLong(3, until);
            ps.setString(4, items);
            ps.executeUpdate();
        }
    }

    void shopWrite(String id, String name, String data) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("shops") + " (id, name, data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, data);
            ps.executeUpdate();
        }
    }

    void shopDelete(String id) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("shops") + " WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ shop counts (SS-5)

    /**
     * One card side's everyone count and one player's count under row locks: both rows are made if
     * missing (left -1, full), read FOR UPDATE, handed to {@code change}, and written back; a player
     * row left at -1 is deleted. All in one transaction, so two servers cannot both take the last lot.
     * The rows are locked exclusively from the first statement (an INSERT IGNORE of a row that is
     * there would take a shared lock, and two servers upgrading theirs would deadlock); a transaction
     * MySQL ends over a lock runs once more.
     */
    <T> T countsLocked(String shop, int card, boolean selling, UUID player, java.util.function.BiFunction<com.mrgregles.bsp_core.shop.ShopLedger.Count, com.mrgregles.bsp_core.shop.ShopLedger.Count, T> change) throws SQLException {
        return SqlRetry.once(() -> countsLockedOnce(shop, card, selling, player, change));
    }

    private <T> T countsLockedOnce(String shop, int card, boolean selling, UUID player, java.util.function.BiFunction<com.mrgregles.bsp_core.shop.ShopLedger.Count, com.mrgregles.bsp_core.shop.ShopLedger.Count, T> change) throws SQLException {
        String side = selling ? "S" : "B", who = player.toString();
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("shop_counts") + " (shop, card, side, who, left_lots, at_ms) VALUES (?, ?, ?, '', -1, 0), (?, ?, ?, ?, -1, 0) "
                    + "ON DUPLICATE KEY UPDATE left_lots = left_lots")) {
                ps.setString(1, shop);
                ps.setInt(2, card);
                ps.setString(3, side);
                ps.setString(4, shop);
                ps.setInt(5, card);
                ps.setString(6, side);
                ps.setString(7, who);
                ps.executeUpdate();
            }
            var all = new com.mrgregles.bsp_core.shop.ShopLedger.Count();
            var mine = new com.mrgregles.bsp_core.shop.ShopLedger.Count();
            try (PreparedStatement ps = c.prepareStatement("SELECT who, left_lots, at_ms FROM " + t("shop_counts") + " WHERE shop = ? AND card = ? AND side = ? AND who IN ('', ?) FOR UPDATE")) {
                ps.setString(1, shop);
                ps.setInt(2, card);
                ps.setString(3, side);
                ps.setString(4, who);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        var into = rs.getString(1).isEmpty() ? all : mine;
                        into.left = rs.getInt(2);
                        into.at = rs.getLong(3);
                    }
                }
            }
            T out = change.apply(all, mine);
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("shop_counts") + " SET left_lots = ?, at_ms = ? WHERE shop = ? AND card = ? AND side = ? AND who = ''")) {
                ps.setInt(1, all.left);
                ps.setLong(2, all.at);
                ps.setString(3, shop);
                ps.setInt(4, card);
                ps.setString(5, side);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(mine.left < 0 ? "DELETE FROM " + t("shop_counts") + " WHERE shop = ? AND card = ? AND side = ? AND who = ?"
                    : "UPDATE " + t("shop_counts") + " SET left_lots = ?, at_ms = ? WHERE shop = ? AND card = ? AND side = ? AND who = ?")) {
                int i = 1;
                if (mine.left >= 0) {
                    ps.setInt(i++, mine.left);
                    ps.setLong(i++, mine.at);
                }
                ps.setString(i++, shop);
                ps.setInt(i++, card);
                ps.setString(i++, side);
                ps.setString(i, who);
                ps.executeUpdate();
            }
            c.commit();
            return out;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** A rotating shop's draw under a row lock (made empty if missing), changed by {@code change} and written back, in one transaction; locked and retried as {@link #countsLocked}. */
    com.mrgregles.bsp_core.shop.ShopLedger.Draw drawLocked(String shop, java.util.function.UnaryOperator<com.mrgregles.bsp_core.shop.ShopLedger.Draw> change) throws SQLException {
        return SqlRetry.once(() -> drawLockedOnce(shop, change));
    }

    private com.mrgregles.bsp_core.shop.ShopLedger.Draw drawLockedOnce(String shop, java.util.function.UnaryOperator<com.mrgregles.bsp_core.shop.ShopLedger.Draw> change) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("shop_draws") + " (shop, shown, drawn_at, next_at) VALUES (?, '', 0, 0) ON DUPLICATE KEY UPDATE drawn_at = drawn_at")) {
                ps.setString(1, shop);
                ps.executeUpdate();
            }
            var d = new com.mrgregles.bsp_core.shop.ShopLedger.Draw();
            try (PreparedStatement ps = c.prepareStatement("SELECT shown, used, drawn_at, next_at, for_shop FROM " + t("shop_draws") + " WHERE shop = ? FOR UPDATE")) {
                ps.setString(1, shop);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ints(rs.getString(1), d.shown);
                        ints(rs.getString(2), d.used);
                        d.drawnAt = rs.getLong(3);
                        d.nextAt = rs.getLong(4);
                        d.forShop = rs.getLong(5);
                    }
                }
            }
            var out = change.apply(d);
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("shop_draws") + " SET shown = ?, used = ?, drawn_at = ?, next_at = ?, for_shop = ? WHERE shop = ?")) {
                ps.setString(1, String.join(",", out.shown.stream().map(String::valueOf).toList()));
                ps.setString(2, String.join(",", out.used.stream().map(String::valueOf).toList()));
                ps.setLong(3, out.drawnAt);
                ps.setLong(4, out.nextAt);
                ps.setLong(5, out.forShop);
                ps.setString(6, shop);
                ps.executeUpdate();
            }
            c.commit();
            return out;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** "3,0,5" into numbers. */
    private static void ints(String text, List<Integer> into) {
        for (String n : text.split(",")) {
            if (!n.isBlank()) {
                into.add(Integer.parseInt(n.trim()));
            }
        }
    }

    /** A rotating shop's draw as it stands, without a lock (empty, with times 0, when there is none). */
    com.mrgregles.bsp_core.shop.ShopLedger.Draw drawRead(String shop) throws SQLException {
        var d = new com.mrgregles.bsp_core.shop.ShopLedger.Draw();
        try (PreparedStatement ps = conn().prepareStatement("SELECT shown, used, drawn_at, next_at, for_shop FROM " + t("shop_draws") + " WHERE shop = ?")) {
            ps.setString(1, shop);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ints(rs.getString(1), d.shown);
                    ints(rs.getString(2), d.used);
                    d.drawnAt = rs.getLong(3);
                    d.nextAt = rs.getLong(4);
                    d.forShop = rs.getLong(5);
                }
            }
        }
        return d;
    }

    void dropDraw(String shop) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("shop_draws") + " WHERE shop = ?")) {
            ps.setString(1, shop);
            ps.executeUpdate();
        }
    }

    /** Every count of a shop, keyed "card:B|S:player" (an empty player for the everyone count). */
    Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> countsRead(String shop) throws SQLException {
        Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> out = new java.util.HashMap<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT card, side, who, left_lots, at_ms FROM " + t("shop_counts") + " WHERE shop = ?")) {
            ps.setString(1, shop);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt(1) + ":" + rs.getString(2) + ":" + rs.getString(3), new com.mrgregles.bsp_core.shop.ShopLedger.Count(rs.getInt(4), rs.getLong(5)));
                }
            }
        }
        return out;
    }

    /** One card side's everyone row and one player's, keyed as {@link #countsRead}. */
    Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> countsReadCard(String shop, int card, boolean selling, UUID player) throws SQLException {
        Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count> out = new java.util.HashMap<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT card, side, who, left_lots, at_ms FROM " + t("shop_counts") + " WHERE shop = ? AND card = ? AND side = ? AND who IN ('', ?)")) {
            ps.setString(1, shop);
            ps.setInt(2, card);
            ps.setString(3, selling ? "S" : "B");
            ps.setString(4, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt(1) + ":" + rs.getString(2) + ":" + rs.getString(3), new com.mrgregles.bsp_core.shop.ShopLedger.Count(rs.getInt(4), rs.getLong(5)));
                }
            }
        }
        return out;
    }

    void countsReset(String shop, int from, int to) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("shop_counts") + " WHERE shop = ? AND card BETWEEN ? AND ?")) {
            ps.setString(1, shop);
            ps.setInt(2, from);
            ps.setInt(3, to);
            ps.executeUpdate();
        }
    }

    void countsDropIf(String shop, int card, boolean selling, UUID player, int left, long at) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("shop_counts") + " WHERE shop = ? AND card = ? AND side = ? AND who = ? AND left_lots = ? AND at_ms = ?")) {
            ps.setString(1, shop);
            ps.setInt(2, card);
            ps.setString(3, selling ? "S" : "B");
            ps.setString(4, player.toString());
            ps.setInt(5, left);
            ps.setLong(6, at);
            ps.executeUpdate();
        }
    }

    /** The first server to call it (a state row records which) copies its counts in; true for that server only. */
    boolean countsMoveOnce(String serverId, Map<String, Map<String, com.mrgregles.bsp_core.shop.ShopLedger.Count>> counts) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            boolean won;
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("state") + " (k, v) VALUES ('shop_counts_moved', ?)")) {
                ps.setString(1, serverId);
                won = ps.executeUpdate() == 1;
            }
            if (won) {
                try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("shop_counts") + " (shop, card, side, who, left_lots, at_ms) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (var shop : counts.entrySet()) {
                        for (var row : shop.getValue().entrySet()) {
                            String[] k = row.getKey().split(":", 3);
                            ps.setString(1, shop.getKey());
                            ps.setInt(2, Integer.parseInt(k[0]));
                            ps.setString(3, k[1]);
                            ps.setString(4, k[2]);
                            ps.setInt(5, row.getValue().left);
                            ps.setLong(6, row.getValue().at);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            }
            c.commit();
            return won;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** One shop's data, or null; with a null id, every shop as id -> data. */
    Map<String, String> shopRead(String id) throws SQLException {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT id, data FROM " + t("shops") + (id == null ? "" : " WHERE id = ?"))) {
            if (id != null) {
                ps.setString(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return out;
    }

    /** A player known to the network by name (case-insensitive), or null. */
    Map.Entry<UUID, String> bankFind(String name) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT uuid, name FROM " + t("bank_accounts") + " WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Map.entry(UUID.fromString(rs.getString(1)), rs.getString(2)) : null;
            }
        }
    }

    // ------------------------------------------------------------------ teams, one record for the network

    /** Writes the team's row and every member row, and drops member rows of people no longer in it. */
    void teamWrite(com.mrgregles.bsp_core.team.Team team) throws SQLException {
        com.mrgregles.bsp_core.team.Team.Note pin = team.pinned();
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("teams") + " (id, name, colour, leader, created_ms, seq, pin_text, pin_by, pin_by_name, pin_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), colour = VALUES(colour), leader = VALUES(leader), seq = GREATEST(seq, VALUES(seq)), pin_text = VALUES(pin_text), pin_by = VALUES(pin_by), pin_by_name = VALUES(pin_by_name), pin_ms = VALUES(pin_ms)")) {
            ps.setString(1, team.id.toString());
            ps.setString(2, team.name);
            ps.setString(3, team.colour.getName());
            ps.setString(4, team.leader.toString());
            ps.setLong(5, team.createdMs);
            ps.setInt(6, team.messageSeq());
            ps.setString(7, pin == null ? "" : pin.text());
            ps.setString(8, pin == null ? "" : pin.by().toString());
            ps.setString(9, pin == null ? "" : pin.byName());
            ps.setLong(10, pin == null ? 0 : pin.ms());
            ps.executeUpdate();
        }
        for (var e : team.members().entrySet()) {
            teamMemberWrite(team.id, e.getKey(), e.getValue());
        }
        StringBuilder keep = new StringBuilder();
        for (UUID m : team.members().keySet()) {
            keep.append(keep.length() == 0 ? "'" : ", '").append(m).append("'");
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("team_members") + " WHERE team = ?" + (keep.length() == 0 ? "" : " AND uuid NOT IN (" + keep + ")"))) {
            ps.setString(1, team.id.toString());
            ps.executeUpdate();
        }
    }

    /** One member's row: name, joined, read and seen; the online server is kept as it is. */
    void teamMemberWrite(UUID teamId, UUID player, com.mrgregles.bsp_core.team.Team.Member m) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("team_members") + " (uuid, team, name, joined_ms, read_seq, seen_ms) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE team = VALUES(team), name = VALUES(name), joined_ms = VALUES(joined_ms), read_seq = GREATEST(read_seq, VALUES(read_seq)), seen_ms = GREATEST(seen_ms, VALUES(seen_ms))")) {
            ps.setString(1, player.toString());
            ps.setString(2, teamId.toString());
            ps.setString(3, m.name);
            ps.setLong(4, m.joinedMs);
            ps.setInt(5, m.readSeq);
            ps.setLong(6, m.seenMs);
            ps.executeUpdate();
        }
    }

    void teamMemberDelete(UUID player) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("team_members") + " WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
    }

    void teamDelete(UUID teamId) throws SQLException {
        for (String table : new String[] {"team_messages", "team_members", "teams"}) {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t(table) + " WHERE " + (table.equals("teams") ? "id" : "team") + " = ?")) {
                ps.setString(1, teamId.toString());
                ps.executeUpdate();
            }
        }
    }

    /** Adds a line, moves the team's counter on, and drops lines beyond {@code keep}. */
    void teamMessageWrite(UUID teamId, com.mrgregles.bsp_core.team.Team.Message m, int keep) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("team_messages") + " (team, seq, ms, uuid, name, text) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, teamId.toString());
            ps.setInt(2, m.seq());
            ps.setLong(3, m.ms());
            ps.setString(4, m.author().toString());
            ps.setString(5, m.authorName());
            ps.setString(6, m.text());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("teams") + " SET seq = GREATEST(seq, ?) WHERE id = ?")) {
            ps.setInt(1, m.seq());
            ps.setString(2, teamId.toString());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("team_messages") + " WHERE team = ? AND seq <= ?")) {
            ps.setString(1, teamId.toString());
            ps.setInt(2, m.seq() - Math.max(0, keep));
            ps.executeUpdate();
        }
    }

    /** Marks where a player is online ("" when they left), for the team screen's "on server-2". */
    void teamOnline(UUID player, String serverId) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("team_members") + " SET online_server = ? WHERE uuid = ?")) {
            ps.setString(1, serverId);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        }
    }

    /** Forgets this server's online marks (a restart after a crash). */
    void teamClearOnline() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("team_members") + " SET online_server = '' WHERE online_server = ?")) {
            ps.setString(1, serverId);
            ps.executeUpdate();
        }
    }

    /** One team whole (members with their online server, the kept messages), or every team with a null id. */
    java.util.List<com.mrgregles.bsp_core.team.Team> teamRead(UUID id) throws SQLException {
        java.util.List<com.mrgregles.bsp_core.team.Team> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT id, name, colour, leader, created_ms, seq, pin_text, pin_by, pin_by_name, pin_ms FROM " + t("teams") + (id == null ? "" : " WHERE id = ?"))) {
            if (id != null) {
                ps.setString(1, id.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    net.minecraft.ChatFormatting colour = net.minecraft.ChatFormatting.getByName(rs.getString("colour"));
                    com.mrgregles.bsp_core.team.Team team = new com.mrgregles.bsp_core.team.Team(UUID.fromString(rs.getString("id")), rs.getString("name"),
                            colour == null || !colour.isColor() ? net.minecraft.ChatFormatting.AQUA : colour, UUID.fromString(rs.getString("leader")), rs.getLong("created_ms"));
                    team.setMessageSeq(rs.getInt("seq"));
                    String pin = rs.getString("pin_text");
                    if (!pin.isEmpty()) {
                        String by = rs.getString("pin_by");
                        team.pin(new com.mrgregles.bsp_core.team.Team.Note(pin, by.isEmpty() ? team.leader : UUID.fromString(by), rs.getString("pin_by_name"), rs.getLong("pin_ms")));
                    }
                    out.add(team);
                }
            }
        }
        for (com.mrgregles.bsp_core.team.Team team : out) {
            try (PreparedStatement ps = conn().prepareStatement("SELECT uuid, name, joined_ms, read_seq, seen_ms, online_server FROM " + t("team_members") + " WHERE team = ? ORDER BY joined_ms")) {
                ps.setString(1, team.id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        com.mrgregles.bsp_core.team.Team.Member m = new com.mrgregles.bsp_core.team.Team.Member(rs.getString("name"), rs.getLong("joined_ms"), rs.getInt("read_seq"));
                        m.seenMs = rs.getLong("seen_ms");
                        m.onlineServer = rs.getString("online_server");
                        team.members().put(UUID.fromString(rs.getString("uuid")), m);
                    }
                }
            }
            try (PreparedStatement ps = conn().prepareStatement("SELECT seq, ms, uuid, name, text FROM " + t("team_messages") + " WHERE team = ? ORDER BY seq")) {
                ps.setString(1, team.id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        team.messages().add(new com.mrgregles.bsp_core.team.Team.Message(rs.getInt("seq"), rs.getLong("ms"), UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getString("text")));
                    }
                }
            }
        }
        return out;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing more to do
            }
            connection = null;
        }
    }

    // ------------------------------------------------------------------ kits, one record for the network

    /** Writes the kit's row and its item rows (the old item rows go first). */
    void kitWrite(com.mrgregles.bsp_core.kit.Kit k) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("kits") + " (id, name, description, icon, in_browser, once, cooldown, price, ranks, servers, grant_kind, grant_servers, enabled, created_ms, taken, last_by, last_ms) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), icon = VALUES(icon), in_browser = VALUES(in_browser), "
                + "once = VALUES(once), cooldown = VALUES(cooldown), price = VALUES(price), ranks = VALUES(ranks), servers = VALUES(servers), grant_kind = VALUES(grant_kind), grant_servers = VALUES(grant_servers), "
                + "enabled = VALUES(enabled), taken = GREATEST(taken, VALUES(taken)), last_by = IF(VALUES(last_ms) >= last_ms, VALUES(last_by), last_by), last_ms = GREATEST(last_ms, VALUES(last_ms))")) {
            ps.setString(1, k.id);
            ps.setString(2, k.name);
            ps.setString(3, k.description);
            ps.setString(4, k.icon.isEmpty() ? "" : k.icon.save(new net.minecraft.nbt.CompoundTag()).toString());
            ps.setInt(5, k.inBrowser ? 1 : 0);
            ps.setInt(6, k.once ? 1 : 0);
            ps.setLong(7, k.cooldownSeconds);
            ps.setLong(8, k.price);
            ps.setString(9, com.mrgregles.bsp_core.kit.Kit.joined(k.ranks));
            ps.setString(10, com.mrgregles.bsp_core.kit.Kit.joined(k.servers));
            ps.setString(11, k.grant.name());
            ps.setString(12, com.mrgregles.bsp_core.kit.Kit.joined(k.grantServers));
            ps.setInt(13, k.enabled ? 1 : 0);
            ps.setLong(14, k.createdMs);
            ps.setInt(15, k.taken);
            ps.setString(16, k.lastTakenBy);
            ps.setLong(17, k.lastTakenMs);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("kit_items") + " WHERE kit = ?")) {
            ps.setString(1, k.id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("kit_items") + " (kit, slot, item, count, nbt) VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < k.items.size(); i++) {
                com.mrgregles.bsp_core.kit.Kit.ItemRow row = com.mrgregles.bsp_core.kit.Kit.rowOf(k.items.get(i));
                ps.setString(1, k.id);
                ps.setInt(2, i);
                ps.setString(3, row.item());
                ps.setInt(4, row.count());
                ps.setString(5, row.nbt());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    void kitDelete(String id) throws SQLException {
        for (String table : new String[] {"kit_redeems", "kit_items", "kits"}) {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t(table) + " WHERE " + (table.equals("kits") ? "id" : "kit") + " = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        }
    }

    /** The taken count and the last taker move on, never back. */
    void kitTaken(String id, String by, long ms) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("kits") + " SET taken = taken + 1, last_by = ?, last_ms = ? WHERE id = ?")) {
            ps.setString(1, by);
            ps.setLong(2, ms);
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    /** One kit by id, or every kit, with its items; items this server cannot resolve are dropped and counted. */
    List<com.mrgregles.bsp_core.kit.Kit> kitRead(@Nullable String id) throws SQLException {
        List<com.mrgregles.bsp_core.kit.Kit> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT id, name, description, icon, in_browser, once, cooldown, price, ranks, servers, grant_kind, grant_servers, enabled, created_ms, taken, last_by, last_ms FROM " + t("kits")
                + (id == null ? " ORDER BY created_ms" : " WHERE id = ?"))) {
            if (id != null) {
                ps.setString(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.mrgregles.bsp_core.kit.Kit k = new com.mrgregles.bsp_core.kit.Kit(rs.getString(1), rs.getString(2), rs.getLong(14));
                    k.description = rs.getString(3);
                    String icon = rs.getString(4);
                    if (!icon.isEmpty()) {
                        try {
                            k.icon = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(icon));
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
                            // no icon then
                        }
                    }
                    k.inBrowser = rs.getInt(5) != 0;
                    k.once = rs.getInt(6) != 0;
                    k.cooldownSeconds = rs.getLong(7);
                    k.price = rs.getLong(8);
                    k.ranks.addAll(com.mrgregles.bsp_core.kit.Kit.split(rs.getString(9)));
                    k.servers.addAll(com.mrgregles.bsp_core.kit.Kit.split(rs.getString(10)));
                    k.grant = com.mrgregles.bsp_core.kit.Kit.Grant.of(rs.getString(11));
                    k.grantServers.addAll(com.mrgregles.bsp_core.kit.Kit.split(rs.getString(12)));
                    k.enabled = rs.getInt(13) != 0;
                    k.taken = rs.getInt(15);
                    k.lastTakenBy = rs.getString(16);
                    k.lastTakenMs = rs.getLong(17);
                    out.add(k);
                }
            }
        }
        for (com.mrgregles.bsp_core.kit.Kit k : out) {
            try (PreparedStatement ps = conn().prepareStatement("SELECT item, count, nbt FROM " + t("kit_items") + " WHERE kit = ? ORDER BY slot")) {
                ps.setString(1, k.id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        net.minecraft.world.item.ItemStack stack = com.mrgregles.bsp_core.kit.Kit.stackOf(new com.mrgregles.bsp_core.kit.Kit.ItemRow(rs.getString(1), rs.getInt(2), rs.getString(3)));
                        if (stack.isEmpty()) {
                            k.missing++;
                        } else {
                            k.items.add(stack);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** A player's redeem row: count and the last time move on, the owed count is what this server says. */
    void kitRedeemWrite(UUID player, com.mrgregles.bsp_core.kit.KitLedger.Redeem r) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("kit_redeems") + " (uuid, kit, scope, count, last_ms, owed) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE count = GREATEST(count, VALUES(count)), last_ms = GREATEST(last_ms, VALUES(last_ms)), owed = VALUES(owed)")) {
            ps.setString(1, player.toString());
            ps.setString(2, r.kit);
            ps.setString(3, r.scope);
            ps.setInt(4, r.count);
            ps.setLong(5, r.lastMs);
            ps.setInt(6, r.owed);
            ps.executeUpdate();
        }
    }

    /** A first-join claim: true when this server's row landed first (no row existed for the player, kit and scope). */
    boolean kitRedeemClaim(UUID player, com.mrgregles.bsp_core.kit.KitLedger.Redeem r) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT IGNORE INTO " + t("kit_redeems") + " (uuid, kit, scope, count, last_ms, owed) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, r.kit);
            ps.setString(3, r.scope);
            ps.setInt(4, r.count);
            ps.setLong(5, r.lastMs);
            ps.setInt(6, r.owed);
            return ps.executeUpdate() == 1;
        }
    }

    List<com.mrgregles.bsp_core.kit.KitLedger.Redeem> kitRedeemsRead(UUID player) throws SQLException {
        List<com.mrgregles.bsp_core.kit.KitLedger.Redeem> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT kit, scope, count, last_ms, owed FROM " + t("kit_redeems") + " WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(com.mrgregles.bsp_core.kit.KitLedger.Redeem.of(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    void kitRedeemReset(UUID player, @Nullable String kit) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("kit_redeems") + " WHERE uuid = ?" + (kit == null ? "" : " AND kit = ?"))) {
            ps.setString(1, player.toString());
            if (kit != null) {
                ps.setString(2, kit);
            }
            ps.executeUpdate();
        }
    }

    /** Every server id the network has seen: the totem grants' server column and the kit rows' scopes. */
    List<String> serverIds() throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT DISTINCT server_id FROM " + t("grants") + " UNION SELECT DISTINCT scope FROM " + t("kit_redeems") + " WHERE scope <> ''");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id != null && !id.isBlank()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the marketplace (one transaction per call)

    private interface Tx<T> {
        T run(Connection c) throws SQLException;
    }

    /**
     * Runs {@code work} as one transaction. A failure before the commit (no connection, a statement, rolled back) throws
     * {@link com.mrgregles.bsp_core.market.MarketDb.Untouched}: nothing changed. A failure at the commit throws as it is: the
     * change may have been made with only the answer lost.
     */
    private <T> T inTransaction(Tx<T> work) throws SQLException {
        try {
            return inTransactionOnce(work);
        } catch (com.mrgregles.bsp_core.market.MarketDb.Untouched e) {
            if (!SqlRetry.lockFailure(e)) {
                throw e;
            }
            // MySQL ended it over a lock (a deadlock with another server, or a lock wait timeout) and rolled it back: once more
            return inTransactionOnce(work);
        }
    }

    private <T> T inTransactionOnce(Tx<T> work) throws SQLException {
        Connection c;
        boolean auto;
        try {
            c = conn();
            auto = c.getAutoCommit();
            c.setAutoCommit(false);
        } catch (SQLException e) {
            throw new com.mrgregles.bsp_core.market.MarketDb.Untouched(e);
        }
        try {
            T out;
            try {
                out = work.run(c);
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // a dead connection rolls back by itself
                }
                if (e instanceof SQLException se) {
                    throw new com.mrgregles.bsp_core.market.MarketDb.Untouched(se);
                }
                throw e;
            }
            c.commit();
            return out;
        } finally {
            try {
                c.setAutoCommit(auto);
            } catch (SQLException ignored) {
                // the next conn() checks the connection anyway
            }
        }
    }

    /** Thrown inside a market transaction to roll it back with a reason instead of an error. */
    private static final class MarketNo extends RuntimeException {
        final String key;

        MarketNo(String key) {
            super(key, null, false, false);
            this.key = key;
        }
    }

    private <T> T marketTx(Tx<T> work, java.util.function.Function<String, T> no) throws SQLException {
        try {
            return inTransaction(work);
        } catch (MarketNo n) {
            return no.apply(n.key);
        }
    }

    /**
     * Locks bank rows in one statement, in UUID order, so two transactions that both touch the same two accounts (a purchase
     * each way, a purchase and a payment, two trades) always take them in the same order and cannot deadlock.
     */
    private void lockAccounts(Connection c, UUID... ids) throws SQLException {
        java.util.TreeSet<String> sorted = new java.util.TreeSet<>();
        for (UUID id : ids) {
            sorted.add(id.toString());
        }
        String marks = String.join(", ", java.util.Collections.nCopies(sorted.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM " + t("bank_accounts") + " WHERE uuid IN (" + marks + ") ORDER BY uuid FOR UPDATE")) {
            int i = 1;
            for (String id : sorted) {
                ps.setString(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // locked
                }
            }
        }
    }

    /** Pays {@code tax} into the lottery pot in one statement that takes the row's write lock at once (and makes the row if it is missing). */
    private void toPot(Connection c, long tax) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("bank_accounts") + " (uuid, name, pieces) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE pieces = pieces + ?")) {
            ps.setString(1, com.mrgregles.bsp_core.market.Market.POT.toString());
            ps.setString(2, com.mrgregles.bsp_core.market.Market.POT_NAME);
            ps.setLong(3, tax);
            ps.setLong(4, tax);
            ps.executeUpdate();
        }
    }

    private void ensurePot(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("bank_accounts") + " (uuid, name, pieces) VALUES (?, ?, 0)")) {
            ps.setString(1, com.mrgregles.bsp_core.market.Market.POT.toString());
            ps.setString(2, com.mrgregles.bsp_core.market.Market.POT_NAME);
            ps.executeUpdate();
        }
    }

    /** Adds {@code delta} to an account; with a negative delta only if the balance covers it. False when it did not change. */
    private boolean addPieces(Connection c, UUID id, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = pieces + ? WHERE uuid = ? AND pieces + ? >= 0")) {
            ps.setLong(1, delta);
            ps.setString(2, id.toString());
            ps.setLong(3, delta);
            return ps.executeUpdate() == 1;
        }
    }

    private long piecesOf(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pieces FROM " + t("bank_accounts") + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private void insertLot(Connection c, com.mrgregles.bsp_core.market.MarketLot lot, boolean ignore) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT " + (ignore ? "IGNORE " : "") + "INTO " + t("market_lots")
                + " (id, seller, seller_name, price, listed_ms, ends_ms, server_id, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, lot.id);
            ps.setString(2, lot.seller.toString());
            ps.setString(3, lot.sellerName.length() > 16 ? lot.sellerName.substring(0, 16) : lot.sellerName);
            ps.setLong(4, lot.price);
            ps.setLong(5, lot.listedMs);
            ps.setLong(6, lot.endsMs);
            ps.setString(7, lot.server);
            ps.setString(8, lot.save().toString());
            ps.executeUpdate();
        }
    }

    /** The open lot, locked for this transaction; null when it is not there. */
    @javax.annotation.Nullable
    private com.mrgregles.bsp_core.market.MarketLot lockLot(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT data FROM " + t("market_lots") + " WHERE id = ? FOR UPDATE")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lotOf(rs.getString(1)) : null;
            }
        }
    }

    private void deleteLot(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("market_lots") + " WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private static com.mrgregles.bsp_core.market.MarketLot lotOf(String data) throws SQLException {
        try {
            return com.mrgregles.bsp_core.market.MarketLot.load(net.minecraft.nbt.TagParser.parseTag(data));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new SQLException("unreadable market lot: " + e.getMessage());
        }
    }

    private void insertCollect(Connection c, UUID player, java.util.List<net.minecraft.world.item.ItemStack> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("market_collect") + " (uuid, item) VALUES (?, ?)")) {
            for (net.minecraft.world.item.ItemStack s : items) {
                if (s.isEmpty()) {
                    continue;
                }
                ps.setString(1, player.toString());
                ps.setString(2, s.save(new net.minecraft.nbt.CompoundTag()).toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private java.util.List<net.minecraft.world.item.ItemStack> readCollect(Connection c, UUID player, boolean lock) throws SQLException {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT item FROM " + t("market_collect") + " WHERE uuid = ? AND taking IS NULL ORDER BY id" + (lock ? " FOR UPDATE" : ""))) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        net.minecraft.world.item.ItemStack s = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(rs.getString(1)));
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        com.mrgregles.bsp_core.BSPCore.LOGGER.error("BSP-Core market: an unreadable item in {}'s Collect box was skipped: {}", player, e.getMessage());
                    }
                }
            }
        }
        return out;
    }

    com.mrgregles.bsp_core.market.MarketDb.Done marketList(com.mrgregles.bsp_core.market.MarketLot lot, long tax, int maxLots) throws SQLException {
        return marketTx(c -> {
            // the seller's row first: their listings on every server queue here, so the count below sees all of them
            lockAccounts(c, lot.seller);
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + t("market_lots") + " WHERE seller = ?")) {
                ps.setString(1, lot.seller.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= maxLots) {
                        throw new MarketNo("lots");
                    }
                }
            }
            if (tax > 0) {
                if (!addPieces(c, lot.seller, -tax)) {
                    throw new MarketNo("short");
                }
                toPot(c, tax);
            }
            insertLot(c, lot, false);
            insertAttempt(c, lot.id);
            return new com.mrgregles.bsp_core.market.MarketDb.Done("ok", lot, piecesOf(c, lot.seller), -1, piecesOf(c, com.mrgregles.bsp_core.market.Market.POT));
        }, com.mrgregles.bsp_core.market.MarketDb.Done::no);
    }

    /** Records a player as seen: the time only moves forward; the name, operator status and server are the latest, and the ranks too unless they could not be read. */
    void lotterySeen(com.mrgregles.bsp_core.lottery.LotteryPlayer p, boolean ranksKnown) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("lottery_players") + " (uuid, name, seen_ms, ranks, op, server_id) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), seen_ms = GREATEST(seen_ms, VALUES(seen_ms)), ranks = IF(? = 1, VALUES(ranks), ranks), op = VALUES(op), server_id = VALUES(server_id)")) {
            String ranks = p.ranksText();
            if (ranks.length() > 1024) { // whole rank names only
                ranks = ranks.substring(0, Math.max(0, ranks.lastIndexOf(',', 1024)));
            }
            ps.setString(1, p.id().toString());
            ps.setString(2, p.name().length() > 16 ? p.name().substring(0, 16) : p.name());
            ps.setLong(3, p.seenMs());
            ps.setString(4, ranks);
            ps.setInt(5, p.op() ? 1 : 0);
            ps.setString(6, p.server().length() > 64 ? p.server().substring(0, 64) : p.server());
            ps.setInt(7, ranksKnown ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /**
     * Claims the lottery's draw for {@code draw} (shared state key {@code lottery_draw}, "id@ms"): true when it was free, or
     * held by a draw older than {@code staleMs} (a server that stopped mid-draw).
     */
    boolean lotteryClaim(String draw, long now, long staleMs) throws SQLException {
        putState(Map.of("lottery_draw", ""), true);
        String held = readStateKey("lottery_draw");
        if (held == null) {
            return false;
        }
        if (!held.isEmpty()) {
            int at = held.lastIndexOf('@');
            long since;
            try {
                since = at < 0 ? 0 : Long.parseLong(held.substring(at + 1));
            } catch (NumberFormatException e) {
                since = 0;
            }
            if (now - since < staleMs) {
                return false;
            }
        }
        return casState("lottery_draw", held, draw + "@" + now);
    }

    /** Frees the lottery's draw if {@code draw} still holds it. */
    void lotteryRelease(String draw) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("state") + " SET v = '' WHERE k = 'lottery_draw' AND v LIKE ?")) {
            ps.setString(1, draw + "@%");
            ps.executeUpdate();
        }
    }

    /**
     * Pays the whole lottery pot to {@code win}'s player in one transaction, with both bank log rows, and puts the win at the
     * front of the shared list of wins: {pieces paid, the winner's balance after}, or {0, -1} when the pot is empty (nothing changed). Throws
     * {@code MarketDb.Untouched} when it surely did not happen; any other exception may have come after the commit.
     */
    long[] lotteryPayout(com.mrgregles.bsp_core.lottery.LotteryWin win) throws SQLException {
        UUID pot = com.mrgregles.bsp_core.market.Market.POT;
        return inTransaction(c -> {
            lockAccounts(c, pot, win.id());
            long amount = piecesOf(c, pot);
            if (amount <= 0) {
                return new long[]{0, -1};
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("bank_accounts") + " SET pieces = 0 WHERE uuid = ?")) {
                ps.setString(1, pot.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("bank_accounts") + " (uuid, name, pieces) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE pieces = pieces + ?")) {
                ps.setString(1, win.id().toString());
                ps.setString(2, win.name().length() > 16 ? win.name().substring(0, 16) : win.name());
                ps.setLong(3, amount);
                ps.setLong(4, amount);
                ps.executeUpdate();
            }
            String old = "";
            try (PreparedStatement ps = c.prepareStatement("SELECT v FROM " + t("state") + " WHERE k = ? FOR UPDATE")) {
                ps.setString(1, com.mrgregles.bsp_core.lottery.LotteryWin.STATE_KEY);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        old = rs.getString(1);
                    }
                }
            }
            var paid = new com.mrgregles.bsp_core.lottery.LotteryWin(win.id(), win.name(), amount, win.ms(), win.entrants(), win.draw());
            String wins = com.mrgregles.bsp_core.lottery.LotteryWin.text(com.mrgregles.bsp_core.lottery.LotteryWin.prepend(paid, com.mrgregles.bsp_core.lottery.LotteryWin.fromText(old)));
            // both log rows in this transaction, so a payout that went in always has them (and an offline winner is told)
            String here = com.mrgregles.bsp_core.bank.Bank.serverId();
            bankLog(c, pot, new BankTransaction(win.ms(), BankTransaction.Type.LOTTERY, win.name(), com.mrgregles.bsp_core.lottery.LotteryDraw.potDetail(win.draw()), -amount, -1, 0, here));
            bankLog(c, win.id(), new BankTransaction(win.ms(), BankTransaction.Type.LOTTERY, com.mrgregles.bsp_core.market.Market.POT_NAME,
                    com.mrgregles.bsp_core.lottery.LotteryDraw.wonDetail(win.entrants()), amount, -1, 0, here));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("state") + " (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)")) {
                ps.setString(1, com.mrgregles.bsp_core.lottery.LotteryWin.STATE_KEY);
                ps.setString(2, wins);
                ps.executeUpdate();
            }
            return new long[]{amount, piecesOf(c, win.id())};
        });
    }

    /**
     * Pays the daily reward if {@code day} was not paid yet, claiming the day and moving the pieces (with the log row) in one
     * transaction: {pieces paid, balance after}; {0, -1} when the day was paid already; {-1, -1} when the player has no bank
     * row (nothing claimed). The claim is one conditional UPDATE on a row made first if missing, so it takes no gap lock and
     * holds under any isolation level. A day more than one ahead of {@code day} (written by a server whose clock ran ahead)
     * counts as not paid.
     */
    long[] dailyReward(UUID id, int day, long amount, String serverId) throws SQLException {
        return marketTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("daily_rewards") + " (uuid, day) VALUES (?, -1)")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            int claimed;
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("daily_rewards") + " SET day = ? WHERE uuid = ? AND (day < ? OR day > ?)")) {
                ps.setInt(1, day);
                ps.setString(2, id.toString());
                ps.setInt(3, day);
                ps.setInt(4, day + 1);
                claimed = ps.executeUpdate();
            }
            if (claimed != 1) {
                return new long[]{0, -1};
            }
            if (!addPieces(c, id, amount)) {
                throw new MarketNo("no_account"); // rolls the claim back too
            }
            bankLog(c, id, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.GRANT, "Shatter Bank", "daily reward", amount, -1, 0, serverId));
            return new long[]{amount, piecesOf(c, id)};
        }, key -> new long[]{-1, -1});
    }

    /** Players seen at or after {@code sinceMs}. */
    List<com.mrgregles.bsp_core.lottery.LotteryPlayer> lotteryPlayers(long sinceMs) throws SQLException {
        List<com.mrgregles.bsp_core.lottery.LotteryPlayer> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT uuid, name, seen_ms, ranks, op, server_id FROM " + t("lottery_players") + " WHERE seen_ms >= ?")) {
            ps.setLong(1, sinceMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new com.mrgregles.bsp_core.lottery.LotteryPlayer(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("seen_ms"),
                            com.mrgregles.bsp_core.lottery.LotteryPlayer.ranksOf(rs.getString("ranks")), rs.getInt("op") != 0, rs.getString("server_id")));
                }
            }
        }
        return out;
    }

    /** Creates the lottery pot's empty bank row if it is not there (at start, so every way into the pot finds it). */
    void marketEnsurePot() throws SQLException {
        ensurePot(conn());
    }

    /** Records that an attempt reached the database; false when it was there already. */
    private boolean insertAttempt(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("market_attempts") + " (id, ms) VALUES (?, ?)")) {
            ps.setString(1, id);
            ps.setLong(2, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        }
    }

    /** Whether a listing (by its lot id) or a held-item write reached the database. */
    boolean marketCommitted(String attempt) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT 1 FROM " + t("market_attempts") + " WHERE id = ?")) {
            ps.setString(1, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Writes what a server held while the database was away, in one transaction, at most once per {@code attempt}: lots from
     * before the network (each at most once, by its id), Collect boxes, and listings whose answer was lost ({@code unsure}:
     * dropped when the listing went in, else their items go to the seller's Collect box).
     */
    void marketFlush(String attempt, java.util.List<com.mrgregles.bsp_core.market.MarketLot> lots, java.util.List<com.mrgregles.bsp_core.market.MarketLot> unsure,
                     java.util.Map<UUID, java.util.List<net.minecraft.world.item.ItemStack>> boxes) throws SQLException {
        inTransaction(c -> {
            if (!insertAttempt(c, attempt)) {
                return false; // written before; only its answer was lost
            }
            for (com.mrgregles.bsp_core.market.MarketLot lot : lots) {
                if (insertAttempt(c, lot.id)) {
                    insertLot(c, lot, true);
                }
            }
            for (com.mrgregles.bsp_core.market.MarketLot lot : unsure) {
                if (insertAttempt(c, lot.id)) { // the listing never went in: its stacks are the seller's again
                    insertCollect(c, lot.seller, lot.items());
                }
            }
            for (var e : boxes.entrySet()) {
                insertCollect(c, e.getKey(), e.getValue());
            }
            return true;
        });
    }

    /** Marks of boxes being emptied that were never finished (a server stopped between taking and delivering) go back; old attempt records go. */
    void marketSweep(long now) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("market_collect") + " SET taking = NULL, taking_ms = 0 WHERE taking IS NOT NULL AND taking_ms < ?")) {
            ps.setLong(1, now - 10 * 60_000L);
            int n = ps.executeUpdate();
            if (n > 0) {
                com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core market: {} Collect box stack(s) left half taken went back into their boxes", n);
            }
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("market_attempts") + " WHERE ms < ?")) {
            ps.setLong(1, now - 30L * 24 * 60 * 60_000L);
            ps.executeUpdate();
        }
    }

    /** The open lots' ids, newest first: no lot data, so cheap however heavy the lots are. */
    java.util.List<String> marketOpenIds() throws SQLException {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT id FROM " + t("market_lots") + " ORDER BY listed_ms DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** The lots with these ids (those still open), read in batches of 200. */
    java.util.List<com.mrgregles.bsp_core.market.MarketLot> marketLots(java.util.List<String> ids) throws SQLException {
        java.util.List<com.mrgregles.bsp_core.market.MarketLot> out = new java.util.ArrayList<>();
        for (int from = 0; from < ids.size(); from += 200) {
            java.util.List<String> part = ids.subList(from, Math.min(ids.size(), from + 200));
            String marks = String.join(", ", java.util.Collections.nCopies(part.size(), "?"));
            try (PreparedStatement ps = conn().prepareStatement("SELECT id, data FROM " + t("market_lots") + " WHERE id IN (" + marks + ")")) {
                for (int i = 0; i < part.size(); i++) {
                    ps.setString(i + 1, part.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            out.add(lotOf(rs.getString(2)));
                        } catch (SQLException e) {
                            com.mrgregles.bsp_core.BSPCore.LOGGER.error("BSP-Core market: lot {} is unreadable and was left out: {}", rs.getString(1), e.getMessage());
                        }
                    }
                }
            }
        }
        return out;
    }

    com.mrgregles.bsp_core.market.MarketDb.Done marketBuy(String id, UUID buyer, long tax, long now) throws SQLException {
        return marketTx(c -> {
            com.mrgregles.bsp_core.market.MarketLot lot = lockLot(c, id);
            if (lot == null || lot.endedBy(now)) {
                throw new MarketNo("gone");
            }
            if (lot.seller.equals(buyer)) {
                throw new MarketNo("own");
            }
            lockAccounts(c, buyer, lot.seller);
            if (!addPieces(c, buyer, -(lot.price + tax))) {
                throw new MarketNo("short");
            }
            if (!addPieces(c, lot.seller, lot.price)) {
                throw new MarketNo("seller"); // no bank row for the seller on the network: nobody would be paid
            }
            if (tax > 0) {
                toPot(c, tax);
            }
            insertCollect(c, buyer, lot.items()); // the items are the buyer's from this commit on, whatever happens to the answer
            deleteLot(c, id);
            return new com.mrgregles.bsp_core.market.MarketDb.Done("ok", lot, piecesOf(c, buyer), piecesOf(c, lot.seller), piecesOf(c, com.mrgregles.bsp_core.market.Market.POT));
        }, com.mrgregles.bsp_core.market.MarketDb.Done::no);
    }

    com.mrgregles.bsp_core.market.MarketDb.Done marketClose(String id) throws SQLException {
        return marketTx(c -> {
            com.mrgregles.bsp_core.market.MarketLot lot = lockLot(c, id);
            if (lot == null) {
                throw new MarketNo("gone");
            }
            insertCollect(c, lot.seller, lot.items());
            deleteLot(c, id);
            return new com.mrgregles.bsp_core.market.MarketDb.Done("ok", lot, -1, -1, -1);
        }, com.mrgregles.bsp_core.market.MarketDb.Done::no);
    }

    void marketAddCollect(UUID player, java.util.List<net.minecraft.world.item.ItemStack> items) throws SQLException {
        inTransaction(c -> {
            insertCollect(c, player, items);
            return true;
        });
    }

    /** Marks what is in the player's box with {@code attempt} and returns it; the rows stay until {@link #marketTaken}. */
    java.util.List<net.minecraft.world.item.ItemStack> marketTakeCollect(UUID player, String attempt) throws SQLException {
        return inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("market_collect") + " SET taking = ?, taking_ms = ? WHERE uuid = ? AND taking IS NULL")) {
                ps.setString(1, attempt);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, player.toString());
                ps.executeUpdate();
            }
            java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT item FROM " + t("market_collect") + " WHERE taking = ? ORDER BY id")) {
                ps.setString(1, attempt);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            net.minecraft.world.item.ItemStack s = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(rs.getString(1)));
                            if (!s.isEmpty()) {
                                out.add(s);
                            }
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                            com.mrgregles.bsp_core.BSPCore.LOGGER.error("BSP-Core market: an unreadable item in {}'s Collect box was skipped: {}", player, e.getMessage());
                        }
                    }
                }
            }
            return out;
        });
    }

    /** The items of {@code attempt} were delivered: their rows go. */
    void marketTaken(String attempt) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("market_collect") + " WHERE taking = ?")) {
            ps.setString(1, attempt);
            ps.executeUpdate();
        }
    }

    /** The items of {@code attempt} were not delivered (its answer was lost): back into the box. */
    void marketUntake(String attempt) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("market_collect") + " SET taking = NULL, taking_ms = 0 WHERE taking = ?")) {
            ps.setString(1, attempt);
            ps.executeUpdate();
        }
    }

    java.util.List<net.minecraft.world.item.ItemStack> marketPeekCollect(UUID player) throws SQLException {
        return readCollect(conn(), player, false);
    }

    // ------------------------------------------------------------------ live trades across servers (one transaction per call)

    /** "done" or "aborted" for an attempt, or null when undecided; locks the row when it exists. */
    @javax.annotation.Nullable
    private String tradeOutcome(Connection c, String attempt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT outcome FROM " + t("trade_outcomes") + " WHERE attempt = ? FOR UPDATE")) {
            ps.setString(1, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Writes the attempt's outcome; false when one was already written (the other one wins). */
    private boolean decide(Connection c, String attempt, String outcome) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + t("trade_outcomes") + " (attempt, outcome, ms) VALUES (?, ?, ?)")) {
            ps.setString(1, attempt);
            ps.setString(2, outcome);
            ps.setLong(3, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        }
    }

    private void addPending(Connection c, UUID player, java.util.List<net.minecraft.world.item.ItemStack> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("pending_items") + " (uuid, item) VALUES (?, ?)")) {
            for (net.minecraft.world.item.ItemStack s : items) {
                if (s.isEmpty()) {
                    continue;
                }
                ps.setString(1, player.toString());
                ps.setString(2, s.save(new net.minecraft.nbt.CompoundTag()).toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** The escrow rows of an owner for an attempt, as items; deletes them. */
    private java.util.List<net.minecraft.world.item.ItemStack> takeEscrow(Connection c, String attempt, UUID owner) throws SQLException {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT item FROM " + t("trade_escrow") + " WHERE attempt = ? AND owner = ? ORDER BY id FOR UPDATE")) {
            ps.setString(1, attempt);
            ps.setString(2, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        net.minecraft.world.item.ItemStack s = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(rs.getString(1)));
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        com.mrgregles.bsp_core.BSPCore.LOGGER.error("BSP-Core trade: an unreadable escrow item of attempt {} was skipped: {}", attempt, e.getMessage());
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("trade_escrow") + " WHERE attempt = ? AND owner = ?")) {
            ps.setString(1, attempt);
            ps.setString(2, owner.toString());
            ps.executeUpdate();
        }
        return out;
    }

    com.mrgregles.bsp_core.trade.TradeDb.Settled tradeSettle(String attempt, UUID a, long aPieces, UUID b, long bPieces, java.util.List<net.minecraft.world.item.ItemStack> hostItems,
                                                             @javax.annotation.Nullable UUID hostItemsTo, @javax.annotation.Nullable UUID escrowOwner, @javax.annotation.Nullable UUID escrowTo) throws SQLException {
        try {
            return inTransaction(c -> {
                if (!decide(c, attempt, "done")) {
                    throw new MarketNo("aborted");
                }
                if (aPieces > 0 || bPieces > 0) {
                    lockAccounts(c, a, b);
                }
                if (aPieces > 0 && !addPieces(c, a, -aPieces)) {
                    throw new MarketNo("short_a");
                }
                if (bPieces > 0 && !addPieces(c, b, -bPieces)) {
                    throw new MarketNo("short_b");
                }
                if (aPieces > 0) {
                    addPieces(c, b, aPieces);
                }
                if (bPieces > 0) {
                    addPieces(c, a, bPieces);
                }
                if (hostItemsTo != null) {
                    addPending(c, hostItemsTo, hostItems);
                }
                if (escrowOwner != null && escrowTo != null) {
                    addPending(c, escrowTo, takeEscrow(c, attempt, escrowOwner));
                }
                return new com.mrgregles.bsp_core.trade.TradeDb.Settled("ok", piecesOf(c, a), piecesOf(c, b));
            });
        } catch (MarketNo n) {
            return new com.mrgregles.bsp_core.trade.TradeDb.Settled(n.key, -1, -1);
        }
    }

    void tradeEscrow(String attempt, UUID owner, java.util.List<net.minecraft.world.item.ItemStack> items) throws SQLException {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("trade_escrow") + " (attempt, owner, item, ms) VALUES (?, ?, ?, ?)")) {
                long now = System.currentTimeMillis();
                for (net.minecraft.world.item.ItemStack s : items) {
                    ps.setString(1, attempt);
                    ps.setString(2, owner.toString());
                    ps.setString(3, s.save(new net.minecraft.nbt.CompoundTag()).toString());
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            insertAttempt(c, "e:" + attempt); // so a lost answer can be asked about (market_attempts)
            return true;
        });
    }

    /** See {@link com.mrgregles.bsp_core.trade.TradeDb#recoverEscrow}. */
    com.mrgregles.bsp_core.trade.TradeDb.Recovered tradeRecoverEscrow(String attempt, UUID owner) throws SQLException {
        return inTransaction(c -> {
            decide(c, attempt, "aborted");
            String outcome = tradeOutcome(c, attempt);
            boolean escrowed;
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + t("market_attempts") + " WHERE id = ?")) {
                ps.setString(1, "e:" + attempt);
                try (ResultSet rs = ps.executeQuery()) {
                    escrowed = rs.next();
                }
            }
            if (!"aborted".equals(outcome)) {
                return new com.mrgregles.bsp_core.trade.TradeDb.Recovered(outcome == null ? "aborted" : outcome, escrowed, java.util.List.of());
            }
            return new com.mrgregles.bsp_core.trade.TradeDb.Recovered("aborted", escrowed, takeEscrow(c, attempt, owner));
        });
    }

    String tradeDecideAbort(String attempt) throws SQLException {
        return inTransaction(c -> {
            decide(c, attempt, "aborted");
            String o = tradeOutcome(c, attempt);
            return o == null ? "aborted" : o;
        });
    }

    java.util.List<net.minecraft.world.item.ItemStack> tradeTakeEscrow(String attempt, UUID owner) throws SQLException {
        return inTransaction(c -> {
            if (!"aborted".equals(tradeOutcome(c, attempt))) {
                return java.util.List.of();
            }
            return takeEscrow(c, attempt, owner);
        });
    }

    /** Escrow older than {@code beforeMs}: each attempt is decided "aborted" unless done, and its items go to their owners as pending items. */
    int tradeSweep(long beforeMs) throws SQLException {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT DISTINCT attempt, owner FROM " + t("trade_escrow") + " WHERE ms < ?")) {
            ps.setLong(1, beforeMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        }
        int n = 0;
        for (String[] r : rows) {
            n += inTransaction(c -> {
                decide(c, r[0], "aborted");
                if (!"aborted".equals(tradeOutcome(c, r[0]))) {
                    return 0;
                }
                UUID owner = UUID.fromString(r[1]);
                java.util.List<net.minecraft.world.item.ItemStack> items = takeEscrow(c, r[0], owner);
                addPending(c, owner, items);
                return items.size();
            });
        }
        // outcome rows a day old: every attempt that could ask about them has long ended (decision D5)
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + t("trade_outcomes") + " WHERE ms < ?")) {
            ps.setLong(1, System.currentTimeMillis() - 24L * 60 * 60_000L);
            ps.executeUpdate();
        }
        return n;
    }
}
