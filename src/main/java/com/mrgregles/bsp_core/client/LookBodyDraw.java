package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.totem.TotemOrbLayout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a look's body and head: the Guardian's eight cuboids, exactly where the baked model has them (its bare variant leaves
 * them out), skinned with the look's texture on the entity cutout layer, lit by the world. Shader packs draw that layer as they
 * draw mobs, so the body looks the same with shaders on and off. While the totem is being stolen it pulses red at the baked
 * steal texture's pace.
 */
public final class LookBodyDraw {
    private LookBodyDraw() {}

    /** How red the steal pulse is now, 0 to 1: the baked steal texture's 8 frames of 2 ticks, gold to red and back. */
    public static float stealPulse(float time) {
        return 0.5f - 0.5f * (float) Math.cos(time * Math.PI * 2 / 16f);
    }

    /**
     * Draws the body in block space (0..1), turned to {@code facing} as the blockstate turns the model.
     * {@code steal} is the red pulse, 0 for none.
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, TotemLook look, Direction facing, int light, float steal,
                            @javax.annotation.Nullable net.minecraft.resources.ResourceLocation skin) {
        draw(pose, buffers, LookTextures.get(look), facing, light, steal, skin);
    }

    /** The same, with the look's texture given (the paint bench's own). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, net.minecraft.resources.ResourceLocation texture, Direction facing, int light, float steal,
                            @javax.annotation.Nullable net.minecraft.resources.ResourceLocation skin) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        int r = 255, g = Math.round(255 * (1 - 0.55f * steal)), b = Math.round(255 * (1 - 0.6f * steal));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-TotemOrbLayout.modelTurn(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (LookLayout.Part p : LookLayout.PARTS) {
            boolean head = p.name().equals("head");
            if (head && skin != null) {
                continue; // drawn last: fetching the skin's render type ends this texture's batch, so nothing may follow it in vc
            }
            if (head) {
                pose.pushPose();
                pose.translate(LookLayout.HEAD_PIVOT[0], LookLayout.HEAD_PIVOT[1], LookLayout.HEAD_PIVOT[2]);
                pose.mulPose(Axis.XP.rotationDegrees(LookLayout.HEAD_ANGLE));
                pose.translate(-LookLayout.HEAD_PIVOT[0], -LookLayout.HEAD_PIVOT[1], -LookLayout.HEAD_PIVOT[2]);
            }
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            for (LookLayout.Face f : LookLayout.Face.values()) {
                int[] d = LookLayout.dims(p, f), at = LookLayout.at(p, f);
                float[] o = LookLayout.origin(p, f), u = LookLayout.uDir(f), v = LookLayout.vDir(f);
                float u0 = at[0] / 64f, v0 = at[1] / 64f, u1 = (at[0] + d[0]) / 64f, v1 = (at[1] + d[1]) / 64f;
                // corners: texel (0,0)'s corner, then down the left edge, across the bottom, up the right
                vertex(vc, m, nm, o, u, v, 0, 0, u0, v0, r, g, b, light, f);
                vertex(vc, m, nm, o, u, v, 0, d[1], u0, v1, r, g, b, light, f);
                vertex(vc, m, nm, o, u, v, d[0], d[1], u1, v1, r, g, b, light, f);
                vertex(vc, m, nm, o, u, v, d[0], 0, u1, v0, r, g, b, light, f);
            }
            if (head) {
                pose.popPose();
            }
        }
        if (skin != null) {
            skinHead(pose, buffers, LookLayout.PARTS[0], skin, r, g, b, light);
        }
        pose.popPose();
    }

    /**
     * Where a player skin keeps each face of its head (64 by 64 skin, base layer; the hat layer is 32 to the right), and whether the
     * skin's picture runs the other way to the look texture's (the top is drawn from above with the face toward the bottom).
     */
    private static int[] skinRect(LookLayout.Face f) {
        return switch (f) {
            case EAST -> new int[]{0, 8, 0};
            case NORTH -> new int[]{8, 8, 0};
            case WEST -> new int[]{16, 8, 0};
            case SOUTH -> new int[]{24, 8, 0};
            case UP -> new int[]{8, 0, 1};
            case DOWN -> new int[]{16, 0, 2};
        };
    }

    /** The owner's own head: the skin's head on the totem's (both 8 by 8 by 8), then the skin's hat layer half a pixel out. */
    private static void skinHead(PoseStack pose, MultiBufferSource buffers, LookLayout.Part p, net.minecraft.resources.ResourceLocation skin,
                                 int r, int g, int b, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(skin)); // as player head blocks draw it
        pose.pushPose();
        pose.translate(LookLayout.HEAD_PIVOT[0], LookLayout.HEAD_PIVOT[1], LookLayout.HEAD_PIVOT[2]);
        pose.mulPose(Axis.XP.rotationDegrees(LookLayout.HEAD_ANGLE));
        pose.translate(-LookLayout.HEAD_PIVOT[0], -LookLayout.HEAD_PIVOT[1], -LookLayout.HEAD_PIVOT[2]);
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int layer = 0; layer < 2; layer++) {
            float grow = layer * 0.5f;
            for (LookLayout.Face f : LookLayout.Face.values()) {
                int[] d = LookLayout.dims(p, f), rect = skinRect(f);
                float[] o = LookLayout.origin(p, f).clone(), u = LookLayout.uDir(f), v = LookLayout.vDir(f);
                // grow the face outward by half a pixel on every side for the hat layer
                float[] n = {f.nx, f.ny, f.nz};
                for (int k = 0; k < 3; k++) {
                    o[k] += grow * (n[k] - u[k] - v[k]);
                }
                float s = (d[0] + 2 * grow) / d[0];
                float[] us = {u[0] * s, u[1] * s, u[2] * s}, vs = {v[0] * s, v[1] * s, v[2] * s};
                float ua = (rect[0] + layer * 32) / 64f, va = rect[1] / 64f, ub = (rect[0] + layer * 32 + 8) / 64f, vb = (rect[1] + 8) / 64f;
                if (rect[2] >= 1) { // drawn the other way round in the skin: the top both ways, the underside left to right
                    float t = ua; ua = ub; ub = t;
                }
                if (rect[2] == 1) {
                    float t = va; va = vb; vb = t;
                }
                vertex(vc, m, nm, o, us, vs, 0, 0, ua, va, r, g, b, light, f);
                vertex(vc, m, nm, o, us, vs, 0, d[1], ua, vb, r, g, b, light, f);
                vertex(vc, m, nm, o, us, vs, d[0], d[1], ub, vb, r, g, b, light, f);
                vertex(vc, m, nm, o, us, vs, d[0], 0, ub, va, r, g, b, light, f);
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] o, float[] u, float[] v, int i, int j,
                               float tu, float tv, int r, int g, int b, int light, LookLayout.Face f) {
        vc.vertex(m, o[0] + i * u[0] + j * v[0], o[1] + i * u[1] + j * v[1], o[2] + i * u[2] + j * v[2])
                .color(r, g, b, 255).uv(tu, tv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, f.nx, f.ny, f.nz).endVertex();
    }
}
