package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock;
import com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The parts of an interface's skin that depend on the group it is in: the frame strips, which run only round the outside
 * of the whole group (an edge is framed when neither neighbour along it is an interface, or when both are but the block
 * between them is missing), and the lit cross on every outer face, blue while the group has plasma, grey while it has
 * none and red on a block the group refuses. The crosses meet their neighbours edge to edge, so a group wears one grid.
 */
public class PlasmaInterfaceRenderer implements BlockEntityRenderer<PlasmaInterfaceBlockEntity> {
    private static final int BLUE = 0x4FB8FF, RED = 0xFF4A3A, GREY = 0x5A6270;
    private static final float W = 1.6f; // frame strip width

    public PlasmaInterfaceRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static boolean joined(Level level, BlockPos pos, int dx, int dy, int dz) {
        return level.getBlockState(pos.offset(dx, dy, dz)).getBlock() instanceof PlasmaInterfaceBlock;
    }

    private static boolean joined(Level level, BlockPos pos, Direction d) {
        return joined(level, pos, d.getStepX(), d.getStepY(), d.getStepZ());
    }

    @Override
    public void render(PlasmaInterfaceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        boolean refused = be.getBlockState().hasProperty(PlasmaInterfaceBlock.REFUSED) && be.getBlockState().getValue(PlasmaInterfaceBlock.REFUSED);
        int colour = refused ? RED : be.lit() ? BLUE : GREY;
        float k = refused ? 0.85f + 0.15f * (float) Math.sin((level.getGameTime() + partialTick) * 0.4) : be.lit() ? 1f : 0.55f;
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit kit = new MachineKit(pose, buffers, light);
        // the frame: twelve edges, each a pair of perpendicular directions
        for (Direction d1 : Direction.values()) {
            for (Direction d2 : Direction.values()) {
                if (d1.ordinal() >= d2.ordinal() || d1.getAxis() == d2.getAxis()) {
                    continue;
                }
                boolean n1 = joined(level, pos, d1), n2 = joined(level, pos, d2);
                boolean diag = joined(level, pos, d1.getStepX() + d2.getStepX(), d1.getStepY() + d2.getStepY(), d1.getStepZ() + d2.getStepZ());
                if ((!n1 && !n2) || (n1 && n2 && !diag)) {
                    float[] lo = {-0.3f, -0.3f, -0.3f}, hi = {16.3f, 16.3f, 16.3f};
                    for (Direction d : new Direction[]{d1, d2}) {
                        int a = d.getAxis().ordinal();
                        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                            lo[a] = 16 - W;
                        } else {
                            hi[a] = W;
                        }
                    }
                    kit.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], MachineKit.Mat.HULL2);
                }
            }
        }
        // the lit cross on every outer face; a face with a nozzle or an extractor tube keeps only the outer halves of its bars
        for (Direction d : Direction.values()) {
            if (joined(level, pos, d)) {
                continue;
            }
            PlasmaInterfaceBlock.Link link = be.getBlockState().getValue(PlasmaInterfaceBlock.SIDES[d.get3DDataValue()]);
            boolean clear = link == PlasmaInterfaceBlock.Link.NONE;
            if (clear) {
                face(kit, d, 2.8f, 7.2f, 13.2f, 8.8f, 0.15f, 0.7f, colour, k);
                face(kit, d, 7.2f, 2.8f, 8.8f, 13.2f, 0.15f, 0.7f, colour, k);
                face(kit, d, 6.3f, 6.3f, 9.7f, 9.7f, 0.15f, 1.0f, colour, k * 1.05f);
            } else {
                face(kit, d, 2.8f, 7.2f, 5.3f, 8.8f, 0.15f, 0.7f, colour, k);
                face(kit, d, 10.7f, 7.2f, 13.2f, 8.8f, 0.15f, 0.7f, colour, k);
                face(kit, d, 7.2f, 2.8f, 8.8f, 5.3f, 0.15f, 0.7f, colour, k);
                face(kit, d, 7.2f, 10.7f, 8.8f, 13.2f, 0.15f, 0.7f, colour, k);
            }
        }
        pose.popPose();
    }

    /** A glowing box standing {@code p0..p1} proud of face {@code d}, covering {@code u0..u1} by {@code v0..v1} of it (the two in-face axes in x, y, z order). */
    private static void face(MachineKit kit, Direction d, float u0, float v0, float u1, float v1, float p0, float p1, int rgb, float k) {
        int a = d.getAxis().ordinal();
        int[] in = switch (d.getAxis()) {
            case X -> new int[]{1, 2};
            case Y -> new int[]{0, 2};
            default -> new int[]{0, 1};
        };
        float[] lo = new float[3], hi = new float[3];
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            lo[a] = 16 + p0;
            hi[a] = 16 + p1;
        } else {
            lo[a] = -p1;
            hi[a] = -p0;
        }
        lo[in[0]] = u0;
        hi[in[0]] = u1;
        lo[in[1]] = v0;
        hi[in[1]] = v1;
        kit.glow(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], rgb, k);
    }
}
