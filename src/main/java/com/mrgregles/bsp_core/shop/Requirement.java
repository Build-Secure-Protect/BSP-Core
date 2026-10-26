package com.mrgregles.bsp_core.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a card or a whole shop needs (spawn shops request SS-8): a permission node (asked of
 * LuckPerms through BSP-Core's bridge, so any node works; without LuckPerms only operators pass)
 * and hours of play (BSP-RankUp's count when it is installed, across the network when its storage
 * is shared; otherwise this server's Time Played). Locked, it shows greyed with the requirement
 * ({@code hide} false) or is not there at all ({@code hide} true). {@code reason} is a short text
 * shown in place of the node or hours.
 */
public record Requirement(String permission, int playtimeHours, boolean hide, String reason) {
    public static final Requirement NONE = new Requirement("", 0, false, "");
    /** The longest reason and permission node a requirement may have: what the shop screen's packet holds. */
    public static final int REASON_MAX = 64, NODE_MAX = 128;

    public boolean any() {
        return !permission.isEmpty() || playtimeHours > 0;
    }

    /** Why this player is locked out, or null when they pass. */
    @Nullable
    public Lock check(Player player) {
        if (!any()) {
            return null;
        }
        boolean perm = !permission.isEmpty() && !hasPermission(player, permission);
        long played = playtimeHours > 0 ? playMinutes(player) : 0;
        boolean play = playtimeHours > 0 && played < playtimeHours * 60L;
        return perm || play ? new Lock(perm, permission, play ? playtimeHours : 0, played, reason, hide) : null;
    }

    /** A failed requirement as the shop screen shows it: which part failed, the node, the hours needed and played (minutes), the reason, and whether the card is hidden. */
    public record Lock(boolean permission, String node, int hours, long playedMinutes, String reason, boolean hide) {}

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Permission", permission);
        t.putInt("PlaytimeHours", playtimeHours);
        t.putBoolean("Hide", hide);
        t.putString("Reason", reason);
        return t;
    }

    public static Requirement load(CompoundTag t) {
        return t.isEmpty() ? NONE : new Requirement(t.getString("Permission"), Math.max(0, t.getInt("PlaytimeHours")), t.getBoolean("Hide"), t.getString("Reason"));
    }

    /**
     * The command form: {@code none}, or words in any order: {@code permission <node>}, {@code
     * playtime <hours>}, {@code show} or {@code hide}, and {@code reason <text to the end>}. Null when
     * it is not one.
     */
    @Nullable
    public static Requirement parse(String text) {
        String[] w = text.trim().split("\\s+");
        if (w.length == 1 && (w[0].equalsIgnoreCase("none") || w[0].isEmpty())) {
            return NONE;
        }
        String permission = "", reason = "";
        int hours = 0;
        boolean hide = false;
        for (int i = 0; i < w.length; i++) {
            switch (w[i].toLowerCase(Locale.ROOT)) {
                case "permission" -> {
                    if (++i >= w.length) {
                        return null;
                    }
                    permission = w[i];
                }
                case "playtime" -> {
                    try {
                        hours = Integer.parseInt(w[++i]);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
                case "hide" -> hide = true;
                case "show" -> hide = false;
                case "reason" -> {
                    reason = String.join(" ", java.util.Arrays.copyOfRange(w, i + 1, w.length));
                    i = w.length;
                }
                default -> {
                    return null;
                }
            }
        }
        Requirement r = new Requirement(permission, Math.max(0, hours), hide, reason);
        return r.any() && reason.length() <= REASON_MAX && permission.length() <= NODE_MAX ? r : null;
    }

    /** The command form back ("permission bsp.shop.gold playtime 10 hide reason Gold rank"), or "none". */
    public String text() {
        if (!any()) {
            return "none";
        }
        StringBuilder b = new StringBuilder();
        if (!permission.isEmpty()) {
            b.append("permission ").append(permission).append(' ');
        }
        if (playtimeHours > 0) {
            b.append("playtime ").append(playtimeHours).append(' ');
        }
        b.append(hide ? "hide" : "show");
        if (!reason.isEmpty()) {
            b.append(" reason ").append(reason);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ the two checks

    /** GameTests: answers in place of LuckPerms (null: ask LuckPerms). */
    @Nullable
    private static java.util.function.BiPredicate<UUID, String> testPermissions;

    public static void usePermissions(@Nullable java.util.function.BiPredicate<UUID, String> answer) {
        testPermissions = answer;
    }

    private static boolean hasPermission(Player player, String node) {
        if (testPermissions != null) {
            return testPermissions.test(player.getUUID(), node);
        }
        Boolean lp = com.mrgregles.bsp_core.compat.LuckPermsBridge.hasPermission(player.getUUID(), node);
        return lp != null ? lp : player.hasPermissions(2);
    }

    /** Minutes played: BSP-RankUp's count when it is there (read by name: RankUp builds on BSP-Core, not the other way), else this server's Time Played. */
    public static long playMinutes(Player player) {
        Long rankUp = rankUpMinutes(player);
        if (rankUp != null) {
            return rankUp;
        }
        return player instanceof ServerPlayer sp ? sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 1200L : 0;
    }

    /** BSP-RankUp's methods, looked up once (a page of locked cards asks for every card). */
    @Nullable
    private static volatile java.lang.reflect.Method progressOf, countsOf;

    @Nullable
    private static Long rankUpMinutes(Player player) {
        if (!(player instanceof ServerPlayer sp) || !net.minecraftforge.fml.ModList.get().isLoaded("bsp_rankup")) {
            return null;
        }
        try {
            java.lang.reflect.Method of = progressOf;
            if (of == null) {
                of = Class.forName("com.mrgregles.bsp_rankup.api.RankUpApi").getMethod("progressOf", net.minecraft.server.MinecraftServer.class, UUID.class);
                progressOf = of;
            }
            Object progress = of.invoke(null, sp.server, sp.getUUID());
            java.lang.reflect.Method c = countsOf;
            if (c == null || !c.getDeclaringClass().isInstance(progress)) {
                c = progress.getClass().getMethod("counts");
                countsOf = c;
            }
            Object counts = c.invoke(progress);
            Object minutes = ((Map<?, ?>) counts).get("playtime");
            return minutes instanceof Number n ? n.longValue() : 0L;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.debug("BSP-RankUp playtime not readable: {}", e.toString());
            return null;
        }
    }
}
