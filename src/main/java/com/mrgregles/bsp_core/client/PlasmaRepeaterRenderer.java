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
        int copper = 0xC87A3C;
        for (int i = 0; i < 5; i++) {
            float x = 2.6f + i * 2.2f, swell = 0, shift = 0;
            if (on) {
                float w = Math.max(0, Mth.sin(t * 2.5f - i * 0.8f));
                swell = 0.55f * w;   // the ring thickens as the pulse passes
                shift = 0.25f * w;   // and nudges forward
            }
            float lo = 3.6f - swell, hi = 12.4f + swell, x0 = x + shift, x1 = x + 1.4f + shift;
            // four sides of the ring, each lit a little differently so it reads as a round copper band rather than a flat glow
            k.glow(x0, hi - 1.2f, lo, x1, hi, hi, copper, 0.95f);               // top, catching the light
            k.glow(x0, lo, lo, x1, lo + 1.2f, hi, copper, 0.5f);                // underside, in shade
            k.glow(x0, lo + 1.2f, lo, x1, hi - 1.2f, lo + 1.2f, copper, 0.72f); // the two sides
            k.glow(x0, lo + 1.2f, hi - 1.2f, x1, hi - 1.2f, hi, copper, 0.72f);
            if (on) { // a faint warm highlight runs over the band with the pulse
                k.glow(x0 - 0.05f, hi - 0.4f, lo + 2, x1 + 0.05f, hi + 0.05f, hi - 2, 0xFFD9B0, 0.4f + 0.5f * Math.max(0, Mth.sin(t * 2.5f - i * 0.8f)));
            }
        }
        pose.popPose();
    }
}
