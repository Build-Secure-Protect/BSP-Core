package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.TotemCompassMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Totem Compass screen: load coins, start tracking, see tracking and cooldown timers, buy cooldown upgrades. */
public class TotemCompassScreen extends AbstractContainerScreen<TotemCompassMenu> {
    private static final int BG = 0xF0171A21, BORDER = 0xFFB58CFF;
    private Button start, upgrade;

    public TotemCompassScreen(TotemCompassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 150;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        int i = 0;
        for (CoinTier tier : CoinTier.values()) {
            final int id = tier.ordinal();
            addRenderableWidget(Button.builder(Component.translatable("tier.bsp_core." + tier.key), b -> press(id))
                    .bounds(leftPos + 8 + (i % 3) * 74, topPos + 42 + (i / 3) * 22, 72, 20).build());
            i++;
        }
        start = addRenderableWidget(Button.builder(Component.translatable("gui.bsp_core.compass.start"), b -> press(TotemCompassMenu.BTN_START))
                .bounds(leftPos + 8, topPos + 96, 108, 20).build());
        upgrade = addRenderableWidget(Button.builder(Component.empty(), b -> press(TotemCompassMenu.BTN_UPGRADE))
                .bounds(leftPos + 120, topPos + 96, 108, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        start.active = menu.stored() > 0 && menu.trackingLeft() == 0 && menu.cooldownLeft() == 0;
        int cost = menu.upgradeCost();
        upgrade.active = cost >= 0;
        upgrade.setMessage(cost < 0 ? Component.translatable("gui.bsp_core.upgrades.maxed") : Component.translatable("gui.bsp_core.compass.upgrade", cost));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, BORDER);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, imageWidth / 2, 8, 0xE3B341);
        Component status;
        if (menu.trackingLeft() > 0) {
            status = Component.translatable("gui.bsp_core.compass.tracking", clock(menu.trackingLeft())).withStyle(ChatFormatting.RED);
        } else if (menu.cooldownLeft() > 0) {
            status = Component.translatable("gui.bsp_core.compass.cooldown", clock(menu.cooldownLeft())).withStyle(ChatFormatting.YELLOW);
        } else {
            status = Component.translatable("gui.bsp_core.compass.ready").withStyle(ChatFormatting.GREEN);
        }
        g.drawCenteredString(font, status, imageWidth / 2, 22, 0xFFFFFF);
        g.drawString(font, Component.translatable("gui.bsp_core.compass.load").withStyle(ChatFormatting.GRAY), 8, 32, 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.compass.stored", clock(menu.stored())).withStyle(ChatFormatting.GOLD), 8, 122, 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.compass.cooldown_len", clock(menu.cooldownSeconds()), menu.level()).withStyle(ChatFormatting.GRAY), 8, 134, 0xFFFFFF, false);
    }

    private static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
