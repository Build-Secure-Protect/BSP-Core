package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.zone.AntiTotemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The moving part of the Anti Totem block ("Warden Cube"): a dark cube with glowing faces in the
 * zone's colour, turning and bobbing inside the block's cage. For an admin holding an Anti Totem
 * block in the main hand it also draws the outline of the zone, in the same colour, so that
 * neighbouring zones can be told apart.
 */
public class AntiTotemRenderer implements BlockEntityRenderer<AntiTotemBlockEntity> {
    public AntiTotemRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(AntiTotemBlockEntity zone, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (zone.getLevel() == null) {
            return;
        }
        float t = (zone.getLevel().getGameTime() + partialTick) / 20f;
        int colour = zone.colour();
        pose.pushPose();
        pose.translate(0.5, 0.5 + Mth.sin(t * 1.5f) * 0.04, 0.5);
        pose.mulPose(Axis.YP.rotation(t * 0.8f));
        pose.mulPose(Axis.XP.rotation(t * 0.5f));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);
        k.box(-2.6f, -2.6f, -2.6f, 2.6f, 2.6f, 2.6f, Mat.HULL);
        float glow = 0.7f + 0.3f * Mth.sin(t * 3);
        for (int s = -1; s <= 1; s += 2) {
            k.glow(-2, -2, s * 2.6f - 0.12f, 2, 2, s * 2.6f + 0.12f, colour, glow);
            k.glow(s * 2.6f - 0.12f, -2, -2, s * 2.6f + 0.12f, 2, 2, colour, glow);
            k.glow(-2, s * 2.6f - 0.12f, -2, 2, s * 2.6f + 0.12f, 2, colour, glow);
        }
        pose.popPose();

        Player player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().is(ModItems.ANTI_TOTEM.get()) && Admins.isAdmin(player)) {
            BlockPos p = zone.getBlockPos();
            AABB box = zone.box().move(-p.getX(), -p.getY(), -p.getZ());
            float r = ((colour >> 16) & 0xFF) / 255f, g = ((colour >> 8) & 0xFF) / 255f, b = (colour & 0xFF) / 255f;
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, 1f);
            // a second, slightly smaller box makes the edge read as a thick line from a distance
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box.deflate(0.05), r, g, b, 0.6f);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AntiTotemBlockEntity zone) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
