package com.mrgregles.bsp_core.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * "NAUGHTY NAUGHTY": the screen a refused steal-back shows (the owner's pick A, a red stamp). For 5 seconds the whole screen is
 * tinted dark red and the words land in huge white letters with a thump and a short shake, then fade over the last second. The
 * villager's "hmm-no" sounds once. Drawn above every other HUD layer; the player can still move and act. A second refusal while
 * it is showing does not start it again.
 */
public final class NaughtyHud implements IGuiOverlay {
    public static final NaughtyHud INSTANCE = new NaughtyHud();
    static final long SHOW_MS = 5000, FADE_MS = 1000, IN_MS = 150, POP_MS = 250, SHAKE_MS = 800;
    /** When the screen started, by {@link Util#getMillis}; 0 when it is not showing. */
    private static long since;

    private NaughtyHud() {}

    /** The packet arrived: start the screen and the sound, unless it is already showing. */
    public static void show() {
        long now = Util.getMillis();
        if (since != 0 && now - since < SHOW_MS) {
            return;
        }
        since = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 1.0F)); // pitch, then volume (the two-number form plays at a quarter)
    }

    /** Opacity at {@code t} ms into the screen: a quick fade in, full, then a fade over the last second. */
    static float alpha(long t) {
        if (t < 0 || t >= SHOW_MS) {
            return 0;
        }
        if (t < IN_MS) {
            return t / (float) IN_MS;
        }
        return t > SHOW_MS - FADE_MS ? (SHOW_MS - t) / (float) FADE_MS : 1;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (since == 0) {
            return;
        }
        long t = Util.getMillis() - since;
        if (t >= SHOW_MS) {
            since = 0;
            return;
        }
        float a = alpha(t);
        if (a <= 0) {
            return; // the very first moment: nothing to draw yet
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // over the hotbar's items and the chat, which are drawn nearer than the HUD's own layer
        g.fill(0, 0, width, height, ((int) (0.62f * 255 * a) << 24) | 0x6E0000);
        g.pose().popPose();
        if (a < 0.03f) {
            return; // text drawn almost clear shows as solid in Minecraft's font, so stop before it does
        }
        Font font = Minecraft.getInstance().font;
        Component words = Component.translatable("gui.bsp_core.naughty");
        int w = font.width(words);
        float fit = (width - 16) / (float) Math.max(1, w);
        float k = Math.max(0.5f, Math.min(4, fit));
        if (t < POP_MS) {
            k = Math.min(k + k * 0.5f * (POP_MS - t) / POP_MS, Math.max(k, width / (float) Math.max(1, w))); // lands from half as big again, never wider than the screen
        }
        float shake = t < SHAKE_MS ? (float) Math.sin(t * 0.06) * 4 * (SHAKE_MS - t) / SHAKE_MS : 0;
        int alpha = (int) (255 * a) << 24;
        g.pose().pushPose();
        g.pose().translate(width / 2f + shake, height / 2f - 4 * k - 10, 400);
        g.pose().scale(k, k, 1);
        g.drawString(font, words, -w / 2 + 1, 1, alpha | 0x5A0000, false);
        g.drawString(font, words, -w / 2, 0, alpha | 0xFFFFFF, false);
        g.pose().popPose();
    }
}
