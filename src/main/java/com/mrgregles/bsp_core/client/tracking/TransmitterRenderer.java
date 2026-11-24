package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.AnimTime;
import com.mrgregles.bsp_core.client.MeshDraw;
import com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The Remote Control Transmitter's moving parts, the owner's pick "Radio Mast" (P4): a small dish on top of the mast that turns, and a red
 * tip light that blinks while someone operates a machine through it. The base, the card slits, the keys and the mast with its collars are
 * the baked model. Light is sampled from the air above the block.
 */
public class TransmitterRenderer implements BlockEntityRenderer<RemoteTransmitterBlockEntity> {
    private static final float PX = 1 / 16f;

    public TransmitterRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(RemoteTransmitterBlockEntity tx, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (tx.getLevel() == null) {
            return;
        }
        int lit = LevelRenderer.getLightColor(tx.getLevel(), tx.getBlockPos().above());
        float t = AnimTime.ticks(partialTick) / 20f;
        MeshDraw mesh = new MeshDraw(buffers);
        pose.pushPose();
        pose.translate(0.5, 16f * PX, 0.5);
        pose.mulPose(Axis.YP.rotation(t * 0.8f));
        // the dish: a hub, eight petals leaning out, a feed arm and its turquoise horn
        mesh.centred(pose, 0, 1f * PX, 0, 2f * PX, 2f * PX, 2f * PX, MeshDraw.Mat.STEEL, lit);
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 45));
            pose.translate(0, 2.6f * PX, 2.2f * PX);
            pose.mulPose(Axis.XP.rotationDegrees(-35));
            mesh.centred(pose, 0, 0, 0, 2.4f * PX, 0.4f * PX, 3.4f * PX, MeshDraw.Mat.TRIM, lit);
            pose.popPose();
        }
        mesh.centred(pose, 0, 4.5f * PX, 0, 0.5f * PX, 4f * PX, 0.5f * PX, MeshDraw.Mat.STEEL, lit);
        mesh.glowCentred(pose, 0, 6.8f * PX, 0, 1.2f * PX, 1.2f * PX, 1.2f * PX, 0x19D3B0, 1f);
        pose.popPose();
        // the tip light on the mast, under the dish: blinks while in use
        boolean blink = tx.inUse() && ((long) (t * 2)) % 2 == 0;
        if (blink) {
            mesh.glowCentred(pose, 8f * PX, 13.5f * PX, 9.5f * PX, 1.2f * PX, 1.2f * PX, 1.2f * PX, 0xD63B2F, 1f);
        } else {
            mesh.centred(pose, 8f * PX, 13.5f * PX, 9.5f * PX, 1.2f * PX, 1.2f * PX, 1.2f * PX, MeshDraw.Mat.COPPER, lit);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(RemoteTransmitterBlockEntity tx) {
        return true; // the dish reaches above the block
    }
}
