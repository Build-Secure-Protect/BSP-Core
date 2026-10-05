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
