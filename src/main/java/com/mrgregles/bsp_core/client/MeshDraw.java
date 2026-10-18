package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapes for block entity renderers that need more than the baked models: boxes at any angle, spheres, rings, polygon frames and
 * lines, skinned with the machine atlas tiles the machines themselves are made of (worn, noisy metal) and shaded by the entity
 * shader's two lights, so faces turn darker as they turn away. Coordinates are in blocks. {@link Mat} is a tile and a tint: the
 * gold tile tinted gives copper and brass. Glowing shapes go through the emissive type, which ignores the world's light and the
 * shading, with a colour instead of a tile.
 */
public final class MeshDraw {
    private static final ResourceLocation ATLAS = new ResourceLocation(BSPCore.MODID, "block/machine_atlas");
    private static final ResourceLocation PLASMA = new ResourceLocation(BSPCore.MODID, "block/plasma_still");

    /** A tile of the machine atlas (64 x 64, tiles of 16) and the tint it is drawn with (white = as is). */
    public enum Mat {
        HULL(0, 0, 0xFFFFFF), HULL2(16, 0, 0xFFFFFF), PANEL(32, 0, 0xFFFFFF), MID(48, 0, 0xFFFFFF), TRIM(0, 16, 0xFFFFFF), STEEL(0, 32, 0xFFFFFF), GOLD(0, 48, 0xFFFFFF),
        /** The gold tile tinted down to copper. */
        COPPER(0, 48, 0xCEA0B0),
        /** The gold tile tinted toward brass. */
        BRASS(0, 48, 0xCEBEC8),
        /** The gold tile warmed up: a copper coil with current in it. */
        COPPER_HOT(0, 48, 0xFFC8C0);

        final int ox, oy, tint;

        Mat(int ox, int oy, int tint) {
            this.ox = ox;
            this.oy = oy;
            this.tint = tint;
        }
    }

    /**
     * Solid parts: the entity cutout type (world light, shaded by the entity lights). Glowing parts: the opaque beacon beam type,
     * full bright and writing depth, so a glow sits properly among the things round it (the emissive entity type writes no depth and
     * lets anything drawn later paint over it). The plasma skin: the translucent beacon beam type.
     */
    private final VertexConsumer solid, emissive, skin;
    private final TextureAtlasSprite atlas, plasma;

