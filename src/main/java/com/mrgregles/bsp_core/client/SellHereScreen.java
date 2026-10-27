package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.shop.SellHereMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/** "Sell here": the 14-slot drop-in grid over the player's inventory in the Cash Machine skin; closing sells. */
public class SellHereScreen extends AbstractContainerScreen<SellHereMenu> implements ScreenFit.Fitted {
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, SLOT_BG = 0xFF0C1A10;
    private static final int SX = 10, SY = 10, SW = 206, SH = 100, KX = 226, KW = 64, KH = 20;

    public SellHereScreen(SellHereMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SellHereMenu.WIDTH;
        this.imageHeight = SellHereMenu.HEIGHT;
        this.inventoryLabelX = SellHereMenu.INV_X;
        this.inventoryLabelY = SellHereMenu.INV_Y - 11;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos + KX, y = topPos + SY + SH - KH;
        if (mx >= x && mx < x + KW && my >= y && my < y + KH) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, BEZEL_DARK);
        g.fill(x, y, x + imageWidth, y + imageHeight, BEZEL);
        g.fill(x, y, x + imageWidth, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            boolean grid = i < SellHereMenu.GRID;
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, grid ? PHOS_DIM | 0xFF000000 : KEY_EDGE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, grid ? SLOT_BG : 0xFFDAD3C3);
        }
        int kx = x + KX, ky = y + SY + SH - KH;
        g.fill(kx, ky, kx + KW, ky + KH + 2, KEY_EDGE);
        g.fill(kx, ky, kx + KW, ky + KH, KEY);
        String label = Component.translatable("gui.bsp_core.shop.sell_now").getString().toUpperCase(Locale.ROOT);
        g.drawString(font, label, kx + KW / 2 - font.width(label) / 2, ky + 6, KEY_TEXT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        CashMachine.fit(g, font, "> " + Component.translatable("gui.bsp_core.shop.sell_here").getString().toUpperCase(Locale.ROOT) + ": " + title.getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, SW - 12, PHOS);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        CashMachine.wrapUp(g, font, Component.translatable("gui.bsp_core.shop.sell_here_hint"), SX + 6, SY + SH - 4, SW - 12, PHOS_DIM);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, KEY_TEXT, false);
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }

    /** Drawn bigger than its own size where the window has room: the client setting shop.screenPercent. */
    @Override
    public float grow() {
        return com.mrgregles.bsp_core.BSPClientConfig.shopScreenScale();
    }
}
