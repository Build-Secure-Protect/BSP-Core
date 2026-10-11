package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.BankAtmBlock;
import com.mrgregles.bsp_core.bank.BankAtmBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the live parts of a Bank ATM on its lower half: the phosphor text on the screen (the idle
 * sign, or the in-use lines with a blinking cursor), the turning amber beacon on the crown and the
 * pulse of the coin hopper's slit. The screen is a 9 x 8.5 pixel window on the front face at
 * block pixels x 3.5..12.5, y 17.5..26 of the two-block model.
 */
public class BankAtmRenderer implements BlockEntityRenderer<BankAtmBlockEntity> {
    private static final int PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544, AMBER_DIM = 0x80F2B544;
    private final Font font;

    public BankAtmRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

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
    public void render(BankAtmBlockEntity atm, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (atm.getLevel() == null || !atm.getBlockState().hasProperty(BankAtmBlock.FACING)) {
            return;
        }
        float t = (atm.getLevel().getGameTime() + partial);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(atm.getBlockState().getValue(BankAtmBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());

        // beacon: a turning amber cross above the crown
        pose.pushPose();
        pose.translate(0.5, 31.8 / 16, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((t * 4f) % 360f));
        Matrix4f bm = pose.last().pose();
        quad(vc, bm, -0.1f, 0, 0.1f, 1.5f / 16, 0, AMBER);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        quad(vc, pose.last().pose(), -0.1f, 0, 0.1f, 1.5f / 16, 0, AMBER);
        pose.popPose();

        // hopper slit pulse: a soft amber glow over the hopper lip, breathing
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.25f);
        int glow = ((int) (pulse * 0x70) << 24) | 0xF2B544;
        quad(vc, pose.last().pose(), 4f / 16, 13.9f / 16, 12f / 16, 14.6f / 16, -0.01f, glow);

        // screen text: canvas of 36 x 34 units over the 9 x 8.5 pixel window, y down
        pose.pushPose();
        pose.translate(3.5 / 16, 26 / 16.0, 0.78 / 16);
        pose.scale(1 / 64f, -1 / 64f, 1 / 64f);
        boolean busy = atm.inUse();
        boolean blink = ((int) (t / 8)) % 2 == 0;
        if (busy) {
            line(pose, buffers, "> ACCOUNT", 2, 4, 1.2f, PHOS);
            line(pose, buffers, "OPEN", 2, 11, 1.2f, PHOS);
            line(pose, buffers, "- - - - -", 2, 19, 1f, DIM);
            line(pose, buffers, blink ? "WORKING _" : "WORKING", 2, 27, 1f, DIM);
        } else {
            line(pose, buffers, "SHATTER", 2, 4, 1.25f, PHOS);
            line(pose, buffers, "BANK", 2, 12, 1.25f, PHOS);
            line(pose, buffers, BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "local").toUpperCase(java.util.Locale.ROOT), 2, 20, 0.9f, DIM);
            if (blink) {
                line(pose, buffers, "INSERT COINS", 2, 27, 0.9f, AMBER);
            }
        }
        pose.popPose();
        pose.popPose();
    }

    /** One line of text at canvas (x, y), {@code k} times the font's natural size, shrunk to the window if needed. */
    private void line(PoseStack pose, MultiBufferSource buffers, String s, float x, float y, float k, int colour) {
        Component c = Component.literal(s);
        float w = font.width(c);
        float maxW = 34 - x;
        if (w * k > maxW) {
            k = maxW / w;
        }
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(k, k, 1f);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
