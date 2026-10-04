package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * Tetrium Crucible: the burner window glows while fuel (or RF) feeds it, the four induction coils
 * light in sequence, the melt rises in the cup, six collar lamps count progress, the mast tip
 * flashes as a batch finishes, and the slag and nugget trays fill as output builds up.
 */
public class TetriumCrucibleRenderer extends SingleMachineRenderer<TetriumCrucibleBlockEntity> {
    public TetriumCrucibleRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    protected void draw(TetriumCrucibleBlockEntity m, float t) {
        boolean working = m.isWorking(), rf = m.rfActive(), hot = m.isBurning() || (rf && working);
        float p = m.progressFraction();

        k.glow(5, 3.2f, -1.1f, 11, 6.8f, -0.8f, rf ? RF : LAVA, hot ? 0.7f + 0.3f * Mth.sin(t * 9) : 0.15f);
        k.corners(0, 2, 0, 16, 8, 16, VIO, 0.55f + 0.45f * Mth.sin(t * 2));
        float[] coils = {10.9f, 12.6f, 14.3f, 16f};
        for (int i = 0; i < coils.length; i++) {
            float glow = working ? 0.35f + 0.65f * Math.max(0, Mth.sin(t * 5 - i * 0.9f)) : 0.18f;
            k.glowWalls(2.3f, coils[i], 2.3f, 13.7f, coils[i] + 0.8f, 13.7f, 0.7f, 0xFF9A4A, glow);
        }
        float level = working ? 0.12f + 0.88f * p : 0.12f;
        k.glow(4.9f, 10.6f, 4.9f, 11.1f, 10.6f + 6 * level, 11.1f, p > 0.88f ? 0xFFE0A0 : 0xD38BFF, working ? 0.85f + 0.15f * Mth.sin(t * 4) : 0.4f);
        leds(6, 3.4f, 8.2f, 1.55f, 1.6f, 1.1f, working ? Math.round(p * 6) : 0, VIO);
        k.glow(12.8f, 23, 12.8f, 13.8f, 24.4f, 13.8f, p > 0.9f ? 0xFFFFFF : VIO, 1f);
        // tray edges and what has collected in them: slag on one side, nuggets on the other
        k.glow(-3, 3.9f, 4.6f, 0, 4.2f, 5, VIO, 0.8f);
        k.glow(16, 3.9f, 11, 19, 4.2f, 11.4f, VIO, 0.8f);
        pile(m.getItems().getStackInSlot(3).getCount(), -2.7f, 4, 5.6f, 0x3A3340);
        pile(m.getItems().getStackInSlot(2).getCount(), 16.2f, 4, 5.6f, 0xB49CE6);
        if (m.hasRfUpgrade()) {
            capacitor(1.6f, 9.5f, 12.2f, 4.4f, 15, 14.6f, rf, t);
        }
    }
}
