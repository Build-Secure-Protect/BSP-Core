package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlock;
import com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** The repeater's five copper rings: still when idle, swelling and travelling toward the front one after another while plasma passes ("Inline Coil", pumping). */
public class PlasmaRepeaterRenderer implements BlockEntityRenderer<PlasmaRepeaterBlockEntity> {
    public PlasmaRepeaterRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PlasmaRepeaterBlockEntity repeater, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (repeater.getLevel() == null) {
            return;
        }
        Direction facing = repeater.getBlockState().getValue(PlasmaRepeaterBlock.FACING);
        boolean on = repeater.flow() > 0;
        float t = (repeater.getLevel().getGameTime() + partialTick) / 20f;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // turn so the model's +x is the block's facing
        switch (facing) {
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> pose.mulPose(Axis.ZP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.ZP.rotationDegrees(-90));
            default -> {
            }
        }
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(-8, -8, -8);
        MachineKit k = new MachineKit(pose, buffers, light);
        for (int i = 0; i < 5; i++) {
            float x = 2.6f + i * 2.2f, swell = 0, shift = 0;
            if (on) {
                float w = Math.max(0, Mth.sin(t * 2.5f - i * 0.8f));
                swell = 0.55f * w;   // the ring thickens as the pulse passes
                shift = 0.25f * w;   // and nudges forward
            }
            k.box(x + shift, 3.6f - swell, 3.6f - swell, x + 1.4f + shift, 12.4f + swell, 12.4f + swell, MachineKit.Mat.ORANGE);
        }
        pose.popPose();
    }
}
