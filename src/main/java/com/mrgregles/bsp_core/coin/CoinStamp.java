package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The team stamp on a pressed Shatter Coin: the team its presser (the factory's owner) was in when it was pressed, or
 * {@link #NONE} outside a team. A coin with no stamp (pressed before stamps, or from loot) is clean and pays anywhere.
 *
 * <p>On a totem someone else owns, only clean coins and coins pressed for the owner's team pay (when the owner is in no team,
 * any coins do). On your own totem any coins pay, and those pressed outside your current team go first. Differently stamped
 * coins do not stack, as their tags differ.
 */
public final class CoinStamp {
    /** The team's id, or {@link #NONE}. */
    public static final String TAG = "PressTeam";
    /** The team's name when it was pressed, for the tooltip (a client cannot look other teams up). */
    public static final String NAME_TAG = "PressTeamName";
    public static final String NONE = "none";

    private CoinStamp() {}

    /** The stamp on a coin: a team id, {@link #NONE}, or null for a clean coin. */
    @Nullable
    public static String of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG) ? tag.getString(TAG) : null;
    }

    /** The team name on a coin pressed for a team, "" if none is kept. */
    public static String teamName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(NAME_TAG);
    }

    /** Stamps {@code stack} for {@code team}, or as pressed outside a team when null. Returns the stack. */
    public static ItemStack stamp(ItemStack stack, @Nullable Team team) {
        CompoundTag tag = stack.getOrCreateTag();
        if (team == null) {
            tag.putString(TAG, NONE);
            tag.remove(NAME_TAG);
        } else {
            tag.putString(TAG, team.id.toString());
            tag.putString(NAME_TAG, team.name);
        }
        return stack;
    }

    /** The team {@code presser} is in now, for stamping (null when in none, or when teams are off). */
    @Nullable
    public static Team teamOf(MinecraftServer server, @Nullable UUID presser) {
        return presser == null || !Teams.enabled() ? null : Teams.teamOf(server, presser);
    }

    /**
     * Which coins pay on a totem, and in which order.
     *
     * @param own        the buyer owns the totem (or it has no owner)
     * @param ownerTeam  the owner's team id when the buyer does not own it; null when the owner is in no team (or unknown)
     * @param buyerTeam  the buyer's own team id, for the order on their own totem; null when in none
     */
    public record Purse(boolean own, @Nullable String ownerTeam, @Nullable String buyerTeam) {
        /** Any coin pays, in inventory order: compass upgrades, creative, and anything not a totem. */
        public static final Purse ANY = new Purse(true, null, null);

        public boolean usable(ItemStack coin) {
            if (own || ownerTeam == null) {
                return true;
            }
            String stamp = of(coin);
            return stamp == null || stamp.equals(ownerTeam);
        }

        /** Lower goes first: on your own totem coins pressed outside your team, then your team's, then clean ones; elsewhere the team's, then clean. */
        int rank(ItemStack coin) {
            String stamp = of(coin);
            if (stamp == null) {
                return 2;
            }
            return own && !stamp.equals(buyerTeam) ? 0 : 1;
        }
    }

    /** The purse for {@code buyer} paying on a totem owned by {@code owner} (null: unowned). Server side. */
    public static Purse forTotem(MinecraftServer server, UUID buyer, @Nullable UUID owner) {
        Team mine = teamOf(server, buyer);
        String buyerTeam = mine == null ? null : mine.id.toString();
        if (owner == null || owner.equals(buyer)) {
            return new Purse(true, null, buyerTeam);
        }
        Team theirs = teamOf(server, owner);
        return new Purse(false, theirs == null ? null : theirs.id.toString(), buyerTeam);
    }

    /** Coins of {@code coin} in the player's inventory that pay under {@code purse}. */
    public static int count(Player player, Item coin, Purse purse) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(coin) && purse.usable(st)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Coins of {@code coin} in the player's inventory that do not pay under {@code purse}. */
    public static int unusable(Player player, Item coin, Purse purse) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(coin) && !purse.usable(st)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Takes {@code amount} coins of {@code coin} that pay under {@code purse}, in its order. Returns how many were taken. */
    public static int take(Player player, Item coin, int amount, Purse purse) {
        Inventory inv = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(coin) && purse.usable(st)) {
                slots.add(i);
            }
        }
        slots.sort(Comparator.comparingInt(i -> purse.rank(inv.getItem(i)))); // stable: inventory order within a rank
        int left = amount;
        for (int slot : slots) {
            if (left <= 0) {
                break;
            }
            ItemStack st = inv.getItem(slot);
            int n = Math.min(left, st.getCount());
            st.shrink(n);
            if (st.isEmpty()) {
                inv.setItem(slot, ItemStack.EMPTY);
            }
            left -= n;
        }
        inv.setChanged();
        return amount - left;
    }
}
