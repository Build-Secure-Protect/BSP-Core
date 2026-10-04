package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinFactoryMenu;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shatter Coin Factory screen, "Floor Plan" layout: the machine seen from above. Each lane is one
 * slice, in the same left-to-right order as the blocks seen from the front: blank slot at the back
 * (top), a bar that fills toward the front as the coin is pressed, coin tray at the front (bottom),
 * then one pip per Motivator. A lane framed red needs a blank or RF, orange has a full tray. Under
 * the lanes: the shared RF gauge with its intake, the power switch and the owner line.
 */
public class CoinFactoryScreen extends AbstractContainerScreen<CoinFactoryMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GHOST = 0xA010151C;
    private static final int TQ = 0xFF19D3B0, IN = 0xFF3A8BFF, OUT = 0xFFFF8A3A, VIO = 0xFFB58CFF, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5;
    private static final int LANE_TOP = 20, LANE_BOTTOM = 102, BAR_TOP = 50, BAR_H = 22, PIP_Y = 95, GAUGE_X = 14, GAUGE_Y = 108, POWER_X = 274, POWER_Y = 104, POWER_W = 38, POWER_H = 16;
    /** 0 = off, 1 = on; eased toward the real state every frame so the knob slides. */
    private float powerAnim = -1f;

    public CoinFactoryScreen(CoinFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CoinFactoryMenu.WIDTH;
        this.imageHeight = CoinFactoryMenu.INV_Y + 82;
        this.inventoryLabelX = CoinFactoryMenu.INV_X;
        this.inventoryLabelY = CoinFactoryMenu.INV_Y - 11;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int laneLeft(int lane) {
        return CoinFactoryMenu.laneX(lane, menu.sliceCount()) - 6;
    }

    private boolean anyBlocked() {
        for (int i = 0; i < menu.sliceCount(); i++) {
            if (menu.state(i) != CoinFactoryBlockEntity.STATE_PRESSING) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (over(mx, my, leftPos + POWER_X, topPos + POWER_Y, POWER_W, POWER_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CoinFactoryMenu.BTN_POWER);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, n = menu.sliceCount();
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);

        // lanes
        for (int i = 0; i < n; i++) {
            int lx = x + laneLeft(i), sx = lx + 6, state = menu.state(i);
            int frame = state == CoinFactoryBlockEntity.STATE_TRAY_FULL ? OUT : state == CoinFactoryBlockEntity.STATE_PRESSING ? DIM : BAD;
            g.fill(lx, y + LANE_TOP, lx + 28, y + LANE_BOTTOM, frame);
            g.fill(lx + 1, y + LANE_TOP + 1, lx + 27, y + LANE_BOTTOM - 1, SLOT_BG);
            // progress bar, filling from the back (top) toward the tray
            g.fill(sx + 4, y + BAR_TOP, sx + 12, y + BAR_TOP + BAR_H, DIM);
            float p = menu.jobTier(i) == null ? 0 : menu.progress(i);
            g.fill(sx + 4, y + BAR_TOP, sx + 12, y + BAR_TOP + Math.round(BAR_H * Mth.clamp(p, 0, 1)), state == CoinFactoryBlockEntity.STATE_TRAY_FULL ? OUT : TQ);
            for (int k = 0; k < CoinFactoryBlockEntity.MOTIVATOR_CELLS; k++) {
                g.fill(sx + k * 6, y + PIP_Y, sx + k * 6 + 4, y + PIP_Y + 4, k < menu.motivators(i) ? VIO : DIM);
            }
        }

        // slots: blue blanks, orange trays, with a faint blank in empty blank slots
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int edge = i >= n * 2 ? 0xFF3A3F4B : i % 2 == 0 ? IN : OUT;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, edge);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
            if (i < n * 2 && i % 2 == 0 && !slot.hasItem()) {
                g.renderFakeItem(new ItemStack(ModItems.BLANKS.get(CoinTier.COPPER).get()), x + slot.x, y + slot.y);
                g.fill(RenderType.guiGhostRecipeOverlay(), x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GHOST);
            }
        }

        // shared RF gauge
        int lit = Math.round(12 * Mth.clamp(menu.energy() / (float) menu.energyCapacity(), 0, 1));
        for (int i = 0; i < 12; i++) {
            g.fill(x + GAUGE_X + 30 + i * 9, y + GAUGE_Y - 1, x + GAUGE_X + 37 + i * 9, y + GAUGE_Y + 7, i < lit ? GOLD : DIM);
        }

        // power switch
        float target = menu.enabled() ? 1f : 0f;
        powerAnim = powerAnim < 0 ? target : powerAnim + (target - powerAnim) * Math.min(1f, 0.35f * (1f + partialTick));
        int px = x + POWER_X, py = y + POWER_Y, colour = powerAnim > 0.5f ? TQ : BAD;
        g.fill(px - 1, py - 1, px + POWER_W + 1, py + POWER_H + 1, colour);
        g.fill(px, py, px + POWER_W, py + POWER_H, SLOT_BG);
        g.fill(px + 4, py + 6, px + POWER_W - 4, py + POWER_H - 6, DIM);
        g.fill(px + 4, py + 6, px + 4 + Math.round((POWER_W - 8) * powerAnim), py + POWER_H - 6, colour);
        int kx = px + 2 + Math.round((POWER_W - 16) * powerAnim);
        g.fill(kx, py + 2, kx + 12, py + POWER_H - 2, 0xFFC9CED8);
        g.fill(kx + 1, py + 3, kx + 11, py + POWER_H - 3, 0xFF8A909C);
        g.fill(kx + 5, py + 4, kx + 7, py + POWER_H - 4, colour);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int n = menu.sliceCount();
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        String state = !menu.enabled() ? "off" : anyBlocked() ? "attention" : "working";
        Component st = Component.translatable("gui.bsp_core.hud." + state);
        g.drawString(font, st, imageWidth - 10 - font.width(st), 8, state.equals("working") ? TQ & 0xFFFFFF : 0xFF6B5C, false);

        int motivators = 0;
        for (int i = 0; i < n; i++) {
            String num = String.valueOf(i + 1);
            g.drawString(font, num, laneLeft(i) + 14 - font.width(num) / 2, LANE_TOP + 2, MUTED, false);
            motivators += menu.motivators(i);
        }

        g.drawString(font, Component.translatable("gui.bsp_core.hud.rf"), GAUGE_X, GAUGE_Y, MUTED, false);
        small(g, Component.translatable("gui.bsp_core.factory.intake", String.format("%,d", menu.intake()), menu.cables()), GAUGE_X + 142, GAUGE_Y + 1, MUTED);
        Component power = Component.translatable("gui.bsp_core.hud.power");
        small(g, power, POWER_X - 4 - Math.round(font.width(power) * 0.75f), POWER_Y + 5, MUTED);

        CoinFactoryBlockEntity factory = menu.getFactory();
        String owner = factory == null || factory.getOwnerName().isEmpty() ? "-" : factory.getOwnerName();
        small(g, Component.translatable("gui.bsp_core.factory.summary", owner, n, CoinFactoryBlockEntity.MAX_SLICES, menu.ownedCount(),
                BSPConfig.getOr(BSPConfig.FACTORY_MAX_PER_PLAYER, 10), motivators), GAUGE_X, GAUGE_Y + 14, MUTED);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    private List<Component> laneTip(int lane) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.bsp_core.factory.lane", lane + 1));
        CoinTier tier = menu.jobTier(lane);
        switch (menu.state(lane)) {
            case CoinFactoryBlockEntity.STATE_NEEDS_BLANK -> tip.add(Component.translatable("gui.bsp_core.factory.need.blank"));
            case CoinFactoryBlockEntity.STATE_NEEDS_RF -> tip.add(Component.translatable("gui.bsp_core.factory.need.rf_short"));
            case CoinFactoryBlockEntity.STATE_TRAY_FULL -> tip.add(Component.translatable("gui.bsp_core.factory.need.output"));
            case CoinFactoryBlockEntity.STATE_REDSTONE -> tip.add(Component.translatable("gui.bsp_core.factory.redstone"));
            default -> {
                if (tier != null) {
                    tip.add(Component.translatable("gui.bsp_core.factory.pressing", Component.translatable("tier.bsp_core." + tier.key), duration(menu.remainingSeconds(lane))));
                } else {
                    tip.add(Component.translatable("gui.bsp_core.factory.starting"));
                }
            }
        }
        if (tier != null) {
            tip.add(Component.literal(Math.round(menu.progress(lane) * 100) + "%"));
        }
        int m = menu.motivators(lane);
        Number cut = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_MOTIVATOR_REDUCTIONS, List.<Number>of()), m, (Number) 0.0);
        tip.add(Component.translatable("gui.bsp_core.factory.motivators", m, Math.round(cut.doubleValue() * 100)));
        return tip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int x = leftPos, y = topPos;
        if (hoveredSlot == null || (!hoveredSlot.hasItem() && menu.getCarried().isEmpty())) {
            for (int i = 0; i < menu.sliceCount(); i++) {
                if (over(mouseX, mouseY, x + laneLeft(i), y + LANE_TOP, 28, LANE_BOTTOM - LANE_TOP)) {
                    List<Component> tip = laneTip(i);
                    if (hoveredSlot != null) {
                        int index = menu.slots.indexOf(hoveredSlot);
                        tip.add(Component.translatable(index % 2 == 0 ? "gui.bsp_core.factory.slot.input" : "gui.bsp_core.factory.slot.output"));
                    }
                    g.renderComponentTooltip(font, tip, mouseX, mouseY);
                }
            }
        }
        if (over(mouseX, mouseY, x + GAUGE_X, y + GAUGE_Y - 2, 140, 11)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.factory.energy",
                    String.format("%,d", menu.energy()), String.format("%,d", menu.energyCapacity())), mouseX, mouseY);
        }
        if (over(mouseX, mouseY, x + POWER_X, y + POWER_Y, POWER_W, POWER_H)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.hud.power_tip"), mouseX, mouseY);
        }
    }

    static String duration(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m " + s + "s";
        return m + "m " + s + "s";
    }
}
