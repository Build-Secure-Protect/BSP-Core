package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.looks.HatModels;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.looks.LookPixels;
import com.mrgregles.bsp_core.totem.TotemOrbLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Draws a totem's hat on its bowed head, plain or dressed: the hat's mesh in flat colours on one white texel of the totem atlas,
 * on the entity cutout layer lit by the world (as the body), and its glowing parts on the opaque beacon beam layer, full bright and
 * writing depth. In the body's colours the "main" cloth takes {@code tint} and the "trim" a darker shade of it.
 */
public final class HatDraw {
    private HatDraw() {}

    private static final ResourceLocation ATLAS_TEX = new ResourceLocation(BSPCore.MODID, "block/shatter_totem_owned");
    private static final RenderType GLOW = RenderType.beaconBeam(InventoryMenu.BLOCK_ATLAS, false);

    /** A role's colour, with the body's colour put in when {@code tint} is given. */
    private static int colour(HatModels.Hat hat, String role, @Nullable Integer tint) {
        if (tint != null) {
            switch (role) {
                case "main" -> {
                    return tint;
                }
                case "main2" -> {
                    return LookPixels.mix(tint, 0xFFFFFF, 0.65f);
                }
                case "trim" -> {
                    return LookPixels.shade(tint, 0.6f);
                }
                case "trim2" -> {
                    return LookPixels.shade(tint, 0.45f);
                }
                default -> {
                }
            }
        }
        return hat.colours().getOrDefault(role, 0xFF00FF);
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, String hatId, Direction facing, @Nullable Integer tint, int light, float time) {
        HatModels.Hat hat = HatModels.get(hatId);
        if (hat == null || hat.quads().isEmpty()) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS_TEX);
        float u = sprite.getU(14.5f), v = sprite.getV(0.5f); // the white orblet texel
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-TotemOrbLayout.modelTurn(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(LookLayout.HEAD_PIVOT[0], LookLayout.HEAD_PIVOT[1], LookLayout.HEAD_PIVOT[2]);
        pose.mulPose(Axis.XP.rotationDegrees(LookLayout.HEAD_ANGLE));
        // the hat's own tilt, round the top of the head
        pose.translate(0, 8, 0);
        pose.mulPose(Axis.XP.rotation(hat.tiltX()));
        pose.mulPose(Axis.ZP.rotation(hat.tiltZ()));
        pose.translate(0, -8, 0);
        float seconds = time / 20f;
        float bob = (float) Math.sin(seconds * 2) * 0.35f, spin = seconds * 7;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (HatModels.Quad q : hat.quads()) {
            if (!q.glow()) {
                quad(solid, m, nm, q, colour(hat, q.role(), tint), u, v, light, bob, spin, false);
            }
        }
        VertexConsumer glow = buffers.getBuffer(GLOW); // fetched just before writing: the beacon types share one buffer
        for (HatModels.Quad q : hat.quads()) {
            if (q.glow()) {
                quad(glow, m, nm, q, colour(hat, q.role(), tint), u, v, LightTexture.FULL_BRIGHT, bob, spin, true);
            }
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, HatModels.Quad q, int rgb, float u, float v, int light,
                             float bob, float spin, boolean glow) {
        float[] p = q.v().clone();
        for (int k = 0; k < 4; k++) {
            if (q.motion() == HatModels.Motion.BOB) {
                p[k * 3 + 1] += bob;
            } else if (q.motion() == HatModels.Motion.SPIN) {
                float x = p[k * 3], z = p[k * 3 + 2], c = (float) Math.cos(spin), s = (float) Math.sin(spin);
                p[k * 3] = x * c - z * s;
                p[k * 3 + 2] = x * s + z * c;
            }
        }
        Vector3f e1 = new Vector3f(p[3] - p[0], p[4] - p[1], p[5] - p[2]), e2 = new Vector3f(p[6] - p[0], p[7] - p[1], p[8] - p[2]);
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() < 1e-12f) {
            n.set(0, 1, 0);
        }
        n.normalize();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int k = 0; k < 4; k++) {
            vc.vertex(m, p[k * 3], p[k * 3 + 1], p[k * 3 + 2]).color(r, g, b, 255).uv(u, v);
            if (!glow) {
                vc.overlayCoords(OverlayTexture.NO_OVERLAY);
            }
            vc.uv2(light).normal(nm, n.x, n.y, n.z).endVertex();
        }
    }
}
