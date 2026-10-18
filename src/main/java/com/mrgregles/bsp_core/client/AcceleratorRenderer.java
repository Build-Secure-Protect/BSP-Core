package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.link.AcceleratorBlockEntity;
import com.mrgregles.bsp_core.link.AcceleratorIntakeBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The formed Plasma Accelerator, the Octagon Synchrotron: once the ring forms its eight blocks vanish and this draws the whole machine
 * from the intake: a thick hull octagon with trim rings on both faces, a copper track inside it, eight coil heads with copper
 * collars pointing at the centre, the intake's lit window on the bottom segment, and in the hole a black sphere in a brass
 * dodecahedron with a blue icosahedron inside. Nothing moves without RF; with RF the heads light in a chase round the ring and the
 * cage turns slowly; while a link delivers, charge pulses race round the track, arcs jump from the heads to the cage, the accretion
 * disc spins and the cage turns fast.
 */
public class AcceleratorRenderer implements BlockEntityRenderer<AcceleratorBlockEntity> {
    private static final int BRASS = 0xB8893A, PLASMA = 0x4FB8FF, PLASMAL = 0xD8F1FF, AMBER = 0xFFB347;
    private static final MeshDraw.Mat HULL = MeshDraw.Mat.HULL, HULL2 = MeshDraw.Mat.HULL2, TRIM = MeshDraw.Mat.TRIM, MID = MeshDraw.Mat.MID, COPPER = MeshDraw.Mat.COPPER;
    // the housing's top rim sits just under the hole so the ring's bottom segment reads as passing through the intake
    private static final float U = 1 / 16f;
    private static final List<float[][]> DODECA = MeshDraw.edges(true), ICOSA = MeshDraw.edges(false);

    public AcceleratorRenderer(BlockEntityRendererProvider.Context ctx) {}

