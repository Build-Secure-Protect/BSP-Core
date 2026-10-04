package com.mrgregles.bsp_core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The operator-only box on machine screens: a DEMO switch that makes the machine play its working
 * animation with nothing in it, for showing machines off at a spawn hub. Players without operator
 * permission never see it, and the server ignores the button from them.
 */
public final class OperatorDemo {
    public static final int W = 62, H = 30;
    private static final int VIO = 0xFFB58CFF, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A;

    private OperatorDemo() {}

    public static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    public static boolean over(double mouseX, double mouseY, int x, int y) {
        return visible() && mouseX >= x && mouseX < x + W && mouseY >= y + 12 && mouseY < y + H;
    }

    /** Draws the box with its top-left corner at (x, y) in screen coordinates. */
    public static void draw(GuiGraphics g, Font font, int x, int y, boolean on, int mouseX, int mouseY) {
        if (!visible()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable("gui.bsp_core.operator"), 0, 0, VIO & 0xFFFFFF, false);
        g.pose().popPose();
        boolean hover = over(mouseX, mouseY, x, y);
        g.fill(x, y + 12, x + W, y + H, on || hover ? VIO : DIM);
        g.fill(x + 1, y + 13, x + W - 1, y + H - 1, on ? 0xFF2A1F45 : SLOT_BG);
        Component label = Component.translatable(on ? "gui.bsp_core.demo.on" : "gui.bsp_core.demo.off");
        g.drawString(font, label, x + W / 2 - font.width(label) / 2, y + 17, on ? VIO & 0xFFFFFF : 0x9AA3B5, false);
    }
}
