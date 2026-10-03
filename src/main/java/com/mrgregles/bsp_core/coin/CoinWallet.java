package com.mrgregles.bsp_core.coin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Treats the Shatter Coins in a player's inventory as one balance, in copper-coin units. */
public final class CoinWallet {
    private CoinWallet() {}

    public static int totalValue(Player player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ShatterCoinItem coin) {
                total += coin.tier.value() * stack.getCount();
            }
        }
        return total;
    }

    /**
     * Takes {@code cost} worth of coins, spending the smallest coins first and returning any
     * overpayment as change in the largest coins that fit.
     *
     * @return false (and takes nothing) if the player cannot afford it
     */
    public static boolean pay(ServerPlayer player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (totalValue(player) < cost) {
            return false;
        }
        Inventory inv = player.getInventory();
        int paid = 0;
        for (CoinTier tier : CoinTier.values()) {
            if (paid >= cost) {
                break;
            }
            int have = inv.countItem(tier.coin());
            int value = tier.value();
            int need = (int) Math.ceil((cost - paid) / (double) value);
            int take = Math.min(have, need);
            if (take > 0) {
                inv.clearOrCountMatchingItems(st -> st.is(tier.coin()), take, player.inventoryMenu.getCraftSlots());
                paid += take * value;
            }
        }
        give(player, paid - cost);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Gives {@code value} as coins, largest tiers first. Any remainder smaller than the cheapest coin is lost. */
    public static void give(ServerPlayer player, int value) {
        CoinTier[] tiers = CoinTier.values();
        for (int i = tiers.length - 1; i >= 0 && value > 0; i--) {
            int v = tiers[i].value();
            int n = value / v;
            value -= n * v;
            while (n > 0) {
                int batch = Math.min(n, 64);
                ItemStack stack = new ItemStack(tiers[i].coin(), batch);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                n -= batch;
            }
        }
    }
}
