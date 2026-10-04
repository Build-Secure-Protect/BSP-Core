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
 * Draws flat-coloured boxes from block entity renderers, for the moving and glowing parts of the
 * machines. Coordinates are in blocks. Uses the white tile of the coin factory atlas so the tint is
 * the colour.
 */
public final class BoxDraw {
    private static final ResourceLocation WHITE_ATLAS = new ResourceLocation(BSPCore.MODID, "block/shatter_coin_factory");

    private final VertexConsumer vc;
    private final float u, v;

    public BoxDraw(MultiBufferSource buffers) {
        this.vc = buffers.getBuffer(RenderType.cutout());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WHITE_ATLAS);
        this.u = sprite.getU(6);
        this.v = sprite.getV(6);
    }

    /** A glowing box that ignores world light. */
    public void glow(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float brightness) {
        box(pose, x0, y0, z0, x1, y1, z1, scale(rgb, brightness), LightTexture.FULL_BRIGHT);
    }

    /** A solid box lit by the world. */
    public void solid(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        box(pose, x0, y0, z0, x1, y1, z1, rgb, light);
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k)), b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    private void box(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float[][] quads = {
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1},
                {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1},
                {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0},
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0},
        };
        for (float[] q : quads) {
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(r, g, b, 255).uv(u, v).uv2(light).normal(nm, q[12], q[13], q[14]).endVertex();
            }
        }
    }

    /** Four bars forming a square ring centred on the current pose origin. */
    public void ring(PoseStack pose, float half, float y0, float y1, float t, int rgb, float brightness) {
        glow(pose, -half, y0, -half, half, y1, -half + t, rgb, brightness);
        glow(pose, -half, y0, half - t, half, y1, half, rgb, brightness);
        glow(pose, -half, y0, -half + t, -half + t, y1, half - t, rgb, brightness);
        glow(pose, half - t, y0, -half + t, half, y1, half - t, rgb, brightness);
    }
}
