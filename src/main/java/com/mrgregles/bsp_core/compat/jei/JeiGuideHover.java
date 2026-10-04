package com.mrgregles.bsp_core.compat.jei;

import com.mrgregles.bsp_core.client.AssemblyGuide;
import com.mrgregles.bsp_core.client.AssemblyGuideScreen;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import javax.annotation.Nullable;

/**
 * "Hold Shift to see how it is built": while the mouse is over a multiblock block in JEI (item
 * list, bookmarks or a recipe page), its tooltip gains a hint, and holding Shift for a moment opens
 * the Assembly Guide for that machine. Only JEI's own item displays count, so shift-clicking stacks
 * around an inventory never opens it by accident.
 */
public final class JeiGuideHover {
    private static final long HOLD_MS = 600, GAP_MS = 250;

    @Nullable
    private static IJeiRuntime runtime;
    private static boolean listening;
    @Nullable
    private static Item held;
    private static long heldSince, lastSeen;

    private JeiGuideHover() {}

    static void attach(IJeiRuntime jei) {
        runtime = jei;
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(JeiGuideHover::onTooltip);
        }
    }

    private static boolean underJeiMouse(Item item) {
        if (runtime == null) {
            return false;
        }
        ItemStack list = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        ItemStack marks = runtime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        ItemStack recipe = runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(null);
        return (list != null && list.is(item)) || (marks != null && marks.is(item)) || (recipe != null && recipe.is(item));
    }

    private static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AssemblyGuideScreen || AssemblyGuide.ofItem(item) == null || !underJeiMouse(item)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!Screen.hasShiftDown()) {
            held = null;
            event.getToolTip().add(Component.translatable("tooltip.bsp_core.guide_hint").withStyle(ChatFormatting.AQUA));
            return;
        }
        if (held != item || now - lastSeen > GAP_MS) {
            held = item;
            heldSince = now;
        }
        lastSeen = now;
        int filled = (int) Math.min(10, (now - heldSince) * 10 / HOLD_MS);
        event.getToolTip().add(Component.translatable("tooltip.bsp_core.guide_opening", "|".repeat(filled) + ".".repeat(10 - filled)).withStyle(ChatFormatting.AQUA));
        if (now - heldSince >= HOLD_MS) {
            held = null;
            Screen current = mc.screen;
            // go back to where the player was, unless that is a container whose server side closes when it is hidden
            Screen parent = current instanceof AbstractContainerScreen<?> && !(current instanceof InventoryScreen) ? null : current;
            mc.tell(() -> {
                AssemblyGuide guide = AssemblyGuide.ofItem(item);
                if (guide != null) {
                    mc.setScreen(new AssemblyGuideScreen(guide, parent));
                }
            });
        }
    }
}
