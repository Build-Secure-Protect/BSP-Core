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
    /** A selected key (the mode that is on): phosphor green with dark text. */
    static final int KEY_ON = 0xFF58F29A, KEY_ON_HOVER = 0xFF7CF7B3, KEY_ON_EDGE = 0xFF2E8B57;
    static final int LINE = 9;

    private CashMachine() {}

    /** Wrapped lines by text and width, so a screen does not split the same text again every frame. */
    private static final int CACHE = 256;
    private static final java.util.Map<Object, List<FormattedCharSequence>> LINES = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Object, List<FormattedCharSequence>> eldest) {
            return size() > CACHE;
        }
    };
    /** A key label laid out once: its lines and the scale they are drawn at. */
    private record KeyLayout(List<FormattedCharSequence> lines, float scale) {}
    private static final java.util.Map<Object, KeyLayout> KEYS = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Object, KeyLayout> eldest) {
            return size() > CACHE;
        }
    };

    /** {@code font.split(text, maxW)}, remembered for the same text and width. */
    static List<FormattedCharSequence> lines(Font font, Component text, int maxW) {
        return LINES.computeIfAbsent(List.of(text, maxW), k -> font.split(text, maxW));
    }

    /** A key with its label upper-cased and fitted: at most {@code h / 9} lines, words never split, shrunk when they would not fit. */
    static void key(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean enabled, boolean hover) {
        key(g, font, x, y, w, h, label, enabled, hover, false);
    }

    static void key(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean enabled, boolean hover, boolean selected) {
        g.fill(x, y, x + w, y + h + 2, selected ? KEY_ON_EDGE : KEY_EDGE);
        g.fill(x, y, x + w, y + h, selected ? (hover ? KEY_ON_HOVER : KEY_ON) : hover ? KEY_HOVER : KEY);
        int maxW = w - 6, maxLines = Math.max(1, Math.round(h / 9f));
        KeyLayout layout = KEYS.computeIfAbsent(List.of(label, w, h), k -> layOut(font, label.toUpperCase(Locale.ROOT), maxW, maxLines));
        List<FormattedCharSequence> lines = layout.lines();
        float scale = layout.scale();
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

    private static KeyLayout layOut(Font font, String text, int maxW, int maxLines) {
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
        return new KeyLayout(lines, scale);
    }

    /** The height a text takes when wrapped to {@code maxW}; 0 for an empty text. */
    static int height(Font font, Component text, int maxW) {
        return text.getString().isEmpty() ? 0 : lines(font, text, maxW).size() * LINE;
    }

    /** Draws a text wrapped to {@code maxW} from the top down; returns the height used. */
    static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int colour) {
        int ty = y;
        for (FormattedCharSequence line : lines(font, text, maxW)) {
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

    /** Three-quarter-size text wrapped to {@code maxW} screen pixels, 7 pixels a line; returns the height used. */
    static int smallWrap(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int colour) {
        int ty = y;
        for (FormattedCharSequence line : lines(font, text, Math.round(maxW / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x, ty, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            ty += 7;
        }
        return ty - y;
    }

    /** {@code text} cut to {@code maxW} with "..." when it is wider. */
    static String cut(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxW - font.width("..."))) + "...";
    }

    /** Clips drawing to a screen-relative rectangle; call {@code g.disableScissor()} after. */
    static void clip(GuiGraphics g, int left, int top, int x, int y, int w, int h) {
        ScreenFit.scissor(g, left + x, top + y, left + x + w, top + y + h);
    }
}
