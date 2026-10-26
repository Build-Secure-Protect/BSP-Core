package com.mrgregles.bsp_core.storage;

import java.sql.SQLException;

/**
 * One more try for a locked transaction that MySQL (or MariaDB) ended over a lock: to break a
 * deadlock between two servers (error 1213), or after waiting too long for another server's lock
 * (1205). The transaction was rolled back, so running it again is safe. Any other failure, or a
 * second one, is thrown.
 */
public final class SqlRetry {
    private SqlRetry() {}

    /** A transaction that may run twice. */
    public interface Work<T> {
        T run() throws SQLException;
    }

    /** True for a transaction ended over a lock: a deadlock (1213) or a lock wait timeout (1205). */
    public static boolean lockFailure(SQLException e) {
        return e.getErrorCode() == 1213 || e.getErrorCode() == 1205;
    }

    public static <T> T once(Work<T> work) throws SQLException {
        try {
            return work.run();
        } catch (SQLException e) {
            if (!lockFailure(e)) {
                throw e;
            }
            return work.run();
        }
    }
}
