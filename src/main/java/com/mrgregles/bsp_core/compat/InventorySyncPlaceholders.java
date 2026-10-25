package com.mrgregles.bsp_core.compat;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * BSP-InventorySync's placeholders (its request R6): an item whose mod a server lacks travels as a placeholder (a named barrier,
 * paper before its D14) holding the original item under the tag {@code bsp_inventorysync:original}, and turns back into it on a
 * server with the mod. Read by tag name: nothing to compile against. Anywhere BSP-Core takes an item for good, or copies one to
 * hand out again, a placeholder is refused: sold, the original would be lost; copied into a shop, a kit or a prize, the original
 * inside would be handed out again and again.
 */
public final class InventorySyncPlaceholders {
    private InventorySyncPlaceholders() {}

    public static final String TAG = "bsp_inventorysync:original";

    /** Whether {@code stack} is a BSP-InventorySync placeholder. */
    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(TAG, Tag.TAG_COMPOUND);
    }
}
