package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shatter Coins for other mods (BSP Region Guard request RG-C9): the five tiers and the coins a player carries, counted as one
 * balance in copper-coin units. The same rules BSP-Core uses everywhere: a tier is worth {@code coin.values} copper (each tier
 * double the one before by default), paying spends the smallest coins first and gives change in the largest coins that fit,
 * giving hands out the largest coins first. Coins are items in the player's inventory; nothing here touches the bank.
 */
public final class BspCoins {
    private BspCoins() {}

    /** The tier keys, cheapest first: {@code copper, gold, diamond, netherite, illyrium}. */
    public static List<String> tiers() {
        List<String> out = new ArrayList<>();
        for (CoinTier t : CoinTier.values()) {
            out.add(t.key);
        }
        return out;
    }

    /** One coin of {@code tier} (a key from {@link #tiers()}, any case) in copper units; 0 for an unknown key. */
    public static int value(String tier) {
        String key = tier == null ? "" : tier.trim().toLowerCase(Locale.ROOT);
        for (CoinTier t : CoinTier.values()) {
            if (t.key.equals(key)) {
                return t.value();
            }
        }
        return 0;
    }

    /** The coins the player carries in their inventory, in copper units. */
    public static int worth(Player player) {
        return CoinWallet.totalValue(player);
    }

    /** Takes {@code value} copper units of coins, smallest first, with change; false and nothing taken when the player is short. */
    public static boolean pay(Player player, int value) {
        return CoinWallet.pay(player, value);
    }

    /** Gives {@code value} copper units as coins, largest first (what does not fit is dropped at the player's feet). */
    public static void give(Player player, int value) {
        CoinWallet.give(player, value);
    }
}
