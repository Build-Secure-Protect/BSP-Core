package com.mrgregles.bsp_core.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * Text helpers shared by the Cash Machine screens, so nothing is drawn past the edge of a key or a
 * phosphor screen: key labels shrink to fit their key, long lines wrap to a width, and single lines
 * that must stay on one row shrink to fit.
 */
final class CashMachine {
    static final int KEY = 0xFFEAE4D5, KEY_HOVER = 0xFFF5F0E4, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A;
    static final int LINE = 9;

    private CashMachine() {}

    /** A key with its label upper-cased and fitted: at most {@code h / 9} lines, words never split, shrunk when they would not fit. */
    static void key(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean enabled, boolean hover) {
        g.fill(x, y, x + w, y + h + 2, KEY_EDGE);
        g.fill(x, y, x + w, y + h, hover ? KEY_HOVER : KEY);
        String text = label.toUpperCase(Locale.ROOT);
        int maxW = w - 6, maxLines = Math.max(1, Math.round(h / 9f));
        float scale = 1f;
        int longest = 0;
        for (String word : text.split(" ")) {
            longest = Math.max(longest, font.width(word));
        }
        if (longest > maxW) {
            scale = (float) maxW / longest;
        }
        List<FormattedCharSequence> lines;
        while (true) {
            lines = font.split(Component.literal(text), (int) (maxW / scale));
            if (lines.size() <= maxLines || scale <= 0.5f) {
                break;
            }
            scale -= 0.05f;
        }
        int lh = Math.round(LINE * scale);
        int ty = y + (h - lines.size() * lh) / 2 + 1;
        int colour = enabled ? KEY_TEXT : KEY_LOCKED;
        for (FormattedCharSequence line : lines) {
            float lw = font.width(line) * scale;
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - lw / 2f, ty, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            ty += lh;
        }
    }

    /** The height a text takes when wrapped to {@code maxW}; 0 for an empty text. */
    static int height(Font font, Component text, int maxW) {
        return text.getString().isEmpty() ? 0 : font.split(text, maxW).size() * LINE;
    }

    /** Draws a text wrapped to {@code maxW} from the top down; returns the height used. */
    static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int colour) {
        int ty = y;
        for (FormattedCharSequence line : font.split(text, maxW)) {
            g.drawString(font, line, x, ty, colour, false);
            ty += LINE;
        }
        return ty - y;
    }

    /** Draws a text wrapped to {@code maxW} so that it ends at {@code bottom}; returns the y its first line starts at (the new bottom for what goes above). */
    static int wrapUp(GuiGraphics g, Font font, Component text, int x, int bottom, int maxW, int colour) {
        int h = height(font, text, maxW);
        if (h > 0) {
            wrap(g, font, text, x, bottom - h, maxW, colour);
        }
        return bottom - h;
    }

    /** One line that must stay a line: shrunk to {@code maxW} when wider. */
    static void fit(GuiGraphics g, Font font, String text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) {
            g.drawString(font, text, x, y, colour, false);
            return;
        }
        float scale = (float) maxW / w;
        g.pose().pushPose();
        g.pose().translate(x, y + (LINE - LINE * scale) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** {@link #fit} with the text's right edge at {@code right}. */
    static void fitRight(GuiGraphics g, Font font, String text, int right, int y, int maxW, int colour) {
        int w = Math.min(font.width(text), maxW);
        fit(g, font, text, right - w, y, maxW, colour);
    }

    /** Clips drawing to a screen-relative rectangle; call {@code g.disableScissor()} after. */
    static void clip(GuiGraphics g, int left, int top, int x, int y, int w, int h) {
        g.enableScissor(left + x, top + y, left + x + w, top + y + h);
    }
}
