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
    public static boolean pay(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (totalValue(player) < cost) {
            return false;
        }
        Inventory inv = player.getInventory();
        int paid = 0;
        ItemStack last = ItemStack.EMPTY; // the last coin taken: any change is broken from it and keeps its team stamp
        for (CoinTier tier : CoinTier.values()) {
            if (paid >= cost) {
                break;
            }
            int value = tier.value();
            int left = Math.min(count(player, tier), (int) Math.ceil((cost - paid) / (double) value));
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack st = inv.getItem(i);
                if (st.is(tier.coin())) {
                    int n = Math.min(left, st.getCount());
                    last = st.copyWithCount(1);
                    st.shrink(n);
                    if (st.isEmpty()) {
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                    left -= n;
                    paid += n * value;
                }
            }
        }
        handOut(player, paid - cost, CoinStamp.of(last), CoinStamp.teamName(last));
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Coins of {@code tier} in the inventory, armour and offhand slots. */
    private static int count(Player player, CoinTier tier) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(tier.coin())) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Takes exactly {@code count} coins of one tier from the inventory; false (and nothing taken) when there are not enough. */
    public static boolean take(Player player, CoinTier tier, int count) {
        Inventory inv = player.getInventory();
        if (count <= 0) {
            return true;
        }
        if (count(player, tier) < count) {
            return false;
        }
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(tier.coin())) {
                int n = Math.min(left, st.getCount());
                st.shrink(n);
                if (st.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                left -= n;
            }
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /**
     * Gives {@code value} as new coins, largest tiers first, stamped for the team the player is in now (as a factory stamps what
     * it presses). Any remainder smaller than the cheapest coin is lost.
     */
    public static void give(Player player, int value) {
        com.mrgregles.bsp_core.team.Team team = player.getServer() == null ? null : CoinStamp.teamOf(player.getServer(), player.getUUID());
        handOut(player, value, team == null ? CoinStamp.NONE : team.id.toString(), team == null ? "" : team.name);
    }

    /** Gives {@code value} as coins, largest tiers first, each with the stamp {@code stamp} (null: clean). */
    private static void handOut(Player player, int value, @javax.annotation.Nullable String stamp, String name) {
        if (player.getServer() == null) {
            stamp = null;
        }
        CoinTier[] tiers = CoinTier.values();
        for (int i = tiers.length - 1; i >= 0 && value > 0; i--) {
            int v = tiers[i].value();
            int n = value / v;
            value -= n * v;
            while (n > 0) {
                int batch = Math.min(n, ShatterCoinItem.STACK);
                ItemStack stack = new ItemStack(tiers[i].coin(), batch);
                if (stamp != null) {
                    stack.getOrCreateTag().putString(CoinStamp.TAG, stamp);
                    if (!name.isEmpty()) {
                        stack.getOrCreateTag().putString(CoinStamp.NAME_TAG, name);
                    }
                }
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                n -= batch;
            }
        }
    }
}
