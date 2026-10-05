package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.DecoyStealPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What a player who does not own a Decoy Totem gets on right-clicking it: a panel in the style of
 * a Shatter Totem's, with the owner's name and a Steal button. Pressing Steal is what springs the decoy.
 */
public class DecoyFakeScreen extends Screen {
    private static final int W = 220, H = 118, BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, BAD = 0xFFD63B2F, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2;
    private final BlockPos pos;
    private final String owner;

    private DecoyFakeScreen(BlockPos pos, String owner) {
        super(Component.translatable("block.bsp_core.shatter_totem"));
        this.pos = pos;
        this.owner = owner;
    }

    public static void open(BlockPos pos, String owner) {
        Minecraft.getInstance().setScreen(new DecoyFakeScreen(pos, owner));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean overSteal(double mx, double my) {
        int x = (width - W) / 2 + 50, y = (height - H) / 2 + 82;
        return mx >= x && mx < x + 120 && my >= y && my < y + 20;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overSteal(mx, my)) {
            BSPNetwork.CHANNEL.sendToServer(new DecoyStealPacket(pos));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(java.util.Locale.ROOT), x + 10, y + 9, TQ & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.decoy.fake.owner", owner), x + 10, y + 30, GOLD & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.decoy.fake.tier"), x + 10, y + 44, TEXT, false);
        g.drawString(font, Component.translatable("gui.bsp_core.decoy.fake.hint"), x + 10, y + 60, MUTED, false);
        boolean hover = overSteal(mouseX, mouseY);
        g.fill(x + 50, y + 82, x + 170, y + 102, BAD);
        g.fill(x + 51, y + 83, x + 169, y + 101, hover ? 0xFF3A1512 : SLOT_BG);
        Component steal = Component.translatable("gui.bsp_core.decoy.fake.steal");
        g.drawString(font, steal, x + 110 - font.width(steal) / 2, y + 88, 0xFF6B5C, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
