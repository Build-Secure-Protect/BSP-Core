package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.vault.CoinVaultBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Draws a joined Coin Vault as one "Deposit Box Wall": a single steel body with bright edge bands,
 * and on the door side a gold-framed wall of deposit drawers, four for every vault block of that
 * face, each with a handle and a keyhole light. Only the block at the lowest corner of the box
 * draws; the blocks themselves are hidden while joined. A single vault block keeps its own model.
 */
public class CoinVaultRenderer implements BlockEntityRenderer<CoinVaultBlockEntity> {
    private static final int GOLD = 0xB8922A, KEY = 0x19D3B0;

    public CoinVaultRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CoinVaultBlockEntity vault, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (vault.getLevel() == null || !vault.drawsBox()) {
            return;
        }
        Direction door = vault.door();
        boolean alongX = door.getAxis() == Direction.Axis.Z; // the door face runs along X
        int w = alongX ? vault.boxX() : vault.boxZ(), d = alongX ? vault.boxZ() : vault.boxX(), h = vault.boxY();
        float W = w * 16, H = h * 16, D = d * 16;
        // the blocks are solid, so light is read just outside the middle of the door
        BlockPos p = vault.getBlockPos();
        double cx = p.getX() + vault.boxX() / 2.0, cz = p.getZ() + vault.boxZ() / 2.0;
        BlockPos front = BlockPos.containing(cx + door.getStepX() * (vault.boxX() / 2.0 + 0.5), p.getY() + h / 2.0, cz + door.getStepZ() * (vault.boxZ() / 2.0 + 0.5));
        int lit = LevelRenderer.getLightColor(vault.getLevel(), front);
        float yRot = switch (door) {
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            default -> 0f;
        };
        pose.pushPose();
        pose.translate(vault.boxX() / 2.0, 0, vault.boxZ() / 2.0);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-w / 2.0, 0, -d / 2.0);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        MachineKit k = new MachineKit(pose, buffers, lit);
        float t = (vault.getLevel().getGameTime() + partialTick) / 20f;

        // body, one plate per block so the plating is not stretched
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1) {
                        k.box(x * 16, y * 16, z == 0 ? 0.6f : z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16, Mat.HULL2);
                    }
                }
            }
        }
        // edge bands on all twelve edges
        float e = 0.12f;
        for (float x : new float[]{-e, W - 1 + e}) {
            for (float y : new float[]{-e, H - 1 + e}) {
                k.box(x, y, 0.4f, x + 1, y + 1, D + e, Mat.TRIM);
            }
            k.box(x, 0, 0.4f, x + 1, H, 1.4f, Mat.TRIM);
            k.box(x, 0, D - 1 + e, x + 1, H, D + e, Mat.TRIM);
        }
        for (float y : new float[]{-e, H - 1 + e}) {
            k.box(0, y, 0.4f, W, y + 1, 1.4f, Mat.TRIM);
            k.box(0, y, D - 1 + e, W, y + 1, D + e, Mat.TRIM);
        }
        // gold inlay on top
        k.glow(2, H, 2, W - 2, H + 0.3f, D - 2, GOLD, 0.9f);
        k.box(3, H, 3, W - 3, H + 0.4f, D - 3, Mat.HULL);

        // the wall of deposit boxes
        k.glow(1.5f, 1.5f, -0.2f, W - 1.5f, H - 1.5f, 0.6f, GOLD, 0.9f);
        float cw = (W - 6) / (w * 2), ch = (H - 6) / (h * 2);
        for (int i = 0; i < w * 2; i++) {
            for (int j = 0; j < h * 2; j++) {
                float x = 3 + i * cw, y = 3 + j * ch;
                k.box(x + 0.4f, y + 0.4f, -1, x + cw - 0.4f, y + ch - 0.4f, 0.6f, (i + j) % 2 == 0 ? Mat.MID : Mat.HULL);
                k.box(x + cw / 2 - 1.2f, y + ch - 2.2f, -1.5f, x + cw / 2 + 1.2f, y + ch - 1.4f, -1, Mat.PANEL);
                boolean on = Mth.sin(t * 2 + (i * 7 + j) * 1.7f) > -0.6f;
                k.glow(x + cw / 2 - 0.5f, y + 1.4f, -1.3f, x + cw / 2 + 0.5f, y + 2.4f, -1, KEY, on ? 1f : 0.25f);
            }
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
