package com.mrgregles.bsp_core.looks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The paint bench's tools over the painting's pixels (in {@link LookLayout} order): where a pixel is, its mirror across the
 * totem's middle, a flood fill within one face, and a painting made from a colour or gradient look (at most 16 colours).
 */
public final class PaintTools {
    private PaintTools() {}

    /** Where a pixel is: its part (index in {@link LookLayout#PARTS}), face, column and row, and its face's first pixel and width. */
    public record Where(int part, LookLayout.Face face, int i, int j, int faceStart, int faceW, int faceH) {
    }

    private static final Where[] WHERE = new Where[TotemLook.PAINT_PIXELS];
    private static final int[] MIRROR = new int[TotemLook.PAINT_PIXELS];
    /** The first pixel of each part's faces: [part][face ordinal]. */
    private static final int[][] FACE_START = new int[LookLayout.PARTS.length][LookLayout.Face.values().length];

    static {
        Map<String, Integer> at = new HashMap<>();
        int[] start = {0};
        LookLayout.forEachPixel((n, part, face, i, j) -> {
            int p = Arrays.asList(LookLayout.PARTS).indexOf(part);
            int[] d = LookLayout.dims(part, face);
            if (i == 0 && j == 0) {
                start[0] = n;
                FACE_START[p][face.ordinal()] = n;
            }
            WHERE[n] = new Where(p, face, i, j, start[0], d[0], d[1]);
            float[] c = LookLayout.texelCentre(part, face, i, j);
            at.put(key(c[0], c[1], c[2], face), n);
        });
        for (int n = 0; n < MIRROR.length; n++) {
            Where w = WHERE[n];
            LookLayout.Part part = LookLayout.PARTS[w.part];
            float[] c = LookLayout.texelCentre(part, w.face, w.i, w.j);
            LookLayout.Face mf = w.face == LookLayout.Face.EAST ? LookLayout.Face.WEST : w.face == LookLayout.Face.WEST ? LookLayout.Face.EAST : w.face;
            MIRROR[n] = at.getOrDefault(key(16 - c[0], c[1], c[2], mf), n);
        }
    }

    private static String key(float x, float y, float z, LookLayout.Face f) {
        return Math.round(x * 100) + "," + Math.round(y * 100) + "," + Math.round(z * 100) + "," + f.ordinal();
    }

    public static Where where(int index) {
        return WHERE[index];
    }

    /** The pixel at column i, row j of a part's face. */
    public static int index(int part, LookLayout.Face face, int i, int j) {
        int[] d = LookLayout.dims(LookLayout.PARTS[part], face);
        return FACE_START[part][face.ordinal()] + j * d[0] + i;
    }

    /** The pixel mirrored across the totem's middle (left arm to right arm, the face's left eye to its right). */
    public static int mirror(int index) {
        return MIRROR[index];
    }

    /** Fills, from {@code from}, every pixel of its face joined to it in the same colour, with palette colour {@code to}. */
    public static void fill(byte[] paint, int from, int to) {
        Where w = WHERE[from];
        int old = pixel(paint, from);
        if (old == to) {
            return;
        }
        Deque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[]{w.i, w.j});
        boolean[] seen = new boolean[w.faceW * w.faceH];
        while (!todo.isEmpty()) {
            int[] p = todo.poll();
            if (p[0] < 0 || p[1] < 0 || p[0] >= w.faceW || p[1] >= w.faceH || seen[p[1] * w.faceW + p[0]]) {
                continue;
            }
            seen[p[1] * w.faceW + p[0]] = true;
            int n = w.faceStart + p[1] * w.faceW + p[0];
            if (pixel(paint, n) != old) {
                continue;
            }
            TotemLook.setPixel(paint, n, to);
            todo.add(new int[]{p[0] + 1, p[1]});
            todo.add(new int[]{p[0] - 1, p[1]});
            todo.add(new int[]{p[0], p[1] + 1});
            todo.add(new int[]{p[0], p[1] - 1});
        }
    }

    public static int pixel(byte[] paint, int i) {
        int b = paint[i >> 1] & 0xFF;
        return (i & 1) == 0 ? b >> 4 : b & 0x0F;
    }

    /** A painting: its palette (up to 16 colours) and pixels. */
    public record Painting(int[] palette, byte[] paint) {
    }

    /** Every pixel in one colour. */
    public static Painting blank(int rgb) {
        return new Painting(new int[]{rgb}, new byte[TotemLook.PAINT_BYTES]);
    }

    /**
     * The look as a painting: a painted look as it is; a colour or gradient pixel by pixel (mottling, face and crack included), its
     * colours cut to the 16 most used, every other pixel taking the nearest of them.
     */
    public static Painting from(TotemLook look) {
        if (look.mode() == TotemLook.Mode.PAINT) {
            return new Painting(look.palette(), look.paint());
        }
        int[] colours = new int[TotemLook.PAINT_PIXELS];
        LookLayout.forEachPixel((n, part, face, i, j) -> colours[n] = LookPixels.colour(look, n, part, face, i, j));
        Map<Integer, Integer> uses = new HashMap<>();
        for (int c : colours) {
            uses.merge(c, 1, Integer::sum);
        }
        // the most used colour first, then each time the colour that is most used and least like those already taken, so a rare
        // but distinct colour (the face, the crack) is kept while the many near shades of a fade share a few slots
        List<Map.Entry<Integer, Integer>> byUse = new ArrayList<>(uses.entrySet());
        byUse.sort((a, b) -> b.getValue() - a.getValue());
        int n = Math.min(TotemLook.MAX_PAINT_COLOURS, byUse.size());
        int[] palette = new int[n];
        boolean[] taken = new boolean[byUse.size()];
        palette[0] = byUse.get(0).getKey();
        taken[0] = true;
        for (int k = 1; k < n; k++) {
            int best = -1;
            double bestScore = -1;
            for (int c = 0; c < byUse.size(); c++) {
                if (taken[c]) {
                    continue;
                }
                long d = Long.MAX_VALUE;
                for (int q = 0; q < k; q++) {
                    d = Math.min(d, distance(palette[q], byUse.get(c).getKey()));
                }
                double score = Math.sqrt(byUse.get(c).getValue()) * Math.sqrt(d);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            taken[best] = true;
            palette[k] = byUse.get(best).getKey();
        }
        byte[] paint = new byte[TotemLook.PAINT_BYTES];
        for (int i = 0; i < colours.length; i++) {
            TotemLook.setPixel(paint, i, nearest(palette, colours[i]));
        }
        return new Painting(palette, paint);
    }

    /** How far apart two colours look (weighted squared distance). */
    static long distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return (long) dr * dr * 3 + (long) dg * dg * 4 + (long) db * db * 2;
    }

    public static int nearest(int[] palette, int rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int k = 0; k < palette.length; k++) {
            long d = distance(palette[k], rgb);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }
}
