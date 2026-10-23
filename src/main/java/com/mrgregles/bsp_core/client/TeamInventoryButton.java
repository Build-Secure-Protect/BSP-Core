package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * TEAM and KITS buttons in the top left corner of the window, on the survival and the creative inventory, so a player with no
 * key bound and no totem in reach can still open the Team screen and the kit browser. They stay clear of everything else there:
 * each frame they step past any other mod's button or widget on the screen and past the inventory panel with its tab rows, and
 * they are reported to JEI and EMI as areas their overlays leave free (see {@link #areas(Screen)}). Hidden with
 * {@code teamButton} / {@code kitsButton} = false in the client config.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class TeamInventoryButton {
    private TeamInventoryButton() {}

    private static final int W = 40, H = 14, GAP = 2, MARGIN = 2;
    /** Room kept free above and below the inventory panel for the tab rows other mods and the creative screen draw there. */
    private static final int TAB_ROWS = 32;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!BSPClientConfig.teamButton() && !BSPClientConfig.kitsButton()) {
            return;
        }
        if (event.getScreen() instanceof InventoryScreen inv) {
            add(event, inv);
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen inv) {
            add(event, inv);
        }
    }

    /** TEAM, then KITS beside it (the kits button has its own switch). */
    private static void add(ScreenEvent.Init.Post event, AbstractContainerScreen<?> inv) {
        int slot = 0;
        if (BSPClientConfig.teamButton()) {
            event.addListener(new Corner(inv, slot++, Component.translatable("gui.bsp_core.team.key"), Component.translatable("gui.bsp_core.team.button.tip"), TeamKeys::open));
        }
        if (BSPClientConfig.kitsButton()) {
            event.addListener(new Corner(inv, slot, Component.translatable("gui.bsp_core.kits.key"), Component.translatable("gui.bsp_core.kits.button.tip"), KitKeys::open));
        }
    }

    /** Where this screen's buttons stand now, as {x, y, width, height}: for JEI and EMI, which keep their overlays out of them. */
    public static List<int[]> areas(Screen screen) {
        List<int[]> out = new ArrayList<>();
        if (screen != null) {
            for (var child : screen.children()) {
                if (child instanceof Corner c && c.visible) {
                    out.add(new int[]{c.getX(), c.getY(), W, H});
                }
            }
        }
        return out;
    }

    /**
     * The top left corner, free of everything else: the {@code slot}th button's place, counting from the corner along the top
     * edge, stepping right past any obstacle and down a row when the left half of the window is used up. Obstacles are the
     * screen's other widgets (other mods' buttons included) and the inventory panel with room for its tab rows.
     */
    static int[] place(AbstractContainerScreen<?> screen, int slot) {
        List<int[]> blocked = new ArrayList<>();
        for (var r : screen.children()) {
            if (r instanceof AbstractWidget w && !(w instanceof Corner) && w.visible) {
                blocked.add(new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()});
            }
        }
        blocked.add(new int[]{screen.getGuiLeft(), screen.getGuiTop() - TAB_ROWS, screen.getXSize(), screen.getYSize() + 2 * TAB_ROWS});
        return CornerLayout.place(blocked, screen.width, screen.height, slot, W, H, GAP, MARGIN);
    }

    /** A button in the top left corner of the window, placed afresh every frame. */
    private static final class Corner extends Button {
        private final AbstractContainerScreen<?> screen;
        private final int slot;

        Corner(AbstractContainerScreen<?> screen, int slot, Component label, Component tip, Runnable open) {
            super(MARGIN + slot * (W + GAP), MARGIN, W, H, label, b -> open.run(), DEFAULT_NARRATION);
            this.screen = screen;
            this.slot = slot;
            setTooltip(Tooltip.create(tip));
        }

        private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5;

        /** Drawn as the totem panel draws a tab: a dark box with a dim edge that turns teal under the mouse. */
        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int[] at = place(screen, slot);
            setX(at[0]);
            setY(at[1]);
            int x = getX(), y = getY();
            g.fill(x, y, x + W, y + H, isHoveredOrFocused() ? TQ : DIM);
            g.fill(x + 1, y + 1, x + W - 1, y + H - 1, BG);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = getMessage().getString();
            g.pose().pushPose();
            g.pose().translate(x + W / 2f - font.width(label) * 3 / 8f, y + 4, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, label, 0, 0, isHoveredOrFocused() ? TQ & 0xFFFFFF : MUTED, false);
            g.pose().popPose();
        }
    }
}
