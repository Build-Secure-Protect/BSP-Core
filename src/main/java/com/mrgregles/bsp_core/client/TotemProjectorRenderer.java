package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The lit parts of the Totem Projector ("Totem Obelisk"): glowing seams on its four faces, a cap,
 * and four shards circling it while it projects; amber when a generator is feeding it but it has no
 * RF of its own; dark when idle. While projecting Fortify or
 * Healing Aura it also draws their cubes, the same way a totem does.
 */
public class TotemProjectorRenderer implements BlockEntityRenderer<TotemProjectorBlockEntity> {
    private static final int TQ = 0x19D3B0, OFF = 0x2B3038, STANDBY = 0xFFB23A; // amber: it has a signal from a generator but no RF of its own

    public TotemProjectorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TotemProjectorBlockEntity projector, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (projector.getLevel() == null) {
            return;
        }
        float t = (projector.getLevel().getGameTime() + partialTick) / 20f;
        boolean on = projector.isActive(), standby = !on && projector.hasSignal();
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);
        float[][] seams = {{5.8f, 7, 0.3f, 2}, {9.9f, 7, 0.3f, 2}, {7, 5.8f, 2, 0.3f}, {7, 9.9f, 2, 0.3f}};
        for (int i = 0; i < seams.length; i++) {
            float[] s = seams[i];
            k.glow(s[0], 5, s[1], s[0] + s[2], 19, s[1] + s[3], on ? TQ : standby ? STANDBY : OFF, on ? 0.6f + 0.4f * Mth.sin(t * 3 + i) : standby ? 0.5f + 0.3f * Mth.sin(t * 2) : 1f);
        }
        k.glow(6.6f, 20, 6.6f, 9.4f, 21.6f, 9.4f, on ? TQ : standby ? STANDBY : OFF, 1f);
        if (on) {
            for (int i = 0; i < 4; i++) {
                float a = t * 0.8f + i * Mth.HALF_PI;
                pose.pushPose();
                pose.translate(8 + Mth.cos(a) * 6, 14 + Mth.sin(t * 1.5f + i) * 2, 8 + Mth.sin(a) * 6);
                pose.mulPose(Axis.YP.rotation(-a));
                k.glow(-0.6f, -1.2f, -0.6f, 0.6f, 1.2f, 0.6f, TQ, 1f);
                pose.popPose();
            }
        }
        pose.popPose();
        if (!on) {
            return;
        }
        int maxDist = BSPConfig.AURA_SPHERE_VIEW_DISTANCE.get();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (!com.mrgregles.bsp_core.BSPClientConfig.showAuras() || cam.distanceToSqr(Vec3.atCenterOf(projector.getBlockPos())) > (double) maxDist * maxDist) {
            return;
        }
        for (Buff b : new Buff[]{Buff.FORTIFY, Buff.HEALING}) {
            int r = b.radius(projector.level(b));
            if (r > 0) {
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                ShatterTotemRenderer.auraCube(buffers, pose, r + 0.5f, UpgradeOrbColors.auraColor(b), 0.45f + 0.2f * Mth.sin(t * 1.6f + b.ordinal()), t + b.ordinal());
                pose.popPose();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TotemProjectorBlockEntity projector) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
