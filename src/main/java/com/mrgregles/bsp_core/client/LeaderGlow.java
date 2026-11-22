package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.registry.ModParticles;
import com.mrgregles.bsp_core.totem.TotemOrbLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Score Leader's totems (concept A, "Gilded Aura"): a thin gold shell breathing round the body and head, see-through and full
 * bright on the emissive translucent layer (as the aura cubes, so shader packs draw it as light), and gold sparks drifting up from
 * the plinth, about four a second per totem, at most {@link #MAX_SPARKS} at once in all, never in the server's packets.
 */
public final class LeaderGlow {
    private LeaderGlow() {}

    /** Glow and sparks are drawn only this close to the viewer. */
    public static final double REACH = 32;
    private static final int MAX_SPARKS = 40, SPARK_TICKS = 5, SPARK_LIFE = 85;
    private static final ResourceLocation ATLAS_TEX = new ResourceLocation(BSPCore.MODID, "block/shatter_totem_owned");
    private static final float OUT = 0.55f;

    private static final Map<Object, Long> LAST = new WeakHashMap<>();
    private static final Deque<Long> RECENT = new ArrayDeque<>();

    /** The shell round the body and head, in block space, turned to {@code facing}. */
    public static void shell(PoseStack pose, MultiBufferSource buffers, Direction facing, float time) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS_TEX);
        float u = sprite.getU(14.5f), v = sprite.getV(0.5f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        int a = Math.round(255 * (0.15f + 0.09f * Mth.sin(time / 20f * 2.2f)));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-TotemOrbLayout.modelTurn(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (LookLayout.Part p : LookLayout.PARTS) {
            boolean head = p.name().equals("head");
            if (head) {
                pose.pushPose();
                pose.translate(LookLayout.HEAD_PIVOT[0], LookLayout.HEAD_PIVOT[1], LookLayout.HEAD_PIVOT[2]);
                pose.mulPose(Axis.XP.rotationDegrees(LookLayout.HEAD_ANGLE));
                pose.translate(-LookLayout.HEAD_PIVOT[0], -LookLayout.HEAD_PIVOT[1], -LookLayout.HEAD_PIVOT[2]);
            }
            box(vc, pose.last().pose(), pose.last().normal(), p.from()[0] - OUT, p.from()[1] - OUT, p.from()[2] - OUT,
                    p.to()[0] + OUT, p.to()[1] + OUT, p.to()[2] + OUT, u, v, a);
            if (head) {
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x0, float y0, float z0, float x1, float y1, float z1, float u, float v, int a) {
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(255, 200, 58, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            }
        }
    }

    /**
     * Lets a spark go from this totem when it is time (every {@link #SPARK_TICKS} ticks of game time, so the shadow pass of a shader
     * pack, which draws the totem again, adds none), within the cap, and when sparks are on for this player.
     */
    public static void sparks(Object totem, Level level, BlockPos pos, long gameTime) {
        Minecraft mc = Minecraft.getInstance();
        if (!BSPClientConfig.leaderSparks() || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Long last = LAST.get(totem);
        if (last != null && gameTime - last < SPARK_TICKS && gameTime >= last) {
            return;
        }
        LAST.put(totem, gameTime);
        while (!RECENT.isEmpty() && (gameTime - RECENT.peekFirst() > SPARK_LIFE || RECENT.peekFirst() > gameTime)) {
            RECENT.pollFirst();
        }
        if (RECENT.size() >= MAX_SPARKS) {
            return;
        }
        RECENT.addLast(gameTime);
        var r = level.random;
        double x = pos.getX() + 0.2 + r.nextDouble() * 0.6, y = pos.getY() + 0.15 + r.nextDouble() * 0.15, z = pos.getZ() + 0.2 + r.nextDouble() * 0.6;
        level.addParticle(ModParticles.LEADER_SPARK.get(), x, y, z, (r.nextDouble() - 0.5) * 0.004, 0.025 + r.nextDouble() * 0.015, (r.nextDouble() - 0.5) * 0.004);
    }
}