    public MeshDraw(MultiBufferSource buffers) {
        this.solid = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        this.emissive = buffers.getBuffer(RenderType.beaconBeam(InventoryMenu.BLOCK_ATLAS, false));
        this.skin = buffers.getBuffer(RenderType.beaconBeam(InventoryMenu.BLOCK_ATLAS, true));
        this.atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS);
        this.plasma = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLASMA);
    }

    public static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k)), b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** A little wear: faces vary a few percent by where they are, and one in seven is a touch grimier. */
    private static float wear(float x, float y, float z) {
        float h = Mth.frac(Mth.sin(x * 127.1f + y * 311.7f + z * 74.7f) * 43758.5453f);
        return (h > 0.86f ? 0.86f : 0.94f) + h * 0.12f;
    }

    /** The entity format carries an overlay, the block format (the beacon beam types) does not. */
    private void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, int rgb, int alpha, float u, float v, int light, float nx, float ny, float nz) {
        if (vc == solid) {
            vc.vertex(m, x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, nx, ny, nz).endVertex();
        } else {
            vc.vertex(m, x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).uv(u, v).uv2(light).normal(nm, nx, ny, nz).endVertex();
        }
    }

    /** The atlas coordinate of a point {@code (fx, fy)} in 0..1 across a tile. */
    private float tu(Mat mat, float fx) {
        return atlas.getU(mat.ox / 4f + 4f * Mth.clamp(fx, 0, 1));
    }

    private float tv(Mat mat, float fy) {
        return atlas.getV(mat.oy / 4f + 4f * Mth.clamp(fy, 0, 1));
    }

    private static float[] normal(float[] a, float[] b, float[] d) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len > 1e-6f ? new float[]{nx / len, ny / len, nz / len} : new float[]{0, 1, 0};
    }

    /** A skinned quad from four corners, the whole tile stretched over it, drawn on both sides. */
    public void quad(PoseStack pose, float[] a, float[] b, float[] c, float[] d, Mat mat, int light) {
        int tint = scale(mat.tint, wear(a[0] + c[0], a[1] + c[1], a[2] + c[2]));
        skinned(solid, pose, a, b, c, d, mat, tint, 255, light);
    }

    private void skinned(VertexConsumer vc, PoseStack pose, float[] a, float[] b, float[] c, float[] d, Mat mat, int tint, int alpha, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float[] n = normal(a, b, d);
        float u0 = tu(mat, 0), u1 = tu(mat, 1), v0 = tv(mat, 0), v1 = tv(mat, 1);
        vertex(vc, m, nm, a[0], a[1], a[2], tint, alpha, u0, v0, light, n[0], n[1], n[2]);
        vertex(vc, m, nm, b[0], b[1], b[2], tint, alpha, u0, v1, light, n[0], n[1], n[2]);
        vertex(vc, m, nm, c[0], c[1], c[2], tint, alpha, u1, v1, light, n[0], n[1], n[2]);
        vertex(vc, m, nm, d[0], d[1], d[2], tint, alpha, u1, v0, light, n[0], n[1], n[2]);
        vertex(vc, m, nm, d[0], d[1], d[2], tint, alpha, u1, v0, light, -n[0], -n[1], -n[2]);
        vertex(vc, m, nm, c[0], c[1], c[2], tint, alpha, u1, v1, light, -n[0], -n[1], -n[2]);
        vertex(vc, m, nm, b[0], b[1], b[2], tint, alpha, u0, v1, light, -n[0], -n[1], -n[2]);
        vertex(vc, m, nm, a[0], a[1], a[2], tint, alpha, u0, v0, light, -n[0], -n[1], -n[2]);
    }

    /** A flat-coloured quad that ignores the world's light. */
    public void glowQuad(PoseStack pose, float[] a, float[] b, float[] c, float[] d, int rgb, int alpha) {
        skinned(emissive, pose, a, b, c, d, Mat.PANEL, scale(rgb, 1.2f), alpha, LightTexture.FULL_BRIGHT);
    }

    /**
     * An axis-aligned skinned box. Each face is cut into cells of half a block at most, every cell wearing the whole tile, so the
     * metal keeps its grain on long segments instead of stretching.
     */
    public void box(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, Mat mat, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        // each face: origin, the two axes it spans (unit), its size along them, its normal
        float[][] faces = {
                {x0, y0, z0, 0, 1, 0, 1, 0, 0, y1 - y0, x1 - x0, 0, 0, -1},
                {x1, y0, z1, 0, 1, 0, -1, 0, 0, y1 - y0, x1 - x0, 0, 0, 1},
                {x0, y0, z1, 0, 1, 0, 0, 0, -1, y1 - y0, z1 - z0, -1, 0, 0},
                {x1, y0, z0, 0, 1, 0, 0, 0, 1, y1 - y0, z1 - z0, 1, 0, 0},
                {x0, y1, z0, 0, 0, 1, 1, 0, 0, z1 - z0, x1 - x0, 0, 1, 0},
                {x0, y0, z1, 0, 0, -1, 1, 0, 0, z1 - z0, x1 - x0, 0, -1, 0},
        };
        for (float[] f : faces) {
            int rows = Math.max(1, Mth.ceil(f[9] / 0.5f)), cols = Math.max(1, Mth.ceil(f[10] / 0.5f));
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    float a0 = f[9] * r / rows, a1 = f[9] * (r + 1) / rows, b0 = f[10] * c / cols, b1 = f[10] * (c + 1) / cols;
                    float[] p00 = {f[0] + f[3] * a0 + f[6] * b0, f[1] + f[4] * a0 + f[7] * b0, f[2] + f[5] * a0 + f[8] * b0};
                    float[] p10 = {f[0] + f[3] * a1 + f[6] * b0, f[1] + f[4] * a1 + f[7] * b0, f[2] + f[5] * a1 + f[8] * b0};
                    float[] p11 = {f[0] + f[3] * a1 + f[6] * b1, f[1] + f[4] * a1 + f[7] * b1, f[2] + f[5] * a1 + f[8] * b1};
                    float[] p01 = {f[0] + f[3] * a0 + f[6] * b1, f[1] + f[4] * a0 + f[7] * b1, f[2] + f[5] * a0 + f[8] * b1};
                    int tint = scale(mat.tint, wear(p00[0] + p11[0], p00[1] + p11[1], p00[2] + p11[2]));
                    float u0 = tu(mat, 0), u1 = tu(mat, 1), v0 = tv(mat, 0), v1 = tv(mat, 1);
                    vertex(solid, m, nm, p00[0], p00[1], p00[2], tint, 255, u0, v0, light, f[11], f[12], f[13]);
                    vertex(solid, m, nm, p10[0], p10[1], p10[2], tint, 255, u0, v1, light, f[11], f[12], f[13]);
                    vertex(solid, m, nm, p11[0], p11[1], p11[2], tint, 255, u1, v1, light, f[11], f[12], f[13]);
                    vertex(solid, m, nm, p01[0], p01[1], p01[2], tint, 255, u1, v0, light, f[11], f[12], f[13]);
                }
            }
        }
    }

    /** A glowing box: one colour, full bright, no shading. */
    public void glow(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float brightness) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int c = scale(rgb, brightness * 1.2f);
        float u0 = tu(Mat.PANEL, 0.2f), u1 = tu(Mat.PANEL, 0.8f), v0 = tv(Mat.PANEL, 0.2f), v1 = tv(Mat.PANEL, 0.8f);
        float[][] quads = {
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1},
                {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1},
                {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0},
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0},
        };
        float[] us = {u0, u0, u1, u1}, vs = {v0, v1, v1, v0};
        for (float[] q : quads) {
            for (int k = 0; k < 4; k++) {
                vertex(emissive, m, nm, q[k * 3], q[k * 3 + 1], q[k * 3 + 2], c, 255, us[k], vs[k], LightTexture.FULL_BRIGHT, q[12], q[13], q[14]);
            }
        }
    }

    /**
     * A cable socket on a face, the Tank Port's look: a square ring of trim proud of the face round a dark plate with a lit window.
     * {@code (cx, cy, cz)} is the centre of the face; the face looks along {@code axis} (0 x, 1 y, 2 z) toward {@code sign};
     * {@code size} is the ring's outer width. The window glows {@code glowRgb} at {@code brightness}.
     */
    public void socket(PoseStack pose, float cx, float cy, float cz, int axis, int sign, float size, int glowRgb, float brightness, int light) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        if (axis == 0) {
            pose.mulPose(Axis.YP.rotationDegrees(sign > 0 ? 90 : -90)); // +Z turned to face +X or -X
        } else if (axis == 1) {
            pose.mulPose(Axis.XP.rotationDegrees(sign > 0 ? -90 : 90)); // +Z turned to face up or down
        } else if (sign < 0) {
            pose.mulPose(Axis.YP.rotationDegrees(180));
        }
        float h = size / 2, w = size / 10, p = 1.2f / 16;
        box(pose, -h, -h, 0, h, -h + w, p, Mat.TRIM, light);
        box(pose, -h, h - w, 0, h, h, p, Mat.TRIM, light);
        box(pose, -h, -h + w, 0, -h + w, h - w, p, Mat.TRIM, light);
        box(pose, h - w, -h + w, 0, h, h - w, p, Mat.TRIM, light);
        box(pose, -h + w, -h + w, 0, h - w, h - w, 0.4f / 16, Mat.HULL, light);
        glow(pose, -0.3f * size, -0.3f * size, 0.5f / 16, 0.3f * size, 0.3f * size, 1.0f / 16, glowRgb, brightness);
        pose.popPose();
    }

    /** A skinned box centred on the pose origin, {@code w x h x d} in size, at offset {@code (x, y, z)}. */
    public void centred(PoseStack pose, float x, float y, float z, float w, float h, float d, Mat mat, int light) {
        box(pose, x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2, mat, light);
    }

    public void glowCentred(PoseStack pose, float x, float y, float z, float w, float h, float d, int rgb, float brightness) {
        glow(pose, x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2, rgb, brightness);
    }

    /**
     * A regular polygon of {@code n} box segments in the XY plane of the current pose, centred on its origin: apothem {@code a}
     * (centre to the middle of a side), each segment {@code thick} across and {@code depth} along Z, the first side turned by
     * {@code offset} radians from "flat on the right".
     */
    public void polygon(PoseStack pose, int n, float a, float thick, float depth, float offset, Mat mat, int light) {
        float len = (float) (2 * a * Math.tan(Math.PI / n)) + thick * 0.8f;
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) (i * 2 * Math.PI / n + offset)));
            centred(pose, a, 0, 0, thick, len, depth, mat, light);
            pose.popPose();
        }
    }

    /** A point on that polygon's outline at angle {@code th}. */
    public static float[] onPolygon(int n, float a, float th, float offset) {
        double k = 2 * Math.PI / n;
        double local = ((th - offset) % k + k) % k - k / 2;
        double r = a / Math.cos(local);
        return new float[]{(float) (Math.cos(th) * r), (float) (Math.sin(th) * r)};
    }

    private static float[] sp(float r, double phi, double theta) {
        return new float[]{(float) (r * Math.cos(phi) * Math.cos(theta)), (float) (r * Math.sin(phi)), (float) (r * Math.cos(phi) * Math.sin(theta))};
    }

    /**
     * The singularity: a sphere that is black where it faces the viewer and glows at its edge, deep violet into plasma blue, the way
     * light bends round a black hole, with a skin of swirling plasma over it that turns with {@code t}. {@code toCamera} is the
     * direction from the sphere's centre to the camera, in the pose's own frame; {@code heat} 0..1 is how lively it is.
     */
    public void blackHole(PoseStack pose, float r, Vec3 toCamera, float heat, float t) {
        int lat = 10, lon = 16;
        Vec3 eye = toCamera.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : toCamera.normalize();
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float uc = tu(Mat.PANEL, 0.5f), vc = tv(Mat.PANEL, 0.5f);
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * i / lat - Math.PI / 2, p1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double t0 = 2 * Math.PI * j / lon, t1 = 2 * Math.PI * (j + 1) / lon;
                float[][] c = {sp(r, p0, t0), sp(r, p1, t0), sp(r, p1, t1), sp(r, p0, t1)};
                for (int k = 0; k < 4; k++) {
                    float[] p = c[k];
                    double nx = p[0] / r, ny = p[1] / r, nz = p[2] / r;
                    double facing = Math.abs(nx * eye.x + ny * eye.y + nz * eye.z); // 1 toward or away from the viewer, 0 at the rim
                    float rim = (float) Math.pow(1 - facing, 3);
                    int colour = lerp(0x000000, 0x3A1E8C, Math.min(1f, rim * 1.4f));
                    colour = lerp(colour, 0x4FB8FF, (float) Math.pow(1 - facing, 9) * (0.5f + 0.5f * heat));
                    vertex(emissive, m, nm, p[0], p[1], p[2], colour, 255, uc, vc, LightTexture.FULL_BRIGHT, (float) nx, (float) ny, (float) nz);
                }
            }
        }
        // the plasma skin: the animated plasma texture on a slightly larger, see-through sphere, turning slowly
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(t * 0.6f));
        pose.mulPose(Axis.ZP.rotation(0.5f));
        m = pose.last().pose();
        nm = pose.last().normal();
        int skin = heat > 0.5f ? 0x9A7CFF : 0x5A3FB0; // the tint of the skin (the field of the same name is its buffer)
        int alpha = Math.round(70 + 90 * heat);
        float rs = r * 1.07f;
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * i / lat - Math.PI / 2, p1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double t0 = 2 * Math.PI * j / lon, t1 = 2 * Math.PI * (j + 1) / lon;
                float[][] c = {sp(rs, p0, t0), sp(rs, p1, t0), sp(rs, p1, t1), sp(rs, p0, t1)};
                float[] us = {plasma.getU(16f * j / lon), plasma.getU(16f * j / lon), plasma.getU(16f * (j + 1) / lon), plasma.getU(16f * (j + 1) / lon)};
                float[] vs = {plasma.getV(16f * i / lat), plasma.getV(16f * (i + 1) / lat), plasma.getV(16f * (i + 1) / lat), plasma.getV(16f * i / lat)};
                for (int k = 0; k < 4; k++) {
                    float[] p = c[k];
                    vertex(this.skin, m, nm, p[0], p[1], p[2], skin, alpha, us[k], vs[k], LightTexture.FULL_BRIGHT, p[0] / rs, p[1] / rs, p[2] / rs);
                }
            }
        }
        pose.popPose();
    }

    private static int lerp(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** A glowing flat ring in the XY plane of the current pose, from radius {@code r0} to {@code r1}. */
    public void glowAnnulus(PoseStack pose, float r0, float r1, int segments, int rgb) {
        for (int i = 0; i < segments; i++) {
            double t0 = 2 * Math.PI * i / segments, t1 = 2 * Math.PI * (i + 1) / segments;
            glowQuad(pose, new float[]{(float) (r0 * Math.cos(t0)), (float) (r0 * Math.sin(t0)), 0}, new float[]{(float) (r1 * Math.cos(t0)), (float) (r1 * Math.sin(t0)), 0},
                    new float[]{(float) (r1 * Math.cos(t1)), (float) (r1 * Math.sin(t1)), 0}, new float[]{(float) (r0 * Math.cos(t1)), (float) (r0 * Math.sin(t1)), 0}, rgb, 255);
        }
    }

    /** A glowing thin line between two points: two crossed quads, so it shows from every angle. */
    public void glowLine(PoseStack pose, float[] p, float[] q, float t, int rgb) {
        float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) {
            return;
        }
        float ax = Math.abs(dx) < 0.9f * len ? 1 : 0, ay = ax == 0 ? 1 : 0;
        float cx = -dz * ay, cy = dz * ax, cz = dx * ay - dy * ax;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        cx = cx / cl * t / 2;
        cy = cy / cl * t / 2;
        cz = cz / cl * t / 2;
        float ex = (dy * cz - dz * cy) / len, ey = (dz * cx - dx * cz) / len, ez = (dx * cy - dy * cx) / len;
        glowQuad(pose, new float[]{p[0] - cx, p[1] - cy, p[2] - cz}, new float[]{p[0] + cx, p[1] + cy, p[2] + cz}, new float[]{q[0] + cx, q[1] + cy, q[2] + cz}, new float[]{q[0] - cx, q[1] - cy, q[2] - cz}, rgb, 255);
        glowQuad(pose, new float[]{p[0] - ex, p[1] - ey, p[2] - ez}, new float[]{p[0] + ex, p[1] + ey, p[2] + ez}, new float[]{q[0] + ex, q[1] + ey, q[2] + ez}, new float[]{q[0] - ex, q[1] - ey, q[2] - ez}, rgb, 255);
    }

    /** The edges of a dodecahedron (30) or icosahedron (30) of circumradius 1, as pairs of points. */
    public static List<float[][]> edges(boolean dodecahedron) {
        List<float[]> v = new ArrayList<>();
        double phi = (1 + Math.sqrt(5)) / 2;
        if (dodecahedron) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        v.add(new float[]{sx, sy, sz});
                    }
                }
            }
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    v.add(new float[]{0, (float) (s1 / phi), (float) (s2 * phi)});
                    v.add(new float[]{(float) (s1 / phi), (float) (s2 * phi), 0});
                    v.add(new float[]{(float) (s1 * phi), 0, (float) (s2 / phi)});
                }
            }
        } else {
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    v.add(new float[]{0, s1, (float) (s2 * phi)});
                    v.add(new float[]{s1, (float) (s2 * phi), 0});
                    v.add(new float[]{(float) (s1 * phi), 0, s2});
                }
            }
        }
        double edge = dodecahedron ? 2 / phi : 2, circ = dodecahedron ? Math.sqrt(3) : Math.sqrt(1 + phi * phi);
        List<float[][]> out = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) {
            for (int j = i + 1; j < v.size(); j++) {
                float[] a = v.get(i), b = v.get(j);
                double d = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
                if (Math.abs(d - edge) < 1e-3) {
                    float k = (float) (1 / circ);
                    out.add(new float[][]{{a[0] * k, a[1] * k, a[2] * k}, {b[0] * k, b[1] * k, b[2] * k}});
                }
            }
        }
        return out;
    }
}
