package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrgregles.bsp_core.machine.MultiblockControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Build guide for the multiblock machines. While a controller's structure is incomplete, every
 * missing block is drawn at half size, gently pulsing, in the exact position it must be placed.
 * Placing the real block makes its ghost disappear; when none are left the machine is formed.
 */
public class MultiblockGhostRenderer<T extends MultiblockControllerBlockEntity> implements BlockEntityRenderer<T> {
    public MultiblockGhostRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T controller, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (controller.getLevel() == null) {
            return;
        }
        var missing = controller.missingParts();
        if (missing.isEmpty()) {
            return;
        }
        float time = controller.getLevel().getGameTime() + partialTick;
        float scale = 0.42f + 0.06f * Mth.sin(time * 0.15f);
        BlockPos origin = controller.getBlockPos();
        var renderer = Minecraft.getInstance().getBlockRenderer();
        for (MultiblockControllerBlockEntity.Part part : missing) {
            if (!controller.getLevel().getBlockState(part.pos()).canBeReplaced()) {
                // the wrong block stands here (for example another machine's port): box it in red and float the right block on top
                double x = part.pos().getX() - origin.getX(), y = part.pos().getY() - origin.getY(), z = part.pos().getZ() - origin.getZ();
                float pulse = 0.6f + 0.4f * Mth.sin(time * 0.3f);
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                        new net.minecraft.world.phys.AABB(x, y, z, x + 1, y + 1, z + 1).inflate(0.02), 1f, 0.15f, 0.1f, pulse);
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                        new net.minecraft.world.phys.AABB(x, y, z, x + 1, y + 1, z + 1).inflate(0.06), 1f, 0.15f, 0.1f, pulse);
                pose.pushPose();
                pose.translate(x + 0.5, y + 1.35 + 0.05 * Mth.sin(time * 0.15f), z + 0.5);
                pose.scale(0.3f, 0.3f, 0.3f);
                pose.translate(-0.5, -0.5, -0.5);
                renderer.renderSingleBlock(part.block().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, RenderType.translucent());
                pose.popPose();
                continue;
            }
            pose.pushPose();
            pose.translate(part.pos().getX() - origin.getX() + 0.5, part.pos().getY() - origin.getY() + 0.5, part.pos().getZ() - origin.getZ() + 0.5);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderSingleBlock(part.block().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T controller) {
        return true;
    }
}
