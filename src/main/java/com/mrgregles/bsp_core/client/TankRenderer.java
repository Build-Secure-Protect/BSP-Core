package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankPortBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * The plasma inside a Plasma Tank, drawn once by the master block as one body across the hollow and half of every shell block: a
 * level that glides toward what the server last said, a stream falling from any port that is taking plasma in above the surface,
 * with a ripple where it lands, the lit uprights that show the level from outside, the sweep of light when the tank forms, and the
 * setting ring on every port.
 */
public class TankRenderer implements BlockEntityRenderer<TankPartBlockEntity> {
    private static final int TQ = 0x19D3B0, BLUE = 0x3AB3DA, ORANGE = 0xF9801D;

    public TankRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(TankPartBlockEntity part) {
        return part.isMaster();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(TankPartBlockEntity part, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (part.getLevel() == null) {
            return;
        }
        if (part instanceof TankPortBlockEntity port) {
            ring(port, pose, buffers, light);
        }
        if (!part.isMaster() || part.w() < 3) {
            return;
        }
        float target = part.capacity() <= 0 ? 0 : Mth.clamp(part.stored() / (float) part.capacity(), 0f, 1f);
        if (part.shown < 0) {
            part.shown = target;
        }
        part.shown += (target - part.shown) * 0.03f * (1f + partialTick);
        float frac = Mth.clamp(part.shown, 0f, 1f);
        if (part.stored() > 0) {
            frac = Math.max(frac, minFrac(part.h())); // a near-empty tank still shows a thin layer, so the plasma can be seen arriving
        }
        TextureAtlasSprite sprite = PlasmaRender.sprite();
        VertexConsumer vc = PlasmaRender.buffer(buffers);
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // the body of plasma: the hollow plus half of every shell block, in sixteenths of the master's block space, so the walls look thin
        float x0 = 8.05f, y0 = 8.05f, z0 = 8.05f, x1 = (part.w() - 1) * 16 + 7.95f, y1 = (part.h() - 1) * 16 + 7.95f, z1 = (part.d() - 1) * 16 + 7.95f;
        PlasmaRender.tank(vc, pose, sprite, x0, y0, z0, x1, y1, z1, frac);
        float surface = y0 + (y1 - y0) * frac;
        float t = (part.getLevel().getGameTime() + partialTick) / 20f;
        BlockPos min = part.getBlockPos();
        gauge(part, pose, buffers, light, surface, t);
        sweep(part, pose, buffers, light, partialTick);
        for (Map.Entry<BlockPos, int[]> e : part.ports().entrySet()) {
            int in = e.getValue()[1];
            if (in <= 0 || frac >= 0.995f) {
                continue;
            }
            BlockPos p = e.getKey();
            int rx = p.getX() - min.getX(), ry = p.getY() - min.getY(), rz = p.getZ() - min.getZ();
            // where the stream leaves the port: its inner face, a little way into the hollow
            float sx = rx * 16 + 8, sy = ry * 16 + 8, sz = rz * 16 + 8;
            boolean top = ry == part.h() - 1;
            if (!top) {
                if (rx == 0) sx = x0 + 3; else if (rx == part.w() - 1) sx = x1 - 3;
                if (rz == 0) sz = z0 + 3; else if (rz == part.d() - 1) sz = z1 - 3;
                if (ry == 0) continue; // a port in the floor fills from below: nothing to see
            } else {
                sy = y1;
            }
            if (sy <= surface + 0.5f) {
                continue; // below the surface: the level just climbs
            }
            float r = 1.1f + 0.3f * Mth.sin(t * 9f), half = Mth.clamp(in / 400f, 0.6f, 2.2f) * r;
            if (!top) { // a short spout from the side port, then the fall
                float ex = rx == 0 ? x0 : rx == part.w() - 1 ? x1 : sx, ez = rz == 0 ? z0 : rz == part.d() - 1 ? z1 : sz;
                PlasmaRender.box(vc, pose, sprite, Math.min(ex, sx) - (rx == 0 || rx == part.w() - 1 ? 0 : half), sy - half, Math.min(ez, sz) - (rz == 0 || rz == part.d() - 1 ? 0 : half), Math.max(ex, sx) + (rx == 0 || rx == part.w() - 1 ? 0 : half), sy + half, Math.max(ez, sz) + (rz == 0 || rz == part.d() - 1 ? 0 : half), 240, 255);
            }
            PlasmaRender.box(vc, pose, sprite, sx - half, surface, sz - half, sx + half, sy, sz + half, 230, 255);
            // the ripple: a thin ring on the surface that grows and fades, twice a second
            float k = (t * 2f) % 1f, rr = 3f + 9f * k;
            int a = (int) (170 * (1f - k));
            PlasmaRender.box(vc, pose, sprite, sx - rr, surface + 0.1f, sz - rr, sx + rr, surface + 0.35f, sz - rr + 0.7f, a, 255);
            PlasmaRender.box(vc, pose, sprite, sx - rr, surface + 0.1f, sz + rr - 0.7f, sx + rr, surface + 0.35f, sz + rr, a, 255);
            PlasmaRender.box(vc, pose, sprite, sx - rr, surface + 0.1f, sz - rr, sx - rr + 0.7f, surface + 0.35f, sz + rr, a, 255);
            PlasmaRender.box(vc, pose, sprite, sx + rr - 0.7f, surface + 0.1f, sz - rr, sx + rr, surface + 0.35f, sz + rr, a, 255);
        }
        pose.popPose();
    }

    /** The smallest fraction drawn while the tank holds anything: a layer 1.5 px thick. */
    public static float minFrac(int h) {
        return 1.5f / Math.max(1f, (h - 1) * 16f);
    }

    /**
     * The lit strip on each of the four upright edges of a formed tank, from the bottom corner node up to the plasma's surface: the
     * frame doubles as a level gauge. The horizontal rails and the corner nodes are lit in the block models.
     */
    private static void gauge(TankPartBlockEntity part, PoseStack pose, MultiBufferSource buffers, int light, float surface, float t) {
        float top = Math.min(surface, part.h() * 16 - 4f);
        if (top <= 4.2f) {
            return;
        }
        MachineKit k = new MachineKit(pose, buffers, light);
        float pulse = 0.8f + 0.2f * Mth.sin(t * 3f);
        float xe = part.w() * 16, ze = part.d() * 16;
        for (float[] c : new float[][]{{-0.3f, 0.9f, -0.3f, 0.9f}, {xe - 0.9f, xe + 0.3f, -0.3f, 0.9f}, {-0.3f, 0.9f, ze - 0.9f, ze + 0.3f}, {xe - 0.9f, xe + 0.3f, ze - 0.9f, ze + 0.3f}}) {
            k.glow(c[0], 4f, c[2], c[1], top, c[3], TQ, pulse);
        }
    }

    /**
     * For a second after the tank forms, a band of turquoise light runs over the shell from the block that completed it, so the
     * change to the formed look reads as a sweep. The blocks themselves switch at once.
     */
    private static void sweep(TankPartBlockEntity part, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
        BlockPos origin = part.origin();
        float age = part.getLevel().getGameTime() - part.formedAt() + partialTick;
        if (origin == null || age < 0 || age >= 24f) {
            return;
        }
        float p = age / 24f;
        BlockPos min = part.getBlockPos();
        int ox = origin.getX() - min.getX(), oy = origin.getY() - min.getY(), oz = origin.getZ() - min.getZ();
        int w = part.w(), h = part.h(), d = part.d();
        int maxD = Math.max(1, Math.max(ox, w - 1 - ox) + Math.max(oy, h - 1 - oy) + Math.max(oz, d - 1 - oz));
        MachineKit k = new MachineKit(pose, buffers, light);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    if (x != 0 && y != 0 && z != 0 && x != w - 1 && y != h - 1 && z != d - 1) {
                        continue;
                    }
                    float at = (Math.abs(x - ox) + Math.abs(y - oy) + Math.abs(z - oz)) / (float) maxD * 0.8f;
                    float f = (p - at) / 0.3f;
                    if (f < 0 || f >= 1) {
                        continue;
                    }
                    k.translucent(buffers, x * 16 - 0.6f, y * 16 - 0.6f, z * 16 - 0.6f, x * 16 + 16.6f, y * 16 + 16.6f, z * 16 + 16.6f, TQ, (1f - f) * 0.6f);
                }
            }
        }
    }

    /** The setting ring on a port's outer faces: turquoise for in and out, blue for input, orange for output; grey while not part of a tank. */
    private static void ring(TankPortBlockEntity port, PoseStack pose, MultiBufferSource buffers, int light) {
        int colour = port.master() == null ? 0x5A6270 : switch (port.mode()) {
            case INPUT -> BLUE;
            case OUTPUT -> ORANGE;
            default -> TQ;
        };
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (port.getLevel() != null && port.getLevel().getBlockState(port.getBlockPos().relative(d)).getBlock() instanceof com.mrgregles.bsp_core.tank.TankBlock) {
                continue; // no ring on a face against the tank
            }
            int a = d.getAxis().ordinal();
            int[] in = switch (d.getAxis()) {
                case X -> new int[]{1, 2};
                case Y -> new int[]{0, 2};
                default -> new int[]{0, 1};
            };
            float[] lo = new float[3], hi = new float[3];
            if (d.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE) {
                lo[a] = 16 + 1.25f;
                hi[a] = 16 + 1.6f;
            } else {
                lo[a] = -1.6f;
                hi[a] = -1.25f;
            }
            for (float[] seg : new float[][]{{2.6f, 2.6f, 13.4f, 3.3f}, {2.6f, 12.7f, 13.4f, 13.4f}, {2.6f, 3.3f, 3.3f, 12.7f}, {12.7f, 3.3f, 13.4f, 12.7f}}) {
                lo[in[0]] = seg[0];
                lo[in[1]] = seg[1];
                hi[in[0]] = seg[2];
                hi[in[1]] = seg[3];
                k.glow(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], colour, 1f);
            }
        }
        pose.popPose();
    }
}
