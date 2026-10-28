package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.trade.TradePostBlock;
import com.mrgregles.bsp_core.trade.TradePostBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.Locale;

/**
 * Draws the live parts of the Trade Post (the Exchange Desk) from its controller, in the model's own frame (pixels, front
 * at z 0 facing north, the second column at x 16 to 32): the phosphor text on the screen (TRADE, then who is at the post or
 * who is trading), the four key names lit amber on the sloped deck, the steel A-frame scales on the desk with a gem in one
 * pan and coins in the other, tipping gently while a trade runs, the corner bumpers, the coins in the desk's tray, the
 * drawer bay's tray sliding out during a trade, and the two beacons on the hood, turning while anyone uses the post.
 * Light comes from the air in front of the post; the glowing parts are full bright.
 */
public class TradePostRenderer implements BlockEntityRenderer<TradePostBlockEntity> {
    private static final int PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544;
    private static final int AMBER_RGB = 0xF2B544, GEM_RGB = 0x3EE6DC;
    private final Font font;

    public TradePostRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public void render(TradePostBlockEntity post, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (post.getLevel() == null || !post.getBlockState().hasProperty(TradePostBlock.FACING)) {
            return;
        }
        Direction facing = post.getBlockState().getValue(TradePostBlock.FACING);
        int lit = LevelRenderer.getLightColor(post.getLevel(), post.getBlockPos().relative(facing));
        float t = (post.getLevel().getGameTime() & 0xFFFFF) + partial; // a float keeps a tick's fractions only below about 8 million; wraps every ~14.5 hours
        boolean trading = post.trading(), used = post.users() > 0;
        MeshDraw d = new MeshDraw(buffers);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);   // model pixels from here on

        screen(post, pose, buffers, t);
        keys(pose, buffers);

        // the corner bumpers at the front of the desk
        for (float x : new float[]{0.6f, 31.4f}) {
            cylinder(d, pose, x, 1, 0.6f, 1, 9, MeshDraw.Mat.TRIM, lit);
        }
        // coins in the desk's tray
        for (int i = 0; i < 3; i++) {
            cylinder(d, pose, 27.4f - i * 1.2f, 11.3f, 7.8f, 0.55f, 0.3f, MeshDraw.Mat.GOLD, lit);
        }
        // the drawer bay's tray, sliding out while a trade runs
        float out = trading ? (Mth.sin(t * 0.06f) + 1) / 2 * 2.2f : 0;
        pose.pushPose();
        pose.translate(0, 0, -out);
        d.box(pose, 10, 3.6f, 0.8f, 22, 4.2f, 9, MeshDraw.Mat.MID, lit);
        d.box(pose, 10, 4.2f, 0.8f, 22, 5.4f, 1.4f, MeshDraw.Mat.MID, lit);
        d.box(pose, 15, 4.5f, 0.4f, 17, 5, 0.8f, MeshDraw.Mat.TRIM, lit);
        pose.popPose();

        scales(d, pose, trading ? Mth.sin(t * 0.055f) * 0.13f : 0.05f, lit);

        // the beacons on the hood's back corners, turning while anyone uses the post
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 28.5f : 3.5f;
            cylinder(d, pose, x, 31.6f, 13, 1.5f, 0.8f, MeshDraw.Mat.TRIM, lit);
            pose.pushPose();
            pose.translate(x, 33.2f, 13);
            d.glowCentred(pose, 0, 0, 0, 1.8f, 1.6f, 1.8f, AMBER_RGB, used ? 1f : 0.55f);
            pose.mulPose(Axis.YP.rotation(used ? (i == 0 ? 1 : -1) * t * 0.11f : 0.6f));
            d.centred(pose, 0, 0, 0, 2.3f, 1.75f, 0.5f, MeshDraw.Mat.HULL, lit);
            pose.popPose();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ text

