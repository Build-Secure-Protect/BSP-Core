package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.land.LandOfficeBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

/**
 * The live parts of the Land Office's Deeds Board: three amber deed cards pinned to the board, which
 * flutter while someone has the screen open; the lamp over the board pulses and the plot squares on
 * the map blink in turn at the same time. Still otherwise.
 */
public class LandOfficeRenderer implements BlockEntityRenderer<LandOfficeBlockEntity> {
    private static final int AMBER = 0xFFF2B544, INK = 0xFF061008, SHADE = 0xA0061008;
    /** Card centres on the board face, model units (x, y), and their resting tilt. */
    private static final float[][] CARDS = {{4f, 11.6f, -0.08f}, {11f, 12.6f, 0.1f}, {7.5f, 8.7f, 0.05f}};
    /** The map's plot squares (x0, y0, x1, y1 on the board face), hidden one at a time while in use. */
    private static final float[][] PLOTS = {{3.1f, 12.2f, 6.9f, 14.1f}, {8.6f, 11.9f, 12.4f, 13.5f}, {4.2f, 8.3f, 7.4f, 10.1f}, {9f, 8f, 12.8f, 10.2f}};

    public LandOfficeRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[][] q = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] p = q[pass == 0 ? i : 3 - i];
                vc.vertex(m, p[0], p[1], z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
            }
        }
    }

    @Override
    public void render(LandOfficeBlockEntity office, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (office.getLevel() == null || !office.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        boolean busy = office.inUse();
        float t = office.getLevel().getGameTime() + partial;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(office.getBlockState().getValue(HorizontalDirectionalBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        float face = 9.4f / 16 - 0.02f; // just in front of the board's map face
        // the deed cards: 3 x 2.2 units of amber with two ink lines and a red pin
        for (int i = 0; i < CARDS.length; i++) {
            float[] c = CARDS[i];
            float tilt = c[2] + (busy ? Mth.sin(t * 0.09f + i) * 0.04f : 0f);
            pose.pushPose();
            pose.translate(c[0] / 16, c[1] / 16, face);
            pose.mulPose(Axis.ZP.rotation(tilt));
            Matrix4f m = pose.last().pose();
            quad(vc, m, -1.5f / 16, -1.1f / 16, 1.5f / 16, 1.1f / 16, 0, AMBER);
            quad(vc, m, -1.1f / 16, 0.25f / 16, 1.1f / 16, 0.45f / 16, -0.004f, INK);
            quad(vc, m, -1.1f / 16, -0.3f / 16, 0.4f / 16, -0.1f / 16, -0.004f, INK);
            quad(vc, m, -0.2f / 16, 0.9f / 16, 0.2f / 16, 1.3f / 16, -0.006f, 0xFFE24B3B);
            pose.popPose();
        }
        Matrix4f m = pose.last().pose();
        if (busy) {
            // one plot square dark in turn, so the map reads as alive; the lamp breathes
            int off = (int) (t / 10) % PLOTS.length;
            float[] p = PLOTS[off];
            quad(vc, m, p[0] / 16, p[1] / 16, p[2] / 16, p[3] / 16, 9.3f / 16 - 0.01f, SHADE);
            float pulse = 0.5f + 0.5f * Mth.sin(t * 0.3f);
            int glow = ((int) (pulse * 0x80) << 24) | 0xF2B544;
            quad(vc, m, 6.8f / 16, 14.5f / 16, 9.2f / 16, 15.5f / 16, 5.8f / 16 - 0.01f, glow);
        }
        pose.popPose();
    }
}
