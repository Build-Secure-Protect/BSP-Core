package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.kit.Kits;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.KitActionPacket;
import com.mrgregles.bsp_core.network.KitViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The players' kit browser: a wall of cards, four a row, each carrying the kit's icon, its name and
 * what it is to the player right now: REDEEM on a ready card, TAKE IT in violet on a kit waiting for
 * them, the rank step to reach, the server to be on, the price against their balance, the slots
 * short, or greyed with the time left after a redeem. A click on a card's icon flips it over the
 * wall to show its items and the full "to get it" lines. Everything shown comes from the server's
 * {@link KitViewPacket}; the screen never works a rule out itself.
 */
public class KitScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 344, H = 236, CARD_W = 77, CARD_H = 86, GAP = 4, COLS = 4, ROWS = 2, GRID_X = 8, GRID_Y = 22, KEY_H = 18;
    private static final int BG = 0xF010151C, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, VIO = 0xFFB58CFF, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2,
            OK = 0x58F29A, WARN = 0xF2B544, RED = 0xFF8F86, READY_BG = 0xFF13211F, OWED_BG = 0xFF1A1630, HOVER = 0xFF1B2230;
    private static final int FX = 40, FY = 24, FW = 264, FH = 182;

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private KitViewPacket data;
    private final List<Hit> hits = new ArrayList<>();
    private int scroll, ticks;
    @Nullable
    private String flipped;

    private KitScreen(KitViewPacket data) {
        super(Component.translatable("gui.bsp_core.kits.title"));
        this.data = data;
    }

    /** Called when a view arrives: opens the browser, or refreshes it if it is showing. */
    public static void receive(KitViewPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof KitScreen screen) {
            screen.data = packet;
        } else if (packet.open()) {
            mc.setScreen(new KitScreen(packet));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) {
            send(KitActionPacket.of(KitActionPacket.Action.REFRESH));
        }
    }

    private static void send(KitActionPacket p) {
        BSPNetwork.CHANNEL.sendToServer(p);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.kits." + key, args);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Nullable
    private KitViewPacket.Card card(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (KitViewPacket.Card c : data.cards()) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (over(mx, my, h.x, h.y, h.w, h.h)) {
                h.run.run();
                return true;
            }
        }
        if (flipped != null) {
            flipped = null;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int rows = (data.cards().size() + COLS - 1) / COLS;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows - ROWS));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (flipped != null) {
                flipped = null;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing helpers

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

    private void smallCentred(GuiGraphics g, Component text, int cx, int y, int colour, int maxW) {
        String s = font.plainSubstrByWidth(text.getString(), (int) (maxW / 0.75f));
        Component c = Component.literal(s);
        small(g, c, cx - smallWidth(c) / 2, y, colour);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, Component label, int colour, boolean enabled, int mx, int my, @Nullable Runnable run) {
        boolean hover = enabled && run != null && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT);
        int c = enabled ? colour & 0xFFFFFF : 0x6B7385;
        if (h < 14) {
            smallCentred(g, label, x + w / 2, y + (h - 6) / 2, c, w - 4);
        } else {
            g.drawString(font, font.plainSubstrByWidth(label.getString(), w - 6), x + w / 2 - Math.min(w - 6, font.width(label)) / 2, y + (h - 8) / 2, c, false);
        }
        if (enabled && run != null) {
            hits.add(new Hit(x, y, w, h, run));
        }
    }

    private void chip(GuiGraphics g, int cx, int y, Component label, int bg, int fg) {
        int w = smallWidth(label) + 8;
        int x = cx - w / 2;
        g.fill(x, y, x + w, y + 10, bg);
        small(g, label, x + 4, y + 2, fg);
    }

    private void icon(GuiGraphics g, ItemStack stack, int x, int y, boolean grey) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2f, 2f, 1f);
        if (grey) {
            g.setColor(0.5f, 0.5f, 0.5f, 1f);
        }
        g.renderItem(stack, 0, 0);
        if (grey) {
            g.setColor(1f, 1f, 1f, 1f);
        }
        g.pose().popPose();
    }

    private void ladder(GuiGraphics g, int x, int y, int stepW, int maxH, int need, int mx, int my) {
        List<String> ladder = data.ladder();
        int n = ladder.size();
        for (int i = 0; i < n; i++) {
            int sh = Math.max(3, maxH * (i + 1) / n), sx = x + i * (stepW + 2);
            int colour = i == data.rankIndex() ? TQ : i == need ? GOLD : DIM;
            g.fill(sx, y + maxH - sh, sx + stepW, y + maxH, colour);
        }
    }

    private int neededIndex(KitViewPacket.Card c) {
        if (data.ladder().isEmpty() || c.text().isEmpty()) {
            return -1;
        }
        int best = -1;
        for (String r : c.text().split(",\\s*")) {
            int i = data.ladder().indexOf(r);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private String itemsLine(KitViewPacket.Card c) {
        return (c.once() ? tr("l.items_once", c.items().size()) : c.cooldownSeconds() > 0 ? tr("l.items_every", c.items().size(), Kits.span(c.cooldownSeconds())) : tr("l.items", c.items().size())).getString();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title, x + 10, y + 8, TQ & 0xFFFFFF, false);
        Component header = data.rank().isEmpty() ? tr("header", Minecraft.getInstance().getUser().getName(), BspBank.format(data.pieces()).replace(" pieces", ""), data.freeSlots())
                : tr("header_rank", Minecraft.getInstance().getUser().getName(), data.rank(), BspBank.format(data.pieces()).replace(" pieces", ""), data.freeSlots());
        small(g, header, x + W - 10 - smallWidth(header), y + 9, MUTED);

        List<KitViewPacket.Card> cards = data.cards();
        int rows = (cards.size() + COLS - 1) / COLS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - ROWS));
        boolean overlay = flipped != null && card(flipped) != null;
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = i + scroll * COLS;
            if (index >= cards.size()) {
                break;
            }
            card(g, cards.get(index), x + GRID_X + (i % COLS) * (CARD_W + GAP), y + GRID_Y + (i / COLS) * (CARD_H + GAP), overlay ? -1 : mouseX, overlay ? -1 : mouseY, !overlay);
        }
        if (rows > ROWS) {
            int track = ROWS * (CARD_H + GAP) - GAP, knob = Math.max(8, track * ROWS / rows), ky = (track - knob) * scroll / (rows - ROWS);
            g.fill(x + W - 10, y + GRID_Y + ky, x + W - 8, y + GRID_Y + ky + knob, DIM);
        }
        if (!data.enabled()) {
            small(g, tr("off"), x + GRID_X + 4, y + GRID_Y + 6, MUTED);
        } else if (cards.isEmpty()) {
            small(g, tr("empty"), x + GRID_X + 4, y + GRID_Y + 6, MUTED);
        }
        small(g, rows > ROWS ? tr("scroll_hint") : tr("hint"), x + GRID_X, y + H - 20, MUTED);
        key(g, x + W - 10 - 70, y + H - 8 - KEY_H, 70, KEY_H, tr("k.close"), MUTED | 0xFF000000, true, overlay ? -1 : mouseX, overlay ? -1 : mouseY, overlay ? null : this::onClose);
        if (overlay) {
            // the wall's hits are dead while a card is flipped: a click outside the card goes back
            hits.clear();
            g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xA0000000);
            flipped(g, card(flipped), x, y, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void card(GuiGraphics g, KitViewPacket.Card c, int cx, int cy, int mx, int my, boolean live) {
        Kits.State state = Kits.State.valueOf(c.state());
        boolean grey = state == Kits.State.REDEEMED || state == Kits.State.COOLDOWN;
        int edge = state == Kits.State.READY ? TQ : state == Kits.State.OWED ? VIO : DIM;
        int bg = state == Kits.State.READY ? READY_BG : state == Kits.State.OWED ? OWED_BG : SLOT;
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, edge);
        g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, bg);
        icon(g, c.icon(), cx + CARD_W / 2 - 16, cy + 5, grey);
        int mid = cx + CARD_W / 2;
        smallCentred(g, Component.literal(c.name()), mid, cy + 40, grey ? MUTED : TEXT, CARD_W - 6);
        if (live) {
            hits.add(new Hit(cx, cy, CARD_W, 50, () -> flipped = c.id()));
        }
        int body = cy + 62;
        switch (state) {
            case READY -> {
                chip(g, mid, cy + 50, tr("c.ready"), 0xFF113D30, OK);
                key(g, cx + 6, body, CARD_W - 12, 13, tr("k.redeem"), TQ, true, mx, my, live ? () -> send(new KitActionPacket(KitActionPacket.Action.REDEEM, c.id())) : null);
                smallCentred(g, Component.literal(itemsLine(c)), mid, body + 15, MUTED, CARD_W - 6);
            }
            case OWED -> {
                chip(g, mid, cy + 50, tr("c.owed"), 0xFF1F1A3A, VIO & 0xFFFFFF);
                key(g, cx + 6, body, CARD_W - 12, 13, tr("k.take"), VIO, true, mx, my, live ? () -> send(new KitActionPacket(KitActionPacket.Action.TAKE, c.id())) : null);
                if (c.number() > 0) {
                    smallCentred(g, tr("l.needs", c.slotsNeeded(), data.freeSlots()), mid, body + 15, RED, CARD_W - 6);
                } else {
                    smallCentred(g, tr("l.fits"), mid, body + 15, OK, CARD_W - 6);
                }
            }
            case REDEEMED -> {
                chip(g, mid, cy + 50, tr("c.redeemed"), 0xFF1B1E26, 0x6B7280);
                smallCentred(g, tr("l.once_only"), mid, body + 4, MUTED, CARD_W - 6);
            }
            case COOLDOWN -> {
                chip(g, mid, cy + 50, tr("c.redeemed"), 0xFF1B1E26, 0x6B7280);
                smallCentred(g, tr("l.again", Kits.span(c.number())), mid, body + 4, MUTED, CARD_W - 6);
            }
            case RANK -> {
                int need = neededIndex(c);
                chip(g, mid, cy + 50, tr("c.rank", need >= 0 ? data.ladder().get(need) : c.text()), DIM, MUTED);
                if (need >= 0) {
                    int n = data.ladder().size(), stepW = Math.max(4, Math.min(10, (CARD_W - 12) / n - 2));
                    ladder(g, mid - (n * (stepW + 2) - 2) / 2, body, stepW, 12, need, mx, my);
                    if (data.rankIndex() >= 0 && data.rankIndex() < need) {
                        smallCentred(g, tr("l.rank_you", data.rank(), need - data.rankIndex()), mid, body + 15, MUTED, CARD_W - 6);
                    } else {
                        smallCentred(g, tr("l.rank_need", data.ladder().get(need)), mid, body + 15, MUTED, CARD_W - 6);
                    }
                } else {
                    smallCentred(g, tr("l.rank_need", c.text()), mid, body + 6, MUTED, CARD_W - 6);
                }
            }
            case SERVER -> {
                chip(g, mid, cy + 50, tr("c.server", c.text()), DIM, MUTED);
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(tr("l.server", c.text()), (int) ((CARD_W - 8) / 0.75f));
                for (int i = 0; i < Math.min(3, lines.size()); i++) {
                    g.pose().pushPose();
                    g.pose().translate(cx + 4, body + 2 + i * 8, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, lines.get(i), 0, 0, MUTED, false);
                    g.pose().popPose();
                }
            }
            case PRICE -> {
                chip(g, mid, cy + 50, tr("c.price", BspBank.format(c.price()).replace(" pieces", "")), 0xFF3B2F14, WARN);
                g.fill(cx + 6, body + 2, cx + CARD_W - 6, body + 7, DIM);
                int fill = (int) ((CARD_W - 12) * Math.min(1.0, (double) data.pieces() / Math.max(1, c.price())));
                g.fill(cx + 6, body + 2, cx + 6 + fill, body + 7, GOLD);
                smallCentred(g, tr("l.have", BspBank.format(data.pieces()).replace(" pieces", "")), mid, body + 11, MUTED, CARD_W - 6);
            }
            case SPACE -> {
                chip(g, mid, cy + 50, tr("c.space", c.number()), 0xFF3B2F14, WARN);
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(tr("l.space", c.number()), (int) ((CARD_W - 8) / 0.75f));
                for (int i = 0; i < Math.min(3, lines.size()); i++) {
                    g.pose().pushPose();
                    g.pose().translate(cx + 4, body + 2 + i * 8, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, lines.get(i), 0, 0, MUTED, false);
                    g.pose().popPose();
                }
            }
            default -> {
            }
        }
    }

    private void flipped(GuiGraphics g, KitViewPacket.Card c, int x, int y, int mx, int my) {
        int fx = x + FX, fy = y + FY;
        Kits.State state = Kits.State.valueOf(c.state());
        g.fill(fx - 1, fy - 1, fx + FW + 1, fy + FH + 1, state == Kits.State.OWED ? VIO : TQ);
        g.fill(fx, fy, fx + FW, fy + FH, BG);
        hits.add(new Hit(fx, fy, FW, FH, () -> {}));
        icon(g, c.icon(), fx + 8, fy + 8, false);
        g.drawString(font, font.plainSubstrByWidth(c.name(), FW - 60), fx + 46, fy + 10, TQ & 0xFFFFFF, false);
        String rule = (c.once() ? tr("f.once") : c.cooldownSeconds() > 0 ? tr("f.every", Kits.span(c.cooldownSeconds())) : Component.empty()).getString();
        small(g, Component.literal(font.plainSubstrByWidth(c.description() + (rule.isEmpty() ? "" : " " + rule), (int) ((FW - 56) / 0.75f))), fx + 46, fy + 22, MUTED);
        int gy = fy + 42, rows = Math.min(4, (c.items().size() + 8) / 9);
        for (int i = 0; i < Math.min(36, c.items().size()); i++) {
            int sx = fx + 8 + (i % 9) * 18, sy = gy + (i / 9) * 18;
            g.fill(sx, sy, sx + 18, sy + 18, DIM);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
            g.renderItem(c.items().get(i), sx + 1, sy + 1);
            g.renderItemDecorations(font, c.items().get(i), sx + 1, sy + 1);
        }
        int ly = gy + rows * 18 + 3;
        small(g, tr("f.items_slots", c.items().size(), c.slotsNeeded()), fx + 8, ly, MUTED);
        ly += 11;
        small(g, tr("f.to_get"), fx + 8, ly, MUTED);
        ly += 9;
        int need = neededIndex(c);
        if (!c.ranks().isEmpty()) {
            boolean ok = state != Kits.State.RANK;
            String rank = need >= 0 ? data.ladder().get(need) : c.ranks();
            Component line = ok ? tr("f.rank_ok", rank) : tr("f.rank", rank);
            small(g, Component.literal((ok ? "✓ " : "✗ ") + line.getString()), fx + 8, ly, ok ? OK : GOLD & 0xFFFFFF);
            if (!ok && need >= 0 && data.rankIndex() >= 0 && data.rankIndex() < need) {
                small(g, tr("f.rank_you", data.rank(), need - data.rankIndex()), fx + 14 + smallWidth(line) + 6, ly, MUTED);
            }
            ly += 9;
            if (need >= 0) {
                int n = data.ladder().size(), stepW = Math.max(6, Math.min(14, 120 / n - 2));
                ladder(g, fx + 16, ly, stepW, 10, need, mx, my);
                small(g, tr("f.ladder"), fx + 16 + n * (stepW + 2) + 6, ly + 2, MUTED);
                ly += 13;
            }
        }
        if (c.price() > 0) {
            boolean ok = data.pieces() >= c.price();
            Component line = tr("f.price", BspBank.format(c.price()).replace(" pieces", ""));
            small(g, Component.literal((ok ? "✓ " : "✗ ") + line.getString()), fx + 8, ly, ok ? OK : GOLD & 0xFFFFFF);
            small(g, tr("f.price_have", BspBank.format(data.pieces()).replace(" pieces", "")), fx + 14 + smallWidth(line) + 6, ly, MUTED);
            ly += 9;
        }
        boolean fits = data.freeSlots() >= c.slotsNeeded() || state == Kits.State.READY;
        Component slots = tr("f.slots", c.slotsNeeded());
        small(g, Component.literal((fits ? "✓ " : "✗ ") + slots.getString()), fx + 8, ly, fits ? OK : GOLD & 0xFFFFFF);
        small(g, tr("f.slots_have", data.freeSlots()), fx + 14 + smallWidth(slots) + 6, ly, MUTED);
        ly += 9;
        if (state == Kits.State.SERVER) {
            small(g, Component.literal("✗ " + tr("f.server", c.text()).getString()), fx + 8, ly, GOLD & 0xFFFFFF);
        } else {
            small(g, Component.literal("✓ " + (c.servers().isEmpty() ? tr("f.any_server") : tr("f.server", c.servers())).getString()), fx + 8, ly, OK);
        }
        ly += 9;
        if (state == Kits.State.COOLDOWN) {
            small(g, Component.literal("✗ " + tr("f.cooldown", Kits.span(c.number())).getString()), fx + 8, ly, MUTED);
        } else if (state == Kits.State.REDEEMED) {
            small(g, Component.literal("✗ " + tr("f.redeemed").getString()), fx + 8, ly, MUTED);
        } else if (state == Kits.State.OWED) {
            small(g, Component.literal("• " + tr("f.waiting").getString()), fx + 8, ly, VIO & 0xFFFFFF);
        }
        int ky = fy + FH - 8 - 16;
        switch (state) {
            case READY -> key(g, fx + 8, ky, 120, 16, tr("k.redeem"), TQ, true, mx, my, () -> send(new KitActionPacket(KitActionPacket.Action.REDEEM, c.id())));
            case OWED -> key(g, fx + 8, ky, 120, 16, tr("k.take"), VIO, c.number() <= 0, mx, my, () -> send(new KitActionPacket(KitActionPacket.Action.TAKE, c.id())));
            default -> {
                Component why = switch (state) {
                    case RANK -> tr("c.rank", need >= 0 ? data.ladder().get(need) : c.text());
                    case SERVER -> tr("f.server", c.text());
                    case PRICE -> tr("c.price", BspBank.format(c.price()).replace(" pieces", ""));
                    case SPACE -> tr("c.space", c.number());
                    case COOLDOWN -> tr("c.cooldown", Kits.span(c.number()));
                    default -> tr("c.redeemed");
                };
                key(g, fx + 8, ky, 150, 16, tr("k.locked", why), TQ, false, mx, my, null);
            }
        }
        key(g, fx + FW - 8 - 56, ky, 56, 16, tr("k.back"), MUTED | 0xFF000000, true, mx, my, () -> flipped = null);
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }
}
