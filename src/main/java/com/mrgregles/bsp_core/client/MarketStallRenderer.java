package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.shop.MarketStallBlock;
import com.mrgregles.bsp_core.shop.MarketStallBlockEntity;
import com.mrgregles.bsp_core.shop.StallSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws the live parts of a Market Stall on its lower half, at the points the skin's model reserves
 * for them: the stock lamp (green while the shop has stock, red when sold out), the open lamp (amber
 * while open, nothing while closed) and the shop's first item turning on the counter. A broken stall
 * (no shop) draws nothing.
 */
public class MarketStallRenderer implements BlockEntityRenderer<MarketStallBlockEntity> {
    private static final int GREEN = 0xFF58F29A, RED = 0xFFE24B3B, AMBER = 0xFFF2B544;

    public MarketStallRenderer(BlockEntityRendererProvider.Context ctx) {}

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

    /** A lamp face given as x0, y0, x1, y1, z in model units, drawn a little in front (towards -z). */
    private static void lamp(VertexConsumer vc, Matrix4f m, float[] f, int argb) {
        quad(vc, m, f[0] / 16, f[1] / 16, f[2] / 16, f[3] / 16, f[4] / 16 - 0.015f, argb);
    }

    @Override
    public void render(MarketStallBlockEntity stall, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = stall.getBlockState();
        if (stall.getLevel() == null || !state.hasProperty(MarketStallBlock.FACING) || state.getValue(MarketStallBlock.BROKEN)) {
            return;
        }
        StallSkin skin = state.getValue(MarketStallBlock.SKIN);
        float t = stall.getLevel().getGameTime() + partial;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(state.getValue(MarketStallBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        Matrix4f m = pose.last().pose();
        lamp(vc, m, skin.stockLamp, stall.stocked ? GREEN : RED);
        if (stall.open) {
            lamp(vc, m, skin.openLamp, AMBER);
        }
        if (!stall.shown.isEmpty()) {
            pose.pushPose();
            pose.translate(skin.item[0] / 16, skin.item[1] / 16, skin.item[2] / 16);
            pose.mulPose(Axis.YP.rotationDegrees((t * 1.5f) % 360f));
            pose.scale(skin.itemScale, skin.itemScale, skin.itemScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stall.shown, ItemDisplayContext.NONE, light, overlay, pose, buffers, stall.getLevel(), 0);
            pose.popPose();
        }
        pose.popPose();
    }
}
