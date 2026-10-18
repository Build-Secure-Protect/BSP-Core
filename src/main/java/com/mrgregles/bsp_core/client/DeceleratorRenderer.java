package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.link.DeceleratorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The formed Plasma Decelerator: the core and the port behind it vanish and this draws both from the core: an octagonal barrel of
 * eight hull staves with three copper hoops round the small black hole in its brass cage, and behind it the port, a hull block with
 * a steel band and a porthole where the plasma leaves. With RF the hoops warm and the cage turns slowly; while plasma arrives the
 * halo lights, the inner cage turns the other way and the porthole glows.
 */
public class DeceleratorRenderer implements BlockEntityRenderer<DeceleratorBlockEntity> {
    private static final int BRASS = 0xB8893A, PLASMA = 0x4FB8FF;
    private static final MeshDraw.Mat HULL2 = MeshDraw.Mat.HULL2, TRIM = MeshDraw.Mat.TRIM;
    private static final float U = 1 / 16f;
    private static final List<float[][]> DODECA = MeshDraw.edges(true), ICOSA = MeshDraw.edges(false);

    public DeceleratorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(DeceleratorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(DeceleratorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.isFormed()) {
            return;
        }
        int state = be.clientState(); // 0 formed, 1 RF connected, 2 receiving
        boolean rf = state >= 1, on = state >= 2;
        // inside the core's own block the light is zero: take the brightest of the block in front of it and the one above
        net.minecraft.core.BlockPos p = be.getBlockPos();
        light = Math.max(net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), p.relative(be.facing())), net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), p.above()));
        float t = (be.getLevel().getGameTime() + partial) / 20f;
        MeshDraw d = new MeshDraw(buffers);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-AcceleratorRenderer.yRot(be.facing())));
        // the barrel runs along +Z (the port is behind the core, at +Z in this frame): staves round the Z axis, hoops across it
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) (i * Math.PI / 4 + Math.PI / 8)));
            d.centred(pose, 7 * U, 0, 0, 1.6f * U, 6.2f * U, 15 * U, HULL2, light);
            pose.popPose();
        }
        for (float z : new float[]{-5.5f * U, 0, 5.5f * U}) {
            pose.pushPose();
            pose.translate(0, 0, z);
            d.polygon(pose, 8, 8.2f * U, 1.2f * U, 1.4f * U, (float) (Math.PI / 8), rf ? MeshDraw.Mat.COPPER_HOT : MeshDraw.Mat.COPPER, light);
            pose.popPose();
        }
        // the small black hole in its cage
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        net.minecraft.world.phys.Vec3 toCam = cam.subtract(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())).yRot((float) Math.toRadians(AcceleratorRenderer.yRot(be.facing())));
        d.blackHole(pose, 2.4f * U, toCam, on ? 1f : rf ? 0.4f : 0.15f, t);
        float spin = on ? t * 0.9f : rf ? t * 0.15f : 0f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(spin));
        pose.mulPose(Axis.XP.rotation(spin * 0.44f));
        for (float[][] e : DODECA) {
            d.glowLine(pose, mul(e[0], 4.6f * U), mul(e[1], 4.6f * U), 0.35f * U, on ? BRASS : MeshDraw.scale(BRASS, 0.7f));
        }
        pose.popPose();
        if (on) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(-t * 1.2f));
            for (float[][] e : ICOSA) {
                d.glowLine(pose, mul(e[0], 3.3f * U), mul(e[1], 3.3f * U), 0.25f * U, PLASMA);
            }
            pose.popPose();
        }
        // the port behind: a hull block with a rim at each end and a cable socket on every free face (back, sides, top, bottom), lit while plasma leaves
        d.box(pose, -7.5f * U, -7.5f * U, 8.5f * U, 7.5f * U, 7.5f * U, 23.5f * U, HULL2, light);
        d.box(pose, -7.9f * U, -7.9f * U, 9.6f * U, 7.9f * U, 7.9f * U, 10.8f * U, TRIM, light);
        d.box(pose, -7.9f * U, -7.9f * U, 21.2f * U, 7.9f * U, 7.9f * U, 22.4f * U, TRIM, light);
        float out = be.outRate() > 0 ? 1f : on ? 0.4f : 0.12f;
        d.socket(pose, 0, 0, 23.5f * U, 2, 1, 10 * U, PLASMA, out, light);
        d.socket(pose, 7.5f * U, 0, 16 * U, 0, 1, 10 * U, PLASMA, out, light);
        d.socket(pose, -7.5f * U, 0, 16 * U, 0, -1, 10 * U, PLASMA, out, light);
        d.socket(pose, 0, 7.5f * U, 16 * U, 1, 1, 10 * U, PLASMA, out, light);
        d.socket(pose, 0, -7.5f * U, 16 * U, 1, -1, 10 * U, PLASMA, out, light);
        pose.popPose();
    }

    private static float[] mul(float[] p, float k) {
        return new float[]{p[0] * k, p[1] * k, p[2] * k};
    }
}
