package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.network.StealStatusPacket;
import net.minecraft.util.Mth;
import com.mrgregles.bsp_core.totem.StealState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Top-centre countdown shown to the thief and the owner while a steal is in progress. */
public final class StealHudOverlay implements IGuiOverlay {
    public static final StealHudOverlay INSTANCE = new StealHudOverlay();
    private static final int BAR_W = 182, BAR_H = 5;
    private static final int GOLD = 0xE3B341, RED = 0xD63B2F, BAR_BG = 0xA0101018;

    private StealHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Font font = gui.getFont();
        int y = 6;
        for (ClientStealState.Entry entry : ClientStealState.entries()) {
            StealStatusPacket p = entry.packet();
            Component line1;
            Component line2 = null;
            boolean thief = p.role() == StealStatusPacket.ROLE_THIEF;
            switch (p.outcome()) {
                case StealStatusPacket.OUTCOME_SUCCESS -> line1 = thief
                        ? Component.translatable("hud.bsp_core.steal.success", p.ownerName()).withStyle(ChatFormatting.GOLD)
                        : Component.translatable("hud.bsp_core.steal.lost").withStyle(ChatFormatting.RED);
                case StealStatusPacket.OUTCOME_FAILED -> line1 = thief
                        ? Component.translatable("hud.bsp_core.steal.failed").withStyle(ChatFormatting.RED)
                        : Component.translatable("hud.bsp_core.steal.defended").withStyle(ChatFormatting.GREEN);
                default -> {
                    StealState s = p.state();
                    if (s == null) {
                        continue;
                    }
                    String time = clock(s.ticksLeft());
                    boolean urgent = s.ticksLeft() <= BSPConfig.STEAL_WARNING_SECONDS.get() * 20;
                    line1 = thief
                            ? Component.translatable("hud.bsp_core.steal.thief", p.ownerName(), time).withStyle(urgent ? ChatFormatting.RED : ChatFormatting.GOLD)
                            : Component.translatable("hud.bsp_core.steal.owner", s.thiefName(), time).withStyle(ChatFormatting.RED);
                    if (s.outside()) {
                        line2 = thief
                                ? Component.translatable("hud.bsp_core.steal.return", (s.graceLeft() + 19) / 20).withStyle(ChatFormatting.YELLOW)
                                : Component.translatable("hud.bsp_core.steal.thief_outside", (s.graceLeft() + 19) / 20).withStyle(ChatFormatting.YELLOW);
                    }
                }
            }
            StealState active = p.outcome() == StealStatusPacket.OUTCOME_ACTIVE ? p.state() : null;
            int textColor = 0xFFFFFF;
            if (active != null && active.ticksLeft() <= BSPConfig.STEAL_WARNING_SECONDS.get() * 20) {
                textColor = pulseColor(partialTick);
            }
            g.drawCenteredString(font, line1, width / 2, y, textColor);
            y += 10;
            if (active != null) {
                drawBar(g, width / 2 - BAR_W / 2, y, active, thief, partialTick);
                y += BAR_H + 3;
            }
            if (line2 != null) {
                g.drawCenteredString(font, line2, width / 2, y, 0xFFFFFF);
                y += 10;
            }
            y += 2;
        }
    }

    /** Time bar: full at the start of the steal, shrinking to nothing; pulses red when nearly done. */
    public static void drawBar(GuiGraphics g, int x, int y, StealState s, boolean thiefSide, float partialTick) {
        boolean urgent = s.ticksLeft() <= BSPConfig.STEAL_WARNING_SECONDS.get() * 20;
        int fill = Math.round(BAR_W * s.fractionLeft());
        int color = urgent ? pulseColor(partialTick) : (thiefSide ? GOLD : RED);
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, BAR_BG);
        g.fill(x, y, x + fill, y + BAR_H, 0xFF000000 | color);
    }

    /** Red that breathes between dim and bright about twice a second. */
    static int pulseColor(float partialTick) {
        float t = (System.currentTimeMillis() % 1000L) / 1000f;
        float k = 0.55f + 0.45f * Mth.sin(t * (float) Math.PI * 2f);
        int r = 0xD6, gc = Math.round(0x3B * k), b = Math.round(0x2F * k);
        return (r << 16) | (gc << 8) | b;
    }

    static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
