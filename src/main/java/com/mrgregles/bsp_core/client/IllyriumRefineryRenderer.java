package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.IllyriumRefineryBlockEntity;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.material.FilterItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Illyrium Refinery. Unformed: the ghost build guide. Formed: the part blocks hide and this draws
 * the whole machine as in the approved concept render (`illyriumRefinery()` in
 * tools/preview/progression.html), same 48-pixel space: deck, filter chamber with cartridge window
 * and life lamps, glass tank with live water level, agitator, centrifuge ring, pump module with
 * gauge and riser pipe, dust feeder with conveyor, collection drawer, controller screen.
 */
public class IllyriumRefineryRenderer extends MultiblockGhostRenderer<IllyriumRefineryBlockEntity> {
    private static final int TQ = 0x19D3B0, WATER = 0x3A8BFF, FLOW = 0x8CC8FF, DIRTY = 0x4F7A6A, PURE = 0x8CFFE6, SCREEN = 0x0C1116, OFF = 0x2B3038, RF = 0xFFD23A, GLASS = 0xBFE6FF;
    private static final int[] FILTER = {0xC8CCD3, 0x6FE7F2, 0x6E6468, 0x19D3B0};

    /** Socket colour for an item port: blue in, orange out, turquoise both, grey off. */
    private static int portColour(com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> 0x3A8BFF;
            case OUTPUT -> 0xFF8A3A;
            case BOTH -> 0x19D3B0;
            default -> 0x565C6B;
        };
    }

    public IllyriumRefineryRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(IllyriumRefineryBlockEntity r, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(r, partialTick, pose, buffers, light, overlay);
        if (!r.isFormed() || r.getLevel() == null) {
            return;
        }
        float t = (r.getLevel().getGameTime() + partialTick) / 20f;
        boolean working = r.isWorking();
        float p = r.progressFraction();
        float water = r.fluidCapacity() <= 0 ? 0 : Math.min(1f, r.fluidAmount() / (float) r.fluidCapacity());
        ItemStack fs = r.filterStack();
        FilterItem fi = fs.getItem() instanceof FilterItem f ? f : null;
        int fc = fi == null ? OFF : FILTER[fi.tier.ordinal()];
        float life = fi == null ? 0 : fi.usesLeft(fs) / (float) Math.max(1, fi.maxUses());

        Direction facing = r.getBlockState().hasProperty(MachineBlock.FACING) ? r.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
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

        // deck
        k.box(0, 0, 0, 48, 6, 48, Mat.HULL2);
        k.plates(0, 0.6f, 0, 48, 5.4f, 48, 1.4f, Mat.MID);
        k.corners(0, 0, 0, 48, 6, 48, TQ, 0.6f + 0.4f * Mth.sin(t * 2));

        // filter chamber with cartridge window and life lamps
        k.box(14, 6, 14, 34, 20, 34, Mat.HULL);
        k.plates(14, 6, 14, 34, 20, 34, 2.2f, Mat.PANEL);
        k.glow(17, 9, 13.2f, 31, 17, 13.6f, SCREEN, 1f);
        k.glow(18, 10, 12.9f, 30, 16, 13.3f, fc, fi == null ? 0.6f : 1f);
        for (int i = 0; i < 5; i++) {
            k.box(19.6f + i * 2.2f, 10, 12.7f, 20f + i * 2.2f, 16, 12.95f, Mat.HULL);
            k.glow(17.4f + i * 2.8f, 18, 13.1f, 19.2f + i * 2.8f, 19.1f, 13.6f, i < Mth.ceil(life * 5) ? fc : OFF, 1f);
        }
        k.box(13, 20, 13, 35, 22, 35, Mat.TRIM);

        // tank frame
        for (float[] c : new float[][]{{15, 15}, {32, 15}, {15, 32}, {32, 32}}) {
            k.box(c[0], 22, c[1], c[0] + 1, 48, c[1] + 1, Mat.TRIM);
        }
        for (float hy : new float[]{30, 39}) {
            k.walls(14.8f, hy, 14.8f, 33.2f, hy + 0.8f, 33.2f, 0.5f, Mat.TRIM);
        }
        k.box(13, 48, 13, 35, 51, 35, Mat.HULL2);
        k.box(19, 51, 19, 29, 53, 29, Mat.TRIM);
        k.glow(22.5f, 53, 22.5f, 25.5f, 55, 25.5f, TQ, 0.3f + 0.7f * (0.5f + 0.5f * Mth.sin(t * 4)));

        // pump module with gauge and riser pipe (same side as the Refinery Pump block)
        k.box(1, 6, 16, 11, 20, 32, Mat.HULL);
        k.plates(1, 6, 16, 11, 20, 32, 2, Mat.PANEL);
        k.box(0.2f, 8.4f, 19.4f, 0.5f, 17.6f, 28.6f, Mat.HULL2);
        if (water > 0.01f) {
            k.glow(0f, 9, 20, 0.3f, 9 + 8 * water, 28, WATER, 1f);
        }
        k.box(4.5f, 20, 22.5f, 7.5f, 47, 25.5f, Mat.TRIM);
        k.box(4.5f, 47, 22.5f, 16, 50, 25.5f, Mat.TRIM);
        for (float cy : new float[]{26, 34, 42}) {
            k.box(4, cy, 22, 8, cy + 1.2f, 26, Mat.MID);
        }

        // dust feeder with conveyor (same side as the Item Hatch block)
        k.box(37, 6, 16, 47, 18, 32, Mat.HULL);
        k.plates(37, 6, 16, 47, 18, 32, 2, Mat.PANEL);
        k.box(38, 18, 17, 46, 22, 31, Mat.MID);
        k.box(39, 22, 18, 45, 23, 30, Mat.HULL);
        k.glow(39.6f, 21.2f, 18.6f, 44.4f, 22.6f, 29.4f, DIRTY, 0.9f);
        k.box(34, 12, 21, 37, 14, 27, Mat.TRIM);

        // --- port sockets: item hatch above the dust feeder, water and RF on the pump side
        float sp = 0.7f + 0.3f * Mth.sin(t * 3);
        k.box(42, 18, 19, 47.4f, 30, 29, Mat.HULL2);
        k.box(47.4f, 20.5f, 20.5f, 48f, 27.5f, 27.5f, Mat.TRIM);
        k.glow(48f, 21.7f, 21.7f, 48.3f, 26.3f, 26.3f, portColour(r.getSide(Direction.from3DDataValue(0))), sp);
        k.box(1, 20, 19, 6, 33.5f, 29, Mat.HULL2);
        k.box(0.4f, 20.5f, 20.5f, 1f, 27.5f, 27.5f, Mat.TRIM);
        k.glow(0.1f, 21.7f, 21.7f, 0.4f, 26.3f, 26.3f, WATER, sp);
        k.box(0.4f, 29f, 21.5f, 1f, 32.4f, 26.5f, Mat.TRIM);
        k.glow(0.1f, 29.8f, 22.5f, 0.4f, 31.6f, 25.5f, RF, r.hasRfUpgrade() ? sp : 0.3f);

        // collection drawer and controller screen
        k.box(18, 6, 2, 30, 10, 14, Mat.MID);
        k.box(18, 10, 2, 30, 11, 2.8f, Mat.TRIM);
        k.glow(17.6f, 9.6f, 1.6f, 30.4f, 10, 2, TQ, 1f);
        int pile = r.outputCount();
        for (int i = 0; i < Math.min(12, pile); i++) {
            float px = 21.5f + (i % 3) * 1.5f, pz = 6 + (i / 3 % 2) * 1.6f + (i % 2) * 0.5f, pyy = 10 + (i / 6) * 1.1f;
            k.glow(px, pyy, pz, px + 1.2f, pyy + 1.1f, pz + 1.2f, PURE, 1f);
        }
        k.box(35, 6, 1, 46, 18, 10, Mat.HULL);
        k.box(36, 8, 0.5f, 45, 17, 1, Mat.PANEL);
        k.glow(37, 10, 0.1f, 44, 15.5f, 0.5f, SCREEN, 1f);
        if (working) {
            k.glow(37.6f, 11.6f, -0.1f, 37.6f + 5.8f * p, 13.2f, 0.1f, TQ, 1f);
        }
        if (r.hasRfUpgrade()) {
            k.box(2.5f, 6, 3, 8, 16, 9, Mat.HULL2);
            for (float f : new float[]{0.25f, 0.5f, 0.75f}) {
                k.glow(2.3f, 6 + 10 * f - 0.4f, 2.8f, 8.2f, 6 + 10 * f + 0.4f, 9.2f, RF, r.rfActive() ? 0.5f + 0.5f * Mth.sin(t * 6 + f * 9) : 0.3f);
            }
        }

        // moving bits while refining: water blips up the riser, dust along the belt, drips into the drawer
        boolean run = working && water > 0.01f;
        if (run) {
            for (int i = 0; i < 4; i++) {
                float f = (t * 0.6f + i / 4f) % 1f;
                k.glow(5.4f, 20 + f * 26, 22, 6.6f, 21.6f + f * 26, 22.4f, FLOW, 1f);
            }
            for (int i = 0; i < 3; i++) {
                float f = (t * 0.5f + i / 3f) % 1f;
                k.glow(36 - f * 2.6f, 14, 22.5f, 37 - f * 2.6f, 14.6f, 25.5f, DIRTY, 1f);
                float d = (t * 0.9f + i / 3f) % 1f;
                k.glow(21 + i * 2.6f, 10 - d * 0.6f, 13 - d * 5, 21.8f + i * 2.6f, 11 - d * 0.6f, 13.8f - d * 5, PURE, 1f);
            }
        }

        // agitator and centrifuge ring
        pose.pushPose();
        pose.translate(24, 0, 24);
        if (run) {
            pose.mulPose(Axis.YP.rotation(t * 1.5f));
        }
        k.box(-0.8f, 24, -0.8f, 0.8f, 50, 0.8f, Mat.TRIM);
        k.box(-5.5f, 25, -0.5f, 5.5f, 27, 0.5f, Mat.MID);
        k.box(-0.5f, 33, -5.5f, 0.5f, 35, 5.5f, Mat.MID);
        pose.popPose();
        pose.pushPose();
        pose.translate(24, 0, 24);
        pose.mulPose(Axis.YP.rotation(run ? t * 2.4f : 0.4f));
        k.walls(-12.5f, 34, -12.5f, 12.5f, 35.4f, 12.5f, 1.4f, Mat.PANEL);
        for (float[] n : new float[][]{{-12.5f, 0}, {12.5f, 0}, {0, -12.5f}, {0, 12.5f}}) {
            k.glow(n[0] - 1.2f, 33.4f, n[1] - 1.2f, n[0] + 1.2f, 36, n[1] + 1.2f, TQ, run ? 0.6f + 0.4f * Mth.sin(t * 5 + n[0]) : 0.4f);
        }
        pose.popPose();

        // water and glass last, on the translucent layer
        if (water > 0.01f) {
            k.translucent(buffers, 16, 22.2f, 16, 32, 22.2f + 25.4f * water, 32, WATER, 0.55f);
        }
        k.translucent(buffers, 15.6f, 22, 15.6f, 32.4f, 48, 32.4f, GLASS, 0.16f);
        pose.popPose();
    }
}
