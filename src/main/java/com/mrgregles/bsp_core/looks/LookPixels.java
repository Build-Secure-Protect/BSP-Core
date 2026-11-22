package com.mrgregles.bsp_core.looks;

/**
 * The colour of every pixel of a look, laid out by {@link LookLayout} as a 64 by 64 texture (0xAARRGGBB, clear where no face is).
 * A colour or gradient is drawn the way the baked totem is: mottled with a darker and a lighter shade, the creeper face on the
 * head and the crack on the chest. A single colour fades to the plinth's stone toward the knees, as the gold totem does.
 */
public final class LookPixels {
    private LookPixels() {}

    private static final int PLINTH = 0x2B2B33, FACE = 0x101418, CRACK = 0x0B0F14;
    /** The creeper face's dark pixels on the head's front, (i, j) as the baked texture has them. */
    private static final int[][] FACE_PX = {{1, 2}, {2, 2}, {1, 3}, {2, 3}, {5, 2}, {6, 2}, {5, 3}, {6, 3}, {3, 4}, {4, 4}, {2, 5}, {3, 5}, {4, 5}, {5, 5}, {2, 6}, {5, 6}};
    /** The chest crack on the torso's front, from its top. */
    private static final int[][] CRACK_PX = {{4, 0}, {4, 1}, {3, 2}, {3, 3}, {4, 4}, {4, 5}, {5, 6}, {4, 7}, {3, 8}, {3, 9}};

    /** The look's texture, row by row (index y * 64 + x), 0xAARRGGBB. */
    public static int[] texture(TotemLook look) {
        int[] out = new int[LookLayout.SIZE * LookLayout.SIZE];
        LookLayout.forEachPixel((n, part, face, i, j) -> {
            int[] at = LookLayout.at(part, face);
            out[(at[1] + j) * LookLayout.SIZE + at[0] + i] = 0xFF000000 | colour(look, n, part, face, i, j);
        });
        return out;
    }

    /** One pixel's colour, 0xRRGGBB. */
    public static int colour(TotemLook look, int index, LookLayout.Part part, LookLayout.Face face, int i, int j) {
        if (look.mode() == TotemLook.Mode.PAINT) {
            return look.palette()[look.pixel(index)];
        }
        float[] c = LookLayout.texelCentre(part, face, i, j);
        if (part.name().equals("head") && face == LookLayout.Face.NORTH && in(FACE_PX, i, j)) {
            return FACE;
        }
        int[] cols = look.colours();
        int base;
        if (look.mode() == TotemLook.Mode.GRADIENT) {
            float t = look.run() == TotemLook.Run.UP ? clamp((24 - c[1]) / 21f) : clamp((c[0] - 1) / 14f);
            base = cols.length == 2 ? mix(cols[0], cols[1], t) : t < 0.5f ? mix(cols[0], cols[1], t * 2) : mix(cols[1], cols[2], (t - 0.5f) * 2);
        } else {
            base = cols[0];
        }
        float r = noise((int) Math.floor(c[0]), (int) Math.floor(c[1]), (int) Math.floor(c[2]), part.name().length());
        int dark = shade(base, look.mode() == TotemLook.Mode.GRADIENT ? 0.72f : 0.66f), light = mix(base, 0xFFFFFF, look.mode() == TotemLook.Mode.GRADIENT ? 0.3f : 0.35f);
        if (part.name().equals("torso") && face == LookLayout.Face.NORTH) {
            if (in(CRACK_PX, i, j)) {
                return CRACK;
            }
            if (in(CRACK_PX, i - 1, j) || in(CRACK_PX, i + 1, j)) {
                return light; // the crack's lit edge
            }
        }
        int px = r < 0.11f ? dark : r < 0.20f ? light : base;
        if (look.mode() == TotemLook.Mode.SOLID) {
            float w = (float) Math.pow(clamp((c[1] - 2) / 12f), 0.75); // toward the base, into the plinth's stone
            px = mix(PLINTH, px, w);
        }
        return px;
    }

    private static boolean in(int[][] set, int i, int j) {
        for (int[] p : set) {
            if (p[0] == i && p[1] == j) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float t) {
        return Math.max(0, Math.min(1, t));
    }

    /** A steady 0..1 value per model pixel, so a look's mottling never shifts. */
    static float noise(int x, int y, int z, int s) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ s * 2654435;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536f;
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int shade(int c, float f) {
        return (Math.min(255, Math.round(((c >> 16) & 0xFF) * f)) << 16) | (Math.min(255, Math.round(((c >> 8) & 0xFF) * f)) << 8) | Math.min(255, Math.round((c & 0xFF) * f));
    }
}
