package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.TotemForgeBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The Totem Forge. Unformed: the ghost build guide. Formed: the part blocks hide and this draws look A,
 * "Hearth and Crucible", as the owner approved it (tools/preview/totem_forge_concepts.html, section 2,
 * function {@code forgeA}), in a 32 by 48 by 32 pixel space with the front at z 0.
 *
 * <p>In that space the controller and the Metal Intake are the column x 16..32, the Forge Hearths x 0..16;
 * the controller's cell is x 16..32, z 0..16. The intake's hatch and fluid flange face out of x 32, the
 * hearths' lava and power sockets out of x 0. The mould stands in the hollow at the totem's real size,
 * its floor at y 10. In demo mode the forge plays the whole sequence on a 30-second loop.
 */
public class TotemForgeRenderer extends MultiblockGhostRenderer<TotemForgeBlockEntity> {
    private static final int TQ = 0x19D3B0, HOT = 0xFFB347, GOLD_CAST = 0xA6863C, ORB = 0x2EC4B6, LAVA = 0xFF7A3A, RF = 0xFFD23A, BLUE = 0x3A8BFF,
            RED = 0xD8322A, LAMP = 0x8FE04A, DARK = 0x101418, OFF = 0x2A2F3A, COPPER = 0xC87A3C, CERAMIC = 0xC9CED8, STEAM = 0xE8EEF7;

    /** The Shatter Totem model's boxes (shatter_totem_geometry.json), one row per mould piece: base, left and right foot, torso, left and right arm (arm and hand), head, core (the orb). */
    static final float[][][] PIECES = {
            {{2, 0, 2, 14, 2, 14}}, {{3, 2, 3, 8, 6, 11}}, {{8, 2, 3, 13, 6, 11}}, {{4, 6, 5, 12, 16, 10}},
            {{1, 8, 2, 4, 12, 9}, {2, 7, -1, 5, 13, 3}}, {{12, 8, 2, 15, 12, 9}, {11, 7, -1, 14, 13, 3}}, {{4, 16, 4, 12, 24, 12}}, {{5, 7, -2, 11, 13, 4}}};

    // the fixed spots drawn every frame, made once
    private static final float[][] RIVETS = {{2.6f, 1.6f}, {28.8f, 1.6f}, {2.6f, 7.6f}, {28.8f, 7.6f}};
    private static final int[] LAMPS = {LAMP, RF, TQ};
    private static final float[] HEARTHS_Z = {8, 24};
    private static final float[][] PILLARS = {{0, 0}, {28, 0}, {0, 28}, {28, 28}};
    private static final float[] PIPE_BANDS = {12, 24, 36};

    public TotemForgeRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** What the forge shows: mould pieces, tank level, pour and set progress. */
    private record Show(int mould, float metal, float pour, float set) {
        boolean done() {
            return set >= 1;
        }

        boolean pouring() {
            return pour > 0 && pour < 1;
        }

        /** The demo loop, as the mock-up's slider: a piece every 5 %, pour 40 to 70 %, set 70 to 95 %, done after. */
        static Show demo(float t) {
            float p = (t % 30f) / 30f * 100f;
            int pieces = Math.min(8, (int) (p / 5));
            float pour = Mth.clamp((p - 40) / 30f, 0, 1), set = Mth.clamp((p - 70) / 25f, 0, 1);
            return new Show((1 << pieces) - 1, p < 40 ? 0.3f + 0.7f * p / 40f : 1 - pour, pour, p >= 95 ? 1 : Math.min(set, 0.999f));
        }
    }

    @Override
    public void render(TotemForgeBlockEntity c, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(c, partialTick, pose, buffers, light, overlay);
        if (!c.isFormed() || c.getLevel() == null) {
            return;
        }
        float t = (c.getLevel().getGameTime() + partialTick) / 20f;
        Show s = c.isDemo() ? Show.demo(t) : new Show(c.mouldMask(), c.metalFraction(), c.pourFraction(), c.setFraction());
        boolean hot = c.isDemo() || c.fluidAmount() > 0, rf = c.rfActive();
        // the controller is a solid block, so its own light reads dark: take the light of the hollow above it
        int lit = LevelRenderer.getLightColor(c.getLevel(), c.getBlockPos().above());

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
        pose.translate(-1, 0, 0); // the controller's cell is x 16..32
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, lit);
        float pulse = 0.75f + 0.25f * Mth.sin(t * 3);

