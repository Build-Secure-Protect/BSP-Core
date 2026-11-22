package com.mrgregles.bsp_core.looks;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The hats, as low-poly meshes in the head's own space (model pixels: the head is x and z -4..4, y 0..8, its front toward -z), the
 * shapes of the concept page's hats. Each quad has a role ("main" cloth, "trim", and named parts with their own colours); a hat in
 * the body's colours repaints "main" (and its "main2" stripes, lighter) and "trim" (darker). Glowing parts (the halo, a wizard's
 * stars) are drawn full bright. Built once per hat; the renderer draws them, so nothing here touches the client.
 */
public final class HatModels {
    private HatModels() {}

    /** How a part moves: not at all, spinning about y round its pivot (a propeller), or bobbing up and down (a halo). */
    public enum Motion { NONE, SPIN, BOB }

    /** One quad: four corners (x, y, z each), its role, whether it glows, and how it moves. */
    public record Quad(float[] v, String role, boolean glow, Motion motion, float pivotY) {
    }

    /** A built hat: its quads, its colours by role, and its tilt round the top of the head (x and z, radians). */
    public record Hat(List<Quad> quads, Map<String, Integer> colours, float tiltX, float tiltZ) {
    }

    private static final Map<String, Hat> BUILT = new HashMap<>();

    /** The hat with this id, or null for none or an unknown id. */
    public static synchronized Hat get(String id) {
        if (!TotemHats.known(id)) {
            return null;
        }
        return BUILT.computeIfAbsent(id, HatModels::build);
    }

    // ------------------------------------------------------------------ building

    private static final float TAU = (float) (Math.PI * 2);

    /** A mesh under construction, with a transform stack like a pose stack. */
    private static final class B {
        final List<Quad> quads = new ArrayList<>();
        final Map<String, Integer> colours = new HashMap<>();
        final Deque<Matrix4f> stack = new ArrayDeque<>();
        Matrix4f m = new Matrix4f();
        boolean glow;
        Motion motion = Motion.NONE;
        float pivotY;
        float tiltX, tiltZ;

        B col(String role, int rgb) {
            colours.put(role, rgb);
            return this;
        }

        void push() {
            stack.push(new Matrix4f(m));
        }

        void pop() {
            m = stack.pop();
        }

        /** Moves, turns (x, then y, then z, as the concept page's meshes do) and scales what follows. */
        void at(float x, float y, float z, float rx, float ry, float rz) {
            m.translate(x, y, z).rotateX(rx).rotateY(ry).rotateZ(rz);
        }

