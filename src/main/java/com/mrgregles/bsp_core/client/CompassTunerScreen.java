package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.Ladder;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.TuneCheck;
import com.mrgregles.bsp_core.compass.CompassTunerMenu;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import com.mrgregles.bsp_core.compass.TunerMaterials;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The Compass Tuner's screen, concept C (the Dial) with the REACH side view under the dial. Left: the dial with the compass in
 * the middle, the progress round the rim with a gold hand, the material slots on the dial (each with the amount a tune takes
 * under it), and under it the reach the tuner gives with the next level dotted in gold. Right: the three cards that tune into a
 * compass, the Tune speed card apart from them, FE and plasma, and TUNE on its own at the bottom.
 */
public class CompassTunerScreen extends AbstractContainerScreen<CompassTunerMenu> implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A, BAD = 0xFFFF6B5C,
            MUTED = 0x9AA3B5, TEXT = 0xE8EAF0, OFF = 0x6B7385, RED_BAR = 0xFFC0392B, PLASMA = 0xFF4FB8FF, PURPLE = 0xFFC08CFF, DIAL = 0xFF0B1016;
    private static final int KX = 140, CARD_W = 90, CARD_H = 26, KEY_W = 20, KEY_H = 11;
    /** Card tops, in ladder order: the three that tune into a compass, then Tune speed under its own heading. */
    private static final int[] CARD_Y = {32, 61, 90, 131};
    private static final int FE_Y = 162, TUNE_Y = 196, TUNE_H = 18;
    private static final int REACH_Y = CompassTunerMenu.DIAL_Y + CompassTunerMenu.DIAL_R + 18, VIEW_X = 10, VIEW_Y = REACH_Y + 14, VIEW_W = 124, VIEW_H = 62;
    private static final int BANNER_Y = REACH_Y + 89;
    private static final int GROUND = 0xFF3D4A3A, GROUND_TEXT = 0x5D6B58, BOX_FILL = 0x3819D3B0;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public CompassTunerScreen(CompassTunerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CompassTunerMenu.WIDTH;
        this.imageHeight = CompassTunerMenu.HEIGHT;
        this.inventoryLabelX = CompassTunerMenu.INV_X;
        this.inventoryLabelY = CompassTunerMenu.INV_Y - 10;
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }

    // ------------------------------------------------------------------ helpers

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    private void smallCentred(GuiGraphics g, Component text, int cx, int y, int colour) {
        small(g, text, cx - smallWidth(text) / 2, y, colour);
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

    private static int ladderColour(Ladder l) {
        return switch (l) {
            case ACROSS -> TQ;
            case DEPTH -> GOLD;
            case COOLDOWN -> PURPLE;
            case SPEED -> PLASMA;
        };
    }

    private static String blocks(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** "1 h 30 m", "45 m", "2 h". */
    private static Component minutes(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) {
            return Component.translatable("gui.bsp_core.compass_tuner.m", m);
        }
        return m == 0 ? Component.translatable("gui.bsp_core.compass_tuner.h", h) : Component.translatable("gui.bsp_core.compass_tuner.hm", h, m);
    }

    private static String clock(int seconds) {
        int h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** What a level of {@code ladder} gives, as shown on its card. */
    private static Component value(Ladder ladder, int level) {
        return switch (ladder) {
            case ACROSS -> Component.translatable("gui.bsp_core.compass_tuner.blocks", blocks(TotemCompassItem.rangeBlocks(Range.ACROSS, level)));
            case DEPTH -> Component.translatable("gui.bsp_core.compass_tuner.blocks", blocks(TotemCompassItem.rangeBlocks(Range.DEPTH, level)));
            case COOLDOWN -> Component.literal(clock(TotemCompassItem.cooldownSeconds(level)));
            case SPEED -> minutes(CompassTunerBlockEntity.tuneMinutes(level));
        };
    }

    private boolean canBuy(Ladder ladder) {
        int level = menu.level(ladder), cost = ladder.cost(level);
        return cost >= 0 && (minecraft.player.isCreative() || minecraft.player.getInventory().countItem(ladder.coin(level).coin()) >= cost);
    }

    private int cardY(Ladder l) {
        return CARD_Y[l.ordinal()];
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ladder l : Ladder.values()) {
            if (over(mx, my, leftPos + KX + CARD_W - KEY_W - 4, topPos + cardY(l) + 3, KEY_W, KEY_H) && canBuy(l)) {
                press(l.ordinal());
                return true;
            }
        }
        if (over(mx, my, leftPos + KX, topPos + TUNE_Y, CARD_W, TUNE_H) && menu.check() == TuneCheck.OK) {
            press(CompassTunerMenu.BTN_TUNE);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        for (Ladder l : Ladder.values()) {
            int level = menu.level(l);
            if (over(mouseX, mouseY, leftPos + KX + CARD_W - KEY_W - 4, topPos + cardY(l) + 3, KEY_W, KEY_H) && level < l.max()) {
                g.renderTooltip(font, Component.translatable("gui.bsp_core.compass_tuner.next_tip." + l.name().toLowerCase(Locale.ROOT), value(l, level + 1)), mouseX, mouseY);
            }
        }
        for (int i = 0; i < CompassTunerBlockEntity.MATERIAL_SLOTS; i++) {
            TunerMaterials.Entry e = TunerMaterials.forSlot(i);
            int[] p = CompassTunerMenu.SLOT_POS[1 + i];
            if (e != null && menu.getSlot(1 + i).getItem().isEmpty() && over(mouseX, mouseY, leftPos + p[0], topPos + p[1], 16, 16)) {
                g.renderTooltip(font, Component.translatable("gui.bsp_core.compass_tuner.material_tip", e.count(), e.icon().getHoverName()), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.drawManaged(() -> {
            dial(g, x, y); // many small fills: drawn as one batch
            reachView(g, x, y);
        });
        for (Ladder l : Ladder.values()) {
            card(g, x + KX, y + cardY(l), l, mouseX, mouseY);
        }
        bar(g, x + KX + 16, y + FE_Y, menu.fe() / 1000.0, RED_BAR);
        bar(g, x + KX + 16, y + FE_Y + 10, menu.plasma() / 1000.0, PLASMA);
        TuneCheck check = menu.check();
        boolean on = check == TuneCheck.OK, hover = on && over(mouseX, mouseY, x + KX, y + TUNE_Y, CARD_W, TUNE_H);
        g.fill(x + KX, y + TUNE_Y, x + KX + CARD_W, y + TUNE_Y + TUNE_H, on ? TQ : DIM);
        g.fill(x + KX + 1, y + TUNE_Y + 1, x + KX + CARD_W - 1, y + TUNE_Y + TUNE_H - 1, hover ? 0xFF14362F : SLOT_BG);
        // slot wells: the big compass well and the material wells with a faint picture of what goes in
        g.fill(x + CompassTunerMenu.DIAL_X - 13, y + CompassTunerMenu.DIAL_Y - 13, x + CompassTunerMenu.DIAL_X + 13, y + CompassTunerMenu.DIAL_Y + 13, DIM);
        g.fill(x + CompassTunerMenu.DIAL_X - 12, y + CompassTunerMenu.DIAL_Y - 12, x + CompassTunerMenu.DIAL_X + 12, y + CompassTunerMenu.DIAL_Y + 12, SLOT_BG);
        for (int i = 0; i < CompassTunerBlockEntity.MATERIAL_SLOTS; i++) {
            TunerMaterials.Entry e = TunerMaterials.forSlot(i);
            if (e == null && menu.getSlot(1 + i).getItem().isEmpty()) {
                continue;
            }
            int[] p = CompassTunerMenu.SLOT_POS[1 + i];
            g.fill(x + p[0] - 1, y + p[1] - 1, x + p[0] + 17, y + p[1] + 17, DIM);
            g.fill(x + p[0], y + p[1], x + p[0] + 16, y + p[1] + 16, SLOT_BG);
            if (e != null && menu.getSlot(1 + i).getItem().isEmpty()) {
                g.renderItem(e.icon(), x + p[0], y + p[1]);
                g.fill(x + p[0], y + p[1], x + p[0] + 16, y + p[1] + 16, 0xA00C0E12); // dimmed: a hint, not an item
            }
        }
        // inventory wells
        for (int row = 0; row < 4; row++) {
            int sy = y + CompassTunerMenu.INV_Y + (row == 3 ? 58 : row * 18);
            for (int col = 0; col < 9; col++) {
                int sx = x + CompassTunerMenu.INV_X + col * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DIM);
                g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
            }
        }
    }

    private void bar(GuiGraphics g, int x, int y, double fraction, int colour) {
        int w = 74, h = 7;
        g.fill(x, y, x + w, y + h, DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BG);
        g.fill(x + 1, y + 1, x + 1 + (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction))), y + h - 1, colour);
    }

    /** The dial: ticks round the rim, the progress arc and its gold hand. */
    private void dial(GuiGraphics g, int x0, int y0) {
        int cx = x0 + CompassTunerMenu.DIAL_X, cy = y0 + CompassTunerMenu.DIAL_Y, r = CompassTunerMenu.DIAL_R;
        disc(g, cx, cy, r, DIM);
        disc(g, cx, cy, r - 2, DIAL);
        for (int k = 0; k < 60; k++) {
            double a = k / 60.0 * Math.PI * 2 - Math.PI / 2;
            int len = k % 5 == 0 ? 6 : 3;
            for (int s = 0; s < len; s++) {
                dot(g, cx + Math.cos(a) * (r - 3 - s), cy + Math.sin(a) * (r - 3 - s), k % 5 == 0 ? 0xFF9AA3B5 : DIM);
            }
        }
        double progress = menu.tuning() ? menu.progress() : 0;
        int ring = r - 12;
        boolean moving = menu.tuning() && menu.stall() == CompassTunerBlockEntity.Stall.NONE;
        for (int step = 0; step < 360; step++) {
            double a = step / 360.0 * Math.PI * 2 - Math.PI / 2;
            int colour = step / 360.0 < progress ? (moving ? TQ : 0xFF6B7385) : 0xFF1A2A2A;
            for (int w = -1; w <= 1; w++) {
                dot(g, cx + Math.cos(a) * (ring + w), cy + Math.sin(a) * (ring + w), colour);
            }
        }
        if (progress > 0) {
            double a = progress * Math.PI * 2 - Math.PI / 2;
            for (int s = 14; s < r - 8; s++) {
                dot(g, cx + Math.cos(a) * s, cy + Math.sin(a) * s, GOLD);
            }
        }
    }

    private static void dot(GuiGraphics g, double x, double y, int colour) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, colour);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    /** The REACH view from layout B: the tuner's reach as a turquoise box, the next level of each dotted in gold. */
    private void reachView(GuiGraphics g, int x0, int y0) {
        int bx = x0 + VIEW_X, by = y0 + VIEW_Y, cx = bx + VIEW_W / 2, cy = by + VIEW_H / 2;
        g.fill(x0 + VIEW_X, y0 + REACH_Y + 3, x0 + VIEW_X + VIEW_W, y0 + REACH_Y + 4, DIM);
        g.fill(bx, by, bx + VIEW_W, by + VIEW_H, DIM);
        g.fill(bx + 1, by + 1, bx + VIEW_W - 1, by + VIEW_H - 1, SLOT_BG);
        g.fill(bx + 1, cy - 9, bx + VIEW_W - 1, cy - 8, GROUND);
        int top = TotemCompassItem.maxRangeLevel(), a = menu.level(Ladder.ACROSS), d = menu.level(Ladder.DEPTH);
        double maxA = TotemCompassItem.rangeBlocks(Range.ACROSS, Math.max(top, a)), maxD = TotemCompassItem.rangeBlocks(Range.DEPTH, Math.max(top, d));
        int hw = Math.max(2, (int) Math.round((VIEW_W / 2 - 5) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.ACROSS, a) / maxA)));
        int hh = Math.max(2, (int) Math.round((VIEW_H / 2 - 10) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.DEPTH, d) / maxD)));
        g.fill(cx - hw, cy - hh, cx + hw, cy + hh, BOX_FILL);
        g.fill(cx - hw, cy - hh, cx + hw, cy - hh + 1, TQ);
        g.fill(cx - hw, cy + hh - 1, cx + hw, cy + hh, TQ);
        g.fill(cx - hw, cy - hh, cx - hw + 1, cy + hh, TQ);
        g.fill(cx + hw - 1, cy - hh, cx + hw, cy + hh, TQ);
        if (a < top || d < top) {
            int nw = a < top ? (int) Math.round((VIEW_W / 2 - 5) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.ACROSS, a + 1) / maxA)) : hw;
            int nh = d < top ? (int) Math.round((VIEW_H / 2 - 10) * Math.sqrt(TotemCompassItem.rangeBlocks(Range.DEPTH, d + 1) / maxD)) : hh;
            for (int x = cx - nw; x < cx + nw; x += 4) {
                g.fill(x, cy - nh, x + 2, cy - nh + 1, GOLD);
                g.fill(x, cy + nh - 1, x + 2, cy + nh, GOLD);
            }
            for (int y = cy - nh; y < cy + nh; y += 4) {
                g.fill(cx - nw, y, cx - nw + 1, y + 2, GOLD);
                g.fill(cx + nw - 1, y, cx + nw, y + 2, GOLD);
            }
        }
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF); // where the holder stands
        g.fill(cx, cy - 4, cx + 1, cy - 2, 0xFFFFFFFF);
    }

    private void card(GuiGraphics g, int x, int y, Ladder l, int mouseX, int mouseY) {
        g.fill(x, y, x + CARD_W, y + CARD_H, DIM);
        g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, SLOT_BG);
        int level = menu.level(l), max = l.max();
        for (int i = 0; i < max; i++) {
            g.fill(x + 4 + i * 5, y + 20, x + 8 + i * 5, y + 23, i < level ? ladderColour(l) : DIM);
        }
        boolean on = canBuy(l), hover = on && over(mouseX, mouseY, x + CARD_W - KEY_W - 4, y + 3, KEY_W, KEY_H);
        int kx = x + CARD_W - KEY_W - 4, ky = y + 3;
        g.fill(kx, ky, kx + KEY_W, ky + KEY_H, on ? TQ : DIM);
        g.fill(kx + 1, ky + 1, kx + KEY_W - 1, ky + KEY_H - 1, hover ? 0xFF14362F : SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        Component status = status();
        g.drawString(font, status, imageWidth - 10 - font.width(status), 8, statusColour(), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        dialLabels(g);
        reachLabels(g);
        small(g, Component.translatable("gui.bsp_core.compass_tuner.tunes_into"), KX, CARD_Y[0] - 8, MUTED);
        small(g, Component.translatable("gui.bsp_core.compass_tuner.this_tuner"), KX, CARD_Y[3] - 8, MUTED);
        for (Ladder l : Ladder.values()) {
            cardLabels(g, KX, cardY(l), l);
        }
        small(g, Component.translatable("gui.bsp_core.compass_tuner.fe"), KX, FE_Y + 1, MUTED);
        small(g, Component.translatable("gui.bsp_core.compass_tuner.pl"), KX, FE_Y + 11, MUTED);
        Component factor = Component.translatable("gui.bsp_core.compass_tuner.speed_x", String.format(Locale.ROOT, "%.1f", menu.factor()));
        small(g, factor, KX + CARD_W - smallWidth(factor), FE_Y + 20, PLASMA & 0xFFFFFF);
        TuneCheck check = menu.check();
        Component key = Component.translatable(menu.tuning() ? "gui.bsp_core.compass_tuner.tuning_key"
                : check == TuneCheck.NOTHING_TO_TUNE ? "gui.bsp_core.compass_tuner.nothing_key" : "gui.bsp_core.compass_tuner.tune");
        g.drawString(font, key, KX + CARD_W / 2 - font.width(key) / 2, TUNE_Y + 5, check == TuneCheck.OK ? TQ & 0xFFFFFF : OFF, false);
        Component why = why(check);
        if (why != null) {
            smallCentred(g, why, KX + CARD_W / 2, TUNE_Y + TUNE_H + 3, MUTED);
        }
        int[] hand = menu.handover();
        if (hand != null) {
            Component text = Component.translatable("gui.bsp_core.compass_tuner.handover", hand[0], hand[1], hand[2]);
            g.fill(10, BANNER_Y, imageWidth - 10, BANNER_Y + 12, 0xFF3A2F0C);
            g.fill(10, BANNER_Y, imageWidth - 10, BANNER_Y + 1, GOLD);
            g.fill(10, BANNER_Y + 11, imageWidth - 10, BANNER_Y + 12, GOLD);
            small(g, text, 14, BANNER_Y + 3, GOLD & 0xFFFFFF);
        }
    }

    private Component status() {
        if (menu.tuning()) {
            return Component.translatable(switch (menu.stall()) {
                case NO_FE -> "gui.bsp_core.compass_tuner.status.no_fe";
                case NO_MATERIALS -> "gui.bsp_core.compass_tuner.status.needs_materials";
                default -> "gui.bsp_core.compass_tuner.status.tuning";
            });
        }
        return Component.translatable(switch (menu.check()) {
            case OK -> "gui.bsp_core.compass_tuner.status.ready";
            case NOTHING_TO_TUNE -> menu.handover() != null ? "gui.bsp_core.compass_tuner.status.handed_over" : "gui.bsp_core.compass_tuner.status.up_to_date";
            case NO_MATERIALS -> "gui.bsp_core.compass_tuner.status.needs_materials";
            case NO_FE -> "gui.bsp_core.compass_tuner.status.no_fe";
            default -> "gui.bsp_core.compass_tuner.status.idle";
        });
    }

    private int statusColour() {
        Component s = status();
        String key = s.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
        if (key.endsWith("no_fe")) {
            return BAD & 0xFFFFFF;
        }
        if (key.endsWith("needs_materials") || key.endsWith("handed_over")) {
            return GOLD & 0xFFFFFF;
        }
        return key.endsWith("idle") || key.endsWith("up_to_date") ? MUTED : TQ & 0xFFFFFF;
    }

    private static Component why(TuneCheck check) {
        String k = switch (check) {
            case TUNING -> "stop";
            case NO_COMPASS -> "no_compass";
            case NOTHING_TO_TUNE -> "nothing";
            case NO_MATERIALS -> "no_materials";
            case NO_FE -> "no_fe";
            case NOT_ALLOWED -> "not_allowed";
            default -> null;
        };
        return k == null ? null : Component.translatable("gui.bsp_core.compass_tuner.why." + k);
    }

    private void dialLabels(GuiGraphics g) {
        int cx = CompassTunerMenu.DIAL_X, cy = CompassTunerMenu.DIAL_Y;
        boolean four = TunerMaterials.forSlot(3) != null;
        Component upper, lower;
        int upperColour = TEXT;
        if (menu.tuning()) {
            switch (menu.stall()) {
                case NO_FE -> {
                    upper = Component.translatable("gui.bsp_core.compass_tuner.paused");
                    upperColour = BAD & 0xFFFFFF;
                }
                case NO_MATERIALS -> {
                    upper = Component.translatable("gui.bsp_core.compass_tuner.waiting");
                    upperColour = GOLD & 0xFFFFFF;
                }
                default -> upper = Component.translatable("gui.bsp_core.compass_tuner.left", clock(menu.leftSeconds()));
            }
            lower = menu.stall() == CompassTunerBlockEntity.Stall.NONE
                    ? Component.translatable("gui.bsp_core.compass_tuner.ready_at", LocalTime.now().plusSeconds(menu.leftSeconds()).format(HM))
                    : Component.translatable("gui.bsp_core.compass_tuner.left", clock(menu.leftSeconds()));
        } else {
            upper = Component.translatable(switch (menu.check()) {
                case NO_COMPASS -> "gui.bsp_core.compass_tuner.put_compass";
                case NOTHING_TO_TUNE -> "gui.bsp_core.compass_tuner.nothing";
                default -> "gui.bsp_core.compass_tuner.ready_to_tune";
            });
            lower = Component.translatable("gui.bsp_core.compass_tuner.length", minutes((menu.leftSeconds() + 59) / 60));
        }
        // with a fourth material its slot sits at 12 o'clock, so the upper line moves under the dial
        if (four) {
            smallCentred(g, upper, cx, cy + CompassTunerMenu.DIAL_R + 5, upperColour);
        } else {
            smallCentred(g, upper, cx, cy - 34, upperColour);
            smallCentred(g, Component.translatable("gui.bsp_core.compass_tuner.used_at_end"), cx, cy + CompassTunerMenu.DIAL_R + 5, MUTED);
        }
        smallCentred(g, lower, cx, cy + 19, MUTED);
        for (int i = 0; i < CompassTunerBlockEntity.MATERIAL_SLOTS; i++) {
            TunerMaterials.Entry e = TunerMaterials.forSlot(i);
            if (e == null) {
                continue;
            }
            int[] p = CompassTunerMenu.SLOT_POS[1 + i];
            ItemStack have = menu.getSlot(1 + i).getItem();
            Component need = Component.literal("x" + e.count());
            int colour = have.getCount() >= e.count() ? GOLD & 0xFFFFFF : BAD & 0xFFFFFF;
            if (i == 2) {
                small(g, need, p[0] + 19, p[1] + 5, colour); // the 6 o'clock slot: beside it, clear of the rim
            } else if (i == 3) {
                small(g, need, p[0] + 19, p[1] + 5, colour);
            } else {
                smallCentred(g, need, p[0] + 8, p[1] + 18, colour);
            }
        }
    }

    private void reachLabels(GuiGraphics g) {
        Component heading = Component.translatable("gui.bsp_core.compass_tuner.reach");
        g.fill(VIEW_X, REACH_Y, VIEW_X + smallWidth(heading) + 4, REACH_Y + 7, BG);
        small(g, heading, VIEW_X, REACH_Y + 1, MUTED);
        int a = menu.level(Ladder.ACROSS), d = menu.level(Ladder.DEPTH);
        Component ground = Component.translatable("gui.bsp_core.compass.ground");
        g.fill(VIEW_X + 2, VIEW_Y + VIEW_H / 2 - 17, VIEW_X + 4 + smallWidth(ground), VIEW_Y + VIEW_H / 2 - 10, SLOT_BG);
        small(g, ground, VIEW_X + 3, VIEW_Y + VIEW_H / 2 - 16, GROUND_TEXT);
        small(g, Component.translatable("gui.bsp_core.compass.across", blocks(TotemCompassItem.rangeBlocks(Range.ACROSS, a))), VIEW_X + 3, VIEW_Y + VIEW_H - 8, TQ & 0xFFFFFF);
        Component upDown = Component.translatable("gui.bsp_core.compass.up_down", blocks(TotemCompassItem.rangeBlocks(Range.DEPTH, d)));
        small(g, upDown, VIEW_X + VIEW_W - 3 - smallWidth(upDown), VIEW_Y + 3, TQ & 0xFFFFFF);
        int top = TotemCompassItem.maxRangeLevel();
        if (a < top || d < top) {
            small(g, Component.translatable("gui.bsp_core.compass_tuner.next_outline"), VIEW_X, VIEW_Y + VIEW_H + 4, GOLD & 0xFFFFFF);
        }
    }

    private void cardLabels(GuiGraphics g, int x, int y, Ladder l) {
        int level = menu.level(l), cost = l.cost(level);
        small(g, Component.translatable("gui.bsp_core.compass_tuner." + l.name().toLowerCase(Locale.ROOT)), x + 4, y + 3, TEXT);
        small(g, value(l, level), x + 4, y + 11, ladderColour(l) & 0xFFFFFF);
        Component plus = Component.literal("+");
        g.drawString(font, plus, x + CARD_W - KEY_W - 4 + KEY_W / 2 - font.width(plus) / 2, y + 5, canBuy(l) ? TQ & 0xFFFFFF : OFF, false);
        Component price;
        int colour;
        if (cost < 0) {
            price = Component.translatable("gui.bsp_core.compass_tuner.top");
            colour = 0x8FE04A;
        } else if (minecraft.player.isCreative()) {
            price = Component.translatable("gui.bsp_core.compass_tuner.free");
            colour = 0x8FE04A;
        } else {
            CoinTier coin = l.coin(level);
            price = Component.translatable("gui.bsp_core.compass_tuner.price", cost, Component.translatable("tier.bsp_core." + coin.key));
            colour = canBuy(l) ? coinColour(coin) : BAD & 0xFFFFFF;
        }
        small(g, price, x + CARD_W - 4 - smallWidth(price), y + 17, colour);
    }
}
