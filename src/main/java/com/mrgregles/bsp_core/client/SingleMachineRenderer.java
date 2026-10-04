package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.MachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Base for the two single-block machines. Their hull is an ordinary block model; this adds the parts
 * that move or light up, in the same 16-pixel space as the model (front at z = 0), following the
 * approved concept render (tools/preview/progression.html).
 */
public abstract class SingleMachineRenderer<T extends MachineBlockEntity> implements BlockEntityRenderer<T> {
    protected static final int VIO = 0xB58CFF, TQ = 0x19D3B0, LAVA = 0xFF7A1A, RF = 0xFFD23A, OFF = 0x2B3038;

    protected MachineKit k;
    protected PoseStack pose;

    @Override
    public final void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (machine.getLevel() == null) {
            return;
        }
        Direction facing = machine.getBlockState().hasProperty(MachineBlock.FACING) ? machine.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
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
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        this.pose = pose;
        this.k = new MachineKit(pose, buffers, light);
        draw(machine, (machine.getLevel().getGameTime() + partialTick) / 20f);
        pose.popPose();
    }

    /** Draws the animated parts. {@code t} is time in seconds. */
    protected abstract void draw(T machine, float t);

    /** A row of progress lamps: the first {@code lit} in {@code colour}, the rest dark. */
    protected void leds(int n, float x0, float y, float z, float step, float w, int lit, int colour) {
        for (int i = 0; i < n; i++) {
            k.glow(x0 + i * step, y, z, x0 + i * step + w, y + 1.1f, z + 0.5f, i < lit ? colour : OFF, 1f);
        }
    }

    /** Up to {@code count} small cubes heaped in a tray. */
    protected void pile(int count, float x0, float y0, float z0, int colour) {
        for (int i = 0; i < Math.min(9, count); i++) {
            float x = x0 + (i % 3) * 1.5f, z = z0 + (i / 3 % 2) * 1.6f + (i % 2) * 0.5f, y = y0 + (i / 6) * 1.1f;
            k.glow(x, y, z, x + 1.2f, y + 1.1f, z + 1.2f, colour, 0.8f);
        }
    }

    /** The RF upgrade as a small capacitor with three bands that pulse while it is feeding the machine. */
    protected void capacitor(float x0, float y0, float z0, float x1, float y1, float z1, boolean active, float t) {
        k.box(x0, y0, z0, x1, y1, z1, Mat.HULL2);
        for (float f : new float[]{0.25f, 0.5f, 0.75f}) {
            float y = y0 + (y1 - y0) * f;
            k.glow(x0 - 0.2f, y - 0.4f, z0 - 0.2f, x1 + 0.2f, y + 0.4f, z1 + 0.2f, RF, active ? 0.5f + 0.5f * Mth.sin(t * 6 + f * 9) : 0.3f);
        }
        k.box(x0 + 0.6f, y1, z0 + 0.6f, x1 - 0.6f, y1 + 0.8f, z1 - 0.6f, Mat.TRIM);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
