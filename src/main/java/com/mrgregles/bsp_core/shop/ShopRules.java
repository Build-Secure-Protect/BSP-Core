package com.mrgregles.bsp_core.shop;

import javax.annotation.Nullable;

/**
 * The limits a shop's settings and cards keep, shared by the Workbench, the commands, the import
 * file and {@link com.mrgregles.bsp_core.api.BspShops}, so a value one of them refuses none of the
 * others lets in. Text is cleaned rather than refused: control characters, line separators and
 * section signs become spaces.
 */
public final class ShopRules {
    private ShopRules() {}

    public static final String ID = "[a-z0-9_]{1,32}";
    public static final int PLAYTIME_MAX = 100_000, SLOTS_MAX = 54, INTERVAL_HOURS_MAX = 8_760;
    public static final long PRICE_MAX = 1_000_000_000_000L, COINS_MAX = 10_000, LIMIT_MAX = 1_000_000_000;
    public static final int SNBT_MAX = 4_096;
    public static final long OFFER_YEARS_MS = 10L * 366 * 24 * 3_600_000L;

    /**
     * {@code text} with every control character (C0 and C1), line or paragraph separator and
     * section sign (Minecraft's colour codes) made a space, runs of spaces made one, and trimmed.
     */
    public static String clean(String text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean bad = c < 0x20 || c >= 0x7F && c <= 0x9F || c == ' ' || c == ' ' || c == '§';
            b.append(bad ? ' ' : c);
        }
        return b.toString().replaceAll(" {2,}", " ").trim();
    }

    /** A shop's name cleaned, or null when it is empty or longer than {@link Shop#NAME_MAX} after cleaning. */
    @Nullable
    public static String name(String text) {
        String n = clean(text);
        return n.isEmpty() || n.length() > Shop.NAME_MAX ? null : n;
    }

    /** True for a permission node of letters, digits and {@code . _ - *}, up to {@link Requirement#NODE_MAX}; empty is no node. */
    public static boolean node(String node) {
        return node.length() <= Requirement.NODE_MAX && node.matches("[A-Za-z0-9._\\-*]*");
    }

    /** A requirement within the limits (its reason cleaned), or null when it is not one. */
    @Nullable
    public static Requirement requirement(String permission, int playtimeHours, boolean hide, String reason) {
        String why = clean(reason);
        if (!node(permission) || playtimeHours < 0 || playtimeHours > PLAYTIME_MAX || why.length() > Requirement.REASON_MAX) {
            return null;
        }
        return new Requirement(permission, playtimeHours, hide, why);
    }

    /** True for opening hours within their mode's day: minutes 0 to 1,439 on the clock, ticks 0 to 23,999 in game. */
    public static boolean hours(OpenHours h) {
        int max = h.mode() == OpenHours.Mode.GAME ? 23_999 : 1_439;
        return h.openMin() >= 0 && h.openMin() <= max && h.closeMin() >= 0 && h.closeMin() <= max;
    }

    /** True for a price that fits: -1 (off) or 0 to 10^12 pieces, or for a coin price at most {@link #COINS_MAX} coins (a typo must never pay out billions). */
    public static boolean price(long price, int currency) {
        return price == -1 || price >= 0 && price <= (currency < 0 ? PRICE_MAX : COINS_MAX);
    }

    /** "bad_price" for a card with a price that does not fit ({@link #price}), or null: checked wherever a price or the currency changes. */
    @Nullable
    public static String prices(ShopItem it) {
        return price(it.buy, it.currency) && price(it.sell, it.currency) ? null : "bad_price";
    }
}
