package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.TeamChatPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The pill above the chat lines while the player's chat goes to the team: "TEAM CHAT" in the team's colour. Gone when they switch back. */
public final class TeamChatBadge implements IGuiOverlay {
    public static final TeamChatBadge INSTANCE = new TeamChatBadge();
    private static final int BG = 0xF010151C;
    private static boolean on;
    private static int colour = 0x55FFFF;
    private static String team = "";

    private TeamChatBadge() {}

    public static void receive(TeamChatPacket msg) {
        on = msg.on();
        ChatFormatting c = ChatFormatting.getById(msg.colour());
        Integer rgb = c == null ? null : c.getColor();
        colour = rgb == null ? 0x55FFFF : rgb;
        team = msg.team();
    }

    public static boolean isOn() {
        return on;
    }

    /** Logging out or changing servers forgets the state; the server sends it again at login. */
    public static void reset() {
        on = false;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = gui.getFont();
        String label = Component.translatable("hud.bsp_core.team.chat").getString();
        boolean typing = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;
        String text = typing && !team.isEmpty() ? label + " · " + team : label;
        int w = font.width(text) * 3 / 4 + 8;
        int x = 4, y = height - 36;
        g.fill(x - 1, y - 1, x + w + 1, y + 10, 0xFF000000 | colour);
        g.fill(x, y, x + w, y + 9, BG);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
