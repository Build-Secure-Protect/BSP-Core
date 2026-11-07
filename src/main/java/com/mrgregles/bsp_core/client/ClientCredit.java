package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.credit.StoreCredit;
import com.mrgregles.bsp_core.network.CreditStripPacket;
import com.mrgregles.bsp_core.network.CreditStripPacket.Summary;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The store credit the open shop or Land Office says the player can spend (Store Credit task 8, pick C): while PAY WITH CREDIT
 * is on and a ticket works there, the screen's status strip becomes the ticket: notched ends, the kind's colour and mark, the
 * credit and the time left. Clicking the strip switches credit off (the plain strip then says so) and on again.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientCredit {
    private ClientCredit() {}

    private static final Map<String, Summary> PLACES = new HashMap<>();

    /** Leaving a server: nothing of its store credit carries over to the next one. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        PLACES.clear();
        wants = true;
        CreditNoticeHud.clear();
    }
    private static boolean wants = true;

    public static void receive(CreditStripPacket msg) {
        PLACES.clear();
        msg.places().forEach(s -> PLACES.put(s.key(), s));
        wants = msg.wants();
    }

    /** The summary for a place, or null when no ticket of the player's works there. */
    @Nullable
    public static Summary get(String key) {
        return PLACES.get(key);
    }

    public static boolean wants() {
        return wants;
    }

    /** The credit that would pay at the place now: 0 when none, or with the switch off. */
    public static long paying(@Nullable Summary s) {
        return s == null || !wants ? 0 : s.total();
    }

    /** The time on the strip: "29 d 4 h", "3 h 10 m", "30 d" before the first purchase, "" with no limit. */
    public static String time(Summary s) {
        long ms = s.endsMs() > 0 ? s.endsMs() - System.currentTimeMillis() : s.durationMs();
        if (s.endsMs() <= 0 && s.durationMs() <= 0) {
            return "";
        }
        long m = Math.max(0, ms) / 60_000L, h = m / 60, d = h / 24;
        return d > 0 ? d + " d" + (h % 24 > 0 ? " " + h % 24 + " h" : "") : h > 0 ? h + " h " + m % 60 + " m" : m + " m";
    }

    /** A ticket item with the summary's look, to draw on the strip. */
    public static ItemStack icon(Summary s) {
        ItemStack stack = StoreCredit.fresh("shown");
        CompoundTag shown = new CompoundTag();
        shown.putString("Colour", s.colour());
        shown.putString("Mark", s.mark());
        stack.getOrCreateTag().put(StoreCredit.SHOWN, shown);
        return stack;
    }

    private static int rgb(String colour) {
        DyeColor d = DyeColor.byName(colour, DyeColor.PURPLE);
        float[] c = d.getTextureDiffuseColors();
        return Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
    }

    private static int shade(int rgb, float f) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        if (f >= 0) {
            r += Math.round((255 - r) * f);
            g += Math.round((255 - g) * f);
            b += Math.round((255 - b) * f);
        } else {
            r = Math.round(r * (1 + f));
            g = Math.round(g * (1 + f));
            b = Math.round(b * (1 + f));
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Draws the strip as the ticket, in the box {@code x, y, w, h}: {@code notch} is the colour round the strip (the bezel), so
     * the notches read as cut out; {@code right} is drawn at the right end (the bank balance).
     */
    public static void drawStrip(GuiGraphics g, Font font, int x, int y, int w, int h, Summary s, String right, int notch) {
        int base = rgb(s.colour());
        g.fill(x, y, x + w, y + h, shade(base, -0.62f));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | base);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, shade(base, 0.35f));
        g.fill(x, y + h / 2 - 3, x + 3, y + h / 2 + 3, notch);
        g.fill(x + w - 3, y + h / 2 - 3, x + w, y + h / 2 + 3, notch);
        g.renderItem(icon(s), x + 5, y + (h - 16) / 2);
        int r = base >> 16 & 255, gg = base >> 8 & 255, b = base & 255;
        int ink = r * 0.3 + gg * 0.59 + b * 0.11 > 160 ? 0x1A1B20 : 0xFFFFFF;
        String time = time(s);
        String label = Component.translatable(time.isEmpty() ? "gui.bsp_core.credit.strip" : "gui.bsp_core.credit.strip_time", s.name(), Bank.format(s.total()), time).getString();
        int rw = font.width(right);
        g.drawString(font, right, x + w - 6 - rw, y + (h - 8) / 2, ink, false);
        CashMachine.fit(g, font, label, x + 24, y + (h - 8) / 2, w - 24 - rw - 14, ink);
    }
}
