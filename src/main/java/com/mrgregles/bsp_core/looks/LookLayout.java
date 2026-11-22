package com.mrgregles.bsp_core.looks;

/**
 * Where a look's pixels go: the eight cuboids of the Guardian model that a look draws (the same as the baked model's, in model
 * pixels, front to the north), each face's place in the 64 by 64 look texture, and the order painted pixels are stored in.
 *
 * <p>Each part takes a block of the texture laid out like a player skin's: the top and bottom faces on the first row (each w by d,
 * after a d-wide gap), then east, north, west and south (d by h, w by h, d by h, w by h). Texel (i, j) of a face sits at
 * {@link #origin} + (i + 0.5) {@link #uDir} + (j + 0.5) {@link #vDir} on the model, so the texture, the renderer and the paint
 * bench all agree. Painted pixels are stored part by part ({@link TotemLook#PARTS}), face by face in {@link Face} order, row by row.
 */
public final class LookLayout {
    private LookLayout() {}

    public static final int SIZE = 64;

    /** The faces in storage order: east, west, up, down, south, north. */
    public enum Face {
        EAST(1, 0, 0), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0), SOUTH(0, 0, 1), NORTH(0, 0, -1);

        public final int nx, ny, nz;

        Face(int nx, int ny, int nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    /** A part: its corners in model pixels and the top-left of its block in the look texture. */
    public record Part(String name, float[] from, float[] to, int u, int v) {
        public int w() {
            return Math.round(to[0] - from[0]);
        }

        public int h() {
            return Math.round(to[1] - from[1]);
        }

        public int d() {
            return Math.round(to[2] - from[2]);
        }
    }

    /** The head turns this many degrees about x, round (8, 16, 8): the Guardian's bowed head. */
    public static final float HEAD_ANGLE = -22.5f;
    public static final float[] HEAD_PIVOT = {8, 16, 8};

    public static final Part[] PARTS = {
            new Part("head", new float[]{4, 16, 4}, new float[]{12, 24, 12}, 0, 0),
            new Part("torso", new float[]{4, 6, 5}, new float[]{12, 16, 10}, 32, 0),
            new Part("knee_l", new float[]{3, 2, 3}, new float[]{8, 6, 11}, 0, 16),
            new Part("knee_r", new float[]{8, 2, 3}, new float[]{13, 6, 11}, 26, 16),
            new Part("arm_l", new float[]{1, 8, 2}, new float[]{4, 12, 9}, 0, 28),
            new Part("arm_r", new float[]{12, 8, 2}, new float[]{15, 12, 9}, 20, 28),
            new Part("hand_l", new float[]{2, 7, -1}, new float[]{5, 13, 3}, 40, 28),
            new Part("hand_r", new float[]{11, 7, -1}, new float[]{14, 13, 3}, 0, 39),
    };

    /** A face's size in texels (width, height). */
    public static int[] dims(Part p, Face f) {
        return switch (f) {
            case EAST, WEST -> new int[]{p.d(), p.h()};
            case UP, DOWN -> new int[]{p.w(), p.d()};
            default -> new int[]{p.w(), p.h()};
        };
    }

    /** A face's top-left texel in the look texture. */
    public static int[] at(Part p, Face f) {
        int w = p.w(), d = p.d();
        return switch (f) {
            case UP -> new int[]{p.u() + d, p.v()};
            case DOWN -> new int[]{p.u() + d + w, p.v()};
            case EAST -> new int[]{p.u(), p.v() + d};
            case NORTH -> new int[]{p.u() + d, p.v() + d};
            case WEST -> new int[]{p.u() + d + w, p.v() + d};
            case SOUTH -> new int[]{p.u() + 2 * d + w, p.v() + d};
        };
    }

    /** The model corner (in model pixels, before the head's turn) at texel (0, 0)'s outer corner. */
    public static float[] origin(Part p, Face face) {
        float[] f = p.from(), t = p.to();
        return switch (face) {
            case EAST -> new float[]{t[0], t[1], t[2]};
            case WEST -> new float[]{f[0], t[1], f[2]};
            case UP -> new float[]{f[0], t[1], f[2]};
            case DOWN -> new float[]{f[0], f[1], t[2]};
            case SOUTH -> new float[]{f[0], t[1], t[2]};
            case NORTH -> new float[]{t[0], t[1], f[2]};
        };
    }

    /** The model step for one texel to the right (i + 1). */
    public static float[] uDir(Face face) {
        return switch (face) {
            case EAST -> new float[]{0, 0, -1};
            case WEST -> new float[]{0, 0, 1};
            case NORTH -> new float[]{-1, 0, 0};
            default -> new float[]{1, 0, 0};
        };
    }

    /** The model step for one texel down (j + 1). */
    public static float[] vDir(Face face) {
        return switch (face) {
            case UP -> new float[]{0, 0, 1};
            case DOWN -> new float[]{0, 0, -1};
            default -> new float[]{0, -1, 0};
        };
    }

    /** Texel (i, j)'s centre on the model, in model pixels, the head's turn included. */
    public static float[] texelCentre(Part p, Face face, int i, int j) {
        float[] o = origin(p, face), u = uDir(face), v = vDir(face);
        float x = o[0] + (i + 0.5f) * u[0] + (j + 0.5f) * v[0];
        float y = o[1] + (i + 0.5f) * u[1] + (j + 0.5f) * v[1];
        float z = o[2] + (i + 0.5f) * u[2] + (j + 0.5f) * v[2];
        if (p.name().equals("head")) {
            double a = Math.toRadians(HEAD_ANGLE), ly = y - HEAD_PIVOT[1], lz = z - HEAD_PIVOT[2];
            float ry = (float) (HEAD_PIVOT[1] + ly * Math.cos(a) - lz * Math.sin(a));
            float rz = (float) (HEAD_PIVOT[2] + ly * Math.sin(a) + lz * Math.cos(a));
            return new float[]{x, ry, rz};
        }
        return new float[]{x, y, z};
    }

    /** Calls {@code each} for every painted pixel in storage order: (pixel index, part, face, i, j). */
    public static void forEachPixel(PixelVisitor each) {
        int n = 0;
        for (Part p : PARTS) {
            for (Face f : Face.values()) {
                int[] d = dims(p, f);
                for (int j = 0; j < d[1]; j++) {
                    for (int i = 0; i < d[0]; i++) {
                        each.visit(n++, p, f, i, j);
                    }
                }
            }
        }
    }

    public interface PixelVisitor {
        void visit(int index, Part part, Face face, int i, int j);
    }
}
