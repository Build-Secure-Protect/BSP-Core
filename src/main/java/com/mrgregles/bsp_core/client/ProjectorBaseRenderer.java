package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/** The plasma in a Projector Base's tank, seen through the glass band, at the level the tank holds; the nozzles glow while a cable feeds it. */
public class ProjectorBaseRenderer implements BlockEntityRenderer<ProjectorBaseBlockEntity> {
    public ProjectorBaseRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ProjectorBaseBlockEntity base, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float target = Mth.clamp(base.tank() / (float) Math.max(1, base.capacity()), 0f, 1f);
        // the level glides toward what the server last said, so a filling or draining tank moves rather than jumps
        base.shown += (target - base.shown) * 0.04f * (1f + partialTick);
        float frac = Mth.clamp(base.shown, 0f, 1f);
        boolean fed = base.hasSignal();
        if (frac <= 0.004f && !fed) {
            return;
        }
        TextureAtlasSprite sprite = PlasmaRender.sprite();
        VertexConsumer vc = PlasmaRender.buffer(buffers);
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // the cavity inside the glass band is 2.2..13.8 across and 4.5..11.5 high; the plasma fills it from the floor
        PlasmaRender.tank(vc, pose, sprite, 2.2f, 4.55f, 2.2f, 13.8f, 11.45f, 13.8f, frac);
        if (fed) { // the nozzles' windows
            for (float[] n : new float[][]{{6.3f, 6.3f, -0.5f, 9.7f, 9.7f, 0.2f}, {6.3f, 6.3f, 15.8f, 9.7f, 9.7f, 16.5f}, {-0.5f, 6.3f, 6.3f, 0.2f, 9.7f, 9.7f}, {15.8f, 6.3f, 6.3f, 16.5f, 9.7f, 9.7f}}) {
                PlasmaRender.box(vc, pose, sprite, n[0], n[1], n[2], n[3], n[4], n[5], 255, 255);
            }
        }
        pose.popPose();
    }
}
