package com.mrgregles.bsp_core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The lottery's admin page by itself ({@code /bsp lottery admin}): the Trade Post's bezel and screen with the title and the
 * close mark where the tabs would be, and {@link LotteryAdminPage} below them.
 */
public class LotteryAdminScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 300, H = 216, BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008,
            SCREEN_EDGE = 0xFF0D3B22, PHOS = 0xFF58F29A, DIM = 0xFF3FB97A;
    private int left, top;
    private LotteryAdminPage page;

    public LotteryAdminScreen() {
        super(Component.translatable("gui.bsp_core.lottery.admin.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (page == null) {
            page = new LotteryAdminPage(font, 14, 31);
            page.opened();
        }
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.player != null && !minecraft.player.hasPermissions(2)) {
            onClose(); // no longer an operator: the server would refuse every key
            return;
        }
        page.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, BEZEL_DARK);
        g.fill(left, top, left + W, top + H, BEZEL);
        g.fill(left, top, left + W, top + 2, BEZEL_LIGHT);
        g.fill(left + 9, top + 9, left + 291, top + 207, SCREEN_EDGE);
        g.fill(left + 10, top + 10, left + 290, top + 206, SCREEN);
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.drawString(font, title, 16, 15, PHOS, false);
        boolean hoverX = mx - left >= 276 && mx - left < 287 && my - top >= 12 && my - top < 23;
        int edge = hoverX ? PHOS : DIM;
        g.fill(276, 12, 287, 13, edge);
        g.fill(276, 22, 287, 23, edge);
        g.fill(276, 12, 277, 23, edge);
        g.fill(286, 12, 287, 23, edge);
        g.drawString(font, "x", 279, 13, PHOS, false);
        g.fill(14, 28, 286, 29, SCREEN_EDGE);
        page.draw(g, mx - left, my - top);
        g.pose().popPose();
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - left, y = my - top;
        if (x >= 276 && x < 287 && y >= 12 && y < 23) {
            onClose();
            return true;
        }
        return page.click(x, y) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return page.scroll(mx - left, my - top, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return page.keyPressed(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return page.charTyped(c) || super.charTyped(c, mods);
    }
}
