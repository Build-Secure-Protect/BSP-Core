package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.plasma.PlasmaItems;
import com.mrgregles.bsp_core.plasma.WaveEmitterItem;
import com.mrgregles.bsp_core.plasma.WaveEmitterMenu;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** The Wave Emitter screen: the cell slot and the switch on the left, what the cell holds and how long it lasts on the right. */
public class WaveEmitterScreen extends AbstractContainerScreen<WaveEmitterMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final int BTN_X = 18, BTN_Y = 72, BTN_W = 56, INFO_X = 90;

    public WaveEmitterScreen(WaveEmitterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = WaveEmitterMenu.WIDTH;
        this.imageHeight = WaveEmitterMenu.HEIGHT;
        this.inventoryLabelX = WaveEmitterMenu.INV_X;
        this.inventoryLabelY = WaveEmitterMenu.INV_Y - 11;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private ItemStack cell() {
        return menu.getSlot(0).getItem();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!cell().isEmpty() && over(mx, my, leftPos + BTN_X, topPos + BTN_Y, BTN_W, 14)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WaveEmitterMenu.BTN_TOGGLE);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.emitter." + key, args);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        // the cell slot
        g.fill(x + WaveEmitterMenu.SLOT_X - 2, y + WaveEmitterMenu.SLOT_Y - 2, x + WaveEmitterMenu.SLOT_X + 18, y + WaveEmitterMenu.SLOT_Y + 18, DIM);
        g.fill(x + WaveEmitterMenu.SLOT_X - 1, y + WaveEmitterMenu.SLOT_Y - 1, x + WaveEmitterMenu.SLOT_X + 17, y + WaveEmitterMenu.SLOT_Y + 17, SLOT_BG);
        // the player's inventory slots
        for (var slot : menu.slots) {
            if (slot.index == 0) {
                continue;
            }
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        ItemStack emitter = menu.emitter(), cell = cell();
        boolean on = WaveEmitterItem.isOn(emitter), running = on && PlasmaItems.stored(cell) > 0;
        int draw = BSPConfig.getOr(BSPConfig.EMITTER_DRAW, 20);
        small(g, tr("cell"), WaveEmitterMenu.SLOT_X - 2, WaveEmitterMenu.SLOT_Y - 12, MUTED);
        // the switch
        boolean can = !cell.isEmpty();
        g.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + 14, SLOT_BG);
        frame(g, BTN_X, BTN_Y, BTN_W, 14, !can ? DIM : on ? ION : TQ);
        String label = tr(on ? "on" : "off").getString();
        small(g, Component.literal(label), BTN_X + BTN_W / 2 - Math.round(font.width(label) * 0.375f), BTN_Y + 4, !can ? MUTED : on ? ION & 0xFFFFFF : TEXT);
        int sy = BTN_Y + 18, colW = Math.round((INFO_X - BTN_X - 6) / 0.75f);
        for (var line : font.split(tr(running ? "state_running" : on ? "state_empty" : can ? "state_off" : "state_no_cell", draw), colW)) {
            g.pose().pushPose();
            g.pose().translate(BTN_X, sy, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, running ? ION & 0xFFFFFF : on ? BAD : MUTED, false);
            g.pose().popPose();
            sy += 8;
        }
        // the right column: what the cell holds
        int ix = INFO_X, iy = 24;
        if (cell.isEmpty()) {
            small(g, tr("hint_cell"), ix, iy, MUTED);
            small(g, tr("hint_offhand"), ix, iy + 10, MUTED);
            return;
        }
        int stored = PlasmaItems.stored(cell), cap = Math.max(1, PlasmaItems.capacity(cell));
        g.drawString(font, cell.getHoverName(), ix, iy, TEXT, false);
        small(g, tr("stored", String.format("%,d", stored), String.format("%,d", cap)), ix, iy + 12, ION & 0xFFFFFF);
        g.fill(ix, iy + 22, ix + 120, iy + 28, SLOT_BG);
        g.fill(ix, iy + 22, ix + Math.round(120 * Mth.clamp(stored / (float) cap, 0, 1)), iy + 28, stored > 0 ? ION : DIM);
        int seconds = stored / Math.max(1, draw * 20);
        String left = seconds >= 3600 ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) : String.format("%d:%02d", seconds / 60, seconds % 60);
        small(g, tr(stored > 0 ? "time_left" : "time_none", left, draw), ix, iy + 32, stored > 0 ? TEXT : BAD);
        small(g, tr("powers"), ix, iy + 46, MUTED);
        int[] p = PlasmaItems.powers(cell);
        int row = 0;
        for (Buff b : PlasmaItems.allowed(cell)) {
            if (p[b.ordinal()] > 0) {
                small(g, tr("power", Component.translatable(b.translationKey()), p[b.ordinal()]), ix, iy + 56 + row * 9, GOLD & 0xFFFFFF);
                row++;
            }
        }
        if (row == 0) {
            small(g, tr("no_powers"), ix, iy + 56, MUTED);
        }
        small(g, tr("slots", row, PlasmaItems.maxPowers(cell)), ix + 70, iy + 46, MUTED);
    }
}
