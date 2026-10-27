package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.OpenRemoteShopPacket;
import com.mrgregles.bsp_core.shop.ShopListMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** The Shopping Network list in the Cash Machine skin: one line per remote shop; click to open it. */
public class ShopListScreen extends AbstractContainerScreen<ShopListMenu> implements ScreenFit.Fitted {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A;
    private static final int SX = 10, SY = 10, SW = 206, SH = 176, KX = 226, KW = 64, KH = 20, KGAP = 4, PER = 13;
    private int page;

    public ShopListScreen(ShopListMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private int pages() {
        return Math.max(1, (menu.shops.size() + PER - 1) / PER);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        if (over(mx, my, x + KX, y + SY, KW, KH) && page > 0) {
            page--;
            return true;
        }
        if (over(mx, my, x + KX, y + SY + KH + KGAP, KW, KH) && page < pages() - 1) {
            page++;
            return true;
        }
        if (over(mx, my, x + KX, y + SY + SH - KH, KW, KH)) {
            onClose();
            return true;
        }
        for (int i = page * PER; i < Math.min(menu.shops.size(), (page + 1) * PER); i++) {
            if (over(mx, my, x + SX + 6, y + SY + 22 + (i - page * PER) * 11, SW - 12, 10)) {
                BSPNetwork.CHANNEL.sendToServer(new OpenRemoteShopPacket(menu.shops.get(i).id()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        key(g, x + KX, y + SY, "prev", page > 0, mx, my);
        key(g, x + KX, y + SY + KH + KGAP, "next", page < pages() - 1, mx, my);
        key(g, x + KX, y + SY + SH - KH, "close", true, mx, my);
    }

    private void key(GuiGraphics g, int x, int y, String label, boolean enabled, int mx, int my) {
        boolean hover = enabled && over(mx, my, x, y, KW, KH);
        g.fill(x, y, x + KW, y + KH + 2, KEY_EDGE);
        g.fill(x, y, x + KW, y + KH, hover ? 0xFFF5F0E4 : KEY);
        CashMachine.fit(g, font, Component.translatable("gui.bsp_core.network." + label).getString().toUpperCase(Locale.ROOT), x + 3, y + 6, KW - 6, enabled ? KEY_TEXT : KEY_LOCKED);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "> " + Component.translatable("gui.bsp_core.network.title").getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, PHOS, false);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        if (menu.shops.isEmpty()) {
            g.drawString(font, Component.translatable("gui.bsp_core.network.empty"), SX + 8, SY + 24, PHOS_DIM, false);
        }
        for (int i = page * PER; i < Math.min(menu.shops.size(), (page + 1) * PER); i++) {
            var e = menu.shops.get(i);
            int ry = SY + 22 + (i - page * PER) * 11;
            boolean hover = over(mx, my, leftPos + SX + 6, topPos + ry, SW - 12, 10);
            String right = (e.open() ? "" : Component.translatable("gui.bsp_core.network.closed").getString() + "  ") + e.kind().name().toLowerCase(Locale.ROOT) + "  " + e.items();
            g.drawString(font, (hover ? "> " : "  ") + font.plainSubstrByWidth(e.name(), SW - 24 - font.width(right)), SX + 6, ry, hover ? PHOS : PHOS_DIM, false);
            g.drawString(font, right, SX + SW - 6 - font.width(right), ry, e.open() ? PHOS_DIM : 0xFF6B5C, false);
        }
        String pg = Component.translatable("gui.bsp_core.network.page", page + 1, pages()).getString();
        g.drawString(font, pg, SX + SW - 6 - font.width(pg), SY + SH - 12, PHOS_DIM, false);
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
