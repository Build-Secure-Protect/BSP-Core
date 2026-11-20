package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import com.mrgregles.bsp_core.compass.TotemCompassMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Totem Compass screen in the Holo HUD style: one tile per coin tier to load tracking time (click to spend one coin of that
 * tier), the stored time, a Start button and the cooldown after tracking. The header shows whether the compass is ready,
 * tracking a rival totem, or cooling down. Under it the REACH panel draws, from the side, how far across and how far up or down
 * the compass finds rival totems. The last line says where the levels come from: a Compass Tuner tunes them into the compass.
 */
public class TotemCompassScreen extends AbstractContainerScreen<TotemCompassMenu> implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A, BAD = 0xFFFF6B5C, MUTED = 0x9AA3B5;
    private static final int TILE_X = 10, TILE_Y = 36, TILE_W = 41, TILE_H = 34, TILE_GAP = 3, BTN_Y = 98, BTN_W = 106, BTN_H = 18;
    /** The REACH panel: its heading, the side view across the whole width, and the line about tuning under it. */
    private static final int REACH_Y = 128, BOX_X = 10, BOX_Y = 140, BOX_W = 216, BOX_H = 52, TUNE_Y = 198;
    private static final int GROUND = 0xFF3D4A3A, GROUND_TEXT = 0x5D6B58, BOX_FILL = 0x3819D3B0;

    public TotemCompassScreen(TotemCompassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 212;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static String blocks(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private int smallWidth(Component text) {
        return Math.round(font.width(text) * 0.75f);
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
        return super.mouseClicked(mx, my, button);
    }

    private void button(GuiGraphics g, int x, int y, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, BTN_W, BTN_H);
        g.fill(x, y, x + BTN_W, y + BTN_H, enabled ? TQ : DIM);
        g.fill(x + 1, y + 1, x + BTN_W - 1, y + BTN_H - 1, hover ? 0xFF14362F : SLOT_BG);
        g.drawString(font, label, x + BTN_W / 2 - font.width(label) / 2, y + 5, enabled ? TQ & 0xFFFFFF : 0x6B7385, false);
    }

    /** The side view: the turquoise box is the reach now, centred on the holder, to the scale of the top levels. */
    private void reachBox(GuiGraphics g, int x0, int y0) {
        int bx = x0 + BOX_X, by = y0 + BOX_Y, cx = bx + BOX_W / 2, cy = by + BOX_H / 2;
        g.fill(bx, by, bx + BOX_W, by + BOX_H, DIM);
        g.fill(bx + 1, by + 1, bx + BOX_W - 1, by + BOX_H - 1, SLOT_BG);
        g.fill(bx + 1, cy - 9, bx + BOX_W - 1, cy - 8, GROUND);
        int top = TotemCompassItem.maxRangeLevel(), aLevel = menu.rangeLevel(Range.ACROSS), dLevel = menu.rangeLevel(Range.DEPTH);
        // the scale's ends: the top level a tuner sells, or a higher one this compass got before the price list was shortened
        double maxA = TotemCompassItem.rangeBlocks(Range.ACROSS, Math.max(top, aLevel)), maxD = TotemCompassItem.rangeBlocks(Range.DEPTH, Math.max(top, dLevel));
        // square roots, so 100 blocks still shows next to 2,500; up or down stays clear of the label rows at the top and bottom
        int hw = Math.max(2, (int) Math.round((BOX_W / 2 - 5) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.ACROSS, aLevel) / maxA)));
        int hh = Math.max(2, (int) Math.round((BOX_H / 2 - 10) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.DEPTH, dLevel) / maxD)));
        g.fill(cx - hw, cy - hh, cx + hw, cy + hh, BOX_FILL);
        g.fill(cx - hw, cy - hh, cx + hw, cy - hh + 1, TQ);
        g.fill(cx - hw, cy + hh - 1, cx + hw, cy + hh, TQ);
        g.fill(cx - hw, cy - hh, cx - hw + 1, cy + hh, TQ);
        g.fill(cx + hw - 1, cy - hh, cx + hw, cy + hh, TQ);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF); // the holder
        g.fill(cx, cy - 4, cx + 1, cy - 2, 0xFFFFFFFF);
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
        button(g, x + 10, y + BTN_Y, Component.translatable("gui.bsp_core.compass.start"), canStart(), mouseX, mouseY);
        g.fill(x + BOX_X, y + REACH_Y + 3, x + imageWidth - 10, y + REACH_Y + 4, DIM); // the heading's rule; the label is drawn over its left end
        reachBox(g, x, y);
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
        small(g, Component.translatable("gui.bsp_core.compass.cooldown_after"), 124, BTN_Y, MUTED);
        g.drawString(font, clock(menu.cooldownSeconds()), 124, BTN_Y + 9, 0xE8EAF0, false);
        reachLabels(g);
        Component tune = Component.translatable(switch (menu.tuneState()) {
            case TotemCompassMenu.TUNED -> "gui.bsp_core.compass.tuned";
            case TotemCompassMenu.UPGRADED_BEFORE_TUNER -> "gui.bsp_core.compass.upgraded_before";
            default -> "gui.bsp_core.compass.untuned";
        });
        small(g, tune, BOX_X, TUNE_Y + 2, menu.tuneState() == TotemCompassMenu.UNTUNED ? GOLD & 0xFFFFFF : TQ & 0xFFFFFF);
    }

    private void reachLabels(GuiGraphics g) {
        Component heading = Component.translatable("gui.bsp_core.compass.reach");
        g.fill(BOX_X, REACH_Y, BOX_X + smallWidth(heading) + 4, REACH_Y + 7, BG); // a gap in the rule for the label
        small(g, heading, BOX_X, REACH_Y + 1, MUTED);
        if (menu.noneInReach()) {
            Component none = Component.translatable("gui.bsp_core.compass.none_in_reach");
            g.fill(imageWidth - 10 - smallWidth(none) - 4, REACH_Y, imageWidth - 10, REACH_Y + 7, BG);
            small(g, none, imageWidth - 10 - smallWidth(none), REACH_Y + 1, BAD & 0xFFFFFF);
        } else if (menu.radiationDetected()) {
            Component felt = Component.translatable("gui.bsp_core.compass.radiation_detected");
            g.fill(imageWidth - 10 - smallWidth(felt) - 4, REACH_Y, imageWidth - 10, REACH_Y + 7, BG);
            small(g, felt, imageWidth - 10 - smallWidth(felt), REACH_Y + 1, GOLD & 0xFFFFFF);
        }
        int aLevel = menu.rangeLevel(Range.ACROSS), dLevel = menu.rangeLevel(Range.DEPTH);
        Component ground = Component.translatable("gui.bsp_core.compass.ground");
        g.fill(BOX_X + 2, BOX_Y + BOX_H / 2 - 17, BOX_X + 4 + smallWidth(ground), BOX_Y + BOX_H / 2 - 10, SLOT_BG); // kept readable over a large reach box
        small(g, ground, BOX_X + 3, BOX_Y + BOX_H / 2 - 16, GROUND_TEXT);
        small(g, Component.translatable("gui.bsp_core.compass.across", blocks(TotemCompassItem.rangeBlocks(Range.ACROSS, aLevel))), BOX_X + 3, BOX_Y + BOX_H - 8, TQ & 0xFFFFFF);
        Component upDown = Component.translatable("gui.bsp_core.compass.up_down", blocks(TotemCompassItem.rangeBlocks(Range.DEPTH, dLevel)));
        small(g, upDown, BOX_X + BOX_W - 3 - smallWidth(upDown), BOX_Y + 3, TQ & 0xFFFFFF);
    }

    private static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }
}
