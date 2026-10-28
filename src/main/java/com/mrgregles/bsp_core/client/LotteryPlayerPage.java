package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.lottery.Lottery;
import com.mrgregles.bsp_core.lottery.LotteryDraw;
import com.mrgregles.bsp_core.lottery.LotteryPageView;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LotteryPageAskPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The Trade Post's LOTTERY page (look B, owner's pick 2026-10-28): a drum of the names of everyone in turns slowly at the
 * left, the middle row marked with amber arrows; beside it the pot, how many are in and the player's own chance; the last
 * five winners as ticket stubs along the bottom. While a draw counts down the drum spins fast and the count shows; when the
 * result comes it slows to a stop on the winner, lit amber. Operators get an ADMIN key, which opens the admin page. It asks
 * the server for its view about once a second while it shows.
 */
public class LotteryPlayerPage {
    private static final int SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544, AMBER_DARK = 0xFF5A4210;
    private static final int DW = 92, DH = 96, ROW = 12, ASK_MS = 1000, STOP_MS = 1800;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private static LotteryPageView view = LotteryPageView.EMPTY;
    private static long receivedAt;

    private final Font font;
    private final int x0, y0, w;
    private final boolean operator;
    private final Runnable openAdmin;
    private long askAt, lastFrame;
    /** The drum's turn in pixels, and the stop on the winner: where it began, where it ends and when. */
    private double offset;
    private int phaseSeen = -1;
    private double stopFrom, stopTo;
    private long stopStart;

    public LotteryPlayerPage(Font font, int x0, int y0, int w, boolean operator, Runnable openAdmin) {
        this.font = font;
        this.x0 = x0;
        this.y0 = y0;
        this.w = w;
        this.operator = operator;
        this.openAdmin = openAdmin;
    }

    public static void receive(LotteryPageView v) {
        view = v;
        receivedAt = System.currentTimeMillis();
    }

    /** Leaving a world or server: nothing of it is shown in the next. */
    public static void reset() {
        view = LotteryPageView.EMPTY;
    }

    /** Whether this page was made with the ADMIN key (the player was an operator then). */
    public boolean operator() {
        return operator;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.lottery.page." + key, args).getString();
    }

    private static boolean over(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    public void opened() {
        askAt = 0;
        tick();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now >= askAt) {
            askAt = now + ASK_MS;
            BSPNetwork.CHANNEL.sendToServer(new LotteryPageAskPacket());
        }
    }

    private LotteryDraw.Phase phase() {
        LotteryDraw.Phase[] all = LotteryDraw.Phase.values();
        return view.phase() >= 0 && view.phase() < all.length ? all[view.phase()] : LotteryDraw.Phase.IDLE;
    }

    /** How long ago the draw's phase began, by this client's clock since the view came. */
    private long phaseAge() {
        return view.phaseMs() + (System.currentTimeMillis() - receivedAt);
    }

