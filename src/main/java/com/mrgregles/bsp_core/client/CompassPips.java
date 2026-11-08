package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

/**
 * The Totem Compass's range levels on its icon in a slot (hotbar, inventory, any container), drawn over the item the way the
 * durability bar is: one turquoise pip per Across level along the bottom edge, from the left, and one gold pip per Up or down
 * level up the right edge, from the bottom. Pips are one pixel with a pixel between them, so six fit along each edge. Not drawn
 * on the compass in the hand.
 */
public final class CompassPips implements IItemDecorator {
    private static final int ACROSS = 0xFF19D3B0, UP_DOWN = 0xFFFFD23A;

    @Override
    public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        int across = Math.min(6, TotemCompassItem.rangeLevel(stack, Range.ACROSS)), upDown = Math.min(6, TotemCompassItem.rangeLevel(stack, Range.DEPTH));
        if (across == 0 && upDown == 0) {
            return false;
        }
        for (int i = 0; i < across; i++) {
            g.fill(RenderType.guiOverlay(), x + 2 + i * 2, y + 15, x + 3 + i * 2, y + 16, ACROSS);
        }
        for (int j = 0; j < upDown; j++) {
            g.fill(RenderType.guiOverlay(), x + 15, y + 13 - j * 2, x + 16, y + 14 - j * 2, UP_DOWN);
        }
        return true; // drawn: Forge resets the render state for the next decorator
    }
}
