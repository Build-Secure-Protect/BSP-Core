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
 * Totem Compass screen in the Holo HUD style: one tile per coin tier to load tracking time (click to
 * spend one coin of that tier), the stored time, a Start button, and the cooldown with its upgrade.
 * The header shows whether the compass is ready, tracking a rival totem, or cooling down. Under it the REACH panel draws, from
 * the side, how far across and how far up or down the compass finds rival totems, with the next level as a gold outline, and
 * the WIDER and DEEPER keys that buy the next level of each, their prices beneath.
 */
public class TotemCompassScreen extends AbstractContainerScreen<TotemCompassMenu> implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A, BAD = 0xFFFF6B5C, MUTED = 0x9AA3B5;
    private static final int TILE_X = 10, TILE_Y = 36, TILE_W = 41, TILE_H = 34, TILE_GAP = 3, BTN_Y = 98, BTN_W = 106, BTN_H = 18;
    /** The REACH panel: its heading, the side view, and the WIDER and DEEPER keys with their prices underneath. */
    private static final int REACH_Y = 152, BOX_X = 10, BOX_Y = 164, BOX_W = 150, BOX_H = 52, KEY_X = 166, KEY_W = 60, KEY_H = 14, WIDER_Y = 164, DEEPER_Y = 192;
    private static final int GROUND = 0xFF3D4A3A, GROUND_TEXT = 0x5D6B58, BOX_FILL = 0x3819D3B0;

    public TotemCompassScreen(TotemCompassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 222;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** The specific coin and count the next cooldown upgrade costs. */
    private CoinTier upgradeCoin() {
        return com.mrgregles.bsp_core.compass.TotemCompassItem.upgradeCoin(menu.level());
    }

    private boolean canUpgrade() {
        int cost = menu.upgradeCost();
        return cost >= 0 && (minecraft.player.isCreative() || minecraft.player.getInventory().countItem(upgradeCoin().coin()) >= cost);
    }

    private boolean canBuyRange(Range r) {
        int level = menu.rangeLevel(r), cost = TotemCompassItem.rangeCost(level);
        return cost >= 0 && (minecraft.player.isCreative() || minecraft.player.getInventory().countItem(TotemCompassItem.rangeCoin(level).coin()) >= cost);
    }


    private static String blocks(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static int coinColour(CoinTier tier) {
        return switch (tier) {
            case COPPER -> 0xD27D46;
            case GOLD -> 0xFFD23A;
            case DIAMOND -> 0x4AEDD9;
            case NETHERITE -> 0xB8A8A8;
            case ILLYRIUM -> 0x7BE0FF;
        };
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
        if (over(mx, my, x + 120, y + BTN_Y, BTN_W, BTN_H) && canUpgrade()) {
            press(TotemCompassMenu.BTN_UPGRADE);
            return true;
        }
        if (over(mx, my, x + KEY_X, y + WIDER_Y, KEY_W, KEY_H) && canBuyRange(Range.ACROSS)) {
            press(TotemCompassMenu.BTN_WIDER);
            return true;
        }
        if (over(mx, my, x + KEY_X, y + DEEPER_Y, KEY_W, KEY_H) && canBuyRange(Range.DEPTH)) {
            press(TotemCompassMenu.BTN_DEEPER);
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

    /** A WIDER or DEEPER key: the label at full size, centred in its 14 pixels. */
    private void rangeKey(GuiGraphics g, int x, int y, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, KEY_W, KEY_H);
        g.fill(x, y, x + KEY_W, y + KEY_H, enabled ? TQ : DIM);
        g.fill(x + 1, y + 1, x + KEY_W - 1, y + KEY_H - 1, hover ? 0xFF14362F : SLOT_BG);
        g.drawString(font, label, x + KEY_W / 2 - font.width(label) / 2, y + 3, enabled ? TQ & 0xFFFFFF : 0x6B7385, false);
    }

    /** The side view: the turquoise box is the reach now (centred on the holder), the dotted gold lines the next level of each. */
    private void reachBox(GuiGraphics g, int x0, int y0) {
        int bx = x0 + BOX_X, by = y0 + BOX_Y, cx = bx + BOX_W / 2, cy = by + BOX_H / 2;
        g.fill(bx, by, bx + BOX_W, by + BOX_H, DIM);
        g.fill(bx + 1, by + 1, bx + BOX_W - 1, by + BOX_H - 1, SLOT_BG);
        g.fill(bx + 1, cy - 9, bx + BOX_W - 1, cy - 8, GROUND);
        int top = TotemCompassItem.maxRangeLevel(), aLevel = menu.rangeLevel(Range.ACROSS), dLevel = menu.rangeLevel(Range.DEPTH);
        // the scale's ends: the top level for sale, or a higher one this compass bought before the price list was shortened
        double maxA = TotemCompassItem.rangeBlocks(Range.ACROSS, Math.max(top, aLevel)), maxD = TotemCompassItem.rangeBlocks(Range.DEPTH, Math.max(top, dLevel));
        // square roots, so 100 blocks still shows next to 2,500; up or down stays clear of the label rows at the top and bottom
        int hw = Math.max(2, (int) Math.round((BOX_W / 2 - 5) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.ACROSS, aLevel) / maxA)));
        int hh = Math.max(2, (int) Math.round((BOX_H / 2 - 10) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.DEPTH, dLevel) / maxD)));
        g.fill(cx - hw, cy - hh, cx + hw, cy + hh, BOX_FILL);
        g.fill(cx - hw, cy - hh, cx + hw, cy - hh + 1, TQ);
        g.fill(cx - hw, cy + hh - 1, cx + hw, cy + hh, TQ);
        g.fill(cx - hw, cy - hh, cx - hw + 1, cy + hh, TQ);
        g.fill(cx + hw - 1, cy - hh, cx + hw, cy + hh, TQ);
        if (aLevel < top) {
            int nw = (int) Math.round((BOX_W / 2 - 5) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.ACROSS, aLevel + 1) / maxA));
            for (int x = cx - nw; x < cx + nw; x += 3) {
                if (x < cx - hw || x >= cx + hw) {
                    g.fill(x, cy - hh, x + 1, cy - hh + 1, GOLD);
                    g.fill(x, cy + hh - 1, x + 1, cy + hh, GOLD);
                }
            }
            g.fill(cx - nw, cy - hh, cx - nw + 1, cy + hh, GOLD);
            g.fill(cx + nw - 1, cy - hh, cx + nw, cy + hh, GOLD);
        }
        if (dLevel < top) {
            int nh = (int) Math.round((BOX_H / 2 - 10) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.DEPTH, dLevel + 1) / maxD));
            for (int y = cy - nh; y < cy + nh; y += 3) {
                if (y < cy - hh || y >= cy + hh) {
                    g.fill(cx - hw, y, cx - hw + 1, y + 1, GOLD);
                    g.fill(cx + hw - 1, y, cx + hw, y + 1, GOLD);
                }
            }
            g.fill(cx - hw, cy - nh, cx + hw, cy - nh + 1, GOLD);
            g.fill(cx - hw, cy + nh - 1, cx + hw, cy + nh, GOLD);
        }
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF); // the holder
        g.fill(cx, cy - 4, cx + 1, cy - 2, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (over(mouseX, mouseY, leftPos + 120, topPos + BTN_Y, BTN_W, BTN_H) && menu.upgradeCost() >= 0) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.compass.upgrade_tip", clock(BSPConfig.getOr(BSPConfig.COMPASS_COOLDOWN_STEP, 60))), mouseX, mouseY);
        }
        for (Range r : Range.values()) {
            int level = menu.rangeLevel(r);
            if (over(mouseX, mouseY, leftPos + KEY_X, topPos + (r == Range.ACROSS ? WIDER_Y : DEEPER_Y), KEY_W, KEY_H) && level < TotemCompassItem.maxRangeLevel()) {
                g.renderTooltip(font, Component.translatable(r == Range.ACROSS ? "gui.bsp_core.compass.wider_tip" : "gui.bsp_core.compass.deeper_tip",
                        blocks(TotemCompassItem.rangeBlocks(r, level + 1))), mouseX, mouseY);
            }
        }
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
        button(g, x + 120, y + BTN_Y, Component.translatable(cost < 0 ? "gui.bsp_core.upgrades.maxed" : "gui.bsp_core.compass.upgrade"), canUpgrade(), mouseX, mouseY);
        if (cost >= 0 && !minecraft.player.isCreative()) {
            // the price sits under the button: the exact coin and how many
            g.renderItem(new ItemStack(upgradeCoin().coin()), x + 120, y + BTN_Y + BTN_H + 3);
        }
        g.fill(x + BOX_X, y + REACH_Y + 3, x + imageWidth - 10, y + REACH_Y + 4, DIM); // the heading's rule; the label is drawn over its left end
        reachBox(g, x, y);
        rangeKey(g, x + KEY_X, y + WIDER_Y, Component.translatable(TotemCompassItem.rangeCost(menu.rangeLevel(Range.ACROSS)) < 0 ? "gui.bsp_core.upgrades.maxed" : "gui.bsp_core.compass.wider"),
                canBuyRange(Range.ACROSS), mouseX, mouseY);
        rangeKey(g, x + KEY_X, y + DEEPER_Y, Component.translatable(TotemCompassItem.rangeCost(menu.rangeLevel(Range.DEPTH)) < 0 ? "gui.bsp_core.upgrades.maxed" : "gui.bsp_core.compass.deeper"),
                canBuyRange(Range.DEPTH), mouseX, mouseY);
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
        int cost = menu.upgradeCost();
        if (cost >= 0 && minecraft.player.isCreative()) {
            g.drawString(font, Component.translatable("gui.bsp_core.tree.free"), 120, BTN_Y + BTN_H + 8, 0x8FE04A, false);
        } else if (cost >= 0) {
            g.drawString(font, Component.translatable("gui.bsp_core.compass.upgrade_price", cost, Component.translatable("tier.bsp_core." + upgradeCoin().key)),
                    138, BTN_Y + BTN_H + 8, canUpgrade() ? 0xE8EAF0 : BAD & 0xFFFFFF, false);
        }
        g.drawString(font, Component.translatable("gui.bsp_core.compass.cooldown_len", clock(menu.cooldownSeconds()), menu.level()), 10, 142, MUTED, false);
        reachLabels(g);
    }

    private void reachLabels(GuiGraphics g) {
        Component heading = Component.translatable("gui.bsp_core.compass.reach");
        g.fill(BOX_X, REACH_Y, BOX_X + smallWidth(heading) + 4, REACH_Y + 7, BG); // a gap in the rule for the label
        small(g, heading, BOX_X, REACH_Y + 1, MUTED);
        if (menu.noneInReach()) {
            Component none = Component.translatable("gui.bsp_core.compass.none_in_reach");
            g.fill(imageWidth - 10 - smallWidth(none) - 4, REACH_Y, imageWidth - 10, REACH_Y + 7, BG);
            small(g, none, imageWidth - 10 - smallWidth(none), REACH_Y + 1, BAD & 0xFFFFFF);
        }
        int aLevel = menu.rangeLevel(Range.ACROSS), dLevel = menu.rangeLevel(Range.DEPTH);
        Component ground = Component.translatable("gui.bsp_core.compass.ground");
        g.fill(BOX_X + 2, BOX_Y + BOX_H / 2 - 17, BOX_X + 4 + smallWidth(ground), BOX_Y + BOX_H / 2 - 10, SLOT_BG); // kept readable over a large reach box
        small(g, ground, BOX_X + 3, BOX_Y + BOX_H / 2 - 16, GROUND_TEXT);
        small(g, Component.translatable("gui.bsp_core.compass.across", blocks(TotemCompassItem.rangeBlocks(Range.ACROSS, aLevel))), BOX_X + 3, BOX_Y + BOX_H - 8, TQ & 0xFFFFFF);
        Component upDown = Component.translatable("gui.bsp_core.compass.up_down", blocks(TotemCompassItem.rangeBlocks(Range.DEPTH, dLevel)));
        small(g, upDown, BOX_X + BOX_W - 3 - smallWidth(upDown), BOX_Y + 3, TQ & 0xFFFFFF);
        if (aLevel < TotemCompassItem.maxRangeLevel() || dLevel < TotemCompassItem.maxRangeLevel()) {
            Component next = Component.translatable("gui.bsp_core.compass.next");
            small(g, next, BOX_X + BOX_W - 3 - smallWidth(next), BOX_Y + BOX_H - 8, GOLD & 0xFFFFFF);
        }
        rangePrice(g, Range.ACROSS, WIDER_Y + KEY_H + 3);
        rangePrice(g, Range.DEPTH, DEEPER_Y + KEY_H + 3);
    }

    /** The next level's price under its key: the coin's colour, red when the player has too few; nothing at the top. */
    private void rangePrice(GuiGraphics g, Range r, int y) {
        int level = menu.rangeLevel(r), cost = TotemCompassItem.rangeCost(level);
        if (cost < 0) {
            return;
        }
        if (minecraft.player.isCreative()) {
            small(g, Component.translatable("gui.bsp_core.compass.free"), KEY_X + 2, y, 0x8FE04A);
            return;
        }
        CoinTier coin = TotemCompassItem.rangeCoin(level);
        small(g, Component.translatable("gui.bsp_core.compass.range_price", cost, Component.translatable("tier.bsp_core." + coin.key)),
                KEY_X + 2, y, canBuyRange(r) ? coinColour(coin) : BAD & 0xFFFFFF);
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
