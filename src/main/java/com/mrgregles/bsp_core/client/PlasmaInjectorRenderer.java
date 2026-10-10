package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.plasma.PlasmaBoost;
import com.mrgregles.bsp_core.plasma.PlasmaInjectorBlock;
import com.mrgregles.bsp_core.plasma.PlasmaInjectorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The live parts of a Plasma Injector, drawn only while plasma arrives: the plasma standing in the glass barrel (as high as the
 * rate, breathing a little), a slug of plasma running down each of the four feed tubes into the machine, the nozzle tip, the
 * lamp on the cap and the port. The static model points north (nozzle at z 0, cable end at z 16); this turns the same way the
 * blockstate turns the model.
 */
public class PlasmaInjectorRenderer implements BlockEntityRenderer<PlasmaInjectorBlockEntity> {
    private static final float[][] TUBES = {{4, 4}, {10, 4}, {4, 10}, {10, 10}};

    public PlasmaInjectorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PlasmaInjectorBlockEntity injector, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int rate = injector.rate();
        if (rate <= 0 || injector.getLevel() == null || !injector.getBlockState().hasProperty(PlasmaInjectorBlock.FACING)) {
            return;
        }
        Direction facing = injector.getBlockState().getValue(PlasmaInjectorBlock.FACING);
        float time = injector.getLevel().getGameTime() + partialTick;
        float speed = 0.5f + rate / 250f; // strokes per second, with the rate
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // the blockstate rotates the north-pointing model by (x, y) as rotateY(-y) then rotateX(-x); do the same
        switch (facing) {
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(-180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-270));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-270));
            default -> {
            }
        }
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(-8, -8, -8);
        TextureAtlasSprite sprite = PlasmaRender.sprite();
        VertexConsumer vc = PlasmaRender.buffer(buffers);
        // the barrel: full of plasma at the top of the curve, a sliver at a trickle, breathing with the strokes
        float frac = Mth.clamp(0.12f + 0.88f * rate / (float) Math.max(1, PlasmaBoost.topRate()), 0, 1);
        float breathe = 0.04f * Mth.sin(time / 20f * speed * Mth.TWO_PI);
        float z1 = 6.1f + (11.9f - 6.1f) * Mth.clamp(frac + breathe, 0.05f, 1f);
        PlasmaRender.box(vc, pose, sprite, 4.6f, 4.6f, 6.1f, 11.4f, 11.4f, z1, 235, 230);
        // a slug of plasma runs down each feed tube toward the machine once per stroke, the four a quarter-stroke apart
        for (int i = 0; i < TUBES.length; i++) {
            float phase = (time / 20f * speed + i * 0.25f) % 1f;
            float z = 4.4f - 4.6f * phase; // from the manifold end of the tube into the machine's face
            PlasmaRender.box(vc, pose, sprite, TUBES[i][0] + 0.4f, TUBES[i][1] + 0.4f, z - 0.9f, TUBES[i][0] + 1.6f, TUBES[i][1] + 1.6f, z + 0.9f, 255, 255);
        }
        PlasmaRender.box(vc, pose, sprite, 7, 7, -0.5f, 9, 9, 1, 255, 255);                    // the nozzle tip
        PlasmaRender.box(vc, pose, sprite, 7.2f, 12.4f, 12.7f, 8.8f, 13.2f, 13.3f, 255, 255);  // the lamp on the cap
        PlasmaRender.box(vc, pose, sprite, 4.4f, 4.4f, 15.9f, 11.6f, 11.6f, 17.1f, 255, 255);  // the port, filling the ring
        pose.popPose();
    }
}
