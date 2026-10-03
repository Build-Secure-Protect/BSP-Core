package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/** The upgrade tree: one row per buff with its level, and an Upgrade button that shows the next cost. */
public class TotemUpgradeScreen extends AbstractContainerScreen<TotemUpgradeMenu> {
    private static final int PANEL_BG = 0xE0101018;
    private static final int PANEL_BORDER = 0xFFE3B341;
    private static final int ROW_Y = 28;
    private static final int ROW_H = 26;
    private static final int BUTTON_W = 96;
    private static final int TEXT_X = 10;

    private final Map<TotemUpgrades.Buff, Button> buttons = new EnumMap<>(TotemUpgrades.Buff.class);

    private static final TotemUpgrades.Buff[] HAND_BUFFS = java.util.Arrays.stream(TotemUpgrades.Buff.values())
            .filter(b -> !b.placedOnly).toArray(TotemUpgrades.Buff[]::new);

    public TotemUpgradeScreen(TotemUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = ROW_Y + ROW_H * HAND_BUFFS.length + 22;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        int i = 0;
        for (TotemUpgrades.Buff buff : HAND_BUFFS) {
            int y = topPos + ROW_Y + i * ROW_H;
            Button b = Button.builder(Component.empty(),
                            btn -> BSPNetwork.CHANNEL.sendToServer(new UpgradeRequestPacket(menu.getHand(), buff.ordinal())))
                    .bounds(leftPos + imageWidth - BUTTON_W - 8, y - 1, BUTTON_W, 20).build();
            addRenderableWidget(b);
            buttons.put(buff, b);
            i++;
        }
        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        ItemStack totem = menu.getTotem();
        int xp = minecraft.player.experienceLevel;
        boolean creative = minecraft.player.isCreative();
        for (var e : buttons.entrySet()) {
            int level = TotemUpgrades.getLevel(totem, e.getKey());
            int cost = e.getKey().costToUpgrade(level);
            Button b = e.getValue();
            if (cost < 0) {
                b.setMessage(Component.translatable("gui.bsp_core.upgrades.maxed"));
                b.active = false;
            } else {
                b.setMessage(Component.translatable("gui.bsp_core.upgrades.buy_cost", cost));
                b.active = !totem.isEmpty() && (creative || xp >= cost);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, PANEL_BORDER);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, imageWidth / 2, 8, 0xE3B341);
        ItemStack totem = menu.getTotem();
        int i = 0;
        for (TotemUpgrades.Buff buff : HAND_BUFFS) {
            int y = ROW_Y + i * ROW_H;
            int level = TotemUpgrades.getLevel(totem, buff);
            g.drawString(font, Component.translatable(buff.translationKey()).withStyle(ChatFormatting.WHITE), TEXT_X, y, 0xFFFFFF);
            g.drawString(font, Component.translatable("gui.bsp_core.upgrades.level", level, buff.maxLevel()).withStyle(ChatFormatting.GRAY),
                    TEXT_X, y + 11, 0xFFFFFF);
            i++;
        }
        Component xpLine = Component.translatable("gui.bsp_core.upgrades.your_xp", minecraft.player.experienceLevel).withStyle(ChatFormatting.GREEN);
        g.drawCenteredString(font, xpLine, imageWidth / 2, imageHeight - 14, 0xFFFFFF);
    }
}
