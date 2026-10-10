package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.SentinelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sentinel's Watch Strip (the owner's pick, 2026-10-10): a bar that slides down from the top edge while anyone is inside the
 * Alarm cube of one of the viewer's totems: the totem (or the steal with its countdown) in gold, the count, and a red chip per
 * intruder with name (level 2) and distance to the totem (level 3). At level 4 the positions are live and, within
 * {@code base.sentinelMarkerRange} in the same dimension, each intruder gets a diamond through the walls with name and distance.
 */
public final class SentinelHud implements IGuiOverlay {
    public static final SentinelHud INSTANCE = new SentinelHud();
    private static final int GOLD = 0xFFFFD23A, RED = 0xFFFF6B5C, GREY = 0xFF9AA3B5, WHITE = 0xFFE8EAF0, BG = 0xD90B0D11, CHIP = 0xFF1C2028;
    private static final int STALE_TICKS = 70, H = 14;

    /** The last word about one totem, and when it came. */
    private record View(SentinelPacket packet, long at) {
    }

    private static final Map<String, View> VIEWS = new LinkedHashMap<>();
    private static float slide; // 0 hidden, 1 shown

    private SentinelHud() {}

    public static void receive(SentinelPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String key = msg.dimension() + "@" + msg.totem().asLong();
        if (msg.intruders().isEmpty() && msg.thief().isEmpty()) {
            VIEWS.remove(key);
        } else {
            VIEWS.put(key, new View(msg, mc.level.getGameTime()));
        }
    }

    private static void expire(long now) {
        VIEWS.values().removeIf(v -> now - v.at > STALE_TICKS);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        expire(now);
        View view = VIEWS.values().stream().findFirst().orElse(null);
        float target = view == null ? 0f : 1f;
        slide = Mth.clamp(slide + (target - slide) * 0.25f * (1f + partialTick), 0f, 1f);
        if (slide <= 0.01f || view == null) {
            return;
        }
        SentinelPacket p = view.packet();
        Font font = gui.getFont();
        int y = Math.round(-H + H * slide);
        g.fill(0, y, width, y + H, BG);
        g.fill(0, y + H - 1, width, y + H, RED);
        int x = 8, ty = y + 3;
        Component head = p.thief().isEmpty() ? Component.translatable("gui.bsp_core.sentinel.totem", p.totem().getX(), p.totem().getY(), p.totem().getZ())
                : Component.translatable("gui.bsp_core.sentinel.steal", p.thief(), p.stealSeconds());
        g.drawString(font, head, x, ty, GOLD, false);
        x += font.width(head) + 12;
        Component count = Component.translatable("gui.bsp_core.sentinel.inside", p.intruders().size());
        g.drawString(font, count, x, ty, WHITE, false);
        x += font.width(count) + 12;
        for (SentinelPacket.Intruder i : p.intruders()) {
            if (p.level() < 2) {
                break;
            }
            String label = i.name() + (i.distance() >= 0 ? " " + Component.translatable("gui.bsp_core.sentinel.distance", i.distance()).getString() : "");
            int w = font.width(label) + 8;
            if (x + w > width - 8) {
                break;
            }
            g.fill(x, y + 1, x + w, y + H - 2, CHIP);
            g.fill(x, y + 1, x + w, y + 2, RED);
            g.fill(x, y + H - 3, x + w, y + H - 2, RED);
            g.drawString(font, label, x + 4, ty, RED, false);
            x += w + 6;
        }
    }

    /** Level 4: a diamond with name and distance through everything, for intruders near the viewer in the same dimension. */
    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Markers {
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || VIEWS.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            int range = BSPConfig.getOr(BSPConfig.SENTINEL_MARKER_RANGE, 128);
            if (range <= 0) {
                return;
            }
            String here = mc.level.dimension().location().toString();
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            Font font = mc.font;
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            for (View v : VIEWS.values()) {
                SentinelPacket p = v.packet();
                if (p.level() < 4 || !p.dimension().equals(here)) {
                    continue;
                }
                for (SentinelPacket.Intruder i : p.intruders()) {
                    if (mc.player.distanceToSqr(i.x(), i.y(), i.z()) > (double) range * range) {
                        continue;
                    }
                    // the diamond: a small cube turned on its corner, drawn without depth so it shows through walls
                    pose.pushPose();
                    pose.translate(i.x() - cam.x, i.y() + 1.2 - cam.y, i.z() - cam.z);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(45));
                    Matrix4f m = pose.last().pose();
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    BufferBuilder b = Tesselator.getInstance().getBuilder();
                    b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    float s = 0.18f;
                    float[][] faces = {{-s, -s, s, s, -s, s, s, s, s, -s, s, s}, {s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -s}, {s, -s, s, s, -s, -s, s, s, -s, s, s, s},
                            {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s}, {-s, s, s, s, s, s, s, s, -s, -s, s, -s}, {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s}};
                    for (float[] f : faces) {
                        for (int k = 0; k < 4; k++) {
                            b.vertex(m, f[k * 3], f[k * 3 + 1], f[k * 3 + 2]).color(255, 107, 92, 200).endVertex();
                        }
                    }
                    Tesselator.getInstance().end();
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableBlend();
                    pose.popPose();
                    // the name and distance, facing the camera, through walls
                    String text = (i.name().isEmpty() ? "?" : i.name()) + (i.distance() >= 0 ? " " + Component.translatable("gui.bsp_core.sentinel.distance", i.distance()).getString() : "");
                    pose.pushPose();
                    pose.translate(i.x() - cam.x, i.y() + 1.6 - cam.y, i.z() - cam.z);
                    pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                    pose.scale(-0.025f, -0.025f, 0.025f);
                    Matrix4f tm = pose.last().pose();
                    font.drawInBatch(text, -font.width(text) / 2f, 0, RED, false, tm, buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                    pose.popPose();
                }
            }
        }
    }
}