        /** Adds a quad facing away from {@code ref}, a point inside the shape (so lighting reads its normal the right way). */
        void quad(String role, float[] a, float[] b, float[] c, float[] d, float[] ref) {
            float[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, e2 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
            float nx = e1[1] * e2[2] - e1[2] * e2[1], ny = e1[2] * e2[0] - e1[0] * e2[2], nz = e1[0] * e2[1] - e1[1] * e2[0];
            float cx = (a[0] + b[0] + c[0] + d[0]) / 4 - ref[0], cy = (a[1] + b[1] + c[1] + d[1]) / 4 - ref[1], cz = (a[2] + b[2] + c[2] + d[2]) / 4 - ref[2];
            if (nx * cx + ny * cy + nz * cz < 0) {
                float[] t = b;
                b = d;
                d = t;
            }
            float[] v = new float[12];
            float[][] pts = {a, b, c, d};
            for (int k = 0; k < 4; k++) {
                Vector3f p = m.transformPosition(new Vector3f(pts[k][0], pts[k][1], pts[k][2]));
                v[k * 3] = p.x;
                v[k * 3 + 1] = p.y;
                v[k * 3 + 2] = p.z;
            }
            quads.add(new Quad(v, role, glow, motion, pivotY));
        }

        /** A box centred at (x, y, z), w by h by d, turned by (rx, ry, rz). */
        void box(String role, float w, float h, float d, float x, float y, float z, float rx, float ry, float rz) {
            push();
            at(x, y, z, rx, ry, rz);
            float a = w / 2, b = h / 2, c = d / 2;
            float[] O = p(0, 0, 0);
            quad(role, p(-a, -b, c), p(a, -b, c), p(a, b, c), p(-a, b, c), O);
            quad(role, p(a, -b, -c), p(-a, -b, -c), p(-a, b, -c), p(a, b, -c), O);
            quad(role, p(a, -b, c), p(a, -b, -c), p(a, b, -c), p(a, b, c), O);
            quad(role, p(-a, -b, -c), p(-a, -b, c), p(-a, b, c), p(-a, b, -c), O);
            quad(role, p(-a, b, c), p(a, b, c), p(a, b, -c), p(-a, b, -c), O);
            quad(role, p(-a, -b, -c), p(a, -b, -c), p(a, -b, c), p(-a, -b, c), O);
            pop();
        }

        void box(String role, float w, float h, float d, float x, float y, float z) {
            box(role, w, h, d, x, y, z, 0, 0, 0);
        }

        /** A cylinder or cone round y, centred at (x, y, z): bottom radius rb, top rt, height h; part of a turn from ts over tl. */
        void cyl(IntFunction<String> role, float rb, float rt, float h, int seg, float x, float y, float z, float rx, float rz, float ts, float tl) {
            push();
            at(x, y, z, rx, 0, rz);
            for (int k = 0; k < seg; k++) {
                float a0 = ts + tl * k / seg, a1 = ts + tl * (k + 1) / seg;
                String r = role.apply(k);
                float[] b0 = p(rb * sin(a0), -h / 2, rb * cos(a0)), b1 = p(rb * sin(a1), -h / 2, rb * cos(a1));
                float[] t0 = p(rt * sin(a0), h / 2, rt * cos(a0)), t1 = p(rt * sin(a1), h / 2, rt * cos(a1));
                quad(r, b0, b1, t1, t0, p(0, 0, 0));
                if (rt > 0) {
                    quad(r, p(0, h / 2, 0), t0, t1, t1, p(0, 0, 0));
                }
                quad(r, p(0, -h / 2, 0), b1, b0, b0, p(0, 0, 0));
            }
            pop();
        }

        void cyl(String role, float rb, float rt, float h, int seg, float x, float y, float z) {
            cyl(k -> role, rb, rt, h, seg, x, y, z, 0, 0, 0, TAU);
        }

        void cyl(String role, float rb, float rt, float h, int seg, float x, float y, float z, float rx, float rz) {
            cyl(k -> role, rb, rt, h, seg, x, y, z, rx, rz, 0, TAU);
        }

        /** A sphere (or its top half) of radius r at (x, y, z), squashed by (sx, sy, sz); the role may change ring by ring or round it. */
        void sphere(RoleAt role, float r, float x, float y, float z, float sx, float sy, float sz, boolean full) {
            push();
            at(x, y, z, 0, 0, 0);
            m.scale(sx, sy, sz);
            int seg = 16, rings = full ? 10 : 6;
            float top = full ? (float) Math.PI : (float) Math.PI / 2;
            for (int i = 0; i < rings; i++) {
                float t0 = top * i / rings, t1 = top * (i + 1) / rings;
                for (int k = 0; k < seg; k++) {
                    float a0 = TAU * k / seg, a1 = TAU * (k + 1) / seg;
                    quad(role.at(i, k), s(r, t0, a0), s(r, t1, a0), s(r, t1, a1), s(r, t0, a1), p(0, r * 0.3f, 0));
                }
            }
            if (!full) {
                for (int k = 0; k < seg; k++) {
                    float a0 = TAU * k / seg, a1 = TAU * (k + 1) / seg;
                    quad(role.at(rings - 1, k), p(0, 0, 0), s(r, top, a1), s(r, top, a0), s(r, top, a0), p(0, r * 0.3f, 0));
                }
            }
            pop();
        }

        void sphere(String role, float r, float x, float y, float z, float sx, float sy, float sz, boolean full) {
            sphere((i, k) -> role, r, x, y, z, sx, sy, sz, full);
        }

        /** A ring round z of radius big, tube radius small, over {@code arc} of a turn from +x through +y. */
        void torus(String role, float big, float small, float arc, int seg, float x, float y, float z, float rx, float ry, float rz) {
            push();
            at(x, y, z, rx, ry, rz);
            int tube = 6;
            for (int k = 0; k < seg; k++) {
                float u0 = arc * k / seg, u1 = arc * (k + 1) / seg;
                for (int j = 0; j < tube; j++) {
                    float v0 = TAU * j / tube, v1 = TAU * (j + 1) / tube;
                    quad(role, t(big, small, u0, v0), t(big, small, u1, v0), t(big, small, u1, v1), t(big, small, u0, v1), t(big, 0, (u0 + u1) / 2, 0));
                }
            }
            pop();
        }

        /** A tapering tube from a to b, radius r0 at a and r1 at b. */
        void between(String role, float[] a, float[] b, float r0, float r1) {
            Vector3f d = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            float len = d.length();
            d.normalize();
            push();
            m.translate((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2);
            m.rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 1, 0), d));
            cyl(k -> role, r0, r1, len, 8, 0, 0, 0, 0, 0, 0, TAU);
            pop();
        }

