package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * "TOTEM RADIATION DETECTED": a chip above the hotbar while the Totem Compass in either hand is tracking, has no rival totem in
 * reach, and feels a rival's radiation ({@link TotemCompassItem#radiationDetected}). Dark, edged in gold like the Sentinel
 * strip's chips, the compass turning on its left and a slow gold pulse on its right (mock-up look B, 2026-11-09).
 */
public final class CompassRadiationHud implements IGuiOverlay {
    public static final CompassRadiationHud INSTANCE = new CompassRadiationHud();
    /** One full turn of the needle in this state: 4 seconds. */
    public static final int TURN_TICKS = 80;
    private static final int GOLD = 0xFFFFD23A, BG = 0xD90B0D11, H = 15;

    private CompassRadiationHud() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        ItemStack compass = held(mc.player.getItemInHand(InteractionHand.MAIN_HAND));
        if (compass == null) {
            compass = held(mc.player.getItemInHand(InteractionHand.OFF_HAND));
        }
        if (compass == null) {
            return;
        }
        Font font = mc.font;
        String text = Component.translatable("hud.bsp_core.compass.radiation_detected").getString();
        int w = font.width(text) + 30, x = width / 2 - w / 2;
        // 2 px above the game's own action-bar line, which Forge lifts over the hearts, armour and food rows
        // (ForgeGui.renderRecordOverlay: at height - max(max(left, right) + 9, 68), its text from 4 above); above the Recall
        // offer card while that is up
        int y = height - Math.max(Math.max(gui.leftHeight, gui.rightHeight) + 9, 68) - 4 - 2 - H;
        if (RecallHud.showing()) {
            y = Math.min(y, RecallHud.top(height) - 1 - 3 - H);
        }
        g.fill(x, y, x + w, y + H, BG);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + H - 1, x + w, y + H, GOLD);
        g.renderItem(compass, x + 3, y - 1);
        g.drawString(font, text, x + 22, y + 4, GOLD, false);
        float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 2000L) / 2000f * (float) Math.PI * 2f);
        int alpha = Math.round((0.35f + 0.65f * pulse) * 255f);
        g.fill(x + w - 7, y + 6, x + w - 4, y + 9, (alpha << 24) | (GOLD & 0xFFFFFF));
    }

    private static ItemStack held(ItemStack s) {
        return s.is(ModItems.TOTEM_COMPASS.get()) && TotemCompassItem.radiationDetected(s) ? s : null;
    }
}
