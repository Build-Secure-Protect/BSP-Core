package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.network.CreditNoticePacket;
import com.mrgregles.bsp_core.network.CreditStripPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The store credit stamp (Store Credit task 9, the owner's pick A): when a ticket's clock starts, the screen washes in the
 * ticket's colour, the ticket drops in large with a stamp, and "CREDIT CLOCK STARTED" with what is left to spend stays for 6
 * seconds before fading; when a ticket runs out, "TICKET EXPIRED" and the ticket crumbles away. The player can still move. Drawn
 * over the HUD; hidden with F1 like the rest of it. Notices that arrive while one shows wait their turn.
 */
public final class CreditNoticeHud implements IGuiOverlay {
    public static final CreditNoticeHud INSTANCE = new CreditNoticeHud();
    static final long SHOW_MS = 6500, IN_MS = 200, FADE_MS = 1000, DROP_MS = 350, CRUMBLE_AT = 1600, CRUMBLE_MS = 2400;
    private static final Deque<CreditNoticePacket> WAITING = new ArrayDeque<>();
    private static CreditNoticePacket showing;
    private static long since;

    private CreditNoticeHud() {}

    public static void show(CreditNoticePacket notice) {
        if (WAITING.size() < 8) {
            WAITING.add(notice);
        }
    }

    /** Forgets every notice (the player left the server). */
    public static void clear() {
        WAITING.clear();
        showing = null;
    }

    private static void next() {
        showing = WAITING.poll();
        since = Util.getMillis();
        if (showing != null) {
            Minecraft.getInstance().getSoundManager().play(showing.end()
                    ? SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 0.8F, 1.0F)
                    : SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.6F, 0.6F));
        }
    }

    static float alpha(long t) {
        if (t < 0 || t >= SHOW_MS) {
            return 0;
        }
        return t < IN_MS ? t / (float) IN_MS : t > SHOW_MS - FADE_MS ? (SHOW_MS - t) / (float) FADE_MS : 1;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (showing == null || Util.getMillis() - since >= SHOW_MS) {
            showing = null;
            // a screen (the shop where the purchase was made, the loading screen at login) would cover it: wait until it closes
            if (WAITING.isEmpty() || Minecraft.getInstance().screen != null) {
                return;
            }
            next();
        }
        long t = Util.getMillis() - since;
        float a = alpha(t);
        if (a <= 0) {
            return;
        }
        CreditNoticePacket n = showing;
        DyeColor dye = DyeColor.byName(n.colour(), DyeColor.PURPLE);
        float[] c = dye.getTextureDiffuseColors();
        int rgb = Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // over the hotbar's items and the chat
        g.fill(0, 0, width, height, ((int) (0.55f * 255 * a) << 24) | rgb);
        g.pose().popPose();
        if (a < 0.03f) {
            return; // text drawn almost clear shows as solid in Minecraft's font
        }
        // the ticket: dropped in from above, and on an end crumbling into falling bits
        float k = Math.max(2, Math.min(5, height / 60f));
        int size = Math.round(16 * k), ix = width / 2 - size / 2, iy = Math.round(height * 0.18f);
        if (t < DROP_MS) {
            iy -= Math.round((DROP_MS - t) / (float) DROP_MS * height * 0.4f);
        }
        if (!n.end() || t < CRUMBLE_AT) {
            g.pose().pushPose();
            g.pose().translate(ix, iy, 350);
            g.pose().scale(k, k, 1);
            g.renderItem(ClientCredit.icon(new CreditStripPacket.Summary("", 0, n.name(), n.colour(), n.mark(), 0, 0)), 0, 0);
            g.pose().popPose();
        } else {
            float f = Math.min(1, (t - CRUMBLE_AT) / (float) CRUMBLE_MS);
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            int bit = Math.max(2, Math.round(k));
            for (int i = 0; i < 64; i++) {
                int bx = i % 8, by = i / 8;
                float delay = ((i * 37) % 101) / 101f * 0.6f, fall = Math.max(0, f - delay) * height * 0.6f;
                if (fall > height * 0.55f) {
                    continue;
                }
                int col = (i * 13) % 7 == 0 ? 0xE8C13A : rgb;
                int px = ix + bx * size / 8 + Math.round((float) Math.sin(i * 1.7) * fall * 0.15f), py = iy + size / 4 + by * size / 16 + Math.round(fall);
                g.fill(px, py, px + bit, py + bit, ((int) (255 * a) << 24) | col);
            }
            g.pose().popPose();
        }
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable(n.end() ? "gui.bsp_core.credit.notice.end" : "gui.bsp_core.credit.notice.start");
        long left = n.endsMs() - System.currentTimeMillis();
        Component sub = n.end() ? Component.translatable("gui.bsp_core.credit.notice.end_sub", n.name(), Bank.format(n.pieces()))
                : Component.translatable("gui.bsp_core.credit.notice.start_sub", n.name(), com.mrgregles.bsp_core.credit.StoreCreditItem.span(Math.max(0, left)), Bank.format(n.pieces()));
        int alpha = (int) (255 * a) << 24, shadow = alpha | (rgb >> 2 & 0x3F3F3F);
        float tk = Math.max(1, Math.min(3, (width - 16) / (float) Math.max(1, font.width(title))));
        int ty = iy + size + 12;
        g.pose().pushPose();
        g.pose().translate(width / 2f, ty, 400);
        g.pose().scale(tk, tk, 1);
        g.drawString(font, title, -font.width(title) / 2 + 1, 1, shadow, false);
        g.drawString(font, title, -font.width(title) / 2, 0, alpha | 0xFFFFFF, false);
        g.pose().popPose();
        float sk = Math.max(0.75f, Math.min(1.5f, (width - 16) / (float) Math.max(1, font.width(sub))));
        g.pose().pushPose();
        g.pose().translate(width / 2f, ty + 8 * tk + 10, 400);
        g.pose().scale(sk, sk, 1);
        g.drawString(font, sub, -font.width(sub) / 2 + 1, 1, shadow, false);
        g.drawString(font, sub, -font.width(sub) / 2, 0, alpha | 0xFFFFFF, false);
        g.pose().popPose();
    }
}