        bottom(k, buffers, c, s, t, hot, rf, pulse);
        hollow(k, s, pulse);
        top(k, pose, s, t, hot);
        mould(k, buffers, s);
        if (s.pouring()) {
            k.glow(15.4f, 10 + s.pour * 24, 15.4f, 16.6f, 34.3f, 16.6f, HOT, 1f);
        }
        if (s.set > 0 && !s.done()) {
            for (int i = 0; i < 7; i++) { // steam off the setting cast
                float f = (t * 0.35f + i / 7f) % 1f, x = 16 + Mth.sin(i * 2.3f) * 5, z = 16 + Mth.cos(i * 1.7f) * 5, y = 30 + f * 8, r = 1 + f * 1.5f;
                k.translucent(buffers, x - r, y - r, z - r, x + r, y + r, z + r, STEAM, 0.45f * (1 - f) * (1 - s.set * 0.6f));
            }
        }
        if (hot) {
            for (int i = 0; i < 5; i++) { // chimney smoke
                float f = (t * 0.25f + i / 5f) % 1f, x = 7 + Mth.sin(i * 1.9f + t) * f * 2, y = 55 + f * 10, r = 0.8f + f * 1.6f;
                k.translucent(buffers, x - r, y - r, 25 - r, x + r, y + r, 25 + r, STEAM, 0.35f * (1 - f));
            }
        }
        pose.popPose();
    }

    private static void bottom(MachineKit k, MultiBufferSource buffers, TotemForgeBlockEntity c, Show s, float t, boolean hot, boolean rf, float pulse) {
        k.box(0, 0, 0, 32, 10, 32, Mat.HULL2);
        k.box(0, 9.3f, 0, 32, 10, 32, Mat.TRIM);
        // front panel across both cells, riveted
        k.box(2, 1, -0.4f, 30, 8.8f, 0, Mat.HULL);
        for (float[] r : RIVETS) {
            k.box(r[0], r[1], -0.6f, r[0] + 0.6f, r[1] + 0.6f, -0.4f, Mat.MID);
        }
        // controller: screen, and lamps for mould complete, tanks full, pour or set under way
        k.glow(18.5f, 4.4f, -0.7f, 28.5f, 8.2f, -0.4f, s.done() ? LAMP : TQ, 0.75f + 0.25f * Mth.sin(t * 3));
        for (int i = 0; i < 3; i++) {
            boolean on = i == 0 ? Integer.bitCount(s.mould) >= 8 : i == 1 ? s.metal >= 0.99f || s.pour > 0 : s.pour > 0;
            k.glow(19.6f + i * 3.4f, 1.8f, -0.75f, 21.6f + i * 3.4f, 3.3f, -0.4f, on ? LAMPS[i] : OFF, 1f);
        }
        // hearth side of the panel: three vents, the red pour button under its clear cover, the lock lever
        for (int i = 0; i < 3; i++) {
            k.glow(3.4f, 5.2f + i * 1.2f, -0.6f, 13.4f, 5.8f + i * 1.2f, -0.4f, LAVA, hot ? 0.7f + 0.3f * Mth.sin(t * 5 + i) : 0.25f);
        }
        k.glow(4.4f, 1.8f, -0.9f, 7.2f, 4.2f, -0.4f, RED, 0.8f);
        k.translucent(buffers, 3.9f, 1.4f, -1.7f, 7.7f, 4.6f, -0.4f, 0xBFE6FF, 0.28f);
        k.box(10, 2, -0.6f, 11.6f, 3.2f, -0.4f, Mat.TRIM);
        k.glow(10.4f, 3.2f, -1.5f, 11.2f, 4.6f, -0.8f, RED, 0.7f);

        // x 32 side, the Metal Intake (z 16..32): item hatch ringed in its side's colour, fluid flange with a valve wheel
        k.box(32, 1.5f, 17, 32.4f, 8.8f, 24.5f, Mat.TRIM);
        k.glow(32.4f, 3, 18.2f, 32.7f, 7.4f, 23.3f, DARK, 1f);
        int ring = portColour(c.getSide(Direction.from3DDataValue(0)));
        k.glow(32.6f, 7.4f, 18.2f, 32.85f, 7.9f, 23.3f, ring, pulse);
        k.glow(32.6f, 2.5f, 18.2f, 32.85f, 3, 23.3f, ring, pulse);
        k.glow(32.6f, 3, 17.7f, 32.85f, 7.4f, 18.2f, ring, pulse);
        k.glow(32.6f, 3, 23.3f, 32.85f, 7.4f, 23.8f, ring, pulse);
        k.box(32, 3.2f, 26.4f, 33.6f, 7.6f, 30.8f, Mat.TRIM);
        k.glow(33.6f, 3.4f, 26.6f, 34, 7.4f, 30.6f, TQ, pulse);
        k.box(34, 4.6f, 27.8f, 36, 6.2f, 29.4f, Mat.MID);
        wheel(k, 36.6f, 5.4f, 28.6f, s.pouring() ? t : 0, true);

        // x 0 side, each Forge Hearth: a lava socket above a power socket wound with copper
        for (float zc : HEARTHS_Z) {
            k.box(-0.4f, 1.5f, zc - 4.5f, 0, 8.8f, zc + 4.5f, Mat.TRIM);
            k.glow(-0.8f, 6, zc - 1.6f, -0.4f, 7.8f, zc + 1.6f, LAVA, hot ? pulse : 0.3f);
            k.glow(-0.8f, 2.4f, zc - 1.6f, -0.4f, 4.8f, zc + 1.6f, RF, rf ? pulse : c.hasRfUpgrade() ? 0.55f : 0.25f);
            k.glow(-1.1f, 2, zc - 2.3f, -0.8f, 2.4f, zc + 2.3f, COPPER, 0.6f);
            k.glow(-1.1f, 4.8f, zc - 2.3f, -0.8f, 5.2f, zc + 2.3f, COPPER, 0.6f);
        }
    }

    private static void hollow(MachineKit k, Show s, float pulse) {
        k.box(5, 10, 5, 27, 10.4f, 27, Mat.MID);
        for (int i = 0; i < 5; i++) {
            k.box(5, 10.4f, 7 + i * 4.4f, 27, 10.6f, 7.8f + i * 4.4f, Mat.HULL);
        }
        for (float[] p : PILLARS) {
            float x = p[0], z = p[1];
            k.box(x, 10, z, x + 4, 38, z + 4, Mat.TRIM);
            k.box(x - 0.2f, 10, z - 0.2f, x + 4.2f, 11, z + 4.2f, Mat.HULL);
            k.box(x - 0.2f, 37, z - 0.2f, x + 4.2f, 38, z + 4.2f, Mat.HULL);
            if (z == 0) { // the front pillars show how full the tanks are
                k.glow(x + 1, 11.5f, -0.25f, x + 3, 36.5f, 0, DARK, 1f);
                if (s.metal > 0.01f) {
                    k.glow(x + 1.3f, 11.8f, -0.4f, x + 2.7f, 11.8f + 24.4f * s.metal, -0.25f, HOT, pulse);
                }
            }
        }
        // back pipes: the hot line up the hearth corner glows as high as the metal stands; the feed line climbs the intake corner
        k.box(-2.6f, 8, 27, -0.6f, 38, 29, Mat.MID);
        if (s.metal > 0.01f) {
            k.glow(-2.75f, 8, 27.4f, -2.6f, 8 + 30 * s.metal, 28.6f, HOT, 0.6f);
        }
        k.box(32.6f, 8, 27, 34.6f, 38, 29, Mat.MID);
        for (float y : PIPE_BANDS) {
            k.box(32.3f, y - 0.3f, 26.7f, 34.9f, y + 0.3f, 29.3f, Mat.TRIM);
            k.box(-2.9f, y - 0.3f, 26.7f, -0.3f, y + 0.3f, 29.3f, Mat.TRIM);
        }
    }

    private static void top(MachineKit k, PoseStack pose, Show s, float t, boolean hot) {
        k.box(0, 38, 0, 32, 48, 32, Mat.HULL2);
        k.box(0, 38, 0, 32, 38.7f, 32, Mat.TRIM);
        k.box(0, 47.3f, 0, 32, 48, 32, Mat.TRIM);
        // windows onto the Melt Tanks: front and x 32 side of the tank column
        k.glow(18, 40, -0.3f, 30, 46, 0, DARK, 1f);
        k.glow(32, 40, 4, 32.3f, 46, 28, DARK, 1f);
        if (s.metal > 0.01f) {
            float lvl = 40.4f + 5.2f * s.metal, glow = 0.85f + 0.15f * Mth.sin(t * 2);
            k.glow(18.5f, 40.4f, -0.45f, 29.5f, lvl, -0.3f, HOT, glow);
            k.glow(32.3f, 40.4f, 4.5f, 32.45f, lvl, 27.5f, HOT, glow);
        }
        for (int i = 0; i < 6; i++) {
            k.box(2 + i * 5.2f, 46.4f, -0.5f, 2.6f + i * 5.2f, 47, -0.3f, Mat.MID);
        }
        // the Pour Head: nozzle under the middle of the top, hand wheel on its front
        k.box(13.8f, 35, 13.8f, 18.2f, 38, 18.2f, Mat.PANEL);
        k.glow(14.9f, 34.3f, 14.9f, 17.1f, 35, 17.1f, s.pouring() ? HOT : OFF, 1f);
        k.box(3, 42.4f, -1, 4, 43.6f, 0, Mat.MID);
        wheel(k, 3.5f, 43, -1.3f, s.pouring() ? t * 2 : 0, false);
        // the Forge Cap: vents and the chimney
        for (int i = 0; i < 3; i++) {
            k.box(2 + i * 4, 48, 18, 4.5f + i * 4, 48.6f, 23, Mat.HULL);
        }
        k.box(5.2f, 48, 23.2f, 8.8f, 54, 26.8f, Mat.HULL);
        k.box(4.6f, 54, 22.6f, 9.4f, 54.6f, 27.4f, Mat.TRIM);
        if (hot) {
            k.glow(5.8f, 53.9f, 23.8f, 8.2f, 54.05f, 26.2f, LAVA, 0.6f);
        }
        // the dial on the feed line, its needle following the tank level
        k.box(34.6f, 28.6f, 26.6f, 35, 31.4f, 29.4f, Mat.PANEL);
        pose.pushPose();
        pose.translate(35.1f, 30, 28);
        pose.mulPose(Axis.XP.rotation(-1.1f + 2.2f * s.metal + (hot ? Mth.sin(t * 9) * 0.05f : 0)));
        k.glow(-0.05f, -0.15f, -0.1f, 0.05f, 1.2f, 0.1f, RED, 1f);
        pose.popPose();
    }

    /** The mould pieces as pale ceramic shells, filled from the bottom as the metal pours; the cast cools from orange to gold as it sets. */
    private static void mould(MachineKit k, MultiBufferSource buffers, Show s) {
        float h = s.done() ? 24 : s.pour * 24;
        int metal = s.set > 0 ? mix(HOT, GOLD_CAST, Math.min(1, s.set * 1.15f)) : HOT;
        float fade = s.set > 0.8f ? 1 - (s.set - 0.8f) * 5 : 1;
        for (int i = 0; i < PIECES.length; i++) {
            if ((s.mould & (1 << i)) == 0 && !s.done()) {
                continue;
            }
            for (float[] b : PIECES[i]) {
                float x0 = b[0] + 8, y0 = b[1] + 10, z0 = b[2] + 8, x1 = b[3] + 8, y1 = b[4] + 10, z1 = b[5] + 8;
                if (i == 7) {
                    k.glow(x0, y0, z0, x1, y1, z1, ORB, 1f);
                    continue;
                }
                float top = Math.min(b[4], h);
                if (top > b[1]) {
                    k.glow(x0, y0, z0, x1, top + 10, z1, metal, s.set < 0.55f ? 1f : 0.8f);
                }
                if (!s.done() && fade > 0) {
                    k.translucent(buffers, x0 - 0.6f, y0 - 0.6f, z0 - 0.6f, x1 + 0.6f, y1 + 0.6f, z1 + 0.6f, CERAMIC, 0.55f * fade);
                }
            }
        }
    }

    /** A four-spoke hand wheel: on the x side when {@code onX}, else on the front, turned by {@code angle}. */
    private static void wheel(MachineKit k, float x, float y, float z, float angle, boolean onX) {
        // spokes as two crossed bars that turn, inside a square rim
        float r = 1.6f, w = 0.28f;
        if (onX) {
            float a = angle % (Mth.PI / 2), c = Mth.cos(a) * r, sn = Mth.sin(a) * r;
            k.glow(x - w, y - c, z - sn, x + w, y + c, z + sn, RED, 0.7f);
            k.glow(x - w, y - sn, z - c, x + w, y + sn, z + c, RED, 0.7f);
            k.glow(x - w, y - r, z - r, x + w, y - r + w, z + r, RED, 0.7f);
            k.glow(x - w, y + r - w, z - r, x + w, y + r, z + r, RED, 0.7f);
        } else {
            k.glow(x - r, y - w, z - w, x + r, y + w, z + w, RED, 0.7f);
            k.glow(x - w, y - r, z - w, x + w, y + r, z + w, RED, 0.7f);
            k.glow(x - r, y - r, z - w, x - r + w, y + r, z + w, RED, 0.7f);
            k.glow(x + r - w, y - r, z - w, x + r, y + r, z + w, RED, 0.7f);
        }
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Socket colour for an item port: blue in, orange out, turquoise both, grey off (as the Illyrium Crucible). */
    private static int portColour(com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> BLUE;
            case OUTPUT -> 0xFF8A3A;
            case BOTH -> TQ;
            default -> 0x565C6B;
        };
    }
}