    /** The screen: a 24 x 11 px window at x 4..28, y 18..29, just in front of the recess at z 11.9. Canvas of 96 x 44 units, y down. */
    private void screen(TradePostBlockEntity post, PoseStack pose, MultiBufferSource buffers, float t) {
        pose.pushPose();
        pose.translate(28, 29, 11.8f);
        pose.mulPose(Axis.YP.rotationDegrees(180));   // text reads from the front (north)
        pose.scale(0.25f, -0.25f, 0.25f);
        boolean blink = ((int) (t / 10)) % 2 == 0;
        if (post.trading()) {
            centre(pose, buffers, "TRADE", 48, 6, 2.4f, PHOS, 92);
            centre(pose, buffers, post.first() + " ⇄ " + post.second(), 48, 30, 1f, DIM, 90);
        } else {
            centre(pose, buffers, "TRADE", 48, 8, 3f, PHOS, 92);
            String sub = post.users() > 0 ? post.users() + " AT THIS POST" : BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "local").toUpperCase(Locale.ROOT);
            centre(pose, buffers, blink && post.users() == 0 ? sub + " _" : sub, 48, 33, 0.85f, DIM, 90);
        }
        pose.popPose();
    }

    /** The key names, flat on each key's top, which tilts with the deck (22.5 degrees about x at y 9.2, z 0). */
    private void keys(PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(16, 9.2f, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-22.5f));
        pose.translate(-16, -9.2f, 0);
        for (TradePostBlock.Tab tab : TradePostBlock.Tab.values()) {
            float cx = (float) (tab.x0 + tab.x1) / 2, w = (float) (tab.x1 - tab.x0) - 0.6f;
            pose.pushPose();
            pose.translate(cx, 9.72f, 2.5f);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(-90));   // lying on the key top, reading from the front edge
            Component c = Component.translatable("gui.bsp_core.trade_post.tab." + tab.name().toLowerCase(Locale.ROOT));
            float k = Math.min(w / font.width(c), 1.9f / 8f);
            pose.scale(k, -k, k);
            font.drawInBatch(c, -font.width(c) / 2f, -4, AMBER, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    /** A line centred on canvas x {@code cx} at canvas y {@code y}, {@code k} times the font's size, shrunk to {@code maxW}. */
    private void centre(PoseStack pose, MultiBufferSource buffers, String s, float cx, float y, float k, int colour, float maxW) {
        Component c = Component.literal(s);
        float w = font.width(c);
        if (w * k > maxW) {
            k = maxW / w;
        }
        pose.pushPose();
        pose.translate(cx - w * k / 2, y, 0);
        pose.scale(k, k, 1f);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // ------------------------------------------------------------------ shapes

    /** An upright eight-sided post with its base at {@code y0}. */
    private static void cylinder(MeshDraw d, PoseStack pose, float cx, float y0, float cz, float r, float h, MeshDraw.Mat mat, int light) {
        pose.pushPose();
        pose.translate(cx, y0 + h / 2, cz);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        d.polygon(pose, 8, r / 2, r, h, (float) (Math.PI / 8), mat, light);
        pose.popPose();
    }

    /** A thin bar from p to q. */
    private static void rod(MeshDraw d, PoseStack pose, float[] p, float[] q, float thick, MeshDraw.Mat mat, int light) {
        float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        pose.pushPose();
        pose.translate((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2);
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, dx / len, dy / len, dz / len));
        d.centred(pose, 0, 0, 0, thick, len, thick, mat, light);
        pose.popPose();
    }

    /**
     * The steel A-frame scales on the desk at (16, 10.6, 7.6), seven tenths of their drawn size: a round base, two legs to the
     * pivot, a beam turned by {@code tilt} radians, and a flat pan hanging on three rods under each end, which stays level.
     */
    private static void scales(MeshDraw d, PoseStack pose, float tilt, int lit) {
        pose.pushPose();
        pose.translate(16, 10.6f, 7.6f);
        pose.scale(0.7f, 0.7f, 0.7f);
        cylinder(d, pose, 0, 0, 0, 2.6f, 0.8f, MeshDraw.Mat.HULL2, lit);
        cylinder(d, pose, 0, 0.8f, 0, 1.8f, 0.5f, MeshDraw.Mat.TRIM, lit);
        rod(d, pose, new float[]{-1.8f, 1.2f, 0}, new float[]{0, 9, 0}, 0.4f, MeshDraw.Mat.TRIM, lit);
        rod(d, pose, new float[]{1.8f, 1.2f, 0}, new float[]{0, 9, 0}, 0.4f, MeshDraw.Mat.TRIM, lit);
        d.box(pose, -1.2f, 4.4f, -0.15f, 1.2f, 4.8f, 0.15f, MeshDraw.Mat.TRIM, lit);
        d.centred(pose, 0, 9.5f, 0, 0.9f, 0.9f, 0.9f, MeshDraw.Mat.TRIM, lit);
        pose.pushPose();
        pose.translate(0, 9, 0);
        pose.mulPose(Axis.ZP.rotation(tilt));
        d.box(pose, -6.2f, -0.22f, -0.22f, 6.2f, 0.22f, 0.22f, MeshDraw.Mat.TRIM, lit);
        d.centred(pose, -6.2f, 0, 0, 0.6f, 0.6f, 0.6f, MeshDraw.Mat.TRIM, lit);
        d.centred(pose, 6.2f, 0, 0, 0.6f, 0.6f, 0.6f, MeshDraw.Mat.HULL, lit);
        d.box(pose, -0.12f, 0, -0.12f, 0.12f, 1.8f, 0.12f, MeshDraw.Mat.HULL, lit);
        pose.popPose();
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 6.2f * Mth.cos(tilt), ey = 9 + side * 6.2f * Mth.sin(tilt);
            pose.pushPose();
            pose.translate(ex, ey, 0);
            for (int i = 0; i < 3; i++) {
                double a = i * 2 * Math.PI / 3 + 0.4;
                rod(d, pose, new float[]{0, -0.3f, 0}, new float[]{(float) Math.cos(a) * 2, -4.4f, (float) Math.sin(a) * 2}, 0.12f, MeshDraw.Mat.TRIM, lit);
            }
            cylinder(d, pose, 0, -4.6f, 0, 2.2f, 0.3f, MeshDraw.Mat.TRIM, lit);
            if (side < 0) {
                pose.pushPose();
                pose.translate(0, -3.7f, 0);
                pose.mulPose(Axis.YP.rotationDegrees(45));
                pose.mulPose(Axis.XP.rotationDegrees(35));
                d.glowCentred(pose, 0, 0, 0, 1.1f, 1.1f, 1.1f, GEM_RGB, 0.9f);
                pose.popPose();
            } else {
                cylinder(d, pose, -0.5f, -4.3f, 0.2f, 0.8f, 0.35f, MeshDraw.Mat.GOLD, lit);
                cylinder(d, pose, 0.4f, -3.95f, -0.2f, 0.8f, 0.35f, MeshDraw.Mat.GOLD, lit);
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
