package com.mrgregles.bsp_core.storage;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC drivers BSP-Core can use, in the order it tries them. The MySQL name is relocated by the full jar's shadowJar
 * (string constants included) to the driver bundled inside it; the slim jar keeps the plain name, so on Mohist it finds the
 * server's own driver. Drivers are created directly: java.sql.DriverManager refuses drivers loaded by a mod class loader.
 */
public final class JdbcDrivers {
    private JdbcDrivers() {}

    /** A loaded driver and the URL scheme it takes ({@code jdbc:mysql://} or {@code jdbc:mariadb://}). */
    public record Found(Driver driver, String urlPrefix) {}

    private static final String[][] DRIVERS = {{"com.mysql.cj.jdbc.Driver", "jdbc:mysql://"}, {"org.mariadb.jdbc.Driver", "jdbc:mariadb://"}};

    /** A new instance of every driver that loads, MySQL first. */
    public static List<Found> available() {
        List<Found> out = new ArrayList<>();
        for (String[] d : DRIVERS) {
            try {
                out.add(new Found((Driver) Class.forName(d[0]).getDeclaredConstructor().newInstance(), d[1]));
            } catch (ReflectiveOperationException | LinkageError | ClassCastException notInstalled) {
                // not on this server
            }
        }
        return out;
    }
}
