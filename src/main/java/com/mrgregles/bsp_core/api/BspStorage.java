package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.storage.JdbcDrivers;

import java.sql.Driver;
import java.util.List;
import java.util.Optional;

/**
 * The JDBC driver BSP-Core uses for its shared database (BSP-Teleport request R9). The full jar bundles MySQL Connector/J
 * under a relocated name that may change; ask here instead of naming it. Create connections with
 * {@code driver.connect(url, props)}: {@code java.sql.DriverManager} refuses drivers loaded by a mod class loader.
 */
public final class BspStorage {
    private BspStorage() {}

    /**
     * A new instance of the driver BSP-Core would use: the MySQL driver bundled in the full jar, or with the slim jar the
     * server's own MySQL driver, else MariaDB's; empty when none is installed. It takes {@code jdbc:mysql://} URLs
     * (MariaDB's driver {@code jdbc:mariadb://}; see {@link #jdbcUrlPrefix()}).
     */
    public static Optional<Driver> jdbcDriver() {
        List<JdbcDrivers.Found> found = JdbcDrivers.available();
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0).driver());
    }

    /** The class name of {@link #jdbcDriver()}, for logs; empty when there is no driver. */
    public static Optional<String> jdbcDriverClass() {
        return jdbcDriver().map(d -> d.getClass().getName());
    }

    /** The URL scheme {@link #jdbcDriver()} takes: {@code jdbc:mysql://} or {@code jdbc:mariadb://}; empty when there is no driver. */
    public static Optional<String> jdbcUrlPrefix() {
        List<JdbcDrivers.Found> found = JdbcDrivers.available();
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0).urlPrefix());
    }
}
