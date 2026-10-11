package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.StallDisplayConfigPacket;
import com.mrgregles.bsp_core.network.StallDisplayConfigPacket.Action;
import com.mrgregles.bsp_core.shop.StallDisplayBlockEntity;
import com.mrgregles.bsp_core.shop.StallDisplayMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Stall Display's settings screen in the Cash Machine skin: what it is linked to, the shop's
 * items to pin one of (or let them all turn), and keys that step the size, the hover height and the
 * interval through fixed values. Every key sends its change at once; Done closes.
 */
public class StallDisplayScreen extends AbstractContainerScreen<StallDisplayMenu> {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A;
    private static final int SX = 10, SY = 10, SW = 206, SH = 176, KX = 226, KW = 64, KH = 20, KGAP = 4;
    private static final int COLS = 11, ROWS = 3, CELL = 18, GRID_Y = 52;
    private static final int[] SCALES = {50, 65, 80, 100, 125, 150, 200}, HEIGHTS = {100, 120, 135, 150, 175, 200, 250}, INTERVALS = {2, 4, 6, 10, 15, 30, 60};

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private int page;

    public StallDisplayScreen(StallDisplayMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.display." + key, args);
    }

    /** The block entity as the client sees it, so each change shows at once. */
    private StallDisplayBlockEntity display() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(menu.pos) instanceof StallDisplayBlockEntity d ? d : null;
    }

    private static int next(int[] steps, int current, int fallback) {
        int cur = current == 0 ? fallback : current;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] >= cur) {
                return steps[(i + 1) % steps.length];
            }
        }
        return steps[0];
    }

    private void send(Action a, int v) {
        BSPNetwork.CHANNEL.sendToServer(new StallDisplayConfigPacket(a, v));
    }

    private void keys() {
        top.clear();
        bottom.clear();
        StallDisplayBlockEntity d = display();
        boolean linked = d != null && d.linked();
        int scale = d == null ? 0 : d.scalePct, height = d == null ? 0 : d.heightPct, interval = d == null ? 0 : d.intervalSec;
        top.add(new Key("size", true, () -> send(Action.SCALE, next(SCALES, scale, (d == null ? 80 : d.defaultScalePct())))));
        top.add(new Key("height", true, () -> send(Action.HEIGHT, next(HEIGHTS, height, (d == null ? 135 : d.defaultHeightPct())))));
        top.add(new Key("interval", true, () -> send(Action.INTERVAL, next(INTERVALS, interval, StallDisplayBlockEntity.defaultIntervalSec()))));
        top.add(new Key("reset", scale != 0 || height != 0 || interval != 0, () -> send(Action.RESET, 0)));
        bottom.add(new Key("link", true, () -> send(Action.LINK, 0)));
        bottom.add(new Key("unlink", linked, () -> send(Action.UNLINK, 0)));
        bottom.add(new Key("done", true, this::onClose));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        int x = leftPos, y = topPos, ky = y + SY;
        for (Key k : top) {
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            Key k = bottom.get(i);
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky -= KH + KGAP;
        }
        StallDisplayBlockEntity d = display();
        int per = COLS * ROWS;
        for (int i = page * per; i < Math.min(menu.items.size(), (page + 1) * per); i++) {
            int c = (i - page * per) % COLS, r = (i - page * per) / COLS;
            if (over(mx, my, x + SX + 4 + c * CELL, y + SY + GRID_Y + r * CELL, CELL, CELL)) {
                int idx = menu.items.get(i).index();
                send(Action.PIN, d != null && d.pinned == idx ? -1 : idx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int pages = Math.max(1, (menu.items.size() + COLS * ROWS - 1) / (COLS * ROWS));
        page = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(delta)));
        return true;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
        int per = COLS * ROWS;
        for (int i = page * per; i < Math.min(menu.items.size(), (page + 1) * per); i++) {
            int c = (i - page * per) % COLS, r = (i - page * per) / COLS;
            if (over(mx, my, leftPos + SX + 4 + c * CELL, topPos + SY + GRID_Y + r * CELL, CELL, CELL)) {
                g.renderTooltip(font, menu.items.get(i).stack(), mx, my);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        int ky = y + SY;
        for (Key k : top) {
            key(g, x + KX, ky, k, mx, my);
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            key(g, x + KX, ky, bottom.get(i), mx, my);
            ky -= KH + KGAP;
        }
        StallDisplayBlockEntity d = display();
        int per = COLS * ROWS;
        for (int i = page * per; i < Math.min(menu.items.size(), (page + 1) * per); i++) {
            int c = (i - page * per) % COLS, r = (i - page * per) / COLS;
            int cx = x + SX + 4 + c * CELL, cy = y + SY + GRID_Y + r * CELL;
            boolean pinned = d != null && d.pinned == menu.items.get(i).index();
            g.fill(cx, cy, cx + CELL, cy + CELL, pinned ? 0xFF2A5A3A : 0xFF0A1F12);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, pinned ? 0xFF143A22 : SCREEN);
            g.renderItem(menu.items.get(i).stack(), cx + 1, cy + 1);
            g.renderItemDecorations(font, menu.items.get(i).stack(), cx + 1, cy + 1);
        }
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    private static String pct(int v, int def) {
        int p = v == 0 ? def : v;
        return String.format(Locale.ROOT, "%d.%02d", p / 100, p % 100) + (v == 0 ? "*" : "");
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        StallDisplayBlockEntity d = display();
        g.drawString(font, "> " + tr("title").getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, PHOS, false);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        if (menu.linked == null) {
            CashMachine.wrap(g, font, tr("not_linked"), SX + 8, SY + 22, SW - 16, RED);
        } else if (menu.shopName.isEmpty()) {
            CashMachine.wrap(g, font, tr("linked_noshop", menu.linked.toShortString()), SX + 8, SY + 22, SW - 16, AMBER);
        } else {
            CashMachine.wrap(g, font, tr("linked", menu.linked.toShortString(), menu.shopName), SX + 8, SY + 22, SW - 16, PHOS_DIM);
        }
        int pinned = d == null ? -1 : d.pinned;
        String pinnedName = "";
        for (var p : menu.items) {
            if (p.index() == pinned) {
                pinnedName = p.stack().getHoverName().getString();
            }
        }
        int interval = d == null || d.intervalSec == 0 ? StallDisplayBlockEntity.defaultIntervalSec() : d.intervalSec;
        CashMachine.fit(g, font, (pinnedName.isEmpty() ? tr("cycle", interval) : tr("pinned", pinnedName)).getString(), SX + 8, SY + 42, SW - 70, pinnedName.isEmpty() ? PHOS : AMBER);
        if (menu.items.isEmpty()) {
            g.drawString(font, tr("no_items"), SX + 8, SY + GRID_Y + 4, PHOS_DIM, false);
        } else {
            int pages = Math.max(1, (menu.items.size() + COLS * ROWS - 1) / (COLS * ROWS));
            if (pages > 1) {
                String pg = tr("page", page + 1, pages).getString();
                g.drawString(font, pg, SX + SW - 8 - font.width(pg), SY + 42, PHOS_DIM, false);
            }
        }
        g.fill(SX + 4, SY + 112, SX + SW - 4, SY + 113, SCREEN_EDGE);
        int ty = SY + 118 + CashMachine.wrap(g, font, tr("pick"), SX + 8, SY + 118, SW - 16, PHOS_DIM);
        int scale = d == null ? 0 : d.scalePct, height = d == null ? 0 : d.heightPct, iv = d == null ? 0 : d.intervalSec;
        CashMachine.fit(g, font, tr("size_line", pct(scale, (d == null ? 80 : d.defaultScalePct())), pct(height, (d == null ? 135 : d.defaultHeightPct())),
                (iv == 0 ? StallDisplayBlockEntity.defaultIntervalSec() + "*" : String.valueOf(iv))).getString(), SX + 8, ty + 3, SW - 16, PHOS);
        CashMachine.wrap(g, font, tr("default_note"), SX + 8, ty + 14, SW - 16, PHOS_DIM);
    }
}
