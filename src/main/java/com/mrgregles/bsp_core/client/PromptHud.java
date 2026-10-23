package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamActionPacket;
import com.mrgregles.bsp_core.network.TeamPromptPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The prompt cards at the top right, where the game's toasts appear: the active one on top and
 * bright, the waiting ones under it and dimmed. Today the cards are team invites; the accept and
 * decline keys act on the top card. Each card counts its own seconds down and leaves by itself.
 */
public final class PromptHud implements IGuiOverlay {
    public static final PromptHud INSTANCE = new PromptHud();
    private static final int CW = 128, CH = 40, GAP = 4, TOP = 28, RIGHT = 6;
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, DIM = 0xFF2A2F3A, TEXT = 0xE6EAF2, MUTED = 0x9AA3B5, TQ = 0xFF19D3B0, RED = 0xFFFF6B5C;

    private record Prompt(TeamPromptPacket invite, long untilMs) {}

    private static final List<Prompt> QUEUE = new ArrayList<>();

    private PromptHud() {}

    public static void receive(TeamPromptPacket msg) {
        QUEUE.removeIf(p -> p.invite().id() == msg.id());
        if (!msg.clear()) {
            QUEUE.add(new Prompt(msg, System.currentTimeMillis() + msg.seconds() * 1000L));
        }
    }

    /** The accept or decline key: answers the top card. */
    public static void answer(boolean accept) {
        if (QUEUE.isEmpty()) {
            return;
        }
        QUEUE.remove(0);
        BSPNetwork.CHANNEL.sendToServer(TeamActionPacket.of(accept ? TeamActionPacket.Action.ACCEPT : TeamActionPacket.Action.DECLINE));
    }

    public static boolean hasPrompt() {
        return !QUEUE.isEmpty();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (QUEUE.isEmpty() || mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        QUEUE.removeIf(p -> p.untilMs() <= now);
        Font font = gui.getFont();
        int x = width - RIGHT - CW, y = TOP;
        for (int i = 0; i < QUEUE.size() && i < 3; i++) {
            Prompt p = QUEUE.get(i);
            card(g, font, mc, x, y, p, i == 0, (int) Math.max(1, (p.untilMs() - now + 999) / 1000));
            y += CH + GAP;
        }
    }

    private void card(GuiGraphics g, Font font, Minecraft mc, int x, int y, Prompt p, boolean active, int seconds) {
        TeamPromptPacket m = p.invite();
        int frame = active ? VIO : DIM;
        g.fill(x - 1, y - 1, x + CW + 1, y + CH + 1, frame);
        g.fill(x, y, x + CW, y + CH, BG);
        // the inviter's face, from the tab list when they are on this server
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(m.inviter());
        if (info != null) {
            PlayerFaceRenderer.draw(g, info.getSkinLocation(), x + 5, y + 5, 12);
        } else {
            g.fill(x + 5, y + 5, x + 17, y + 17, DIM);
        }
        int tx = x + 21, tw = CW - 26;
        int title = active ? VIO & 0xFFFFFF : MUTED;
        small(g, font, Component.translatable("hud.bsp_core.team.invite.title").getString(), tx, y + 4, title);
        ChatFormatting c = ChatFormatting.getById(m.colour());
        Component line = Component.literal(m.inviterName() + " · ").withStyle(s -> s.withColor(active ? TEXT : MUTED))
                .append(Component.literal(m.team()).withStyle(c == null ? ChatFormatting.AQUA : c));
        CashMachine.fit(g, font, line.getString(), tx, y + 12, tw, active ? TEXT : MUTED);
        small(g, font, Component.translatable("hud.bsp_core.team.invite.about", m.members(), m.maxMembers(), m.leader()).getString(), tx, y + 23, tw, MUTED);
        String accept = TeamKeys.ACCEPT.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        String decline = TeamKeys.DECLINE.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        small(g, font, Component.translatable("hud.bsp_core.team.invite.keys", accept, decline, seconds).getString(), tx, y + 31, tw, active ? TQ & 0xFFFFFF : MUTED);
        // the time left, as a line along the bottom edge
        int full = Math.max(1, m.seconds());
        int w = (int) ((long) CW * Math.min(seconds, full) / full);
        g.fill(x, y + CH - 1, x + w, y + CH, active ? VIO : DIM);
    }

    private static void small(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        small(g, font, text, x, y, Integer.MAX_VALUE, colour);
    }

    /** Three-quarter size, shrunk further when wider than {@code maxW}. */
    private static void small(GuiGraphics g, Font font, String text, int x, int y, int maxW, int colour) {
        float scale = Math.min(0.75f, maxW / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
