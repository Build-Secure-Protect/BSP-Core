package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.bank.DepositBox;
import com.mrgregles.bsp_core.bank.SafetyDepositBoxBlock;
import com.mrgregles.bsp_core.bank.SafetyDepositBoxBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Draws the viewer's own state on a Safety Deposit Box: the middle drawer's label plate and the lamp
 * strip turn amber while the box is rented and red when the rent has run out, with a red tag on the
 * handle; nothing lights for a player who has never rented. Every player sees their own box.
 */
public class SafetyDepositBoxRenderer implements BlockEntityRenderer<SafetyDepositBoxBlockEntity> {
    private static final int AMBER = 0xFFF2B544, RED = 0xFFE24B3B;

    public SafetyDepositBoxRenderer(BlockEntityRendererProvider.Context ctx) {}

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
    public void render(SafetyDepositBoxBlockEntity box, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (box.getLevel() == null || !box.getBlockState().hasProperty(SafetyDepositBoxBlock.FACING)) {
            return;
        }
        long now = System.currentTimeMillis();
        DepositBox.State state = ClientBank.boxSlots <= 0 ? DepositBox.State.NONE : ClientBank.boxUntilMs > now ? DepositBox.State.OPEN : DepositBox.State.EXPIRED;
        if (state == DepositBox.State.NONE) {
            return;
        }
        int colour = state == DepositBox.State.OPEN ? AMBER : RED;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(box.getBlockState().getValue(SafetyDepositBoxBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        Matrix4f m = pose.last().pose();
        float y = (1.2f + 4.7f) / 16;   // the middle drawer
        quad(vc, m, 2.3f / 16, y + 2.85f / 16, 5.7f / 16, y + 3.7f / 16, -0.12f / 16, colour);   // label plate
        quad(vc, m, 15.3f / 16, 1f / 16, 15.8f / 16, 15f / 16, -0.32f / 16, colour);            // lamp strip
        if (state == DepositBox.State.EXPIRED) {
            quad(vc, m, 6.2f / 16, y + 0.3f / 16, 7.4f / 16, y + 1.6f / 16, -0.9f / 16, RED);    // the tag on the handle
        }
        pose.popPose();
    }
}
