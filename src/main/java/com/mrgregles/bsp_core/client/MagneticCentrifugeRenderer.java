package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Magnetic Centrifuge, the "Armoured Spin Drum" from the approved concept
 * (tools/preview/spin_drum_concepts.html, treatment A). Unformed: the ghost build guide. Formed:
 * the part blocks hide and this draws every layer in the same 48-pixel space: base plate, corner
 * pillars, hazard-striped top rails, status lamps, plated sides with three slit windows and a vent
 * grille, and inside them the ribbed, banded drum with its bolted lid. Stacked drums turn opposite
 * ways. With the Copper Tetrium Coil fitted a copper band shows behind the windows and glows blue
 * while charging. The bottom layer carries the controller screen, the Item Hatch socket (left)
 * and the Power Port socket (right).
 */
public class MagneticCentrifugeRenderer extends MultiblockGhostRenderer<MagneticCentrifugeBlockEntity> {
    private static final int TQ = 0x19D3B0, MAG2 = 0x7FB3FF, COPPER = 0xC87A3C, HAZ = 0xE3B341, OFF = 0x2B3038, ITEM = 0x3A8BFF, RF = 0xFFD23A, SCREEN = 0x0C1116;
    private static final int SIDES = 8; // the drum is drawn as boxes turned about its axis: 8 boxes make a 16-sided drum

