package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.machine.MachineBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Illyrium Crucible. Unformed: the ghost build guide. Formed: the part blocks hide themselves and
 * this draws the whole machine exactly as in the approved concept render (`illyriumCrucible()` in
 * tools/preview/progression.html), in the same 48-pixel coordinate space: casing deck, four lava
 * pylons with live gauges, floor conduits, the vessel with its melt, stirring cross, containment
 * rings, top gantry and beacon, controller with screen and lamps, input hatch and output tray.
 */
public class IllyriumCrucibleRenderer extends MultiblockGhostRenderer<IllyriumCrucibleBlockEntity> {
    private static final int TQ = 0x19D3B0, DIRTY = 0x4F7A6A, LAVA = 0xFF7A1A, RF = 0xFFD23A, SCREEN = 0x0C1116, OFF = 0x2B3038;

    /** Socket colour for an item port: blue in, orange out, turquoise both, grey off. */
    private static int portColour(com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> 0x3A8BFF;
            case OUTPUT -> 0xFF8A3A;
            case BOTH -> 0x19D3B0;
            default -> 0x565C6B;
        };
    }

    public IllyriumCrucibleRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(IllyriumCrucibleBlockEntity c, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(c, partialTick, pose, buffers, light, overlay);
        if (!c.isFormed() || c.getLevel() == null) {
            return;
        }
        float t = (c.getLevel().getGameTime() + partialTick) / 20f; // seconds, as in the concept page
        boolean working = c.isWorking();
        float p = c.progressFraction();
        float fuel = c.fluidCapacity() <= 0 ? 0 : Math.min(1f, c.fluidAmount() / (float) c.fluidCapacity());
        boolean rf = c.rfActive();
        int fuelColour = rf ? RF : LAVA, melt = c.isAlloying() ? TQ : DIRTY;

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
        // concept space: 48 px cube, controller cell is x 16..32, z 0..16
        pose.translate(-1, 0, 0);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);

        // deck
        k.box(0, 0, 0, 48, 6, 48, Mat.HULL2);
        k.plates(0, 0.6f, 0, 48, 5.4f, 48, 1.4f, Mat.MID);
        k.corners(0, 0, 0, 48, 6, 48, TQ, 0.6f + 0.4f * Mth.sin(t * 2));

        // four lava pylons, each centred on its column of pylon blocks (cell centre 8 or 40) so pipes and cables,
        // which attach at the centre of a block face, line up with the sockets
        float[][] py = {{3.5f, 3.5f}, {35.5f, 3.5f}, {3.5f, 35.5f}, {35.5f, 35.5f}};
        float sp = 0.7f + 0.3f * Mth.sin(t * 3);
        float rfGlow = c.hasRfUpgrade() ? sp : 0.3f;
        for (float[] q : py) {
            float x = q[0], z = q[1];
            k.box(x, 6, z, x + 9, 40, z + 9, Mat.HULL);
            k.box(x + 0.5f, 40, z + 0.5f, x + 8.5f, 42, z + 8.5f, Mat.PANEL);
            k.glow(x + 2.5f, 42, z + 2.5f, x + 6.5f, 43, z + 6.5f, TQ, 1f);
            for (float by : new float[]{14, 22, 30}) {
                k.box(x - 0.3f, by, z - 0.3f, x + 9.3f, by + 1, z + 9.3f, Mat.TRIM);
            }
            float fz = z < 24 ? z - 0.5f : z + 9, fx = x < 24 ? x - 0.5f : x + 9;
            k.box(x + 2.4f, 8, fz, x + 6.6f, 38, fz + 0.5f, Mat.HULL2);
            k.box(fx, 8, z + 2.4f, fx + 0.5f, 38, z + 6.6f, Mat.HULL2);
            float top = 8.6f + 28.8f * fuel, pulse = 0.75f + 0.25f * Mth.sin(t * 3 + x);
            if (fuel > 0.01f) {
                float gz = z < 24 ? fz - 0.3f : fz + 0.3f, gx = x < 24 ? fx - 0.3f : fx + 0.3f;
                k.glow(x + 3, 8.6f, gz, x + 6, top, gz + 0.5f, fuelColour, pulse);
                k.glow(gx, 8.6f, z + 3, gx + 0.5f, top, z + 6, fuelColour, pulse);
            }
            // port sockets: a collar from the pylon out to the block face, on both outward sides, at the centre of
            // each pylon block. Lower block: orange (lava). Upper block: yellow (RF, dim until an RF upgrade is fitted).
            float cx = x + 4.5f, cz = z + 4.5f;
            float z0 = z < 24 ? 0 : z + 9, z1 = z < 24 ? z : 48, zg = z < 24 ? -0.3f : 48;
            float x0 = x < 24 ? 0 : x + 9, x1 = x < 24 ? x : 48, xg = x < 24 ? -0.3f : 48;
            for (int layer = 0; layer < 2; layer++) {
                float cy = 24 + 16 * layer;
                int colour = layer == 0 ? LAVA : RF;
                float a = layer == 0 ? sp : rfGlow;
                k.box(cx - 3.5f, cy - 3.5f, z0, cx + 3.5f, cy + 3.5f, z1, Mat.TRIM);
                k.glow(cx - 2.3f, cy - 2.3f, zg, cx + 2.3f, cy + 2.3f, zg + 0.3f, colour, a);
                k.box(x0, cy - 3.5f, cz - 3.5f, x1, cy + 3.5f, cz + 3.5f, Mat.TRIM);
                k.glow(xg, cy - 2.3f, cz - 2.3f, xg + 0.3f, cy + 2.3f, cz + 2.3f, colour, a);
            }
        }
        // item sockets sit flush with the outward face of each hatch block, at its centre.
        // left hatch (port 0, the tray side)
        k.box(44.5f, 19, 18, 47.4f, 29, 30, Mat.HULL2);
        k.box(47.4f, 20.5f, 20.5f, 48f, 27.5f, 27.5f, Mat.TRIM);
        k.glow(48f, 21.7f, 21.7f, 48.3f, 26.3f, 26.3f, portColour(c.getSide(Direction.from3DDataValue(0))), sp);
        // right hatch (port 1, the hopper side)
        k.box(0, 20.5f, 20.5f, 0.6f, 27.5f, 27.5f, Mat.TRIM);
        k.glow(-0.3f, 21.7f, 21.7f, 0, 26.3f, 26.3f, portColour(c.getSide(Direction.from3DDataValue(1))), sp);

        // floor conduits from the pylons to the vessel
        float cp = fuel > 0.01f ? 0.55f + 0.2f * Mth.sin(t * 3) : 0.15f;
        k.glow(10, 6, 22, 15, 9, 26, fuelColour, cp);
        k.glow(33, 6, 22, 38, 9, 26, fuelColour, cp);
        k.glow(22, 6, 10, 26, 9, 15, fuelColour, cp);
        k.glow(22, 6, 33, 26, 9, 38, fuelColour, cp);

        // vessel
        for (float[] l : new float[][]{{15, 15}, {31, 15}, {15, 31}, {31, 31}}) {
            k.box(l[0], 6, l[1], l[0] + 2, 12, l[1] + 2, Mat.TRIM);
        }
        k.box(14, 12, 14, 34, 14, 34, Mat.MID);
        k.walls(14, 14, 14, 34, 28, 34, 2, Mat.PANEL);
        for (float by : new float[]{16, 21, 26}) {
            k.walls(13.5f, by, 13.5f, 34.5f, by + 1.2f, 34.5f, 0.5f, Mat.HULL);
        }
        k.glowWalls(13.3f, 18.4f, 13.3f, 34.7f, 19.2f, 34.7f, 0.4f, TQ, 0.6f + 0.4f * Mth.sin(t * 2));
        float level = working ? 0.15f + 0.8f * p : 0.1f;
        k.glow(16.2f, 14.2f, 16.2f, 31.8f, 14.2f + 13.2f * level, 31.8f, melt, working ? 0.8f + 0.2f * Mth.sin(t * 3) : 0.45f);

        // top gantry and beacon
        k.box(3, 43, 6, 45, 46, 10, Mat.HULL2);
        k.box(3, 43, 38, 45, 46, 42, Mat.HULL2);
        k.box(6, 43, 10, 10, 46, 38, Mat.HULL2);
        k.box(38, 43, 10, 42, 46, 38, Mat.HULL2);
        k.box(21, 43, 10, 27, 46, 38, Mat.HULL2);
        k.box(20, 46, 20, 28, 49, 28, Mat.TRIM);
        k.glow(22.5f, 49, 22.5f, 25.5f, 51, 25.5f, TQ, 0.3f + 0.7f * (0.5f + 0.5f * Mth.sin(t * 4)));

        // controller with screen, progress bar and lamps
        k.box(17, 6, -2, 31, 18, 2.5f, Mat.HULL);
        k.box(18, 7, -2.5f, 30, 17, -2, Mat.PANEL);
        k.glow(19, 9.5f, -2.9f, 29, 15.5f, -2.5f, SCREEN, 1f);
        if (working) {
            k.glow(19.6f, 11.2f, -3.1f, 19.6f + 8.8f * p, 13, -2.9f, TQ, 1f);
        }
        for (int i = 0; i < 5; i++) {
            k.glow(19.6f + i * 1.9f, 14, -3.1f, 20.8f + i * 1.9f, 15.1f, -2.9f, i < Math.round(p * 5) && working ? TQ : OFF, 1f);
        }
        // input hatch (left) and output tray (right)
        k.box(0.6f, 20, 18, 4.6f, 28, 30, Mat.HULL2);
        k.box(0, 27, 17.4f, 5.2f, 28.5f, 30.6f, Mat.TRIM);
        k.glow(1.2f, 28.5f, 19, 4, 29, 29, TQ, 0.6f);
        k.box(47, 6, 18, 52, 9, 30, Mat.MID);
        k.box(51.4f, 9, 18, 52, 11, 30, Mat.TRIM);
        k.glow(47, 8.8f, 17.5f, 52, 9.1f, 18, TQ, 1f);
        if (c.hasRfUpgrade()) {
            k.box(32.5f, 6, -1.5f, 37, 15, 2.5f, Mat.HULL2);
            for (float f : new float[]{0.25f, 0.5f, 0.75f}) {
                k.glow(32.3f, 6 + 9 * f - 0.4f, -1.7f, 37.2f, 6 + 9 * f + 0.4f, 2.7f, RF, rf ? 0.5f + 0.5f * Mth.sin(t * 6 + f * 9) : 0.3f);
            }
        }

        // stirring cross (turns while working)
        pose.pushPose();
        pose.translate(24, 0, 24);
        if (working) {
            pose.mulPose(Axis.YP.rotation(t * 1.8f));
        }
        k.box(-1, 17, -1, 1, 43, 1, Mat.TRIM);
        k.box(-6.5f, 17, -0.6f, 6.5f, 19.4f, 0.6f, Mat.MID);
        k.box(-0.6f, 17, -6.5f, 0.6f, 19.4f, 6.5f, Mat.MID);
        pose.popPose();

        // containment rings
        float glow = working ? 1f : 0.4f;
        pose.pushPose();
        pose.translate(24, Mth.sin(t * 1.3f) * 0.6f, 24);
        pose.mulPose(Axis.YP.rotation(working ? t * 0.9f : 0));
        k.glowWalls(-11.5f, 31, -11.5f, 11.5f, 32.2f, 11.5f, 1.2f, TQ, glow);
        pose.popPose();
        pose.pushPose();
        pose.translate(24, 0, 24);
        pose.mulPose(Axis.YP.rotation(working ? -t * 1.4f : 0.6f));
        k.glowWalls(-10.5f, 34, -10.5f, 10.5f, 34.8f, 10.5f, 0.8f, TQ, glow * 0.9f);
        pose.popPose();

        pose.popPose();
    }
}
