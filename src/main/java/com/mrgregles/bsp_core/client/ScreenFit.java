package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.Window;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shrinks a BSP screen when the window is smaller than its panel (a high GUI scale or a small window, down to Minecraft's
 * 320 x 240), so no button or line is cut off at the edge. The screen is laid out in a larger virtual size and drawn scaled
 * down; mouse positions are scaled up to match. Where the panel fits, nothing here does anything.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class ScreenFit {
    private ScreenFit() {}

    /** A screen with a panel of a known size. */
    public interface Fitted {
        int panelWidth();

        int panelHeight();
    }

    private static final int MARGIN = 4;
    /** The scale of the screen being drawn, for scissor rectangles (which ignore the pose). */
    private static float drawing = 1f;

    /** The scale the screen is drawn at: 1 when its panel fits the window. */
    static float scale(Screen screen) {
        if (!(screen instanceof Fitted f)) {
            return 1f;
        }
        Window w = Minecraft.getInstance().getWindow();
        float s = Math.min((w.getGuiScaledWidth() - MARGIN) / (float) f.panelWidth(), (w.getGuiScaledHeight() - MARGIN) / (float) f.panelHeight());
        return Math.max(0.5f, Math.min(1f, s));
    }

    /** {@link GuiGraphics#enableScissor} for a rectangle in the screen's own (possibly scaled) coordinates. */
    static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        float s = drawing;
        g.enableScissor((int) Math.floor(x0 * s), (int) Math.floor(y0 * s), (int) Math.ceil(x1 * s), (int) Math.ceil(y1 * s));
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Pre event) {
        if (event.getScreen() instanceof Fitted) {
            Window w = Minecraft.getInstance().getWindow();
            float s = scale(event.getScreen());
            event.getScreen().width = Math.round(w.getGuiScaledWidth() / s);
            event.getScreen().height = Math.round(w.getGuiScaledHeight() / s);
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof Fitted)) {
            return;
        }
        float s = scale(screen);
        Window w = Minecraft.getInstance().getWindow();
        if (screen.width != Math.round(w.getGuiScaledWidth() / s) || screen.height != Math.round(w.getGuiScaledHeight() / s)) {
            screen.resize(Minecraft.getInstance(), w.getGuiScaledWidth(), w.getGuiScaledHeight()); // the panel changed size: lay out again
        }
        if (s >= 1f) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        drawing = s;
        try {
            screen.renderWithTooltip(g, (int) (event.getMouseX() / s), (int) (event.getMouseY() / s), event.getPartialTick());
        } finally {
            drawing = 1f;
            g.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        float s = scale(event.getScreen());
        if (s < 1f) {
            event.setCanceled(true);
            event.getScreen().mouseClicked(event.getMouseX() / s, event.getMouseY() / s, event.getButton());
        }
    }

    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        float s = scale(event.getScreen());
        if (s < 1f) {
            event.setCanceled(true);
            event.getScreen().mouseReleased(event.getMouseX() / s, event.getMouseY() / s, event.getButton());
        }
    }

    @SubscribeEvent
    public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        float s = scale(event.getScreen());
        if (s < 1f) {
            event.setCanceled(true);
            event.getScreen().mouseDragged(event.getMouseX() / s, event.getMouseY() / s, event.getMouseButton(), event.getDragX() / s, event.getDragY() / s);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        float s = scale(event.getScreen());
        if (s < 1f) {
            event.setCanceled(true);
            event.getScreen().mouseScrolled(event.getMouseX() / s, event.getMouseY() / s, event.getScrollDelta());
        }
    }
}
