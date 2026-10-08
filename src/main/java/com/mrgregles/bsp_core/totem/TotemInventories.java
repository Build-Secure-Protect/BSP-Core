package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Inventory helpers for Shatter Totems. */
public final class TotemInventories {
    private TotemInventories() {}

    /** A real totem: the item, and not a spent husk left behind when its totem was reissued. */
    public static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.SHATTER_TOTEM.get()) && !TotemIdentity.isSpent(stack);
    }

    /** Removes every totem from the player's own inventory (main, offhand, armour) and returns them. */
    public static List<ItemStack> removeAll(Player player) {
        List<ItemStack> removed = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isTotem(stack)) {
                removed.add(stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (isTotem(carried)) {
            removed.add(carried.copy());
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        return removed;
    }

    /**
     * Puts a totem back into the player's inventory. Prefers the selected hotbar slot, then any free
     * slot. If the inventory is full, a non-totem item is displaced and dropped instead, so this never
     * drops a totem (which could be refused and loop).
     */
    public static void giveBack(Player player, ItemStack totem) {
        Inventory inv = player.getInventory();
        if (inv.getItem(inv.selected).isEmpty()) {
            inv.setItem(inv.selected, totem);
            return;
        }
        if (inv.add(totem)) {
            return;
        }
        int slot = isTotem(inv.getItem(inv.selected)) ? firstNonTotemSlot(inv) : inv.selected;
        if (slot < 0) {
            player.containerMenu.setCarried(totem); // every slot holds a totem; keep it on the cursor
            return;
        }
        ItemStack displaced = inv.getItem(slot);
        inv.setItem(slot, totem);
        player.drop(displaced, false, false);
    }

    private static int firstNonTotemSlot(Inventory inv) {
        for (int i = 0; i < inv.items.size(); i++) {
            if (!isTotem(inv.items.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
