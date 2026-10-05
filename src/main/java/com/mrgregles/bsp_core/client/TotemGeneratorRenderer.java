package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.projector.TotemGeneratorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The ring of light that rises around a totem standing on Totem Generators, shown only while the
 * generators have RF. Only the generator directly under the totem draws it. Two rings climb from the top of the generators and fade and
 * narrow as they go. The ring is as wide as the array: one block alone, wider with the four
 * side generators, and the full three blocks across when all nine are joined.
 */
public class TotemGeneratorRenderer implements BlockEntityRenderer<TotemGeneratorBlockEntity> {
    private static final int TQ = 0x19D3B0, SEGMENTS = 32;

    public TotemGeneratorRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float radius(int count) {
        return count >= 9 ? 23 : count >= 5 ? 17 : count >= 2 ? 12 : 8;
    }

    @Override
    public void render(TotemGeneratorBlockEntity generator, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (generator.getLevel() == null || !generator.isCentre() || !generator.isPowered()) {
            return;
        }
        float t = (generator.getLevel().getGameTime() + partialTick) / 20f, r = radius(generator.arrayCount());
        float seg = (float) (2 * Math.PI * r / SEGMENTS) * 0.56f;
        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);
        for (int ring = 0; ring < 2; ring++) {
            float phase = (t * 0.4f + ring * 0.5f) % 1f, y = 0.5f + phase * 15, scale = 1 - phase * 0.3f, bright = 1 - phase;
            for (int i = 0; i < SEGMENTS; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(i * 360f / SEGMENTS));
                k.glow(r * scale - 0.4f, y, -seg * scale, r * scale + 0.4f, y + 0.6f, seg * scale, TQ, bright);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