    static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(AcceleratorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(AcceleratorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.isFormed()) {
            return;
        }
        int state = be.clientState(); // 0 formed, 1 RF connected, 2 delivering
        boolean rf = state >= 1, on = state >= 2;
        // the light the dispatcher gives is read inside the intake's own block, which is solid and so dark: take the hole's instead
        light = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.hole());
        float t = (be.getLevel().getGameTime() + partial) / 20f; // seconds
        MeshDraw d = new MeshDraw(buffers);
        pose.pushPose();
        // the intake's own frame, turned to its facing, then the hole's centre one block up
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(be.facing())));
        pose.translate(0, 1.5, 0);
        // the ring: hull octagon, trim rings, copper track, heads
        d.polygon(pose, 8, 21 * U, 5.5f * U, 12 * U, 0, HULL2, light);
        for (float z : new float[]{-7 * U, 7 * U}) {
            pose.pushPose();
            pose.translate(0, 0, z);
            d.polygon(pose, 8, 23.6f * U, 0.8f * U, 1.6f * U, 0, TRIM, light);
            pose.popPose();
        }
        d.polygon(pose, 8, 13.5f * U, 1.4f * U, 1.4f * U, 0, on ? MeshDraw.Mat.COPPER_HOT : COPPER, light);
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) (i * Math.PI / 4)));
            d.centred(pose, 16 * U, 0, 0, 4 * U, 6 * U, 6 * U, HULL, light);
            for (float x : new float[]{15 * U, 17 * U}) {
                d.centred(pose, x, 0, 0, 1 * U, 7.2f * U, 7.2f * U, rf ? MeshDraw.Mat.COPPER_HOT : COPPER, light);
            }
            float chase = rf ? Math.max(0.15f, Mth.sin(t * 3 - i * (float) Math.PI / 4)) : 0.08f;
            float k = on ? 0.8f + 0.2f * Mth.sin(t * 6 + i) : chase;
            d.glow(pose, 13 * U, -2 * U, -2 * U, 13.8f * U, 2 * U, 2 * U, AMBER, k);
            pose.popPose();
        }
        // the intake: a hull housing filling its block from the ring's bottom segment up to cable height, with a cable socket on each
        // side face and one underneath, where a run can meet it; the sockets glow with what arrives
        d.box(pose, -7 * U, -24 * U, -7 * U, 7 * U, -8.2f * U, 7 * U, HULL2, light);
        d.box(pose, -7.4f * U, -10 * U, -7.4f * U, 7.4f * U, -8.2f * U, 7.4f * U, MID, light);
        float in = be.inRate() > 0 ? 1f : rf ? 0.35f : 0.12f;
        d.socket(pose, 7 * U, -16 * U, 0, 0, 1, 10 * U, PLASMAL, in, light);
        d.socket(pose, -7 * U, -16 * U, 0, 0, -1, 10 * U, PLASMAL, in, light);
        d.socket(pose, 0, -16 * U, 7 * U, 2, 1, 10 * U, PLASMAL, in, light);
        d.socket(pose, 0, -16 * U, -7 * U, 2, -1, 10 * U, PLASMAL, in, light);
        d.socket(pose, 0, -24 * U, 0, 1, -1, 10 * U, PLASMAL, in, light);
        // the black hole: black toward the viewer, lensing violet and blue at its rim, a skin of swirling plasma; then its cages
        float spin = on ? t * 0.9f : rf ? t * 0.15f : 0f;
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        net.minecraft.world.phys.Vec3 toCam = cam.subtract(net.minecraft.world.phys.Vec3.atCenterOf(be.hole())).yRot((float) Math.toRadians(yRot(be.facing())));
        d.blackHole(pose, 5.6f * U, toCam, on ? 1f : rf ? 0.4f : 0.15f, t);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(spin));
        pose.mulPose(Axis.XP.rotation(spin * 0.44f));
        int brass = on ? MeshDraw.scale(BRASS, 1f) : MeshDraw.scale(BRASS, 0.7f);
        for (float[][] e : DODECA) {
            d.glowLine(pose, mul(e[0], 10.5f * U), mul(e[1], 10.5f * U), 0.5f * U, brass);
        }
        pose.popPose();
        if (on) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(-t * 1.2f));
            for (float[][] e : ICOSA) {
                d.glowLine(pose, mul(e[0], 7 * U), mul(e[1], 7 * U), 0.35f * U, PLASMA);
            }
            pose.popPose();
            // the accretion disc, tilted and spinning
            pose.pushPose();
            pose.mulPose(Axis.XP.rotation(1.25f));
            pose.mulPose(Axis.ZP.rotation(t * 1.6f));
            d.glowAnnulus(pose, 7.6f * U, 8.4f * U, 24, MeshDraw.scale(PLASMA, 0.9f + 0.1f * Mth.sin(t * 5)));
            for (int i = 0; i < 4; i++) { // bright spots on the disc, so the spin shows
                double a = i * Math.PI / 2;
                d.glowCentred(pose, (float) (8 * U * Math.cos(a)), (float) (8 * U * Math.sin(a)), 0, 1.2f * U, 1.2f * U, 0.6f * U, PLASMAL, 1f);
            }
            pose.popPose();
            // charge pulses round the copper track
            for (int i = 0; i < 6; i++) {
                float th = t * 2.2f + i * (float) Math.PI / 3;
                float[] p = MeshDraw.onPolygon(8, 13.5f * U, th, 0);
                d.glowCentred(pose, p[0], p[1], 0, 1.6f * U, 1.6f * U, 1.6f * U, PLASMAL, 1f);
            }
            // arcs from the heads to the cage, flickering
            for (int i = 0; i < 8; i++) {
                if (Mth.sin(t * 7 + i * 1.7f) <= 0.55f) {
                    continue;
                }
                double a = i * Math.PI / 4;
                float[] from = {(float) (Math.cos(a) * 13 * U), (float) (Math.sin(a) * 13 * U), 0};
                float[] to = {(float) (Math.cos(a + Mth.sin(t * 9 + i)) * 7 * U), (float) (Math.sin(a + Mth.cos(t * 8 + i)) * 7 * U), Mth.sin(t * 11 + i) * 2 * U};
                d.glowLine(pose, from, to, 0.4f * U, PLASMAL);
            }
        }
        pose.popPose();
    }

    private static float[] mul(float[] p, float k) {
        return new float[]{p[0] * k, p[1] * k, p[2] * k};
    }
}
