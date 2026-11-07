package com.mrgregles.bsp_core.credit;

import java.util.List;
import java.util.Locale;

/**
 * Where a purchase is made, for deciding which tickets may pay for it.
 * <ul>
 *   <li>{@link #shop}: a shop and its parents (a ticket for a shop also pays in its sub-shops);</li>
 *   <li>{@link #plot}: a Land Office plot for sale, by name ("any plot" or the plot's name);</li>
 *   <li>{@link #ground}: open ground or enlarging a claim ("any plot" only);</li>
 *   <li>{@link #teleport}: a BSP-Teleport price.</li>
 * </ul>
 */
public record CreditPlace(Type type, List<String> shops, String plot) {
    public enum Type { SHOP, PLOT, GROUND, TELEPORT }

    /** A shop: its id first, then its parents' ids (outermost last). */
    public static CreditPlace shop(List<String> shopAndParents) {
        return new CreditPlace(Type.SHOP, List.copyOf(shopAndParents), "");
    }

    public static CreditPlace shop(String... shopAndParents) {
        return shop(List.of(shopAndParents));
    }

    public static CreditPlace plot(String name) {
        return new CreditPlace(Type.PLOT, List.of(), name == null ? "" : name.trim());
    }

    public static CreditPlace ground() {
        return new CreditPlace(Type.GROUND, List.of(), "");
    }

    public static CreditPlace teleport() {
        return new CreditPlace(Type.TELEPORT, List.of(), "");
    }

    /** Whether tickets of {@code kind} may pay here. */
    public boolean coveredBy(CreditKind kind) {
        return switch (type) {
            case SHOP -> shops.stream().anyMatch(kind.shops::contains);
            case PLOT -> kind.anyPlot || !plot.isEmpty() && kind.plots.stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).equals(plot.toLowerCase(Locale.ROOT)));
            case GROUND -> kind.anyPlot;
            case TELEPORT -> kind.teleports;
        };
    }
}