        /** A horn or a straw: tubes through the points, radius from r0 to r1, with ball joints. */
        void horn(String[] roles, float[][] pts, float r0, float r1) {
            int n = pts.length - 1;
            for (int k = 0; k < n; k++) {
                float ra = r0 + (r1 - r0) * k / n, rb = r0 + (r1 - r0) * (k + 1) / n;
                String role = roles[Math.min(roles.length - 1, k * roles.length / n)];
                between(role, pts[k], pts[k + 1], ra, rb);
                if (k > 0) {
                    sphere(role, ra, pts[k][0], pts[k][1], pts[k][2], 1, 1, 1, true);
                }
            }
            sphere(roles[roles.length - 1], r1, pts[n][0], pts[n][1], pts[n][2], 1, 1, 1, true);
        }

        /** A five-pointed star, r across, depth thick, facing -z, at (x, y, z). */
        void star(String role, float r, float depth, float x, float y, float z) {
            push();
            at(x, y, z, 0, 0, 0);
            float[] O = p(0, 0, 0);
            float[][] pts = new float[10][];
            for (int k = 0; k < 10; k++) {
                float a = (float) Math.PI / 2 + k * (float) Math.PI / 5, rr = k % 2 == 1 ? r * 0.45f : r;
                pts[k] = new float[]{(float) Math.cos(a) * rr, (float) Math.sin(a) * rr};
            }
            for (int k = 0; k < 10; k++) {
                float[] a = pts[k], b = pts[(k + 1) % 10];
                quad(role, p(0, 0, -depth / 2), p(b[0], b[1], -depth / 2), p(a[0], a[1], -depth / 2), p(a[0], a[1], -depth / 2), O);
                quad(role, p(0, 0, depth / 2), p(a[0], a[1], depth / 2), p(b[0], b[1], depth / 2), p(b[0], b[1], depth / 2), O);
                quad(role, p(a[0], a[1], -depth / 2), p(b[0], b[1], -depth / 2), p(b[0], b[1], depth / 2), p(a[0], a[1], depth / 2), O);
            }
            pop();
        }

        static float[] p(float x, float y, float z) {
            return new float[]{x, y, z};
        }

        static float[] s(float r, float theta, float phi) {
            return p(r * (float) Math.sin(theta) * sin(phi), r * (float) Math.cos(theta), r * (float) Math.sin(theta) * cos(phi));
        }

        static float[] t(float big, float small, float u, float v) {
            float c = big + small * (float) Math.cos(v);
            return p(c * (float) Math.cos(u), c * (float) Math.sin(u), small * (float) Math.sin(v));
        }

