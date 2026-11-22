package com.mrgregles.bsp_core.looks;

import java.util.List;

/**
 * The hats a totem can wear. VIP ({@link TotemLooks#DRESS_PERMISSION}) has every hat not named in {@code looks.exclusiveHats};
 * any hat can also be given on its own with {@link #permission}, for a store purchase or a season prize.
 */
public final class TotemHats {
    private TotemHats() {}

    /** Every hat id, in the order the LOOKS tab lists them. A new hat goes last. */
    public static final List<String> IDS = List.of("top_hat", "halo", "flat_cap", "beer_cap", "sports_cap", "beanie", "wizard", "devil",
            "cowboy", "viking", "party", "chef", "propeller");

    public static boolean known(String id) {
        return IDS.contains(id);
    }

    /** The LuckPerms permission that gives this one hat, e.g. {@code bsp_core.looks.hat.top_hat}. */
    public static String permission(String id) {
        return "bsp_core.looks.hat." + id;
    }
}
