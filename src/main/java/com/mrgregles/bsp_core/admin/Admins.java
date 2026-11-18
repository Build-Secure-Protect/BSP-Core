package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Who may use BSP-Core's admin tools. Two levels:
 * <ul>
 *   <li><b>Admin</b>: full control (settings, resets, seasons, rewards). The players named in
 *       {@code admin.admins}; if that list is empty, every server operator.</li>
 *   <li><b>Moderator</b>: may look but not change. Admins, plus the players named in
 *       {@code admin.moderators}.</li>
 * </ul>
 */
public final class Admins {
    private Admins() {}

    private static boolean listed(List<? extends String> names, Player player) {
        String name = player.getGameProfile().getName();
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin(Player player) {
        List<? extends String> admins = BSPConfig.getOr(BSPConfig.ADMINS, List.<String>of());
        return admins.isEmpty() ? player.hasPermissions(2) : listed(admins, player);
    }

    public static boolean isModerator(Player player) {
        return isAdmin(player) || listed(BSPConfig.getOr(BSPConfig.MODERATORS, List.<String>of()), player);
    }

    /** The LuckPerms permission that lets a moderator refund bank rows and cancel market entries on the admin panel's Bank &amp; Market page. */
    public static final String BANK_PERMISSION = "bsp_core.admin.bank";

    /** Test hook: when set, answers whether a player holds {@link #BANK_PERMISSION} in place of LuckPerms. */
    public static java.util.function.Predicate<Player> bankPermissionSource;

    /**
     * Who may refund and cancel on the Bank &amp; Market page: an admin, or a moderator holding {@link #BANK_PERMISSION} in LuckPerms.
     * With no LuckPerms (or no answer from it), only admins.
     */
    public static boolean canBank(Player player) {
        if (isAdmin(player)) {
            return true;
        }
        if (!isModerator(player)) {
            return false;
        }
        if (bankPermissionSource != null) {
            return bankPermissionSource.test(player);
        }
        return Boolean.TRUE.equals(com.mrgregles.bsp_core.compat.LuckPermsBridge.hasPermission(player.getUUID(), BANK_PERMISSION));
    }
}
