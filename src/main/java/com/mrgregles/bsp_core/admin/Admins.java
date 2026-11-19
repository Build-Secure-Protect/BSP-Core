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

    /** Test hook: when set, answers who is an admin in place of the config list and operator levels (GameTests give mock players no op level). */
    public static java.util.function.Predicate<Player> adminSource;

    public static boolean isAdmin(Player player) {
        if (adminSource != null) {
            return adminSource.test(player);
        }
        List<? extends String> admins = BSPConfig.getOr(BSPConfig.ADMINS, List.<String>of());
        return admins.isEmpty() ? player.hasPermissions(2) : listed(admins, player);
    }

    public static boolean isModerator(Player player) {
        return isAdmin(player) || listed(BSPConfig.getOr(BSPConfig.MODERATORS, List.<String>of()), player);
    }

    /** The LuckPerms permission that lets a moderator add, remove and extend bounties and blank their notes on the admin panel's BOUNTIES tab. */
    public static final String BOUNTY_PERMISSION = "bsp_core.admin.bounty";

    /** Test hook: when set, answers whether a player holds {@link #BOUNTY_PERMISSION} in place of LuckPerms. */
    public static java.util.function.Predicate<Player> bountyPermissionSource;

    /** Who may change bounties: an admin, or a moderator holding {@link #BOUNTY_PERMISSION} in LuckPerms. Without LuckPerms, only admins. */
    public static boolean canBounty(Player player) {
        if (isAdmin(player)) {
            return true;
        }
        if (!isModerator(player)) {
            return false;
        }
        if (bountyPermissionSource != null) {
            return bountyPermissionSource.test(player);
        }
        return Boolean.TRUE.equals(com.mrgregles.bsp_core.compat.LuckPermsBridge.hasPermission(player.getUUID(), BOUNTY_PERMISSION));
    }

    /**
     * Whether a player, online or not, is staff (an admin or a moderator): online, as {@link #isModerator}; offline, named in
     * {@code admin.admins} or {@code admin.moderators}, or (when {@code admin.admins} is empty) an operator of level 2 or more in
     * this server's ops list.
     */
    public static boolean isStaff(net.minecraft.server.MinecraftServer server, java.util.UUID id, String name) {
        Player online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return isModerator(online);
        }
        List<? extends String> admins = BSPConfig.getOr(BSPConfig.ADMINS, List.<String>of());
        if (named(admins, name) || named(BSPConfig.getOr(BSPConfig.MODERATORS, List.<String>of()), name)) {
            return true;
        }
        if (!admins.isEmpty()) {
            return false;
        }
        net.minecraft.server.players.ServerOpListEntry op = server.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(id, name));
        return op != null && op.getLevel() >= 2;
    }

    private static boolean named(List<? extends String> names, String name) {
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
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
