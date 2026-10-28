package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.lottery.Lottery;
import com.mrgregles.bsp_core.lottery.LotteryAdmin;
import com.mrgregles.bsp_core.lottery.LotteryAdminView;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LotteryAdminActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The lottery's admin page (look C, owner's pick 2026-10-28), drawn in the Trade Post behind its cog tab and alone in
 * {@link LotteryAdminScreen}: every player seen this season in a table, each IN or OUT and why, and at the right the pot,
 * the days a player must have been seen within (− and +), the LuckPerms ranks left out (tick to leave out), ADD and TAKE (a
 * card asking for the pieces and a reason) and DRAW, which asks for a second press. Coordinates are the screen's own (the
 * page starts at {@code x0}, {@code y0} and is 272 by 171); everything shown comes from the server.
 */
public class LotteryAdminPage {
    private static final int SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, SEL = 0xFF0F2D1C, PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544,
            AMBER_DARK = 0xFF6A4C10, STRIPE = 0xFF08160D;
    private static final int TW = 176, ROWS = 11, ROW_H = 11, RANK_ROWS = 5, RW = 90;
    private static final long REFRESH_MS = 5000, ARM_MS = 3000, MESSAGE_MS = 8000;

    private static LotteryAdminView view = LotteryAdminView.EMPTY;
    private static int serial;

    private final Font font;
    private final int x0, y0, rx;
    /** The last view this page read: a view that came before it opened is not read again, so an old message never shows. */
    private int seenSerial = serial, tableScroll, rankScroll;
    private String message = "";
    private long refreshAt, armedUntil, messageAt;
    /** The ADD or TAKE card while it is open, its two fields and which one has the keyboard. */
    @Nullable
    private LotteryAdmin.Action card;
    private String amountText = "", reasonText = "";
    private int focus;

    public LotteryAdminPage(Font font, int x0, int y0) {
        this.font = font;
        this.x0 = x0;
        this.y0 = y0;
        this.rx = x0 + TW + 6;
    }

    /**
     * A view from the server; one sent by the command opens the page by itself unless it is already showing. A container
     * screen open then is closed properly first (its menu too), so the server never keeps a menu the player cannot see.
     */
    public static void receive(LotteryAdminView v) {
        view = v;
        serial++;
        Minecraft mc = Minecraft.getInstance();
        if (v.open() && !(mc.screen instanceof LotteryAdminScreen) && !(mc.screen instanceof TradePostScreen t && t.adminShown())) {
            if (mc.player != null && mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                mc.player.closeContainer();
            }
            mc.setScreen(new LotteryAdminScreen());
        }
    }

    /** Leaving a world or server: nothing of it is shown in the next. */
    public static void reset() {
        view = LotteryAdminView.EMPTY;
        serial++;
    }

    /** True while the ADD or TAKE card is open: it takes every click on the screen. */
    public boolean cardOpen() {
        return card != null;
    }

    private static void send(LotteryAdmin.Action action, String text, long amount, boolean flag) {
        BSPNetwork.CHANNEL.sendToServer(new LotteryAdminActionPacket(action, text, amount, flag));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.lottery.admin." + key, args).getString();
    }

    private static boolean over(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    /** The page has just been shown: ask for it now, then every few seconds while it stays. */
    public void opened() {
        refreshAt = 0;
        tick();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now >= refreshAt) {
            refreshAt = now + REFRESH_MS;
            send(LotteryAdmin.Action.REFRESH, "", 0, false);
        }
    }

