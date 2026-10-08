package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The plasma inside a cable, seen through the pipe's glass. It sits at a level that rises with what
 * is flowing (full at the projector's need) and its surface ripples along the pipe in the direction
 * the plasma runs. A cable nobody feeds any more (cut off, or its run gone) drains away in a second.
 */
public class PlasmaCableRenderer implements BlockEntityRenderer<PlasmaCableBlockEntity> {
    private static final ResourceLocation PLASMA = new ResourceLocation(BSPCore.MODID, "block/plasma_still");
    private static final float LO = 6.3f, HI = 9.7f; // the cavity inside the 5..11 px pipe, in model pixels
    private static final int SLICES = 8;
    /** Reports come about every two seconds; after this many ticks without one the pipe is taken as cut and drains. */
    private static final int STALE = 55, DRAIN = 20;

    public PlasmaCableRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static final int COPPER = 0xC87A3C, TQ = 0x19D3B0, WHITE = 0xF4F7FA;

    /** The wrench-set ends: a copper collar with a turquoise arrow out (Output) or in (Input), a dark plate for Off, a copper and white collar for Link. */
    private static void ends(PlasmaCableBlockEntity cable, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState state = cable.getBlockState();
        for (Direction d : Direction.values()) {
            PlasmaCableBlockEntity.End end = cable.end(d);
            if (end == PlasmaCableBlockEntity.End.NORMAL) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            switch (d) { // turn so the model's +x is the end's direction
                case WEST -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                case SOUTH -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
                case NORTH -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                case UP -> pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
                case DOWN -> pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
                default -> {
                }
            }
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            pose.translate(-8, -8, -8);
            MachineKit k = new MachineKit(pose, buffers, light);
            boolean joined = state.getValue(TotemCableBlock.SIDES[d.get3DDataValue()]);
            if (end == PlasmaCableBlockEntity.End.OFF || !joined) {
                // a plate over the mouth of the core and a small plug
                k.box(10.6f, 4.6f, 4.6f, 11.4f, 11.4f, 11.4f, MachineKit.Mat.HULL2);
                k.box(11.4f, 6.2f, 6.2f, 11.9f, 9.8f, 9.8f, MachineKit.Mat.TRIM);
            } else {
                // the collar round the arm's mouth: four sides, in copper, or copper and white for a Link
                float x0 = 13.2f, x1 = 15.2f;
                int c = end == PlasmaCableBlockEntity.End.LINK ? WHITE : COPPER;
                k.glow(x0, 10.6f, 4.7f, x1, 11.3f, 11.3f, c, 0.95f);
                k.glow(x0, 4.7f, 4.7f, x1, 5.4f, 11.3f, c, 0.55f);
                k.glow(x0, 5.4f, 4.7f, x1, 10.6f, 5.4f, c, 0.75f);
                k.glow(x0, 5.4f, 10.6f, x1, 10.6f, 11.3f, c, 0.75f);
                if (end == PlasmaCableBlockEntity.End.LINK) {
                    k.glow(x0, 10.6f, 4.7f, x0 + 0.9f, 11.3f, 11.3f, COPPER, 0.95f);
                    k.glow(x0, 4.7f, 4.7f, x0 + 0.9f, 5.4f, 11.3f, COPPER, 0.55f);
                    k.glow(x0, 5.4f, 4.7f, x0 + 0.9f, 10.6f, 5.4f, COPPER, 0.75f);
                    k.glow(x0, 5.4f, 10.6f, x0 + 0.9f, 10.6f, 11.3f, COPPER, 0.75f);
                } else {
                    // the arrow on the top of the collar: a shaft and a wider head, pointing out or in
                    boolean out = end == PlasmaCableBlockEntity.End.OUTPUT;
                    k.glow(11.4f, 11.3f, 7.4f, 15.6f, 11.75f, 8.6f, TQ, 1f);
                    float hx0 = out ? 15.2f : 11.4f, hx1 = out ? 16.1f : 12.3f;
                    k.glow(hx0, 11.3f, 6.3f, hx1, 11.75f, 9.7f, TQ, 1f);
                    k.glow(out ? 16.1f : 10.9f, 11.3f, 7.2f, out ? 16.6f : 11.4f, 11.75f, 8.8f, TQ, 1f);
                }
            }
            pose.popPose();
        }
    }

    @Override
    public void render(PlasmaCableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (cable.getLevel() == null) {
            return;
        }
        if (cable.anyEndSet()) {
            ends(cable, pose, buffers, light);
        }
        if (cable.flow() <= 0) {
            return;
        }
        long age = cable.age();
        if (age > STALE + DRAIN) {
            return;
        }
        float drain = age <= STALE ? 1f : 1f - (age - STALE) / (float) DRAIN;
        float t = (cable.getLevel().getGameTime() + partialTick) / 20f;
        float frac = Mth.clamp(cable.flow() / (float) Math.max(1, BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100)), 0.15f, 1f) * drain;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PLASMA);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        BlockState state = cable.getBlockState();
        Direction out = cable.out(), in = cable.in();
        float depth = (HI - LO) * frac;
        // the core: a wave running from the way in toward the way out
        Direction axis = out != null ? out : in != null ? in.getOpposite() : Direction.EAST;
        wave(vc, pose, sprite, LO, LO, LO, HI, HI, HI, depth, axis, t, drain);
        for (Direction d : Direction.values()) {
            if (!state.getValue(TotemCableBlock.SIDES[d.get3DDataValue()])) {
                continue;
            }
            // the arm's cavity, out to the block edge; the wave runs outward on the way out and inward on the way in
            float[] b = switch (d) {
                case NORTH -> new float[]{LO, LO, 0, HI, HI, LO};
                case SOUTH -> new float[]{LO, LO, HI, HI, HI, 16};
                case WEST -> new float[]{0, LO, LO, LO, HI, HI};
                case EAST -> new float[]{HI, LO, LO, 16, HI, HI};
                case UP -> new float[]{LO, HI, LO, HI, 16, HI};
                case DOWN -> new float[]{LO, 0, LO, HI, LO, HI};
            };
            wave(vc, pose, sprite, b[0], b[1], b[2], b[3], b[4], b[5], depth, d == in ? d.getOpposite() : d, t, drain);
        }
        pose.popPose();
    }

    /**
     * Fills the cavity {@code x0..x1, y0..y1, z0..z1} to {@code depth} from its floor, sliced along {@code along} so each slice's
     * level and brightness ride a wave that travels that way. Vertical cavities fill as a centred column instead.
     */
    private static void wave(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, float depth, Direction along, float t, float drain) {
        boolean vertical = along.getAxis() == Direction.Axis.Y;
        float len = along.getAxis() == Direction.Axis.X ? x1 - x0 : along.getAxis() == Direction.Axis.Y ? y1 - y0 : z1 - z0;
        if (len <= 0) {
            return;
        }
        int sign = along.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        float step = len / SLICES;
        for (int i = 0; i < SLICES; i++) {
            float a = i * step, b = a + step;
            // the wave: position along the pipe minus time, so crests move the way the plasma goes
            float phase = (sign > 0 ? a : len - a) / 3.4f - t * 3.2f;
            float ripple = 0.22f * Mth.sin(phase) * drain, bright = 0.82f + 0.18f * Mth.sin(phase + 1f);
            float d = Mth.clamp(depth + ripple, 0.3f, HI - LO);
            float sx0 = x0, sx1 = x1, sy0 = y0, sy1 = y1, sz0 = z0, sz1 = z1;
            switch (along.getAxis()) {
                case X -> { sx0 = x0 + a; sx1 = x0 + b; }
                case Y -> { sy0 = y0 + a; sy1 = y0 + b; }
                case Z -> { sz0 = z0 + a; sz1 = z0 + b; }
            }
            if (vertical) { // a column whose width breathes with the wave
                float half = d / 2f, cx = (x0 + x1) / 2f, cz = (z0 + z1) / 2f;
                box(vc, pose, sprite, cx - half, sy0, cz - half, cx + half, sy1, cz + half, bright);
            } else {
                box(vc, pose, sprite, sx0, y0, sz0, sx1, y0 + d, sz1, bright);
            }
        }
    }

    private static void box(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1, float y1, float z1, float bright) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int c = Math.round(255 * bright), a = 225;
        float u0 = sprite.getU(0), u1 = sprite.getU(16), v0 = sprite.getV(0), v1 = sprite.getV(16);
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            vc.vertex(m, f[0], f[1], f[2]).color(c, c, c, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[3], f[4], f[5]).color(c, c, c, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[6], f[7], f[8]).color(c, c, c, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
            vc.vertex(m, f[9], f[10], f[11]).color(c, c, c, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nm, f[12], f[13], f[14]).endVertex();
        }
    }
}
