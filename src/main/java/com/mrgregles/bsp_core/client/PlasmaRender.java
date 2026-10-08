package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws Wave Plasma inside a tank for block entity renderers: the Projector Base now, a multiblock tank later.
 * Everything is in sixteenths of a block, in the pose the caller gives. The plasma writes depth, so a tank's own
 * inner walls behind it cannot paint over it, and it is drawn full bright so it glows in the dark.
 */
public final class PlasmaRender {
    private static final ResourceLocation PLASMA = new ResourceLocation(BSPCore.MODID, "block/plasma_still");

    private PlasmaRender() {}

    public static TextureAtlasSprite sprite() {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLASMA);
    }

    /** The buffer plasma is drawn into: translucent, lit by the caller, writing depth, flushed before the world's glass is drawn over it. */
    public static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
    }

    /**
     * Fills the box {@code x0..x1, y0..y1, z0..z1} from the floor up to {@code frac} of its height, in sixteenths.
     * The top slice is a touch brighter so the surface reads as a level line from any angle.
     */
    public static void tank(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, float frac) {
        if (frac <= 0.004f) {
            return;
        }
        float top = y0 + (y1 - y0) * Math.min(1f, frac), lip = Math.min(0.4f, top - y0);
        box(vc, pose, sprite, x0, y0, z0, x1, top - lip, z1, 250, 255);
        box(vc, pose, sprite, x0, top - lip, z0, x1, top, z1, 255, 255);
    }

    /** A solid plasma box, for nozzle windows and the like. */
    public static void box(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, int alpha, int shade) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float u0 = sprite.getU(0), u1 = sprite.getU(16), v0 = sprite.getV(0), v1 = sprite.getV(16);
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            int c = f[13] > 0 ? 255 : shade; // the top face keeps its full colour; the sides sit a touch darker so the edges of the body read
            vertex(vc, m, nm, f, 0, u0, v1, c, alpha);
            vertex(vc, m, nm, f, 3, u1, v1, c, alpha);
            vertex(vc, m, nm, f, 6, u1, v0, c, alpha);
            vertex(vc, m, nm, f, 9, u0, v0, c, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] f, int i, float u, float v, int c, int a) {
        vc.vertex(m, f[i], f[i + 1], f[i + 2]).color(c, c, c, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
    }
}