    /** Text drawn larger than the font, its left edge at {@code x}. */
    private void big(GuiGraphics g, String text, int x, int y, float scale, int maxW, int colour) {
        float s = Math.min(scale, (float) maxW / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void turnDrum() {
        long now = System.currentTimeMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.2, (now - lastFrame) / 1000.0);
        lastFrame = now;
        int total = Math.max(1, view.names().size()) * ROW;
        LotteryDraw.Phase p = phase();
        if (p.ordinal() != phaseSeen) {
            phaseSeen = p.ordinal();
            int at = view.names().indexOf(view.winner());
            if (p == LotteryDraw.Phase.WON && at >= 0) {
                // slow to a stop on the winner, after at least one more turn
                stopFrom = offset;
                double base = Math.floor(offset / total) * total + at * ROW;
                stopTo = base + total * (base - offset < total ? 2 : 1);
                stopStart = phaseAge() > STOP_MS ? 0 : now;
            }
        }
        if (p == LotteryDraw.Phase.WON && view.names().contains(view.winner())) {
            double f = stopStart == 0 ? 1 : Math.min(1, (now - stopStart) / (double) STOP_MS);
            offset = stopFrom + (stopTo - stopFrom) * (1 - Math.pow(1 - f, 3));
        } else {
            offset += dt * (p == LotteryDraw.Phase.COUNTING ? 160 : 6);
        }
    }

    /** Draws the page; {@code left} and {@code top} are the screen's corner, for the drum's clipping. */
    public void draw(GuiGraphics g, int mx, int my, int left, int top) {
        turnDrum();
        LotteryDraw.Phase p = phase();
        List<String> names = view.names();
        // the drum
        int mid = y0 + DH / 2 - ROW / 2;
        boolean won = p == LotteryDraw.Phase.WON;
        g.fill(x0, y0, x0 + DW, y0 + 1, SCREEN_EDGE);
        g.fill(x0, y0 + DH - 1, x0 + DW, y0 + DH, SCREEN_EDGE);
        g.fill(x0, y0, x0 + 1, y0 + DH, SCREEN_EDGE);
        g.fill(x0 + DW - 1, y0, x0 + DW, y0 + DH, SCREEN_EDGE);
        if (names.isEmpty()) {
            CashMachine.smallWrap(g, font, Component.translatable("gui.bsp_core.lottery.page.nobody"), x0 + 6, mid - 4, DW - 12, DIM);
        } else {
            int total = names.size() * ROW;
            double off = ((offset % total) + total) % total;
            CashMachine.clip(g, left, top, x0 + 1, y0 + 1, DW - 2, DH - 2);
            int copies = DH / total + 2; // enough copies of a short list to fill the drum
            for (int k = -copies; k <= copies; k++) {
                for (int i = 0; i < names.size(); i++) {
                    int y = (int) Math.round(mid + i * ROW + k * total - off);
                    if (y < y0 - ROW || y > y0 + DH) {
                        continue;
                    }
                    int d = Math.abs(y - mid);
                    int colour = d < ROW / 2 ? (won ? AMBER : PHOS) : d < ROW * 2 ? DIM : SCREEN_EDGE;
                    String n = names.get(i);
                    int nw = Math.min(DW - 20, font.width(n));
                    CashMachine.fit(g, font, n, x0 + (DW - nw) / 2, y + 2, DW - 20, colour);
                }
            }
            g.disableScissor();
        }
        int line = won ? AMBER : SCREEN_EDGE;
        g.fill(x0 + 1, mid - 1, x0 + DW - 1, mid, line);
        g.fill(x0 + 1, mid + ROW, x0 + DW - 1, mid + ROW + 1, line);
        g.drawString(font, "▶", x0 + 2, mid + 2, AMBER, false);
        g.drawString(font, "◀", x0 + DW - 8, mid + 2, AMBER, false);

        // the pot, how many are in, and the player's chance or the draw
        int rx = x0 + DW + 8, rw = w - DW - 8;
        g.drawString(font, tr("pot"), rx, y0, DIM, false);
        big(g, Bank.format(view.pot()), rx, y0 + 10, 1.5f, rw, AMBER);
        g.drawString(font, tr("in_drum"), rx, y0 + 29, DIM, false);
        big(g, String.valueOf(view.in()), rx, y0 + 39, 1.25f, rw, PHOS);
        if (p == LotteryDraw.Phase.COUNTING) {
            long step = Math.max(1, view.countdownMs() / 6);
            long n = Math.max(1, 5 - Math.max(0, phaseAge() / step - 1)); // as the chat: 5 a second in, then down to 1
            g.drawString(font, tr("drawing_in"), rx, y0 + 56, DIM, false);
            big(g, String.valueOf(n), rx, y0 + 66, 2f, rw, AMBER);
        } else if (won) {
            g.drawString(font, tr("winner"), rx, y0 + 56, DIM, false);
            big(g, view.winner(), rx, y0 + 66, 1.25f, rw, AMBER);
            CashMachine.fit(g, font, Bank.format(view.winPieces()), rx, y0 + 80, rw, PHOS);
        } else {
            g.drawString(font, tr("chance"), rx, y0 + 56, DIM, false);
            Lottery.Standing[] all = Lottery.Standing.values();
            Lottery.Standing s = view.standing() >= 0 && view.standing() < all.length ? all[view.standing()] : null;
            if (s == null) {
                g.drawString(font, "...", rx, y0 + 67, DIM, false);
            } else if (s == Lottery.Standing.IN) {
                big(g, tr("one_in", view.in()), rx, y0 + 66, 1.25f, rw, PHOS);
            } else {
                String why = switch (s) {
                    case OPERATOR -> tr("out_op");
                    case RANK -> tr("out_rank", view.rank());
                    default -> tr("out_away", view.days());
                };
                CashMachine.smallWrap(g, font, Component.literal(why), rx, y0 + 66, rw, AMBER);
            }
        }

        // the last winners, as ticket stubs
        g.drawString(font, tr("winners"), x0, y0 + 102, DIM, false);
        List<LotteryPageView.Win> wins = view.wins();
        if (wins.isEmpty()) {
            g.drawString(font, tr("no_winners"), x0, y0 + 116, SCREEN_EDGE, false);
        }
        int gap = 3, sw = (w - gap * (LotteryPageView.WINS - 1)) / LotteryPageView.WINS;
        for (int i = 0; i < wins.size(); i++) {
            LotteryPageView.Win win = wins.get(i);
            int sx = x0 + i * (sw + gap), sy = y0 + 112;
            g.fill(sx, sy, sx + sw, sy + 1, AMBER_DARK);
            g.fill(sx, sy + 31, sx + sw, sy + 32, AMBER_DARK);
            g.fill(sx, sy, sx + 1, sy + 32, AMBER_DARK);
            g.fill(sx + sw - 1, sy, sx + sw, sy + 32, AMBER_DARK);
            g.fill(sx - 1, sy + 13, sx + 1, sy + 18, SCREEN);
            g.fill(sx + sw - 1, sy + 13, sx + sw + 1, sy + 18, SCREEN);
            String day = DAY.format(Instant.ofEpochMilli(win.ms()).atZone(ZoneId.systemDefault()));
            centred(g, win.name(), sx, sy + 3, sw, PHOS);
            centred(g, Bank.format(win.pieces()), sx, sy + 12, sw, AMBER);
            centred(g, day, sx, sy + 21, sw, DIM);
        }

        // the foot: how it works, and the operators' key
        CashMachine.fit(g, font, tr("note"), x0, y0 + 150, operator ? w - 50 : w, DIM);
        if (operator) {
            boolean hover = over(mx, my, x0 + w - 44, y0 + 146, 44, 16);
            int edge = hover ? PHOS : DIM;
            int kx = x0 + w - 44, ky = y0 + 146;
            g.fill(kx, ky, kx + 44, ky + 1, edge);
            g.fill(kx, ky + 15, kx + 44, ky + 16, edge);
            g.fill(kx, ky, kx + 1, ky + 16, edge);
            g.fill(kx + 43, ky, kx + 44, ky + 16, edge);
            String a = tr("admin");
            g.drawString(font, a, kx + (44 - font.width(a)) / 2, ky + 4, PHOS, false);
        }
    }

    private void centred(GuiGraphics g, String text, int x, int y, int w, int colour) {
        int tw = Math.min(w - 4, font.width(text));
        CashMachine.fit(g, font, text, x + (w - tw) / 2, y, w - 4, colour);
    }

    public boolean click(double x, double y) {
        if (operator && over(x, y, x0 + w - 44, y0 + 146, 44, 16)) {
            openAdmin.run();
            return true;
        }
        return false;
    }
}
