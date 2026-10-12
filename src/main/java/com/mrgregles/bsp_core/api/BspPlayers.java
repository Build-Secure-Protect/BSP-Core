package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.coin.CoinWallet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Who a player is to BSP-Core (admin, moderator), their Shatter Coins in hand, and where totems may go. */
public final class BspPlayers {
    private BspPlayers() {}

    public static boolean isAdmin(Player player) {
        return Admins.isAdmin(player);
    }

    public static boolean isModerator(Player player) {
        return Admins.isModerator(player);
    }

    /** The value, in Copper coins, of every Shatter Coin in the player's inventory. */
    public static int coinValue(Player player) {
        return CoinWallet.totalValue(player);
    }

    /** Takes coins worth {@code value} Copper from the inventory, giving change in smaller coins; false and nothing taken when they cannot afford it. */
    public static boolean payCoins(Player player, int value) {
        return CoinWallet.pay(player, value);
    }

    /** Gives coins worth {@code value} Copper, largest tiers first. */
    public static void giveCoins(Player player, int value) {
        CoinWallet.give(player, value);
    }

    /** Whether totems may be placed or dropped in this dimension (the Overworld, Nether and End by default). */
    public static boolean isDimensionAllowed(ResourceLocation dimension) {
        return BSPConfig.isDimensionAllowed(dimension);
    }
}
