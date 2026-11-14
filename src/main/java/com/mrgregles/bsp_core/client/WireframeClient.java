package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.WireframePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the Wireframe power shows this player, and the drawing (look A of {@code tools/preview/wireframe_concepts.html}, the owner's pick):
 * each outsider a projector reports is drawn through walls as a Wire Figure, every body part of a player outlined in thin pink lines,
 * turned with their body, the head with their look, arms and legs swinging with their walk. A projector's list is kept 45 ticks.
 */
public final class WireframeClient {
    private static final int KEEP_TICKS = 45, RANGE = 160;
    /** The Wireframe pool's pink, a little lighter so thin lines read. */
    private static final int R = 0xFF, G = 0x86, B = 0xA0, A = 230;

    private record Seen(int[] ids, long at) {
    }

    private static final Map<BlockPos, Seen> SEEN = new HashMap<>();

    private WireframeClient() {}

    public static void receive(WireframePacket msg) {
        var level = Minecraft.getInstance().level;
        SEEN.put(msg.projector(), new Seen(msg.ids(), level == null ? 0 : level.getGameTime()));
    }

    /** The entity ids to draw as wireframes now. */
    public static Set<Integer> shown() {
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        SEEN.values().removeIf(s -> now - s.at() > KEEP_TICKS || now < s.at());
        Set<Integer> out = new HashSet<>();
        for (Seen s : SEEN.values()) {
            for (int id : s.ids()) {
                out.add(id);
            }
        }
        return out;
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Draw {
        private Draw() {}

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SEEN.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            Set<Integer> ids = shown();
            if (ids.isEmpty()) {
                return;
            }
            // the players to draw first: a builder begun and then left with nothing in it would keep the shared buffer from ever being reset
            java.util.List<Player> players = new java.util.ArrayList<>();
            for (int id : ids) {
                if (mc.level.getEntity(id) instanceof Player p && p != mc.player && !p.isRemoved() && p.distanceToSqr(mc.player) <= (double) RANGE * RANGE) {
                    players.add(p);
                }
            }
            if (players.isEmpty()) {
                return;
            }
            float pt = event.getPartialTick();
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (Player p : players) {
                figure(b, pose, p, pt, cam);
            }
            Tesselator.getInstance().end();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        /** One player as six outlined boxes, in game pixels (16 a block) like the player model, scaled to their height (crouching, swimming). */
        private static void figure(BufferBuilder b, PoseStack pose, Player p, float pt, Vec3 cam) {
            Vec3 at = p.getPosition(pt);
            float body = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), head = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - body;
            float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
            float walk = p.walkAnimation.position(pt), speed = Math.min(1f, p.walkAnimation.speed(pt));
            float swing = Mth.cos(walk * 0.6662f) * 1.4f * speed * 0.5f; // the player model's own swing, in radians
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-body));
            // the model is 32 pixels tall; the game draws it 0.9375 of a block's sixteenths wide, and as tall as the player stands now
            pose.scale(0.9375f / 16f, p.getBbHeight() / 32f, 0.9375f / 16f);
            // head turns with the look
            part(b, pose, 0, 24, 0, -head, pitch, -4, 0, -4, 4, 8, 4);
            part(b, pose, 0, 12, 0, 0, 0, -4, 0, -2, 4, 12, 2);
            // arms hang from the shoulders, legs from the hips, swinging in turn
            part(b, pose, 6, 22, 0, 0, Mth.RAD_TO_DEG * -swing, -2, -10, -2, 2, 2, 2);
            part(b, pose, -6, 22, 0, 0, Mth.RAD_TO_DEG * swing, -2, -10, -2, 2, 2, 2);
            part(b, pose, 2, 12, 0, 0, Mth.RAD_TO_DEG * swing, -2, -12, -2, 2, 0, 2);
            part(b, pose, -2, 12, 0, 0, Mth.RAD_TO_DEG * -swing, -2, -12, -2, 2, 0, 2);
            pose.popPose();
        }

        /** A box from (x0,y0,z0) to (x1,y1,z1) about a joint at (jx,jy,jz), turned {@code yaw} then {@code pitchDeg} at the joint: its twelve edges. */
        private static void part(BufferBuilder b, PoseStack pose, float jx, float jy, float jz, float yaw, float pitchDeg,
                                 float x0, float y0, float z0, float x1, float y1, float z1) {
            pose.pushPose();
            pose.translate(jx, jy, jz);
            if (yaw != 0) {
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            }
            if (pitchDeg != 0) {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitchDeg));
            }
            Matrix4f m = pose.last().pose();
            float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] edge : e) {
                for (int k : edge) {
                    b.vertex(m, c[k][0], c[k][1], c[k][2]).color(R, G, B, A).endVertex();
                }
            }
            pose.popPose();
        }
    }
}
