package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Where everything sits on the VIPs board (look B, owner's pick 2026-11-22), in Score Screen canvas pixels (64 a panel, y down):
 * the purple banner with the top message (or the board's title) across the top, the footer with the bottom message and the
 * SUPPORT key, and between them the band where rows of heads slide sideways. The renderer draws from it and a click is tested
 * against its key, so the two always agree.
 *
 * @param bannerBottom the banner's lower edge (it starts at 2)
 * @param topLines     the banner's lines, at {@code topPx}
 * @param dotsY        where the rotation's dots sit, or -1 for none
 * @param footTop      the footer's upper edge, or {@code h - 2} when there is no footer
 * @param bottomLines  the footer's message lines, at {@code bottomPx}
 * @param key          the SUPPORT key's box {x0, y0, x1, y1}, or null when the screen has no link
 * @param keyText      the key's text (the admin's label, or SUPPORT, and an arrow)
 * @param tilesTop     the top of the band of heads
 * @param tilesBottom  the bottom of the band of heads
 * @param rows         rows of heads that fit (0 on a screen too small for any)
 * @param tile         a head's frame, square
 * @param rowH         a row's height: the frame, the name under it and a gap
 */
public record VipBoardLayout(float bannerBottom, List<FormattedCharSequence> topLines, float topPx, float dotsY,
                             float footTop, List<FormattedCharSequence> bottomLines, float bottomPx,
                             float[] key, Component keyText, float keyPx,
                             float tilesTop, float tilesBottom, int rows, float tile, float rowH) {
    private static final Pattern CODES = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    /** Line height as a share of the text's size. */
    public static final float LEADING = 1.15f;

    /** An admin's text with its {@code &} colour codes turned into the font's own formatting codes. */
    public static String colours(String s) {
        return CODES.matcher(s).replaceAll(m -> "§" + m.group(1).toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean hasLink(ScoreScreenBlockEntity.VipTexts texts) {
        return !texts.link().isEmpty() && ScoreScreenBlockEntity.validLink(texts.link());
    }

    /**
     * The layout of a {@code w} by {@code h} canvas at text unit {@code u}, for this screen's texts, the board's {@code title}
     * (shown when there is no top message), and whether the rotation's dots need a place.
     */
    public static VipBoardLayout of(Font font, Style style, ScoreScreenBlockEntity.VipTexts texts, Component title, float w, float h, float u, boolean dots) {
        // the banner: the top message in at most two lines, shrinking to fit; without one, the title on one line
        boolean message = !texts.top().isEmpty();
        Component top = message ? Component.literal(colours(texts.top())).withStyle(style) : title.copy().withStyle(style);
        float topPx = message ? 10 * u : 13 * u;
        Wrapped banner = wrap(font, top, topPx, w - 16 * u, message ? 2 : 1, 5 * u);
        float bannerBottom = 2 + 4 * u + banner.lines.size() * banner.px * LEADING + 3 * u;
        float dotsY = dots ? bannerBottom + 2 * u : -1;
        float contentTop = dots ? dotsY + 6 * u : bannerBottom + 3 * u;

        // the footer: the key at the right (only with a usable link), the bottom message beside it
        float[] key = null;
        Component keyText = Component.empty();
        float keyPx = 9 * u, kw = 0, kh = 14 * u;
        if (hasLink(texts)) {
            Component label = texts.label().isEmpty() ? Component.translatable("screen.bsp_core.vips.support") : Component.literal(colours(texts.label()));
            keyText = Component.empty().append(label).append(" ▸").withStyle(style);
            kw = Math.max(40 * u, Math.min(w * 0.45f, font.width(keyText) * keyPx / 8f + 10 * u));
        }
        Wrapped bottom = texts.bottom().isEmpty() ? new Wrapped(List.of(), 8 * u)
                : wrap(font, Component.literal(colours(texts.bottom())).withStyle(style), 8 * u, kw > 0 ? w - 6 * u - kw - 12 * u : w - 12 * u, 2, 5 * u);
        float footH = 0;
        if (kw > 0 || !bottom.lines.isEmpty()) {
            footH = Math.max(kw > 0 ? kh + 8 * u : 0, bottom.lines.size() * bottom.px * LEADING + 8 * u);
        }
        float footTop = h - 2 - footH;
        if (kw > 0) {
            float x1 = w - 6 * u, y0 = footTop + (footH - kh) / 2;
            key = new float[]{x1 - kw, y0, x1, y0 + kh};
        }

        // the band of heads: as many rows as fit, smaller heads before none
        float tilesTop = contentTop, tilesBottom = footTop - 3 * u, band = tilesBottom - tilesTop;
        float tile = 24 * u, rowH = tile + 12 * u;
        int rows = (int) Math.floor(band / rowH);
        if (rows < 1) {
            tile = 16 * u;
            rowH = tile + 10 * u;
            rows = Math.max(0, (int) Math.floor(band / rowH));
        }
        return new VipBoardLayout(bannerBottom, banner.lines, banner.px, dotsY, footTop, bottom.lines, bottom.px, key, keyText, keyPx,
                tilesTop, tilesBottom, rows, tile, rowH);
    }

    /** The width the bottom message is wrapped to and drawn in: beside the key (6 from its left, 6 from the screen's), or the screen less 6 each side. */
    public static float messageWidth(float w, float[] key, float u) {
        return key != null ? key[0] - 12 * u : w - 12 * u;
    }

    /** Whether canvas point (x, y) is on the SUPPORT key. */
    public boolean onKey(float x, float y) {
        return key != null && x >= key[0] && x <= key[2] && y >= key[1] && y <= key[3];
    }

    private record Wrapped(List<FormattedCharSequence> lines, float px) {}

    /** {@code text} split into at most {@code maxLines} lines {@code maxWidth} wide, shrinking from {@code px} down to {@code minPx}; past that the last lines are dropped. */
    private static Wrapped wrap(Font font, Component text, float px, float maxWidth, int maxLines, float minPx) {
        float size = px;
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, (int) (maxWidth * 8f / size)));
        while (lines.size() > maxLines && size * 0.9f >= minPx) {
            size *= 0.9f;
            lines = font.split(text, Math.max(1, (int) (maxWidth * 8f / size)));
        }
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        return new Wrapped(List.copyOf(lines), size);
    }
}
