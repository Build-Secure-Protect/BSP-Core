package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.plasma.BatteryChargerBlock;
import com.mrgregles.bsp_core.plasma.BatteryChargerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** The battery or cell standing in the cradle, and the plasma pads above and below it, lit only while a cable feeds the charger. */
public class BatteryChargerRenderer implements BlockEntityRenderer<BatteryChargerBlockEntity> {
    public BatteryChargerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(BatteryChargerBlockEntity charger, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!charger.getBlockState().hasProperty(BatteryChargerBlock.FACING)) {
            return;
        }
        net.minecraft.world.item.ItemStack item = charger.getItems().getStackInSlot(0);
        if (!charger.hasSignal() && item.isEmpty()) {
            return;
        }
        Direction facing = charger.getBlockState().getValue(BatteryChargerBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // the model faces north; turn as the blockstate turns it
        switch (facing) {
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            default -> {
            }
        }
        if (!item.isEmpty()) { // the item stands in the cradle between the pads, facing the open front
            pose.pushPose();
            pose.translate(0, -0.5 + 7.5 / 16.0, 0);
            // block items carry a half-size "fixed" transform of their own, so a battery needs about twice the scale of a cell to fill the cradle
            boolean block = item.getItem() instanceof net.minecraft.world.item.BlockItem;
            float s = block ? 0.95f : 0.34f;
            pose.scale(s, s, s);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(item, net.minecraft.world.item.ItemDisplayContext.FIXED, light, overlay, pose, buffers, charger.getLevel(), 0);
            pose.popPose();
        }
        if (charger.hasSignal()) {
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            pose.translate(-8, -8, -8);
            TextureAtlasSprite sprite = PlasmaRender.sprite();
            VertexConsumer vc = PlasmaRender.buffer(buffers);
            PlasmaRender.box(vc, pose, sprite, 4, 3, 4, 12, 3.6f, 12, 255, 255);
            PlasmaRender.box(vc, pose, sprite, 4, 11.4f, 4, 12, 12, 12, 255, 255);
            PlasmaRender.box(vc, pose, sprite, 6.3f, 6.3f, 15.8f, 9.7f, 9.7f, 16.5f, 255, 255); // the port on the back
        }
        pose.popPose();
    }
}
