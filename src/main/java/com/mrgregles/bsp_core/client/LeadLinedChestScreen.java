package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.shield.LeadLinedChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Lead-Lined Chest's screen, look B of the mock-ups: a vanilla chest's layout (27 or 54 slots, the inventory and hotbar below) in
 * blue-grey lead with rivets in the corners, and a green SHIELDED tag with the radiation sign on the Inventory line, opposite the label,
 * where it fits at both sizes. Hovering the tag says what it means. Drawn in code, no texture.
 */
public class LeadLinedChestScreen extends AbstractContainerScreen<LeadLinedChestMenu> {
    static final int EDGE = 0xFF15181C, PANEL = 0xFF5D6573, HI = 0xFF8A93A2, LO = 0xFF2E333B, SLOT = 0xFF3A404A, SLOT_LO = 0xFF1F2328, SLOT_HI = 0xFF737C8A,
            RIVET = 0xFF9AA3B0, TITLE = 0xFFE6EAF2, SHADOW = 0xFF262A30, TAG = 0xFF173A26, TAG_EDGE = 0xFF2F7A4C, GREEN = 0xFF58F29A;
    /** The radiation sign, 7 by 7. */
    private static final String[] TREFOIL = {"##...##", "###.###", ".##.##.", "...#...", "..###..", ".#####.", "..###.."};
    private static final Component SHIELDED = Component.translatable("gui.bsp_core.lead_chest.shielded"),
            SHIELDED_TIP = Component.translatable("gui.bsp_core.lead_chest.shielded_tip");

    private final int rows;

    public LeadLinedChestScreen(LeadLinedChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.rows = menu.rows();
        this.imageHeight = LeadLinedChestMenu.imageHeight(rows);
        this.inventoryLabelY = LeadLinedChestMenu.inventoryLabelY(rows);
    }

    /** The tag's width: the sign, a gap, the word, a pixel of margin each side. */
    int tagWidth() {
        return font.width(SHIELDED) + 13;
    }

    int tagX() {
        return imageWidth - 8 - tagWidth();
    }

    int tagY() {
        return inventoryLabelY - 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        int tx = leftPos + tagX(), ty = topPos + tagY();
        if (mouseX >= tx && mouseX < tx + tagWidth() && mouseY >= ty && mouseY < ty + 11 && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, font.split(SHIELDED_TIP, 200), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, HI);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, HI);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, LO);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, LO);
        for (int[] r : new int[][] {{3, 3}, {w - 5, 3}, {3, h - 5}, {w - 5, h - 5}}) {
            g.fill(x + r[0], y + r[1], x + r[0] + 2, y + r[1] + 2, RIVET);
            g.fill(x + r[0] + 1, y + r[1] + 1, x + r[0] + 2, y + r[1] + 2, LO);
        }
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, SLOT);
            g.fill(sx, sy, sx + 17, sy + 1, SLOT_LO);
            g.fill(sx, sy, sx + 1, sy + 17, SLOT_LO);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, SLOT_HI);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, SLOT_HI);
        }
        // the SHIELDED tag on the Inventory line
        int tx = x + tagX(), ty = y + tagY(), tw = tagWidth();
        g.fill(tx, ty, tx + tw, ty + 11, TAG);
        g.fill(tx, ty, tx + tw, ty + 1, TAG_EDGE);
        for (int r = 0; r < TREFOIL.length; r++) {
            for (int c = 0; c < TREFOIL[r].length(); c++) {
                if (TREFOIL[r].charAt(c) == '#') {
                    g.fill(tx + 3 + c, ty + 2 + r, tx + 4 + c, ty + 3 + r, GREEN);
                }
            }
        }
        g.drawString(font, SHIELDED, tx + 12, ty + 2, GREEN, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX + 1, titleLabelY + 1, SHADOW, false);
        g.drawString(font, title, titleLabelX, titleLabelY, TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX + 1, inventoryLabelY + 1, SHADOW, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TITLE, false);
    }
}
