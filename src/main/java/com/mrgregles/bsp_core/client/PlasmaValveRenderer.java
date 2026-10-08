package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.plasma.PlasmaValveBlock;
import com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The valve's moving parts: the copper hand wheel on top, turned as far as the limit is set (not spinning); the needle on
 * the side gauge; the lamp beside it, blue while open and red while shut; and the plasma in the two windows, the inlet
 * side at the pressure offered and the outlet side at what passes.
 */
public class PlasmaValveRenderer implements BlockEntityRenderer<PlasmaValveBlockEntity> {
    private static final int COPPER = 0xC87A3C;

    public PlasmaValveRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PlasmaValveBlockEntity valve, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (valve.getLevel() == null || !valve.getBlockState().hasProperty(PlasmaValveBlock.AXIS)) {
            return;
        }
        Direction.Axis axis = valve.getBlockState().getValue(PlasmaValveBlock.AXIS);
        boolean open = valve.open();
        float setting = Mth.clamp(valve.limit() / (float) Math.max(1, PlasmaValveBlockEntity.maxLimit()), 0f, 1f);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // turn so the model's +x is the block's axis, matching the block model's rotations
        if (axis == Direction.Axis.Z) {
            pose.mulPose(Axis.YP.rotationDegrees(-90));
        } else if (axis == Direction.Axis.Y) {
            pose.mulPose(Axis.ZP.rotationDegrees(90));
        }
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(-8, -8, -8);
        MachineKit k = new MachineKit(pose, buffers, light);
        // the hand wheel: a ring of eight copper bars, two spokes and a hub, turned by the setting (one and a half turns from shut to open)
        pose.pushPose();
        pose.translate(8, 15.3, 8);
        pose.mulPose(Axis.YP.rotationDegrees(setting * 540f));
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 45f));
            k.glow(-1.45f, -0.35f, 2.75f, 1.45f, 0.35f, 3.45f, COPPER, i % 2 == 0 ? 0.9f : 0.7f);
            pose.popPose();
        }
        k.glow(-3.1f, -0.25f, -0.3f, 3.1f, 0.25f, 0.3f, COPPER, 0.8f);
        k.glow(-0.3f, -0.25f, -3.1f, 0.3f, 0.25f, 3.1f, COPPER, 0.8f);
        k.glow(-0.7f, -0.5f, -0.7f, 0.7f, 0.5f, 0.7f, COPPER, 1f);
        pose.popPose();
        // the gauge needle on the south face, swept three quarters of a turn, and the lamp beside the gauge
        pose.pushPose();
        pose.translate(8, 8, 12.1);
        pose.mulPose(Axis.ZP.rotationDegrees(135f - 270f * setting));
        k.glow(-0.25f, -0.4f, -0.1f, 0.25f, 1.9f, 0.25f, 0xFFD23A, 1f);
        pose.popPose();
        k.glow(3.1f, 9.4f, 11.9f, 4.3f, 10.4f, 12.25f, open ? 0x4FB8FF : 0xFF4A3A, open ? 1f : 0.9f);
        // the windows: plasma stands in each cavity at the rate's share of a projector's need
        int need = Math.max(1, BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100));
        float fin = Mth.clamp(valve.in() / (float) need, 0f, 1f), fout = Mth.clamp(valve.out() / (float) need, 0f, 1f);
        if (fin > 0.004f || fout > 0.004f) {
            Direction inlet = valve.inlet();
            boolean inletAtPlus = inlet != null && inlet.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            TextureAtlasSprite sprite = PlasmaRender.sprite();
            VertexConsumer vc = PlasmaRender.buffer(buffers);
            float lo = inletAtPlus ? fout : fin, hi = inletAtPlus ? fin : fout;
            PlasmaRender.tank(vc, pose, sprite, 1.6f, 5.4f, 5.4f, 5.4f, 10.6f, 10.6f, lo);
            PlasmaRender.tank(vc, pose, sprite, 10.6f, 5.4f, 5.4f, 14.4f, 10.6f, 10.6f, hi);
        }
        pose.popPose();
    }
}
