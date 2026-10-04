package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Builds machines out of textured boxes at render time, in model pixels (16 per block), mirroring
 * the kit used for the approved concept renders so the in-game machines match them. Materials are
 * tiles of {@code machine_atlas.png}; glow materials ignore world light.
 */
public final class MachineKit {
    /** Tile origin in atlas pixels (64 px atlas) and whether the material glows. */
    public enum Mat {
        HULL(0, 0, false), HULL2(16, 0, false), PANEL(32, 0, false), MID(48, 0, false), TRIM(0, 16, false),
        VIOLET(16, 16, true), TURQ(32, 16, true), ORANGE(48, 16, true);

        final int ox, oy;
        final boolean glow;

        Mat(int ox, int oy, boolean glow) {
            this.ox = ox;
            this.oy = oy;
            this.glow = glow;
        }
    }

    private static final ResourceLocation ATLAS = new ResourceLocation(BSPCore.MODID, "block/machine_atlas");
    private final VertexConsumer vc;
    private final TextureAtlasSprite sprite;
    private final PoseStack pose;
    private final int light;

    public MachineKit(PoseStack pose, MultiBufferSource buffers, int light) {
        this.pose = pose;
        this.light = light;
        this.vc = buffers.getBuffer(RenderType.cutout());
        this.sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS);
    }

    public void box(float x0, float y0, float z0, float x1, float y1, float z1, Mat mat) {
        quads(x0, y0, z0, x1, y1, z1, mat, 0xFFFFFF, mat.glow ? LightTexture.FULL_BRIGHT : light);
    }

    /** A glowing box in an arbitrary colour at a brightness of 0..1. */
    public void glow(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k)), b = Math.min(255, Math.round((rgb & 0xFF) * k));
        quads(x0, y0, z0, x1, y1, z1, Mat.VIOLET, (r << 16) | (g << 8) | b, LightTexture.FULL_BRIGHT, true);
    }

    /** A see-through box (glass, water). Drawn on the translucent layer. */
    public void translucent(MultiBufferSource buffers, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha) {
        VertexConsumer tv = buffers.getBuffer(RenderType.translucent());
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF, a = Math.round(alpha * 255);
        float u = sprite.getU((Mat.VIOLET.ox + 7) / 4f), v = sprite.getV((Mat.VIOLET.oy + 7) / 4f);
        float[][] faces = {
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1}, {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1},
                {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0}, {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0}};
        for (float[] q : faces) {
            for (int k = 0; k < 4; k++) {
                tv.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(r, g, b, a).uv(u, v).uv2(LightTexture.FULL_BRIGHT).normal(nm, q[12], q[13], q[14]).endVertex();
            }
        }
    }

    public void walls(float x0, float y0, float z0, float x1, float y1, float z1, float t, Mat mat) {
        box(x0, y0, z0, x1, y1, z0 + t, mat);
        box(x0, y0, z1 - t, x1, y1, z1, mat);
        box(x0, y0, z0 + t, x0 + t, y1, z1 - t, mat);
        box(x1 - t, y0, z0 + t, x1, y1, z1 - t, mat);
    }

    public void glowWalls(float x0, float y0, float z0, float x1, float y1, float z1, float t, int rgb, float k) {
        glow(x0, y0, z0, x1, y1, z0 + t, rgb, k);
        glow(x0, y0, z1 - t, x1, y1, z1, rgb, k);
        glow(x0, y0, z0 + t, x0 + t, y1, z1 - t, rgb, k);
        glow(x1 - t, y0, z0 + t, x1, y1, z1 - t, rgb, k);
    }

    /** Raised plates on the four sides of a box, to break up flat faces. */
    public void plates(float x0, float y0, float z0, float x1, float y1, float z1, float inset, Mat mat) {
        float e = 0.45f;
        box(x0 + inset, y0 + inset, z0 - e, x1 - inset, y1 - inset, z0, mat);
        box(x0 + inset, y0 + inset, z1, x1 - inset, y1 - inset, z1 + e, mat);
        box(x0 - e, y0 + inset, z0 + inset, x0, y1 - inset, z1 - inset, mat);
        box(x1, y0 + inset, z0 + inset, x1 + e, y1 - inset, z1 - inset, mat);
    }

    /** Glowing strips on the four vertical corners of a box. */
    public void corners(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float k) {
        float e = 0.35f, w = 0.9f;
        float[][] at = {{x0 - e, z0 - e}, {x1 - w + e, z0 - e}, {x0 - e, z1 - w + e}, {x1 - w + e, z1 - w + e}};
        for (float[] c : at) {
            glow(c[0], y0, c[1], c[0] + w, y1, c[1] + w, rgb, k);
        }
    }

    private void quads(float x0, float y0, float z0, float x1, float y1, float z1, Mat mat, int rgb, int lightmap) {
        quads(x0, y0, z0, x1, y1, z1, mat, rgb, lightmap, false);
    }

    private void quads(float x0, float y0, float z0, float x1, float y1, float z1, Mat mat, int rgb, int lightmap, boolean flat) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float w = x1 - x0, h = y1 - y0, d = z1 - z0;
        // each face: 4 corners, normal, and its size in pixels for the texture window
        float[][] faces = {
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, w, h},
                {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, w, h},
                {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, d, h},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, d, h},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, w, d},
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, w, d},
        };
        for (float[] q : faces) {
            float fw = flat ? 1 : Math.min(16f, Math.max(1f, q[15])), fh = flat ? 1 : Math.min(16f, Math.max(1f, q[16]));
            float u0 = sprite.getU((mat.ox + (flat ? 7 : 0)) / 4f), u1 = sprite.getU((mat.ox + (flat ? 7 : 0) + fw) / 4f);
            float v0 = sprite.getV((mat.oy + (flat ? 7 : 0)) / 4f), v1 = sprite.getV((mat.oy + (flat ? 7 : 0) + fh) / 4f);
            float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(r, g, b, 255).uv(uv[k][0], uv[k][1]).uv2(lightmap).normal(nm, q[12], q[13], q[14]).endVertex();
            }
        }
    }
}
