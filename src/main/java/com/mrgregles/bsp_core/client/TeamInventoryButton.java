package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TEAM and KITS buttons above the inventory, so a player with no key bound and no totem in reach can
 * still open the Team screen and the kit browser. They follow the inventory panel when the recipe
 * book slides it. Hidden with {@code teamButton} / {@code kitsButton} = false in the client config.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class TeamInventoryButton {
    private TeamInventoryButton() {}

    private static final int W = 40, H = 14;

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
        int dx = 0;
        if (BSPClientConfig.teamButton()) {
            event.addListener(new Follower(inv, dx, Component.translatable("gui.bsp_core.team.key"), Component.translatable("gui.bsp_core.team.button.tip"), TeamKeys::open));
            dx += W + 4;
        }
        if (BSPClientConfig.kitsButton()) {
            event.addListener(new Follower(inv, dx, Component.translatable("gui.bsp_core.kits.key"), Component.translatable("gui.bsp_core.kits.button.tip"), KitKeys::open));
        }
    }

    /** A button that sits just above the panel's top left corner, wherever the panel is this frame. */
    private static final class Follower extends Button {
        private final AbstractContainerScreen<?> screen;
        private final int dx;

        Follower(AbstractContainerScreen<?> screen, int dx, Component label, Component tip, Runnable open) {
            super(screen.getGuiLeft() + dx, screen.getGuiTop() - H - 2, W, H, label, b -> open.run(), DEFAULT_NARRATION);
            this.screen = screen;
            this.dx = dx;
            setTooltip(Tooltip.create(tip));
        }

        private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5;

        /** Drawn as the totem panel draws a tab: a dark box with a dim edge that turns teal under the mouse. */
        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            setX(screen.getGuiLeft() + dx);
            setY(screen.getGuiTop() - H - 2);
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
