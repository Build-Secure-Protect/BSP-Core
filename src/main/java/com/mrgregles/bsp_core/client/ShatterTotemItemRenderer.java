package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws the dropped totem the way a dropped item looks: bobbing and slowly turning, a little above the ground. */
public class ShatterTotemItemRenderer extends EntityRenderer<ShatterTotemItemEntity> {
    private final ItemRenderer items;

    public ShatterTotemItemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemRenderer();
        shadowRadius = 0.15F;
        shadowStrength = 0.75F;
    }

    @Override
    public void render(ShatterTotemItemEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float t = entity.tickCount + partialTick;
        float bob = Mth.sin(t / 10f + entity.getId()) * 0.1f + 0.1f;
        pose.translate(0, 0.25f + bob, 0);
        pose.mulPose(Axis.YP.rotation(t / 20f + entity.getId()));
        items.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShatterTotemItemEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
