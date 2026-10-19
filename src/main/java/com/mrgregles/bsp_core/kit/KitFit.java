package com.mrgregles.bsp_core.kit;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Whether a kit fits a player's main inventory, worked out on a copy before anything is given: each
 * stack first tops up stacks of the same item and tag, then takes empty slots. Armour and offhand
 * slots are never used. The answer is how many slots are short (0 when it fits).
 */
public final class KitFit {
    private KitFit() {}

    public static int slotsShort(Inventory inventory, List<ItemStack> kit) {
        return slotsShort(inventory.items, kit);
    }

    /** {@code slots} is the main inventory (36 for a player); it is copied, never changed. */
    public static int slotsShort(NonNullList<ItemStack> slots, List<ItemStack> kit) {
        NonNullList<ItemStack> copy = NonNullList.withSize(slots.size(), ItemStack.EMPTY);
        for (int i = 0; i < slots.size(); i++) {
            copy.set(i, slots.get(i).copy());
        }
        int short_ = 0;
        for (ItemStack given : kit) {
            if (given.isEmpty()) {
                continue;
            }
            ItemStack left = given.copy();
            for (int i = 0; i < copy.size() && !left.isEmpty(); i++) {
                ItemStack have = copy.get(i);
                if (!have.isEmpty() && ItemStack.isSameItemSameTags(have, left) && have.getCount() < have.getMaxStackSize()) {
                    int move = Math.min(left.getCount(), have.getMaxStackSize() - have.getCount());
                    have.grow(move);
                    left.shrink(move);
                }
            }
            for (int i = 0; i < copy.size() && !left.isEmpty(); i++) {
                if (copy.get(i).isEmpty()) {
                    int move = Math.min(left.getCount(), left.getMaxStackSize());
                    ItemStack placed = left.copy();
                    placed.setCount(move);
                    copy.set(i, placed);
                    left.shrink(move);
                }
            }
            if (!left.isEmpty()) {
                short_ += (left.getCount() + left.getMaxStackSize() - 1) / left.getMaxStackSize();
            }
        }
        return short_;
    }

    /** How many main-inventory slots are empty. */
    public static int freeSlots(Inventory inventory) {
        int n = 0;
        for (ItemStack s : inventory.items) {
            if (s.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
