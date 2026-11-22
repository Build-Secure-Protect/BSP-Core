package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.compass.CompassTunerBlock;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The moving parts of the Compass Tuner (concept A, the Gimbal Cradle); the still parts are the block model from
 * {@code tools/gen_tuner_assets.py}. Drawn here: the two gimbal rings (brass outside, turning about the upright; copper inside,
 * tipping on the brass ring's pivots), the compass lying on its pin with its needle while one is in the tuner, the countdown on
 * the front screen with a progress strip, the TUNE label on its plate, and plasma glowing in the glass bulb while it arrives.
 * The rings turn slowly at rest and quicker while a tune runs. The model points north; this turns the same way the blockstate
 * turns the model. Light is taken from the air in front of the tuner, so the parts are not drawn dark inside the block's cell.
 */
public class CompassTunerRenderer implements BlockEntityRenderer<CompassTunerBlockEntity> {
    private static final float PX = 1 / 16f;
    /** The gimbal's centre, in model pixels: over the pin, inside the four posts. */
    private static final float GX = 8, GY = 10.2f, GZ = 8, OUTER = 5.2f, INNER = 4.3f;
    private static final int TEAL = 0x19D3B0, RED = 0xFF6B5C, GOLD = 0xFFD23A;

    private final Font font;

    public CompassTunerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(CompassTunerBlockEntity tuner, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (tuner.getLevel() == null || !tuner.getBlockState().hasProperty(CompassTunerBlock.FACING)) {
            return;
        }
        Direction facing = tuner.getBlockState().getValue(CompassTunerBlock.FACING);
        int lit = LevelRenderer.getLightColor(tuner.getLevel(), tuner.getBlockPos().relative(facing));
        int litTop = LevelRenderer.getLightColor(tuner.getLevel(), tuner.getBlockPos().above());
        float t = AnimTime.ticks(partialTick) / 20f;
        boolean running = tuner.isTuning() && !tuner.clientStalled();

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        switch (facing) {
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(-180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-270));
            default -> {
            }
        }
        pose.translate(-0.5, 0, -0.5);

        MeshDraw mesh = new MeshDraw(buffers);
        gimbal(mesh, pose, t, running, litTop);
        if (tuner.clientCompass()) {
            compass(mesh, pose, t, running, litTop);
        }
        if (tuner.clientPlasma()) {
            plasma(pose, buffers, t);
        }
        frontPanel(tuner, pose, buffers, lit, running);
        pose.popPose();
    }

    /** The brass ring turns about the upright; the copper ring inside it tips on the brass ring's side pivots. */
    private void gimbal(MeshDraw mesh, PoseStack pose, float t, boolean running, int light) {
        float spin = running ? 1.6f : 0.35f;
        pose.pushPose();
        pose.translate(GX * PX, GY * PX, GZ * PX);
        pose.mulPose(Axis.YP.rotation(t * spin));
        mesh.polygon(pose, 24, OUTER * PX, 0.7f * PX, 0.7f * PX, 0, MeshDraw.Mat.BRASS, light);
        // the pivots the brass ring turns on (top and bottom) and the ones it carries the copper ring by (left and right)
        mesh.centred(pose, 0, (OUTER + 0.5f) * PX, 0, 0.9f * PX, 1.0f * PX, 0.9f * PX, MeshDraw.Mat.GOLD, light);
        mesh.centred(pose, 0, -(OUTER + 0.5f) * PX, 0, 0.9f * PX, 1.0f * PX, 0.9f * PX, MeshDraw.Mat.GOLD, light);
        mesh.centred(pose, (INNER + 0.6f) * PX, 0, 0, 1.2f * PX, 0.7f * PX, 0.7f * PX, MeshDraw.Mat.BRASS, light);
        mesh.centred(pose, -(INNER + 0.6f) * PX, 0, 0, 1.2f * PX, 0.7f * PX, 0.7f * PX, MeshDraw.Mat.BRASS, light);
        pose.mulPose(Axis.XP.rotation(t * spin * 1.6f + 0.8f));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        mesh.polygon(pose, 20, INNER * PX, 0.55f * PX, 0.55f * PX, 0, MeshDraw.Mat.COPPER, light);
        pose.popPose();
    }

    /** The compass lying on the pin inside the rings: a brass puck, its pale face, and a needle that swings, or spins while tuning. */
    private void compass(MeshDraw mesh, PoseStack pose, float t, boolean running, int light) {
        pose.pushPose();
        pose.translate(GX * PX, GY * PX, GZ * PX);
        float bob = running ? 0.15f * Mth.sin(t * 3) : 0;
        pose.translate(0, bob * PX, 0);
        pose.mulPose(Axis.YP.rotation(t * (running ? 0.9f : 0.2f)));
        mesh.centred(pose, 0, 0, 0, 3.4f * PX, 0.6f * PX, 3.4f * PX, MeshDraw.Mat.BRASS, light);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(45));
        mesh.centred(pose, 0, 0, 0, 3.4f * PX, 0.6f * PX, 3.4f * PX, MeshDraw.Mat.BRASS, light);
        pose.popPose();
        mesh.glowCentred(pose, 0, 0.32f * PX, 0, 2.6f * PX, 0.05f * PX, 2.6f * PX, 0xEFE6CC, 0.55f);
        float needle = running ? t * 6 : 0.6f * Mth.sin(t * 1.3f);
        pose.mulPose(Axis.YP.rotation(needle));
        mesh.glowCentred(pose, 0, 0.4f * PX, -0.6f * PX, 0.3f * PX, 0.1f * PX, 1.2f * PX, 0xD0261C, 0.8f);
        mesh.glowCentred(pose, 0, 0.4f * PX, 0.6f * PX, 0.3f * PX, 0.1f * PX, 1.2f * PX, 0x5A6070, 0.6f);
        pose.popPose();
    }

    /** Plasma standing in the glass bulb on the back-right post, breathing. */
    private void plasma(PoseStack pose, MultiBufferSource buffers, float t) {
        pose.pushPose();
        pose.scale(PX, PX, PX);
        TextureAtlasSprite sprite = PlasmaRender.sprite();
        VertexConsumer vc = PlasmaRender.buffer(buffers);
        float top = 13.2f + 0.8f * (0.5f + 0.5f * Mth.sin(t * 2.5f));
        PlasmaRender.box(vc, pose, sprite, 13.2f, 11.7f, 13.2f, 15.8f, top, 15.8f, 235, 230);
        pose.popPose();
    }

    /** The countdown on the screen (glass at z 0.45, x 3.2 to 8.8, y 1.3 to 2.6), its progress strip, and TUNE on the plate. */
    private void frontPanel(CompassTunerBlockEntity tuner, PoseStack pose, MultiBufferSource buffers, int lit, boolean running) {
        pose.pushPose();
        pose.scale(PX, PX, PX);
        String time;
        int colour;
        if (!tuner.isTuning()) {
            time = tuner.clientCompass() ? "--:--" : "";
            colour = TEAL;
        } else if (!running) {
            boolean materials = tuner.clientStall() == CompassTunerBlockEntity.Stall.NO_MATERIALS;
            time = Component.translatable(materials ? "gui.bsp_core.compass_tuner.status.needs_materials" : "gui.bsp_core.compass_tuner.status.no_fe").getString();
            colour = materials ? GOLD : RED;
        } else {
            double rate = tuner.clientRate();
            int left = rate <= 0 ? 0 : (int) Math.ceil((1 - tuner.clientProgress()) / rate);
            int h = left / 3600, m = left % 3600 / 60, s = left % 60;
            time = h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
            colour = TEAL;
        }
        if (!time.isEmpty()) {
            label(pose, buffers, Component.literal(time), 6.0f, 2.05f, 0.4f, 5.0f, 1.0f, colour, LightTexture.FULL_BRIGHT);
        }
        if (tuner.isTuning()) {
            // the progress strip along the bottom of the glass
            pose.pushPose();
            MeshDraw mesh = new MeshDraw(buffers);
            pose.scale(16, 16, 16); // MeshDraw works in blocks
            float p = (float) Mth.clamp(tuner.clientProgress(), 0, 1);
            mesh.glow(pose, 3.4f * PX, 1.35f * PX, 0.4f * PX, (3.4f + 5.2f * p) * PX, 1.5f * PX, 0.44f * PX, running ? TEAL : GOLD, 0.9f);
            pose.popPose();
        }
        label(pose, buffers, Component.translatable("gui.bsp_core.compass_tuner.tune"), 11.1f, 1.95f, 0.5f, 2.2f, 0.8f, 0x19D3B0, lit);
        pose.popPose();
    }

    /** Text centred on {@code (cx, cy)} on a north-facing face at depth {@code z}, at most {@code maxW} wide and {@code maxH} tall. */
    private void label(PoseStack pose, MultiBufferSource buffers, Component c, float cx, float cy, float z, float maxW, float maxH, int colour, int light) {
        float w = font.width(c);
        float k = Math.min(maxW / Math.max(1, w), maxH / 8f);
        pose.pushPose();
        pose.translate(cx, cy, z);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(k, -k, k);
        font.drawInBatch(c, -w / 2f, -4, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CompassTunerBlockEntity tuner) {
        return false;
    }
}
