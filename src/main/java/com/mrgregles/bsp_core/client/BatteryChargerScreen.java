package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.plasma.BatteryChargerMenu;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** The Battery Charger screen: the tank and the item filling on the left, the powers to stamp on the right. */
public class BatteryChargerScreen extends AbstractContainerScreen<BatteryChargerMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final int LIST_X = 120, LIST_Y = 36, ROW = 14, BTN_X = 236, BTN_W = 54;

    public BatteryChargerScreen(BatteryChargerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BatteryChargerMenu.WIDTH;
        this.imageHeight = BatteryChargerMenu.HEIGHT;
        this.inventoryLabelX = BatteryChargerMenu.INV_X;
        this.inventoryLabelY = BatteryChargerMenu.INV_Y - 11;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean canStamp(int i) {
        return menu.mayEdit() && menu.kind() != 0 && (menu.stamped(i) > 0 || (menu.offered(i) > 0 && menu.stampedCount() < menu.maxPowers()));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Buff[] list = BatteryChargerMenu.list(menu.kind());
        for (int i = 0; i < list.length; i++) {
            if (canStamp(i) && over(mx, my, leftPos + BTN_X, topPos + LIST_Y + i * ROW - 2, BTN_W, ROW - 2)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.charger." + key, args);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
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
        int sx = x + BatteryChargerMenu.SLOT_X, sy = y + BatteryChargerMenu.SLOT_Y;
        g.fill(sx - 2, sy - 2, sx + 18, sy + 18, menu.kind() != 0 ? ION : DIM);
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
        // the charger's tank and the item's fill
        g.fill(x + 10, y + 84, x + 110, y + 92, DIM);
        g.fill(x + 10, y + 84, x + 10 + Math.round(100 * Mth.clamp(menu.tankPermille() / 1000f, 0, 1)), y + 92, ION);
        if (menu.fillPermille() >= 0) {
            g.fill(x + 10, y + 112, x + 110, y + 120, DIM);
            g.fill(x + 10, y + 112, x + 10 + Math.round(100 * Mth.clamp(menu.fillPermille() / 1000f, 0, 1)), y + 120, menu.filling() ? ION : 0xFF2F6B8F);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                g.fill(x + BatteryChargerMenu.INV_X + col * 18 - 1, y + BatteryChargerMenu.INV_Y + row * 18 - 1, x + BatteryChargerMenu.INV_X + col * 18 + 17, y + BatteryChargerMenu.INV_Y + row * 18 + 17, SLOT_BG);
            }
        }
        for (int col = 0; col < 9; col++) {
            g.fill(x + BatteryChargerMenu.INV_X + col * 18 - 1, y + BatteryChargerMenu.INV_Y + 57, x + BatteryChargerMenu.INV_X + col * 18 + 17, y + BatteryChargerMenu.INV_Y + 75, SLOT_BG);
        }
        Buff[] list = BatteryChargerMenu.list(menu.kind());
        for (int i = 0; i < list.length; i++) {
            int by = y + LIST_Y + i * ROW - 2;
            boolean on = menu.stamped(i) > 0, can = canStamp(i);
            g.fill(x + BTN_X, by, x + BTN_X + BTN_W, by + ROW - 2, on ? TQ : can ? 0xFF4A5568 : DIM);
            g.fill(x + BTN_X + 1, by + 1, x + BTN_X + BTN_W - 1, by + ROW - 3, can && over(mouseX, mouseY, x + BTN_X, by, BTN_W, ROW - 2) ? 0xFF14362F : SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        boolean signal = menu.signal();
        g.drawString(font, tr(signal ? (menu.filling() ? "filling" : "fed") : "no_signal"), 10, 24, signal ? TQ & 0xFFFFFF : BAD, false);
        small(g, tr("tank", menu.tankPermille() / 10, BSPConfig.getOr(BSPConfig.CHARGER_TANK, 4000)), 10, 74, MUTED);
        small(g, tr("arriving", menu.delivered()), 10, 95, MUTED);
        if (menu.kind() == 0) {
            small(g, tr("empty"), 10, 104, MUTED);
        } else {
            small(g, tr(menu.kind() == 1 ? "battery" : "cell", menu.fillPermille() / 10), 10, 104, TEXT);
            small(g, menu.maxPowers() == 0 ? tr("plasma_only") : tr("stamps", menu.stampedCount(), menu.maxPowers()), 10, 123, MUTED);
        }
        small(g, tr("col.power"), LIST_X, 24, MUTED);
        small(g, tr("col.offered"), LIST_X + 76, 24, MUTED);
        Buff[] list = BatteryChargerMenu.list(menu.kind());
        for (int i = 0; i < list.length; i++) {
            int ry = LIST_Y + i * ROW, off = menu.offered(i), st = menu.stamped(i);
            g.drawString(font, Component.translatable(list[i].translationKey()), LIST_X, ry, off > 0 || st > 0 ? TEXT : 0x6B7385, false);
            g.drawString(font, off > 0 ? Integer.toString(off) : "-", LIST_X + 84, ry, off > 0 ? GOLD & 0xFFFFFF : 0x6B7385, false);
            Component label = tr(st > 0 ? "stamped" : off > 0 ? "stamp" : "not_offered");
            small(g, label, BTN_X + BTN_W / 2 - Math.round(font.width(label) * 0.375f), ry + 1, st > 0 ? TQ & 0xFFFFFF : off > 0 ? MUTED : 0x6B7385);
        }
        if (!menu.mayEdit()) {
            small(g, tr("not_owner"), LIST_X, LIST_Y + list.length * ROW + 2, BAD);
        } else if (menu.kind() == 2) {
            small(g, tr("cell_note"), LIST_X, LIST_Y + list.length * ROW + 2, MUTED);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
}
