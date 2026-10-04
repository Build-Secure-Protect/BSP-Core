package com.mrgregles.bsp_core.client;

import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Combination Forge: nine nuggets sit in the mould and fuse into a glowing ingot while the press
 * head rides its lit rails and strikes, faster as the job nears its end. Chase lights on the rails
 * and five bed lamps show progress, a halo above the head and all the glow take the colour of the
 * metal (violet Tetrium, turquoise Illyrium), and two front sockets show the fitted upgrades.
 */
public class CombinationForgeRenderer extends SingleMachineRenderer<CombinationForgeBlockEntity> {
    public CombinationForgeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    protected void draw(CombinationForgeBlockEntity m, float t) {
        boolean working = m.isWorking(), rf = m.rfActive();
        float p = working ? m.progressFraction() : 0;
        ItemStack in = m.getItems().getStackInSlot(0);
        boolean illyrium = in.is(ModItems.ILLYRIUM_NUGGET.get()) && m.hasIllyriumUpgrade();
        int glow = illyrium ? TQ : VIO, light = illyrium ? 0x8CFFE6 : 0xB49CE6, base = illyrium ? 0x19D3B0 : 0x6B4FA3;

        k.corners(0, 1.5f, 0, 16, 6, 16, glow, 0.55f + 0.45f * Mth.sin(t * 2));
        // the charge in the mould shrinks as the ingot grows
        if ((m.isDemo() || !in.isEmpty()) && p < 0.9f) {
            float s = Math.max(0.05f, 1 - p * 1.1f) * 0.65f;
            for (int i = 0; i < 9; i++) {
                float cx = 6.25f + (i % 3) * 1.7f, cz = 4.85f + (i / 3) * 1.7f;
                k.glow(cx - s, 8.5f, cz - s, cx + s, 8.5f + 1.1f * s / 0.65f, cz + s, light, 0.75f);
            }
        }
        if (p > 0.1f) {
            float hw = 2.2f * p, hd = 1.5f * p, heat = 0.55f * (1 - p);
            int hot = Mth.color(Mth.lerp(heat, ((base >> 16) & 0xFF) / 255f, 1f), Mth.lerp(heat, ((base >> 8) & 0xFF) / 255f, 0.69f), Mth.lerp(heat, (base & 0xFF) / 255f, 0.38f));
            k.glow(8 - hw, 8.5f, 6.5f - hd, 8 + hw, 8.5f + 1.4f * Math.max(0.1f, p), 6.5f + hd, hot, 1f);
        }
        // rails, crown, lamps, sockets
        for (int i = 0; i < 7; i++) {
            float on = working && Math.floorMod((int) (t * 8) + i, 7) == 0 ? 1f : 0.3f;
            k.glow(3.6f, 8.5f + i * 2.2f, 11.4f, 3.95f, 9.7f + i * 2.2f, 12.6f, glow, on);
            k.glow(12.05f, 8.5f + i * 2.2f, 11.4f, 12.4f, 9.7f + i * 2.2f, 12.6f, glow, on);
        }
        k.glow(7, 26, 11.4f, 9, 27.6f, 12.6f, glow, 1f);
        leds(5, 2, 6.2f, -0.3f, 1.5f, 1, Math.round(p * 5), glow);
        k.glow(3.1f, 2.7f, -0.95f, 6.1f, 4.5f, -0.6f, m.hasIllyriumUpgrade() ? TQ : OFF, 1f);
        k.glow(9.9f, 2.7f, -0.95f, 12.9f, 4.5f, -0.6f, m.hasRfUpgrade() ? RF : OFF, 1f);
        if (m.hasRfUpgrade()) {
            capacitor(12.8f, 7, 2, 15, 12, 5, rf, t);
        }

        // the press head: parked high when idle, striking while working
        float speed = 3 + p * 6;
        float lift = working ? 1.6f - (float) Math.sqrt(Math.abs(Mth.sin(t * speed))) * 2.6f : 1.6f;
        pose.pushPose();
        pose.translate(0, lift, 0);
        k.box(4.4f, 13, 3, 11.6f, 16, 10, Mat.PANEL);
        k.box(5, 12.5f, 3.6f, 11, 13, 9.4f, Mat.HULL);
        k.box(3.6f, 14, 10, 12.4f, 15.4f, 12.4f, Mat.MID);
        k.box(6.6f, 16, 5, 9.4f, 17, 8, Mat.TRIM);
        k.glowWalls(4.6f, 12.6f, 3.2f, 11.4f, 12.9f, 9.8f, 0.5f, glow, working ? 0.6f + 0.4f * Mth.sin(t * speed * 2) : 0.4f);
        // halo turning above the head
        pose.translate(8, 0, 6.5);
        pose.mulPose(Axis.YP.rotation(working ? t * 1.5f : 0));
        k.glowWalls(-4.6f, 19, -4.6f, 4.6f, 19.7f, 4.6f, 0.7f, glow, working ? 1f : 0.45f);
        pose.popPose();
    }
}
