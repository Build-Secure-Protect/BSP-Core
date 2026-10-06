package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws what the static block model cannot: the small upgrade orbs hovering around the plinth
 * (one per bought upgrade, tinted by level) and translucent spheres showing the radius of the
 * placed-only auras (Fortify, Healing).
 */
public class ShatterTotemRenderer implements BlockEntityRenderer<ShatterTotemBlockEntity> {
    private static final ResourceLocation ATLAS_TEX = new ResourceLocation(BSPCore.MODID, "block/shatter_totem_owned");
    /** White orblet region of the atlas, in 0..16 sprite units (px 56..60 of 64). */
    private static final float ORB_U0 = 14.0f, ORB_U1 = 15.0f, ORB_V0 = 0.0f, ORB_V1 = 1.0f;
    private static final float ORB_SIZE = 2f / 16f;
    private static final float ORB_RING_RADIUS = 7.5f / 16f;
    private static final float ORB_HEIGHT = 3f / 16f;
    private static final int SPHERE_LAT = 14, SPHERE_LON = 24;

    public ShatterTotemRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ShatterTotemBlockEntity totem, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS_TEX);
        long gameTime = totem.getLevel() == null ? 0 : totem.getLevel().getGameTime();
        float time = gameTime + partialTick;

        TotemUpgrades.Buff[] buffs = TotemUpgrades.Buff.values();
        int active = 0;
        for (TotemUpgrades.Buff b : buffs) {
            if (totem.getUpgradeLevel(b) > 0) active++;
        }

        // --- orbs: evenly spaced around the plinth, bobbing gently, full-bright
        if (active > 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
            int n = buffs.length;
            for (int i = 0; i < n; i++) {
                int lvl = totem.getUpgradeLevel(buffs[i]);
                if (lvl <= 0) continue;
                double a = Math.PI / 2 + (2 * Math.PI * i / n) + Math.PI / n;
                float x = 0.5f + (float) Math.cos(a) * ORB_RING_RADIUS;
                float z = 0.5f - (float) Math.sin(a) * ORB_RING_RADIUS;   // model was mirrored to face north
                float y = 2f / 16f + ORB_HEIGHT + Mth.sin(time * 0.1f + i * 2.1f) * 0.04f;
                int rgb = UpgradeOrbColors.levelColor(buffs[i], lvl, buffs[i].maxLevel());
                pose.pushPose();
                pose.translate(x, y, z);
                pose.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.03f + i));
                cube(vc, pose, sprite, ORB_SIZE, rgb);
                pose.popPose();
            }
        }

        // --- aura cubes, only when the viewer is close to the totem and has not hidden them
        int maxDist = BSPConfig.AURA_SPHERE_VIEW_DISTANCE.get();
        net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (!com.mrgregles.bsp_core.BSPClientConfig.showAuras() || cam.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(totem.getBlockPos())) > (double) maxDist * maxDist) {
            return;
        }
        for (TotemUpgrades.Buff b : buffs) {
            if (!b.placedOnly) continue;
            int lvl = totem.getUpgradeLevel(b);
            int r = b.radius(lvl);
            if (r <= 0) continue;
            // The emissive translucent layer blends without writing depth. The ordinary translucent layer does write depth,
            // which made the sphere hide machines and anything else drawn after it that stood behind its surface.
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            auraCube(buffers, pose, r + 0.5f, UpgradeOrbColors.auraColor(b), 0.55f + 0.25f * Mth.sin(time * 0.08f + b.ordinal()));
            pose.popPose();
        }
    }

    /**
     * Draws one aura cube ("Wire Edges") about the current origin: the twelve edges as thin lit bars and the
     * faces almost clear, so the exact extent can be read while the view stays open. Used by the Projector too.
     * {@code half} is the distance from the centre to each face; {@code edge} is the edges' brightness, 0 to 1.
     */
    public static void auraCube(MultiBufferSource buffers, PoseStack pose, float half, int rgb, float edge) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS_TEX);
        // the emissive translucent layer blends without writing depth, so the cube never hides what stands behind its faces
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        float u = sprite.getU(ORB_U0 + 0.5f), v = sprite.getV(ORB_V0 + 0.5f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        quadBox(vc, m, nm, -half, -half, -half, half, half, half, u, v, r, g, b, 14);
        float t = Math.max(0.04f, half / 60f); // the bars thicken a little with the cube so they stay visible from afar
        int a = Math.round(Mth.clamp(edge, 0f, 1f) * 255);
        for (float[] e : new float[][]{
                {-half, -half, -half, half, -half, -half}, {-half, -half, half, half, -half, half}, {-half, half, -half, half, half, -half}, {-half, half, half, half, half, half}, // along x
                {-half, -half, -half, -half, half, -half}, {half, -half, -half, half, half, -half}, {-half, -half, half, -half, half, half}, {half, -half, half, half, half, half}, // along y
                {-half, -half, -half, -half, -half, half}, {half, -half, -half, half, -half, half}, {-half, half, -half, -half, half, half}, {half, half, -half, half, half, half}}) { // along z
            quadBox(vc, m, nm, Math.min(e[0], e[3]) - t, Math.min(e[1], e[4]) - t, Math.min(e[2], e[5]) - t, Math.max(e[0], e[3]) + t, Math.max(e[1], e[4]) + t, Math.max(e[2], e[5]) + t, u, v, r, g, b, a);
        }
    }

    /** An axis-aligned box of six quads, one colour and alpha, drawn on both sides. */
    private static void quadBox(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x0, float y0, float z0, float x1, float y1, float z1, float u, float v, int r, int g, int b, int a) {
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
                for (int i : order) {
                    vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
                }
            }
        }
    }

    private static void cube(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float size, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float h = size / 2f;
        float u0 = sprite.getU(ORB_U0), u1 = sprite.getU(ORB_U1), v0 = sprite.getV(ORB_V0), v1 = sprite.getV(ORB_V1);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        // six faces, counter-clockwise from outside
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1},    // south (+z)
                {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1}, // north (-z)
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0},    // east (+x)
                {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0}, // west (-x)
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0},    // up
                {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0}, // down
        };
        for (float[] f : faces) {
            blockVertex(vc, m, nm, f[0], f[1], f[2], u0, v1, r, g, b, f[12], f[13], f[14]);
            blockVertex(vc, m, nm, f[3], f[4], f[5], u1, v1, r, g, b, f[12], f[13], f[14]);
            blockVertex(vc, m, nm, f[6], f[7], f[8], u1, v0, r, g, b, f[12], f[13], f[14]);
            blockVertex(vc, m, nm, f[9], f[10], f[11], u0, v0, r, g, b, f[12], f[13], f[14]);
        }
    }

    private static void blockVertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float u, float v,
                                    int r, int g, int b, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(u, v).uv2(LightTexture.FULL_BRIGHT).normal(nm, nx, ny, nz).endVertex();
    }

    /** UV sphere drawn with a single white texel so the tint is uniform. */
    private static void sphere(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float radius, int rgb, float alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float u = sprite.getU(ORB_U0 + 0.5f), v = sprite.getV(ORB_V0 + 0.5f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF, a = Math.round(alpha * 255);
        for (int lat = 0; lat < SPHERE_LAT; lat++) {
            float t0 = (float) Math.PI * lat / SPHERE_LAT, t1 = (float) Math.PI * (lat + 1) / SPHERE_LAT;
            for (int lon = 0; lon < SPHERE_LON; lon++) {
                float p0 = (float) (2 * Math.PI * lon / SPHERE_LON), p1 = (float) (2 * Math.PI * (lon + 1) / SPHERE_LON);
                entityVertex(vc, m, nm, radius, t0, p0, u, v, r, g, b, a);
                entityVertex(vc, m, nm, radius, t1, p0, u, v, r, g, b, a);
                entityVertex(vc, m, nm, radius, t1, p1, u, v, r, g, b, a);
                entityVertex(vc, m, nm, radius, t0, p1, u, v, r, g, b, a);
            }
        }
    }

    private static void entityVertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float radius, float theta, float phi,
                                     float u, float v, int r, int g, int b, int a) {
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        vc.vertex(m, nx * radius, ny * radius, nz * radius).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, nx, ny, nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(ShatterTotemBlockEntity totem) {
        return totem.largestAuraRadius() > 0;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
