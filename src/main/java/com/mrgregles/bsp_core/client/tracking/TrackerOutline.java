package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The green marks on tagged machines while the Machine Tagger is in either hand, the owner's pick "Brackets and Name" (P7): bright green
 * brackets on the eight corners of each tagged block and its name with its distance floating above it, drawn through walls, for tags in
 * this world within {@code tracking.outlineRange} blocks.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class TrackerOutline {
    private static final float K = 0.28f, T = 0.035f;

    private TrackerOutline() {}

    static boolean holding(Player p) {
        return p.getMainHandItem().is(ModItems.MACHINE_TAGGER.get()) || p.getOffhandItem().is(ModItems.MACHINE_TAGGER.get());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !holding(mc.player)) {
            return;
        }
        CompoundTag view = ClientTracking.view();
        ListTag tags = view.getList("tags", Tag.TAG_COMPOUND);
        int range = view.getInt("outline");
        String dim = mc.level.dimension().location().toString();
        java.util.List<BlockPos> marked = new java.util.ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag t = tags.getCompound(i);
            if (t.contains("pos") && dim.equals(t.getString("dim"))) {
                BlockPos p = NbtUtils.readBlockPos(t.getCompound("pos"));
                if (p.distSqr(mc.player.blockPosition()) <= (double) range * range) {
                    marked.add(p);
                }
            }
        }
        if (marked.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (BlockPos p : marked) {
            brackets(b, m, p.getX() - 0.02f, p.getY() - 0.02f, p.getZ() - 0.02f, 1.04f);
        }
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
        labels(event, mc, tags, dim, range, cam);
    }

    /** Three short bars from each of the cube's eight corners. */
    private static void brackets(BufferBuilder b, Matrix4f m, float x, float y, float z, float s) {
        for (int ix = 0; ix <= 1; ix++) {
            for (int iy = 0; iy <= 1; iy++) {
                for (int iz = 0; iz <= 1; iz++) {
                    float cx = x + ix * s, cy = y + iy * s, cz = z + iz * s;
                    float dx = ix == 0 ? 1 : -1, dy = iy == 0 ? 1 : -1, dz = iz == 0 ? 1 : -1;
                    bar(b, m, cx, cy, cz, cx + dx * K, cy + dy * T, cz + dz * T);
                    bar(b, m, cx, cy, cz, cx + dx * T, cy + dy * K, cz + dz * T);
                    bar(b, m, cx, cy, cz, cx + dx * T, cy + dy * T, cz + dz * K);
                }
            }
        }
    }

    /** A small solid box between two corners, bright green. */
    private static void bar(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1) {
        float ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        float[][] faces = {{ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz}, {bx, ay, az, ax, ay, az, ax, by, az, bx, by, az}, {bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz},
                {ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az}, {ax, by, bz, bx, by, bz, bx, by, az, ax, by, az}, {ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                b.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(61, 255, 110, 235).endVertex();
            }
        }
    }

    /** The name and distance above each marked machine, facing the camera, through walls. */
    private static void labels(RenderLevelStageEvent event, Minecraft mc, ListTag tags, String dim, int range, Vec3 cam) {
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag t = tags.getCompound(i);
            if (!t.contains("pos") || !dim.equals(t.getString("dim"))) {
                continue;
            }
            BlockPos p = NbtUtils.readBlockPos(t.getCompound("pos"));
            double d = Math.sqrt(p.distSqr(mc.player.blockPosition()));
            if (d > range) {
                continue;
            }
            pose.pushPose();
            pose.translate(p.getX() + 0.5 - cam.x, p.getY() + 1.45 - cam.y, p.getZ() + 0.5 - cam.z);
            pose.mulPose(event.getCamera().rotation());
            float s = 0.025f * (float) Math.max(1, d / 12);
            pose.scale(-s, -s, s);
            Matrix4f m = pose.last().pose();
            String name = Hud.tagName(t), dist = Math.round(d) + " m";
            font.drawInBatch(name, -font.width(name) / 2f, -10, 0xFF3DFF6E, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0xC0050A08, LightTexture.FULL_BRIGHT);
            font.drawInBatch(dist, -font.width(dist) / 2f, 0, 0xFFB8FFCC, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0xC0050A08, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
