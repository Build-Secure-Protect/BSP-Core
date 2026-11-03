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
 * bright, the waiting ones under it and dimmed. The cards are team invites, alliance invites (teal)
 * and alliance breaks (red, nothing to answer); the accept and decline keys act on the first card
 * that can be answered. Each card counts its own seconds down and leaves by itself.
 */
public final class PromptHud implements IGuiOverlay {
    public static final PromptHud INSTANCE = new PromptHud();
    private static final int CW = 128, CH = 40, GAP = 4, TOP = 28, RIGHT = 6, SHOWN = 3;
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, DIM = 0xFF2A2F3A, TEXT = 0xE6EAF2, MUTED = 0x9AA3B5, TQ = 0xFF19D3B0, RED = 0xFFFF6B5C, TEAL = 0xFF4FD8C8;

    private record Prompt(TeamPromptPacket invite, long untilMs) {}

    private static final List<Prompt> QUEUE = new ArrayList<>();

    private PromptHud() {}

    public static void receive(TeamPromptPacket msg) {
        QUEUE.removeIf(p -> p.invite().id() == msg.id());
        if (!msg.clear()) {
            QUEUE.add(new Prompt(msg, System.currentTimeMillis() + msg.seconds() * 1000L));
        }
    }

    /** The accept or decline key: answers the first card that can be answered. */
    public static void answer(boolean accept) {
        long now = System.currentTimeMillis();
        QUEUE.removeIf(p -> p.untilMs() <= now); // never answer a card that ran out while the HUD was hidden
        for (int i = 0; i < QUEUE.size() && i < SHOWN; i++) { // only a card that is drawn
            TeamPromptPacket m = QUEUE.get(i).invite();
            if (m.answerable()) {
                QUEUE.remove(i);
                boolean ally = m.kind() == TeamPromptPacket.ALLY_INVITE;
                BSPNetwork.CHANNEL.sendToServer(TeamActionPacket.of(ally ? (accept ? TeamActionPacket.Action.ALLY_ACCEPT : TeamActionPacket.Action.ALLY_DECLINE)
                        : accept ? TeamActionPacket.Action.ACCEPT : TeamActionPacket.Action.DECLINE));
                return;
            }
        }
    }

    /** Whether a card waits for the accept or decline key. */
    public static boolean hasPrompt() {
        return QUEUE.stream().limit(SHOWN).anyMatch(p -> p.invite().answerable());
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
        int keys = -1; // the card the accept and decline keys answer
        for (int i = 0; i < QUEUE.size() && i < SHOWN && keys < 0; i++) {
            if (QUEUE.get(i).invite().answerable()) {
                keys = i;
            }
        }
        for (int i = 0; i < QUEUE.size() && i < SHOWN; i++) {
            Prompt p = QUEUE.get(i);
            card(g, font, mc, x, y, p, i == keys, (int) Math.max(1, (p.untilMs() - now + 999) / 1000));
            y += CH + GAP;
        }
    }

    private void card(GuiGraphics g, Font font, Minecraft mc, int x, int y, Prompt p, boolean active, int seconds) {
        TeamPromptPacket m = p.invite();
        int accent = m.kind() == TeamPromptPacket.ALLY_BROKEN ? RED : m.kind() == TeamPromptPacket.ALLY_INVITE ? TEAL : VIO;
        int frame = active || m.kind() == TeamPromptPacket.ALLY_BROKEN ? accent : DIM;
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
        boolean lit = active || m.kind() == TeamPromptPacket.ALLY_BROKEN;
        int title = lit ? accent & 0xFFFFFF : MUTED;
        String titleKey = switch (m.kind()) {
            case TeamPromptPacket.ALLY_INVITE -> "hud.bsp_core.alliance.invite.title";
            case TeamPromptPacket.ALLY_BROKEN -> "hud.bsp_core.alliance.broken.title";
            default -> "hud.bsp_core.team.invite.title";
        };
        small(g, font, Component.translatable(titleKey).getString(), tx, y + 4, title);
        ChatFormatting c = ChatFormatting.getById(m.colour());
        Component line = Component.literal(m.inviterName() + " · ").withStyle(s -> s.withColor(lit ? TEXT : MUTED))
                .append(Component.literal(m.team()).withStyle(c == null ? ChatFormatting.AQUA : c));
        CashMachine.fit(g, font, line.getString(), tx, y + 12, tw, lit ? TEXT : MUTED);
        String about = switch (m.kind()) {
            case TeamPromptPacket.ALLY_INVITE -> Component.translatable("hud.bsp_core.alliance.invite.about", m.leader(), m.members(), m.maxMembers()).getString();
            case TeamPromptPacket.ALLY_BROKEN -> Component.translatable("hud.bsp_core.alliance.broken.about", m.leader()).getString();
            default -> Component.translatable("hud.bsp_core.team.invite.about", m.members(), m.maxMembers(), m.leader()).getString();
        };
        small(g, font, about, tx, y + 23, tw, m.kind() == TeamPromptPacket.ALLY_BROKEN ? TEXT : MUTED);
        if (m.answerable()) {
            String accept = TeamKeys.ACCEPT.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
            String decline = TeamKeys.DECLINE.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
            small(g, font, Component.translatable("hud.bsp_core.team.invite.keys", accept, decline, seconds).getString(), tx, y + 31, tw, active ? TQ & 0xFFFFFF : MUTED);
        } else {
            small(g, font, Component.translatable("hud.bsp_core.alliance.broken.out", m.extra()).getString(), tx, y + 31, tw, RED & 0xFFFFFF);
        }
        // the time left, as a line along the bottom edge
        int full = Math.max(1, m.seconds());
        int w = (int) ((long) CW * Math.min(seconds, full) / full);
        g.fill(x, y + CH - 1, x + w, y + CH, lit ? accent : DIM);
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