    public MagneticCentrifugeRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** A many-sided upright cylinder about (24, 24), built from boxes that share its axis. */
    private static void prism(MachineKit k, PoseStack pose, float r, float y0, float y1, Mat mat) {
        float w = r * (float) Math.tan(Math.PI / (2 * SIDES));
        for (int i = 0; i < SIDES; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 180f / SIDES));
            k.box(-r, y0, -w, r, y1, w, mat);
            pose.popPose();
        }
    }

    @Override
    public void render(MagneticCentrifugeBlockEntity c, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(c, partialTick, pose, buffers, light, overlay);
        if (!c.isFormed() || c.getLevel() == null) {
            return;
        }
        float t = (c.getLevel().getGameTime() + partialTick) / 20f;
        boolean working = c.isWorking(), coil = c.hasCoil() || c.isDemo(), charging = working && coil && (c.charging() || c.isDemo());
        int layers = c.layers();

        Direction facing = c.getBlockState().hasProperty(MachineBlock.FACING) ? c.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        float yRot = switch (facing) {
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            default -> 0f;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(-1, 0, 0);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);

        for (int i = 0; i < layers; i++) {
            pose.pushPose();
            pose.translate(0, i * 16, 0);
            // base plate, corner pillars with two bands each, and the top frame
            k.box(0, 0, 0, 48, 2, 48, Mat.HULL2);
            k.box(1, 2, 1, 47, 3, 47, Mat.HULL);
            for (float[] p : new float[][]{{0, 0}, {43, 0}, {0, 43}, {43, 43}}) {
                k.box(p[0], 2, p[1], p[0] + 5, 14, p[1] + 5, Mat.TRIM);
                k.box(p[0] + 0.6f, 5, p[1] + 0.6f, p[0] + 4.4f, 6, p[1] + 4.4f, Mat.HULL);
                k.box(p[0] + 0.6f, 10, p[1] + 0.6f, p[0] + 4.4f, 11, p[1] + 4.4f, Mat.HULL);
            }
            k.box(0, 14, 0, 48, 16, 5, Mat.HULL2);
            k.box(0, 14, 43, 48, 16, 48, Mat.HULL2);
            k.box(0, 14, 5, 5, 16, 43, Mat.HULL2);
            k.box(43, 14, 5, 48, 16, 43, Mat.HULL2);
            // the same dressing on each of the four sides
            for (int side = 0; side < 4; side++) {
                pose.pushPose();
                pose.translate(24, 0, 24);
                pose.mulPose(Axis.YP.rotationDegrees(side * 90f));
                pose.translate(-24, 0, -24);
                for (int x = 7; x < 41; x += 4) {
                    if ((x / 4) % 2 == 1) {
                        k.glow(x, 14.2f, -0.2f, x + 2, 15.8f, 0, HAZ, 0.8f);
                    } else {
                        k.box(x, 14.2f, -0.2f, x + 2, 15.8f, 0, Mat.HULL);
                    }
                }
                k.glow(6, 11.5f, -0.4f, 7.6f, 13, 0, working ? TQ : OFF, 1f);
                k.glow(40.4f, 11.5f, -0.4f, 42, 13, 0, working ? TQ : OFF, 1f);
                // armour: sill, lintel and four mullions leave three slit windows; a vent grille sits in the lintel
                k.box(5, 3, 0.6f, 43, 5.4f, 2.2f, Mat.HULL2);
                k.box(5, 10.6f, 0.6f, 43, 14, 2.2f, Mat.HULL2);
                for (float x : new float[]{5, 16.5f, 29.5f, 41}) {
                    k.box(x, 5.4f, 0.6f, x + 2, 10.6f, 2.2f, Mat.HULL2);
                }
                for (int x = 8; x < 40; x += 3) {
                    k.box(x, 11.4f, 0.3f, x + 1.6f, 13.2f, 0.7f, Mat.HULL);
                }
                if (coil) {
                    k.glow(7, 7.2f, 2.4f, 41, 8.6f, 3.2f, charging ? MAG2 : COPPER, charging ? 0.6f + 0.4f * Mth.sin(t * 7 + side + i) : 0.85f);
                }
                pose.popPose();
            }
            // bearing collars, fixed to the frame
            pose.pushPose();
            pose.translate(24, 0, 24);
            prism(k, pose, 13, 1.5f, 3.3f, Mat.MID);
            prism(k, pose, 5, 12.8f, 14.4f, Mat.MID);
            // the drum: neighbouring layers turn opposite ways
            float speed = working ? t * (5 + layers) * 57.3f * (i % 2 == 0 ? 1 : -1) : i * 11f;
            pose.mulPose(Axis.YP.rotationDegrees(speed));
            prism(k, pose, 16, 3.5f, 12.5f, Mat.PANEL);
            for (float y : new float[]{4.1f, 7.5f, 10.9f}) {
                prism(k, pose, 16.5f, y, y + 1, Mat.TRIM);
            }
            for (int rib = 0; rib < 12; rib++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(rib * 30f));
                k.box(15.2f, 3.2f, -1.2f, 16.9f, 12.8f, 1.2f, rib % 3 == 0 ? Mat.HULL : Mat.MID);
                pose.popPose();
            }
            prism(k, pose, 14, 12.5f, 13.3f, Mat.HULL2);
            for (int bolt = 0; bolt < 6; bolt++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(bolt * 60f));
                k.box(11.4f, 13.3f, -0.6f, 12.6f, 13.9f, 0.6f, Mat.TRIM);
                pose.popPose();
            }
            k.glow(-2.4f, 13.3f, -2.4f, 2.4f, 14.9f, 2.4f, coil ? MAG2 : TQ, working ? 0.7f + 0.3f * Mth.sin(t * 5) : 0.35f);
            pose.popPose();
            pose.popPose();
        }

        // bottom layer: controller screen with three keys, hatch socket on the left, power socket on the right
        k.box(15, 3, -1.2f, 33, 13, 0, Mat.HULL);
        k.glow(16.5f, 6.5f, -1.6f, 26, 12, -1.2f, working ? (coil ? MAG2 : TQ) : SCREEN, 1f);
        if (working) {
            k.glow(17.3f, 7.3f, -1.75f, 17.3f + 7.9f * c.progressFraction(), 8.5f, -1.6f, 0xFFFFFF, 0.9f);
        }
        for (int key = 0; key < 3; key++) {
            k.glow(27.5f, 10.4f - key * 2, -1.6f, 31.5f, 11.6f - key * 2, -1.2f, key == 0 ? RF : OFF, 1f);
        }
        k.box(16.5f, 4, -1.6f, 31.5f, 5.4f, -1.2f, Mat.MID);
        k.box(-1.2f, 4, 17, 0, 12, 31, Mat.HULL);
        k.glow(-1.6f, 5.5f, 20, -1.2f, 10.5f, 28, ITEM, 1f);
        k.box(48, 4, 17, 49.2f, 12, 31, Mat.HULL);
        k.glow(49.2f, 5.5f, 20, 49.6f, 10.5f, 28, RF, c.energyStored() > 0 || c.isDemo() ? 1f : 0.35f);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
