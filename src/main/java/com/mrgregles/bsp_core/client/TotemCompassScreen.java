package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.TotemCompassMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Totem Compass screen in the Holo HUD style: one tile per coin tier to load tracking time (click to
 * spend one coin of that tier), the stored time, a Start button, and the cooldown with its upgrade.
 * The header shows whether the compass is ready, tracking a rival totem, or cooling down.
 */
public class TotemCompassScreen extends AbstractContainerScreen<TotemCompassMenu> {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A, BAD = 0xFFFF6B5C, MUTED = 0x9AA3B5;
    private static final int TILE_X = 10, TILE_Y = 36, TILE_W = 41, TILE_H = 34, TILE_GAP = 3, BTN_Y = 98, BTN_W = 106, BTN_H = 18;

    public TotemCompassScreen(TotemCompassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 146;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private boolean canStart() {
        return menu.stored() > 0 && menu.trackingLeft() == 0 && menu.cooldownLeft() == 0;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        for (CoinTier tier : CoinTier.values()) {
            if (over(mx, my, x + TILE_X + tier.ordinal() * (TILE_W + TILE_GAP), y + TILE_Y, TILE_W, TILE_H)) {
                press(tier.ordinal());
                return true;
            }
        }
        if (over(mx, my, x + 10, y + BTN_Y, BTN_W, BTN_H) && canStart()) {
            press(TotemCompassMenu.BTN_START);
            return true;
        }
        if (over(mx, my, x + 120, y + BTN_Y, BTN_W, BTN_H) && menu.upgradeCost() >= 0) {
            press(TotemCompassMenu.BTN_UPGRADE);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void button(GuiGraphics g, int x, int y, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, BTN_W, BTN_H);
        g.fill(x, y, x + BTN_W, y + BTN_H, enabled ? TQ : DIM);
        g.fill(x + 1, y + 1, x + BTN_W - 1, y + BTN_H - 1, hover ? 0xFF14362F : SLOT_BG);
        g.drawString(font, label, x + BTN_W / 2 - font.width(label) / 2, y + 5, enabled ? TQ & 0xFFFFFF : 0x6B7385, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        for (CoinTier tier : CoinTier.values()) {
            if (over(mouseX, mouseY, leftPos + TILE_X + tier.ordinal() * (TILE_W + TILE_GAP), topPos + TILE_Y, TILE_W, TILE_H)) {
                g.renderTooltip(font, Component.translatable("gui.bsp_core.compass.tile", Component.translatable("tier.bsp_core." + tier.key), clock(seconds(tier))), mouseX, mouseY);
            }
        }
    }

    private static int seconds(CoinTier tier) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.COMPASS_SECONDS_PER_COIN, List.<Integer>of()), tier.ordinal() + 1, 0);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        for (CoinTier tier : CoinTier.values()) {
            int tx = x + TILE_X + tier.ordinal() * (TILE_W + TILE_GAP), ty = y + TILE_Y;
            boolean hover = over(mouseX, mouseY, tx, ty, TILE_W, TILE_H);
            g.fill(tx, ty, tx + TILE_W, ty + TILE_H, hover ? TQ : DIM);
            g.fill(tx + 1, ty + 1, tx + TILE_W - 1, ty + TILE_H - 1, SLOT_BG);
            g.renderItem(new ItemStack(tier.coin()), tx + TILE_W / 2 - 8, ty + 3);
        }
        int cost = menu.upgradeCost();
        button(g, x + 10, y + BTN_Y, Component.translatable("gui.bsp_core.compass.start"), canStart(), mouseX, mouseY);
        button(g, x + 120, y + BTN_Y, cost < 0 ? Component.translatable("gui.bsp_core.upgrades.maxed") : Component.translatable("gui.bsp_core.compass.upgrade", cost), cost >= 0, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        Component status;
        int colour;
        if (menu.trackingLeft() > 0) {
            status = Component.translatable("gui.bsp_core.compass.hud.tracking", clock(menu.trackingLeft()));
            colour = BAD;
        } else if (menu.cooldownLeft() > 0) {
            status = Component.translatable("gui.bsp_core.compass.hud.cooldown", clock(menu.cooldownLeft()));
            colour = GOLD;
        } else {
            status = Component.translatable("gui.bsp_core.compass.hud.ready");
            colour = TQ;
        }
        g.drawString(font, status, imageWidth - 10 - font.width(status), 8, colour & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.compass.load"), 10, 24, MUTED, false);
        for (CoinTier tier : CoinTier.values()) {
            String add = "+" + clock(seconds(tier));
            g.drawString(font, add, TILE_X + tier.ordinal() * (TILE_W + TILE_GAP) + TILE_W / 2 - font.width(add) / 2, TILE_Y + 22, 0xE8EAF0, false);
        }
        g.drawString(font, Component.translatable("gui.bsp_core.compass.stored", clock(menu.stored())), 10, 80, GOLD & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("gui.bsp_core.compass.cooldown_len", clock(menu.cooldownSeconds()), menu.level()), 10, 124, MUTED, false);
    }

    private static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