    // ------------------------------------------------------------------ drawing

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean lit, boolean enabled, int mx, int my) {
        boolean hover = enabled && over(mx, my, x, y, w, h);
        if (lit && enabled) {
            g.fill(x, y, x + w, y + h, hover ? 0xFF7CF7B3 : PHOS);
            CashMachine.fit(g, font, label, x + (w - Math.min(w - 4, font.width(label))) / 2, y + (h - 8) / 2, w - 4, SCREEN);
            return;
        }
        int edge = enabled ? (hover ? PHOS : DIM) : SCREEN_EDGE;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        CashMachine.fit(g, font, label, x + (w - Math.min(w - 4, font.width(label))) / 2, y + (h - 8) / 2, w - 4, enabled ? PHOS : SCREEN_EDGE);
    }

    private String ago(long ms) {
        long m = Math.max(0, view.now() - ms) / 60000;
        return m < 1 ? tr("now") : m < 60 ? m + " m" : m < 60 * 24 ? m / 60 + " h" : m / (60 * 24) + " d";
    }

    /** The rank shown for a row: the one leaving them out, else their first rank other than "default". */
    private static String rankOf(LotteryAdminView.Row r) {
        if (!r.rank().isEmpty()) {
            return r.rank();
        }
        String shown = "";
        for (String s : r.ranks().split(",")) {
            if (!s.isEmpty() && (shown.isEmpty() || shown.equals("default"))) {
                shown = s;
            }
        }
        return shown.isEmpty() ? "-" : shown;
    }

    private static String standing(LotteryAdminView.Row r) {
        Lottery.Standing[] all = Lottery.Standing.values();
        Lottery.Standing s = r.standing() >= 0 && r.standing() < all.length ? all[r.standing()] : Lottery.Standing.AWAY;
        return switch (s) {
            case IN -> tr("in");
            case OPERATOR -> tr("out_op");
            case RANK -> tr("out_rank");
            case AWAY -> tr("out_away");
        };
    }

    public void draw(GuiGraphics g, int mx, int my) {
        if (seenSerial != serial) {
            seenSerial = serial;
            if (!view.message().isEmpty()) {
                message = Component.translatable(view.message(), view.args().toArray()).getString();
                messageAt = System.currentTimeMillis();
                if (view.message().endsWith(".drawing")) {
                    armedUntil = 0;
                }
            }
            tableScroll = Math.max(0, Math.min(tableScroll, view.rows().size() - ROWS));
            rankScroll = Math.max(0, Math.min(rankScroll, view.ranks().size() - RANK_ROWS));
        }
        // the player table
        g.drawString(font, tr("player"), x0 + 2, y0 + 1, DIM, false);
        g.drawString(font, tr("seen"), x0 + 72, y0 + 1, DIM, false);
        g.drawString(font, tr("rank"), x0 + 102, y0 + 1, DIM, false);
        List<LotteryAdminView.Row> rows = view.rows();
        if (rows.isEmpty()) {
            g.drawString(font, tr("nobody"), x0 + 2, y0 + 13, DIM, false);
        }
        for (int i = 0; i < ROWS && tableScroll + i < rows.size(); i++) {
            LotteryAdminView.Row r = rows.get(tableScroll + i);
            int y = y0 + 12 + i * ROW_H;
            if (i % 2 == 1) {
                g.fill(x0, y - 1, x0 + TW, y + ROW_H - 1, STRIPE);
            }
            boolean in = r.standing() == Lottery.Standing.IN.ordinal();
            CashMachine.fit(g, font, r.name(), x0 + 2, y, 66, in ? PHOS : DIM);
            CashMachine.fit(g, font, ago(r.seenMs()), x0 + 72, y, 28, DIM);
            CashMachine.fit(g, font, rankOf(r), x0 + 102, y, 36, DIM);
            CashMachine.fitRight(g, font, standing(r), x0 + TW - 2, y, 36, in ? PHOS : AMBER);
        }
        if (rows.size() > ROWS) {
            g.drawString(font, (tableScroll > 0 ? "▲" : " ") + (tableScroll + ROWS < rows.size() ? "▼" : ""), x0 + TW - 14, y0 + 1, DIM, false);
        }
        CashMachine.fit(g, font, tr("footer", view.in(), view.out(), view.seen()), x0 + 2, y0 + 137, TW - 4, DIM);
        g.fill(rx - 3, y0, rx - 2, y0 + 146, SCREEN_EDGE);

        // the controls
        g.drawString(font, tr("pot"), rx, y0 + 1, DIM, false);
        CashMachine.fit(g, font, Bank.format(view.pot()), rx, y0 + 11, RW, AMBER);
        g.drawString(font, tr("seen_within"), rx, y0 + 23, DIM, false);
        key(g, rx, y0 + 32, 12, 11, "-", false, view.days() > 1, mx, my);
        String days = tr("days", view.days());
        g.drawString(font, days, rx + (RW - font.width(days)) / 2, y0 + 34, PHOS, false);
        key(g, rx + RW - 12, y0 + 32, 12, 11, "+", false, view.days() < 90, mx, my);
        g.drawString(font, tr("left_out"), rx, y0 + 47, DIM, false);
        if (view.ranks().size() > RANK_ROWS) {
            key(g, rx + RW - 21, y0 + 46, 10, 9, "▲", false, rankScroll > 0, mx, my);
            key(g, rx + RW - 10, y0 + 46, 10, 9, "▼", false, rankScroll + RANK_ROWS < view.ranks().size(), mx, my);
        }
        if (view.ranks().isEmpty()) {
            CashMachine.fit(g, font, tr("no_ranks"), rx, y0 + 58, RW, SCREEN_EDGE);
        }
        for (int i = 0; i < RANK_ROWS && rankScroll + i < view.ranks().size(); i++) {
            String rank = view.ranks().get(rankScroll + i);
            boolean out = view.leftOut().contains(rank);
            int y = y0 + 57 + i * 10;
            boolean hover = over(mx, my, rx, y - 1, RW, 10);
            g.fill(rx, y, rx + 7, y + 7, hover ? PHOS : DIM);
            g.fill(rx + 1, y + 1, rx + 6, y + 6, SCREEN);
            if (out) {
                g.fill(rx + 2, y + 2, rx + 5, y + 5, PHOS);
            }
            CashMachine.fit(g, font, rank, rx + 10, y, RW - 10, out ? PHOS : DIM);
        }
        key(g, rx, y0 + 110, 43, 12, tr("add"), card == LotteryAdmin.Action.ADD, true, mx, my);
        key(g, rx + 47, y0 + 110, 43, 12, tr("take"), card == LotteryAdmin.Action.TAKE, true, mx, my);
        boolean armed = System.currentTimeMillis() < armedUntil;
        if (view.busy()) {
            key(g, rx, y0 + 126, RW, 20, tr("drawing_key"), false, false, mx, my);
        } else {
            boolean hover = over(mx, my, rx, y0 + 126, RW, 20);
            g.fill(rx, y0 + 126, rx + RW, y0 + 146, AMBER_DARK);
            g.fill(rx, y0 + 126, rx + RW, y0 + 144, hover ? 0xFFF7C866 : AMBER);
            String label = armed ? tr("sure") : tr("draw");
            CashMachine.fit(g, font, label, rx + (RW - Math.min(RW - 6, font.width(label))) / 2, y0 + 131, RW - 6, SCREEN);
        }
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt < MESSAGE_MS) {
            CashMachine.fit(g, font, message, x0 + 2, y0 + 152, 268, AMBER);
        }
        if (card != null) {
            drawCard(g, mx, my);
        }
    }

    private void drawCard(GuiGraphics g, int mx, int my) {
        g.fill(x0 - 4, y0 - 21, x0 + 276, y0 + 175, 0xA0000000);
        int bx = x0 + 56, by = y0 + 38;
        g.fill(bx, by, bx + 160, by + 70, SCREEN);
        g.fill(bx, by, bx + 160, by + 1, PHOS);
        g.fill(bx, by + 69, bx + 160, by + 70, PHOS);
        g.fill(bx, by, bx + 1, by + 70, PHOS);
        g.fill(bx + 159, by, bx + 160, by + 70, PHOS);
        String title = tr(card == LotteryAdmin.Action.ADD ? "card_add" : "card_take");
        g.drawString(font, title, bx + (160 - font.width(title)) / 2, by + 5, PHOS, false);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        field(g, bx + 8, by + 17, amountText, tr("pieces"), focus == 0, caret);
        field(g, bx + 8, by + 33, reasonText, tr("reason"), focus == 1, caret);
        key(g, bx + 8, by + 51, 68, 12, tr(card == LotteryAdmin.Action.ADD ? "add" : "take"), true, amount() > 0 && !reasonText.isBlank(), mx, my);
        key(g, bx + 84, by + 51, 68, 12, tr("cancel"), false, true, mx, my);
    }

    private void field(GuiGraphics g, int x, int y, String text, String hint, boolean focused, boolean caret) {
        g.fill(x, y, x + 144, y + 12, focused ? SEL : 0xFF0A1F13);
        if (text.isEmpty() && !focused) {
            g.drawString(font, hint, x + 3, y + 2, DIM, false);
        } else {
            CashMachine.fit(g, font, text + (focused && caret ? "_" : ""), x + 3, y + 2, 138, PHOS);
        }
    }

    private long amount() {
        try {
            return amountText.isEmpty() ? 0 : Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ input

    /** A click in the screen's own coordinates; true when the page took it (a card open takes every click). */
    public boolean click(double x, double y) {
        if (card != null) {
            int bx = x0 + 56, by = y0 + 38;
            if (over(x, y, bx + 8, by + 17, 144, 12)) {
                focus = 0;
            } else if (over(x, y, bx + 8, by + 33, 144, 12)) {
                focus = 1;
            } else if (over(x, y, bx + 8, by + 51, 68, 12)) {
                confirmCard();
            } else if (over(x, y, bx + 84, by + 51, 68, 12)) {
                card = null;
            }
            return true;
        }
        if (over(x, y, rx, y0 + 32, 12, 11) && view.days() > 1) {
            send(LotteryAdmin.Action.DAYS, "", -1, false);
            return true;
        }
        if (over(x, y, rx + RW - 12, y0 + 32, 12, 11) && view.days() < 90) {
            send(LotteryAdmin.Action.DAYS, "", 1, false);
            return true;
        }
        if (view.ranks().size() > RANK_ROWS && over(x, y, rx + RW - 21, y0 + 46, 10, 9)) {
            rankScroll = Math.max(0, rankScroll - 1);
            return true;
        }
        if (view.ranks().size() > RANK_ROWS && over(x, y, rx + RW - 10, y0 + 46, 10, 9)) {
            rankScroll = Math.min(view.ranks().size() - RANK_ROWS, rankScroll + 1);
            return true;
        }
        for (int i = 0; i < RANK_ROWS && rankScroll + i < view.ranks().size(); i++) {
            if (over(x, y, rx, y0 + 56 + i * 10, RW, 10)) {
                String rank = view.ranks().get(rankScroll + i);
                send(LotteryAdmin.Action.RANK, rank, 0, !view.leftOut().contains(rank));
                return true;
            }
        }
        if (over(x, y, rx, y0 + 110, 43, 12) || over(x, y, rx + 47, y0 + 110, 43, 12)) {
            card = over(x, y, rx, y0 + 110, 43, 12) ? LotteryAdmin.Action.ADD : LotteryAdmin.Action.TAKE;
            amountText = "";
            reasonText = "";
            focus = 0;
            return true;
        }
        if (over(x, y, rx, y0 + 126, RW, 20) && !view.busy()) {
            if (System.currentTimeMillis() < armedUntil) {
                armedUntil = 0;
                send(LotteryAdmin.Action.DRAW, "", 0, false);
            } else {
                armedUntil = System.currentTimeMillis() + ARM_MS;
            }
            return true;
        }
        return false;
    }

    private void confirmCard() {
        if (card != null && amount() > 0 && !reasonText.isBlank()) {
            send(card, reasonText.trim(), amount(), false);
            card = null;
        }
    }

    public boolean scroll(double x, double y, double delta) {
        int step = (int) -Math.signum(delta);
        if (over(x, y, rx, y0 + 46, RW, 60)) {
            rankScroll = Math.max(0, Math.min(Math.max(0, view.ranks().size() - RANK_ROWS), rankScroll + step));
        } else {
            tableScroll = Math.max(0, Math.min(Math.max(0, view.rows().size() - ROWS), tableScroll + step * 3));
        }
        return true;
    }

    /** True when a card is open: it takes every key (Escape closes it, Enter confirms, Tab moves between its fields). */
    public boolean keyPressed(int key) {
        if (card == null) {
            return false;
        }
        switch (key) {
            case 256 -> card = null;
            case 257, 335 -> confirmCard();
            case 258 -> focus = 1 - focus;
            case 259 -> {
                if (focus == 0 && !amountText.isEmpty()) {
                    amountText = amountText.substring(0, amountText.length() - 1);
                } else if (focus == 1 && !reasonText.isEmpty()) {
                    reasonText = reasonText.substring(0, reasonText.length() - 1);
                }
            }
            default -> {
                // other keys do nothing while the card is open
            }
        }
        return true;
    }

    public boolean charTyped(char c) {
        if (card == null) {
            return false;
        }
        if (focus == 0 && c >= '0' && c <= '9' && amountText.length() < 12) {
            amountText += c;
        } else if (focus == 1 && c >= ' ' && c != 127 && reasonText.length() < 60) {
            reasonText += c;
        }
        return true;
    }
}
