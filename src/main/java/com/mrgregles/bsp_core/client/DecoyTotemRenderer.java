package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlock;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws a Decoy Totem differently for different viewers.
 * <ul>
 *   <li>Working, seen by anyone but its owner: the real Shatter Totem model, as an owned totem
 *       facing the way the decoy was placed. Nothing else is drawn, so it cannot be told apart.</li>
 *   <li>Working, seen by its owner: the "Hollow Idol" (an open cage with the magnet core turning
 *       inside) with a faint ghost of the totem around it.</li>
 *   <li>Without power or broken, seen by anyone: the bare idol, dark, and collapsed if broken.</li>
 * </ul>
 */
public class DecoyTotemRenderer implements BlockEntityRenderer<DecoyTotemBlockEntity> {
    private static final int CORE = 0x7FB3FF, DEAD = 0x2B3038;
    private static final float GHOST_ALPHA = 0.3f;

    public DecoyTotemRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DecoyTotemBlockEntity decoy, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (decoy.getLevel() == null) {
            return;
        }
        Player viewer = Minecraft.getInstance().player;
        boolean mine = viewer != null && decoy.isOwner(viewer), active = decoy.isActive(), broken = decoy.isBroken();
        BlockState totem = ModBlocks.SHATTER_TOTEM.get().defaultBlockState()
                .setValue(ShatterTotemBlock.FACING, decoy.getBlockState().getValue(DecoyTotemBlock.FACING)).setValue(ShatterTotemBlock.GLOW, ShatterTotemBlock.Glow.OWNED);
        var blocks = Minecraft.getInstance().getBlockRenderer();
        if (active && !mine) {
            blocks.renderSingleBlock(totem, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            return;
        }
        float t = (decoy.getLevel().getGameTime() + partialTick) / 20f;
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, light);
        float top = broken ? 7 : 13;
        k.box(3, 0, 3, 13, 2, 13, Mat.HULL2);
        for (float[] p : new float[][]{{5, 5}, {10.2f, 5}, {5, 10.2f}, {10.2f, 10.2f}}) {
            k.box(p[0], 2, p[1], p[0] + 0.8f, top, p[1] + 0.8f, Mat.TRIM);
        }
        for (float y : broken ? new float[]{4.5f} : new float[]{4.5f, 8.5f, 12.2f}) {
            k.box(5, y, 5, 11, y + 0.6f, 5.6f, Mat.MID);
            k.box(5, y, 10.4f, 11, y + 0.6f, 11, Mat.MID);
            k.box(5, y, 5.6f, 5.6f, y + 0.6f, 10.4f, Mat.MID);
            k.box(10.4f, y, 5.6f, 11, y + 0.6f, 10.4f, Mat.MID);
        }
        if (broken) { // fallen pieces
            k.box(9, 2, 11.2f, 14, 2.8f, 12, Mat.TRIM);
            k.box(2, 2, 6, 3, 2.8f, 11, Mat.MID);
        }
        pose.pushPose();
        pose.translate(8, broken ? 3.6f : 8 + (active ? Mth.sin(t * 2) * 0.5f : 0), 8);
        if (active) {
            pose.mulPose(Axis.YP.rotation(t * 1.4f));
            pose.mulPose(Axis.XP.rotation(t * 0.9f));
        }
        k.glow(-1.6f, -1.6f, -1.6f, 1.6f, 1.6f, 1.6f, active ? CORE : DEAD, active ? 0.75f + 0.25f * Mth.sin(t * 4) : 1f);
        pose.popPose();
        pose.popPose();
        if (active) {
            // what everyone else sees, shown faintly so the owner can check the disguise
            MultiBufferSource faint = type -> new FaintConsumer(buffers.getBuffer(type), GHOST_ALPHA);
            blocks.renderSingleBlock(totem, pose, faint, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, Sheets.translucentCullBlockSheet());
        }
    }
}