        Hat done() {
            return new Hat(List.copyOf(quads), Map.copyOf(colours), tiltX, tiltZ);
        }
    }

    interface RoleAt {
        String at(int ring, int around);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    /** Points along a smooth curve through {@code pts} (Catmull-Rom), {@code per} steps between each pair. */
    static float[][] curve(float[][] pts, int per) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < pts.length - 1; i++) {
            float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
            for (int s = 0; s < per; s++) {
                float t = (float) s / per, t2 = t * t, t3 = t2 * t;
                float[] q = new float[3];
                for (int k = 0; k < 3; k++) {
                    q[k] = 0.5f * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                }
                out.add(q);
            }
        }
        out.add(pts[pts.length - 1]);
        return out.toArray(new float[0][]);
    }

    private static Hat build(String id) {
        B b = new B();
        final float PI = (float) Math.PI;
        switch (id) {
            case "top_hat" -> {
                b.col("main", 0x1D1E24).col("under", 0x101116).col("lid", 0x2C2E36).col("trim", 0xB0203A).col("gold", 0xFFD23A).col("hole", 0x1D1E24);
                b.cyl("main", 6.2f, 6.2f, 0.7f, 28, 0, 8.55f, 0);
                b.cyl("under", 6.25f, 6.25f, 0.2f, 28, 0, 8.12f, 0);
                b.cyl("main", 3.6f, 3.9f, 6.9f, 20, 0, 12.25f, 0);
                b.cyl("lid", 3.95f, 3.95f, 0.35f, 20, 0, 15.75f, 0);
                b.cyl("trim", 3.66f, 3.72f, 1.4f, 20, 0, 9.65f, 0);
                b.box("gold", 1.7f, 1.2f, 0.35f, 0, 9.65f, -3.8f);
                b.box("hole", 0.9f, 0.55f, 0.4f, 0, 9.65f, -3.83f);
                b.tiltZ = -0.07f;
            }
            case "halo" -> {
                b.col("glow", 0xFFD23A).col("mote", 0xFFF3C4);
                b.glow = true;
                b.motion = Motion.BOB;
                b.torus("glow", 4.3f, 0.55f, TAU, 32, 0, 12.6f, 0, PI / 2, 0, 0);
                for (int k = 0; k < 3; k++) {
                    float a = k * TAU / 3;
                    b.box("mote", 0.5f, 0.5f, 0.5f, cos(a) * 4.3f, 13.5f, sin(a) * 4.3f, 0, a, 0);
                }
            }
            case "flat_cap" -> {
                b.col("main", 0x6B5A44).col("trim", 0x4A3D2E).col("stitch", 0x3A3024);
                b.push();
                b.m.scale(1, 1, 1.06f);
                b.cyl("main", 4.8f, 4.75f, 1.6f, 20, 0, 8.8f, 0.2f);
                b.pop();
                b.cyl("main", 4.75f, 4.4f, 1.1f, 20, 0, 10.0f, 0.55f, 0.16f, 0);
                b.push();
                b.m.translate(0, 8.45f, -3.7f).rotateX(0.12f).scale(1, 1, 0.62f);
                b.cyl(k -> "trim", 4.5f, 4.5f, 0.45f, 20, 0, 0, 0, 0, 0, PI / 2, PI);
                b.pop();
                b.cyl("trim", 0.65f, 0.65f, 0.4f, 10, 0, 10.75f, 0.9f);
                b.box("stitch", 7.6f, 0.1f, 0.18f, 0, 8.05f, -4.2f);
            }
            case "beer_cap" -> {
                b.col("main", 0xECEEF2).col("trim", 0xC8283C).col("metal", 0x9AA3B5).col("can", 0xB0203A).col("label", 0xFFD23A)
                        .col("lid", 0xC9CED8).col("straw", 0xF0C060).col("badge", 0xFFFFFF);
                b.sphere("main", 4.9f, 0, 7.7f, 0, 1, 0.86f, 1, false);
                b.cyl("trim", 5.1f, 5.05f, 0.9f, 24, 0, 8.0f, 0);
                b.box("trim", 2.6f, 1.3f, 0.3f, 0, 10.2f, -4.2f, 0.62f, 0, 0);
                b.box("badge", 1.8f, 0.7f, 0.32f, 0, 10.2f, -4.22f, 0.62f, 0, 0);
                for (int sx : new int[]{-1, 1}) {
                    float x = sx * 5.75f;
                    b.box("metal", 1.0f, 3.4f, 0.6f, sx * 4.95f, 10.4f, 0);
                    b.cyl("trim", 1.75f, 1.75f, 0.6f, 14, x, 9.1f, 0);
                    b.cyl("trim", 1.75f, 1.75f, 0.6f, 14, x, 11.9f, 0);
                    b.cyl("can", 1.5f, 1.5f, 3.8f, 16, x, 10.5f, 0);
                    b.cyl("label", 1.52f, 1.52f, 0.25f, 16, x, 9.6f, 0);
                    b.cyl("label", 1.52f, 1.52f, 0.25f, 16, x, 11.4f, 0);
                    b.cyl("lid", 1.32f, 1.32f, 0.25f, 16, x, 12.5f, 0);
                    float[][] path = curve(new float[][]{{x, 12.5f, 0}, {x * 0.95f, 14.3f, -0.6f}, {sx * 4.0f, 13.8f, -3.6f}, {sx * 2.6f, 8.6f, -5.6f}, {sx * 0.9f, 2.7f, -4.7f}}, 5);
                    for (int k = 0; k < path.length - 1; k++) {
                        b.between("straw", path[k], path[k + 1], 0.3f, 0.3f);
                    }
                }
            }
            case "sports_cap" -> {
                b.col("main", 0x2A62C8).col("trim", 0xFFFFFF).col("under", 0x2A6A3A).col("metal", 0xC9CED8).col("logo", 0xFFFFFF).col("seam", 0x1F4A96);
                b.sphere((i, k) -> k % 3 == 0 && i < 5 ? "seam" : "main", 4.75f, 0, 7.5f, 0, 1, 0.8f, 1, false);
                b.sphere("trim", 0.6f, 0, 11.3f, 0, 1, 1, 1, true);
                b.push();
                b.m.translate(0, 8.0f, -3.4f).rotateX(-0.1f).scale(1, 1, 0.95f);
                b.cyl(k -> "main", 4.6f, 4.6f, 0.4f, 20, 0, 0, 0, 0, 0, PI / 2, PI);
                b.cyl(k -> "under", 4.5f, 4.5f, 0.12f, 20, 0, -0.24f, 0, 0, 0, PI / 2, PI);
                b.pop();
                b.box("logo", 2.3f, 2.3f, 0.2f, 0, 9.7f, -3.98f, 0.58f, 0, 0);
                b.box("main", 0.5f, 1.4f, 0.22f, -0.3f, 9.7f, -4.06f, 0.58f, 0, 0);
                b.box("trim", 3.2f, 0.7f, 0.35f, 0, 8.1f, 4.72f);
                b.box("metal", 0.7f, 0.7f, 0.45f, 0, 8.1f, 4.78f);
            }
            case "beanie" -> {
                b.col("main", 0xC8283C).col("main2", 0xE9ECF2).col("trim", 0xA81F30).col("trim2", 0x86182A).col("pom", 0xE9ECF2).col("tag", 0xFFD23A);
                b.sphere((i, k) -> i % 2 == 0 ? "main" : "main2", 4.8f, 0, 7.4f, 0, 1, 1.08f, 1, false);
                b.cyl(k -> k % 2 == 0 ? "trim" : "trim2", 4.95f, 4.95f, 2.3f, 28, 0, 8.45f, 0, 0, 0, 0, TAU);
                float py = 7.4f + 4.8f * 1.08f + 1.1f;
                b.sphere("pom", 1.7f, 0, py, 0, 1, 1, 1, true);
                for (int k = 0; k < 7; k++) {
                    float a = k * TAU / 7;
                    b.sphere("pom", 0.75f, cos(a) * 1.3f, py + sin(k * 2.3f) * 0.8f, sin(a) * 1.3f, 1, 1, 1, true);
                }
                b.box("tag", 1.4f, 0.9f, 0.2f, 2.3f, 8.3f, -4.98f);
            }
            case "wizard" -> {
                b.col("main", 0x3A2A8A).col("trim", 0xFFD23A).col("glow", 0xFFD23A);
                b.cyl("main", 6.3f, 6.3f, 0.5f, 28, 0, 8.45f, 0);
                b.cyl("trim", 3.95f, 3.95f, 0.9f, 22, 0, 9.2f, 0);
                float[][] segs = {{3.85f, 3.2f, 3.2f, 0}, {3.2f, 2.4f, 3.0f, 0.16f}, {2.4f, 1.5f, 2.8f, 0.24f}, {1.5f, 0.45f, 2.6f, 0.36f}};
                b.push();
                b.m.translate(0, 9.6f, 0);
                for (int k = 0; k < segs.length; k++) {
                    float rb = segs[k][0], rt = segs[k][1], h = segs[k][2];
                    b.m.rotateX(segs[k][3]);
                    b.cyl("main", rb, rt, h, 18, 0, h / 2, 0);
                    if (k < 3) {
                        b.glow = true;
                        b.star("glow", 0.75f, 0.15f, (k - 1) * 1.1f, h * 0.5f, -(rb + rt) / 2 - 0.12f);
                        b.glow = false;
                    }
                    b.m.translate(0, h, 0);
                }
                b.glow = true;
                b.sphere("glow", 0.55f, 0, 0, 0, 1, 1, 1, true);
                b.glow = false;
                b.pop();
                b.glow = true;
                b.torus("glow", 0.7f, 0.22f, PI * 1.3f, 12, 1.9f, 10.9f, -3.65f, 0, 0, 0.8f);
                b.glow = false;
            }
            case "devil" -> {
                b.col("band", 0x16181D).col("main", 0xB0141C).col("mid", 0x7A0E14).col("trim", 0x3A0A0E);
                b.torus("band", 4.35f, 0.38f, PI, 28, 0, 4.4f, 0.6f, 0, 0, 0);
                for (int sx : new int[]{-1, 1}) {
                    float[][] pts = {{2.7f, 8.0f, -0.3f}, {3.6f, 9.6f, -0.6f}, {4.5f, 11.1f, -1.0f}, {4.9f, 12.6f, -1.5f}, {4.6f, 13.9f, -2.0f}, {3.9f, 14.6f, -2.4f}};
                    for (float[] p : pts) {
                        p[0] *= sx;
                    }
                    b.horn(new String[]{"main", "main", "mid", "trim"}, pts, 1.3f, 0.14f);
                }
            }
            case "cowboy" -> {
                b.col("main", 0x8A5A32).col("trim", 0x4E3420).col("metal", 0xC9CED8);
                b.box("main", 9.2f, 0.5f, 13.4f, 0, 8.4f, 0);
                b.box("main", 9.0f, 0.5f, 1.8f, 0, 8.22f, -7.3f, 0.2f, 0, 0);
                b.box("main", 9.0f, 0.5f, 1.8f, 0, 8.22f, 7.3f, -0.2f, 0, 0);
                for (int sx : new int[]{-1, 1}) {
                    b.box("main", 2.8f, 0.5f, 12.4f, sx * 5.95f, 8.97f, 0, 0, 0, sx * 0.42f);
                }
                b.box("main", 6.4f, 4.0f, 7.4f, 0, 10.9f, 0.2f);
                b.box("main", 2.4f, 0.9f, 6.8f, -1.75f, 13.3f, 0.3f);
                b.box("main", 2.4f, 0.9f, 6.8f, 1.75f, 13.3f, 0.3f);
                b.box("trim", 1.1f, 1.8f, 0.9f, -1.4f, 12.2f, -3.25f);
                b.box("trim", 1.1f, 1.8f, 0.9f, 1.4f, 12.2f, -3.25f);
                b.box("trim", 6.6f, 1.0f, 7.6f, 0, 9.4f, 0.2f);
                b.cyl("metal", 0.75f, 0.75f, 0.3f, 12, 0, 9.4f, -3.6f, PI / 2, 0);
            }
            case "viking" -> {
                b.col("main", 0x8A909C).col("trim", 0xB07A3A).col("rivet", 0xE0B070).col("horn", 0xEFE6CC).col("tip", 0xC9B88A);
                b.sphere("main", 4.75f, 0, 7.6f, 0, 1, 0.92f, 1, false);
                b.cyl("trim", 4.9f, 4.9f, 1.3f, 24, 0, 8.3f, 0);
                for (int k = 0; k < 12; k++) {
                    float a = k * TAU / 12;
                    b.sphere("rivet", 0.32f, sin(a) * 4.98f, 8.3f, cos(a) * 4.98f, 1, 1, 1, true);
                }
                b.torus("trim", 4.55f, 0.42f, PI, 24, 0, 7.6f, 0, 0, PI / 2, 0);
                b.box("trim", 1.1f, 3.4f, 0.5f, 0, 6.9f, -4.95f);
                for (int sx : new int[]{-1, 1}) {
                    b.cyl("trim", 1.3f, 1.3f, 0.8f, 12, sx * 4.95f, 9.6f, 0, 0, PI / 2);
                    float[][] pts = {{4.9f, 9.6f, 0}, {6.1f, 10.2f, -0.3f}, {7.1f, 11.4f, -0.6f}, {7.6f, 13.0f, -0.9f}, {7.4f, 14.6f, -1.2f}, {6.8f, 15.6f, -1.4f}};
                    for (float[] p : pts) {
                        p[0] *= sx;
                    }
                    b.horn(new String[]{"horn", "horn", "horn", "tip"}, pts, 1.15f, 0.15f);
                }
            }
            case "party" -> {
                b.col("main", 0xF27BBD).col("main2", 0x4AA3F0).col("ruffle", 0xFFD23A).col("pom", 0xFFD23A);
                b.cyl(k -> k % 2 == 0 ? "main" : "main2", 3.1f, 0.12f, 7.6f, 20, 0, 11.95f, 0, 0, 0, 0, TAU);
                b.torus("ruffle", 3.15f, 0.42f, TAU, 20, 0, 8.25f, 0, PI / 2, 0, 0);
                b.sphere("pom", 1.15f, 0, 15.9f, 0, 1, 1, 1, true);
                b.tiltZ = -0.22f;
            }
            case "chef" -> {
                b.col("main", 0xF4F4F6).col("main2", 0xE2E3E8);
                b.cyl(k -> k % 3 == 0 ? "main2" : "main", 4.2f, 4.25f, 2.8f, 24, 0, 9.45f, 0, 0, 0, 0, TAU);
                b.sphere("main", 3.3f, 0, 13.3f, 0, 1, 0.85f, 1, true);
                for (int k = 0; k < 5; k++) {
                    float a = k * TAU / 5 + 0.3f;
                    b.sphere("main", 2.4f, cos(a) * 1.9f, 12.6f, sin(a) * 1.9f, 1, 0.85f, 1, true);
                }
            }
            case "propeller" -> {
                b.col("main", 0xC8283C).col("q2", 0xFFD23A).col("q3", 0x2A62C8).col("q4", 0x2FA860).col("trim", 0xFFD23A)
                        .col("metal", 0xC9CED8).col("hub", 0xC8283C).col("blade", 0x2A62C8);
                String[] q = {"main", "q2", "q3", "q4"};
                b.sphere((i, k) -> q[(k / 2) % 4], 4.8f, 0, 7.5f, 0, 1, 0.95f, 1, false);
                b.cyl("trim", 4.95f, 4.95f, 0.9f, 24, 0, 8.0f, 0);
                b.cyl("metal", 0.28f, 0.28f, 1.8f, 8, 0, 12.9f, 0);
                b.sphere("hub", 0.6f, 0, 13.8f, 0, 1, 1, 1, true);
                b.motion = Motion.SPIN;
                b.pivotY = 13.8f;
                for (int sx : new int[]{-1, 1}) {
                    b.box("blade", 4.4f, 0.16f, 1.2f, sx * 2.5f, 13.8f, 0, sx * 0.3f, 0, 0);
                }
                b.motion = Motion.NONE;
            }
            default -> {
            }
        }
        return b.done();
    }
}
