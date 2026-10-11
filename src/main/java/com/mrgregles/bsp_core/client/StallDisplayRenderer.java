package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.shop.StallDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Floats the Stall Display's current item at the skin's point (or the admin's size and height), turning slowly and bobbing; nothing while unlinked or closed. */
public class StallDisplayRenderer implements BlockEntityRenderer<StallDisplayBlockEntity> {
    public StallDisplayRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float yRot(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public void render(StallDisplayBlockEntity display, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (display.shown.isEmpty() || display.getLevel() == null) {
            return;
        }
        float t = display.getLevel().getGameTime() + partial;
        pose.pushPose();
        var state = display.getBlockState();
        var skin = display.skin();
        float scale = display.scale();
        // items inside the boiler booth's jar and the street cart's frame have little room: a small bob
        float bob = skin == com.mrgregles.bsp_core.shop.StallSkin.STEAMPUNK || skin == com.mrgregles.bsp_core.shop.StallSkin.YATAI ? 0.015f : 0.05f;
        if (state.hasProperty(com.mrgregles.bsp_core.shop.StallDisplayBlock.FACING)) {
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-yRot(state.getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.FACING))));
            pose.translate(-0.5, 0, -0.5);
        }
        pose.translate(skin.displayItem[0] / 16, display.height() + Mth.sin(t * 0.08f) * bob, skin.displayItem[2] / 16);
        pose.mulPose(Axis.YP.rotationDegrees((t * 1.5f) % 360f));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(display.shown, ItemDisplayContext.GROUND, light, overlay, pose, buffers, display.getLevel(), 0);
        pose.popPose();
    }
}
