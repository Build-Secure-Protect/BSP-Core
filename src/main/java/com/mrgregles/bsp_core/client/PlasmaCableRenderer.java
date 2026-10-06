package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The plasma inside a cable, seen through the pipe's glass: a level that rises with what is flowing
 * (full at the projector's need), the moving plasma texture, and bright pulses that travel the way
 * the plasma is going. An idle cable shows a thin trace, or nothing after a while.
 */
public class PlasmaCableRenderer implements BlockEntityRenderer<PlasmaCableBlockEntity> {
    private static final ResourceLocation PLASMA = new ResourceLocation(BSPCore.MODID, "block/plasma_still");
    private static final float LO = 6.3f, HI = 9.7f; // the cavity inside the 5..11 px pipe, in model pixels

    public PlasmaCableRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PlasmaCableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (cable.getLevel() == null) {
            return;
        }
        int flow = cable.flow();
        if (flow <= 0) {
            return;
        }
        float t = (cable.getLevel().getGameTime() + partialTick) / 20f;
        float frac = Mth.clamp(flow / (float) Math.max(1, BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100)), 0.12f, 1f);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLASMA);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        BlockState state = cable.getBlockState();
        float h = LO + (HI - LO) * frac; // the level, measured from the bottom of the cavity
        // the core, then each joined arm out to the block edge
        liquid(vc, pose, sprite, LO, LO, LO, HI, h, HI, 0.85f);
        for (Direction d : Direction.values()) {
            if (!state.getValue(TotemCableBlock.SIDES[d.get3DDataValue()])) {
                continue;
            }
            float[] b = arm(d, h);
            liquid(vc, pose, sprite, b[0], b[1], b[2], b[3], b[4], b[5], 0.85f);
        }
        // pulses run from the way in to the way out
        Direction out = cable.out(), in = cable.in();
        if (out != null) {
            for (int i = 0; i < 2; i++) {
                float k = (t * 0.9f + i * 0.5f) % 1f;
                float[] p = along(in, out, k);
                float c = (LO + h) / 2f, r = 0.9f + 0.5f * frac;
                liquid(vc, pose, sprite, p[0] - r, Math.max(LO, c - r), p[2] - r, p[0] + r, Math.min(h, c + r), p[2] + r, 1f, p[1]);
            }
        }
        pose.popPose();
    }

    /** The cavity of the arm in direction {@code d}, filled to level {@code h} (vertical arms fill to a width instead). */
    private static float[] arm(Direction d, float h) {
        return switch (d) {
            case NORTH -> new float[]{LO, LO, 0, HI, h, LO};
            case SOUTH -> new float[]{LO, LO, HI, HI, h, 16};
            case WEST -> new float[]{0, LO, LO, LO, h, HI};
            case EAST -> new float[]{HI, LO, LO, 16, h, HI};
            case UP -> new float[]{8 - (h - LO) / 2, HI, 8 - (h - LO) / 2, 8 + (h - LO) / 2, 16, 8 + (h - LO) / 2};
            case DOWN -> new float[]{8 - (h - LO) / 2, 0, 8 - (h - LO) / 2, 8 + (h - LO) / 2, LO, 8 + (h - LO) / 2};
        };
    }

    /** A point {@code k} of the way through the cable, entering from {@code in} (or the centre) and leaving by {@code out}. Returns x, y, z. */
    private static float[] along(Direction in, Direction out, float k) {
        float[] a = in == null ? new float[]{8, 8, 8} : new float[]{8 + in.getStepX() * 8, 8 + in.getStepY() * 8, 8 + in.getStepZ() * 8};
        float[] b = {8 + out.getStepX() * 8, 8 + out.getStepY() * 8, 8 + out.getStepZ() * 8};
        if (k < 0.5f) {
            float s = k * 2;
            return new float[]{a[0] + (8 - a[0]) * s, a[1] + (8 - a[1]) * s, a[2] + (8 - a[2]) * s};
        }
        float s = (k - 0.5f) * 2;
        return new float[]{8 + (b[0] - 8) * s, 8 + (b[1] - 8) * s, 8 + (b[2] - 8) * s};
    }

    private static void liquid(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, float alpha) {
        liquid(vc, pose, sprite, x0, y0, z0, x1, y1, z1, alpha, Float.NaN);
    }

    /** A box of plasma; the sprite is animated by itself. When {@code bright} is a number the box is drawn whiter, for a pulse. */
    private static void liquid(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, float alpha, float bright) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int c = Float.isNaN(bright) ? 255 : 255, a = Math.round(alpha * 255);
        float u0 = sprite.getU(0), u1 = sprite.getU(16), v0 = sprite.getV(0), v1 = sprite.getV(16);
        if (!Float.isNaN(bright)) { // a pulse: a small patch of the brightest part of the texture
            u0 = sprite.getU(6);
            u1 = sprite.getU(10);
            v0 = sprite.getV(6);
            v1 = sprite.getV(10);
        }
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            vc.vertex(m, f[0], f[1], f[2]).color(c, c, c, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[3], f[4], f[5]).color(c, c, c, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[6], f[7], f[8]).color(c, c, c, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[9], f[10], f[11]).color(c, c, c, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
        }
    }
}
