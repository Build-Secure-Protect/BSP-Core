package com.mrgregles.bsp_core.storage;

import net.minecraft.core.GlobalPos;

import java.sql.Connection;
import java.sql.Driver;
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
        // The driver is created directly: java.sql.DriverManager refuses drivers that were loaded by a mod class loader.
        String[][] drivers = {{"com.mysql.cj.jdbc.Driver", "jdbc:mysql://"}, {"org.mariadb.jdbc.Driver", "jdbc:mariadb://"}};
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        SQLException last = null;
        for (String[] d : drivers) {
            Driver driver;
            try {
                driver = (Driver) Class.forName(d[0]).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError notInstalled) {
                continue;
            }
            String url = d[1] + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&connectTimeout=5000&socketTimeout=15000";
            try {
                Connection c = driver.connect(url, props);
                if (c != null) {
                    return c;
                }
            } catch (SQLException e) {
                last = e;
            }
        }
        throw last != null ? last : new SQLException("No MySQL or MariaDB JDBC driver is available to BSP-Core");
    }

    private Connection conn() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // already gone
                }
            }
            connection = open();
        }
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
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_accounts") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL DEFAULT '', "
                    + "pieces BIGINT NOT NULL DEFAULT 0, c0 INT NOT NULL DEFAULT 0, c1 INT NOT NULL DEFAULT 0, c2 INT NOT NULL DEFAULT 0, c3 INT NOT NULL DEFAULT 0, c4 INT NOT NULL DEFAULT 0, "
                    + "accept_pay TINYINT NOT NULL DEFAULT 1, remote_shops TINYINT NOT NULL DEFAULT 0, seen_ms BIGINT NOT NULL DEFAULT 0, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX (name))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_log") + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, ms BIGINT NOT NULL, "
                    + "type VARCHAR(16) NOT NULL, with_name VARCHAR(64) NOT NULL, detail VARCHAR(255) NOT NULL, pieces BIGINT NOT NULL, coin_tier INT NOT NULL, coin_count INT NOT NULL, "
                    + "server_id VARCHAR(64) NOT NULL, INDEX (uuid, ms))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shops") + " (id VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL, data MEDIUMTEXT NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bank_boxes") + " (uuid CHAR(36) NOT NULL PRIMARY KEY, slots INT NOT NULL, rent_until BIGINT NOT NULL, "
                    + "items MEDIUMTEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
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

    /** Takes out, and returns, everything waiting for the given players. */
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
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
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

    void bankFlags(UUID id, Boolean acceptPay, Boolean remoteShops, Long seenMs) throws SQLException {
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
        if (seenMs != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + t("bank_accounts") + " SET seen_ms = GREATEST(seen_ms, ?) WHERE uuid = ?")) {
                ps.setLong(1, seenMs);
                ps.setString(2, id.toString());
                ps.executeUpdate();
            }
        }
    }

    void bankLog(UUID id, BankTransaction tx) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + t("bank_log") + " (uuid, ms, type, with_name, detail, pieces, coin_tier, coin_count, server_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setLong(2, tx.ms());
            ps.setString(3, tx.type().name());
            ps.setString(4, tx.with().length() > 64 ? tx.with().substring(0, 64) : tx.with());
            ps.setString(5, tx.detail().length() > 255 ? tx.detail().substring(0, 255) : tx.detail());
            ps.setLong(6, tx.pieces());
            ps.setInt(7, tx.coinTier());
            ps.setInt(8, tx.coinCount());
            ps.setString(9, tx.server());
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
        try (PreparedStatement ps = conn().prepareStatement("SELECT ms, type, with_name, detail, pieces, coin_tier, coin_count, server_id FROM " + t("bank_log")
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
                    out.add(new BankTransaction(rs.getLong(1), type, rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getInt(7), rs.getString(8)));
                }
            }
        }
        return out;
    }

    void bankResetRemote() throws SQLException {
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("UPDATE " + t("bank_accounts") + " SET remote_shops = 0");
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
}
