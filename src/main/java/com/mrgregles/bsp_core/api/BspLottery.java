package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.lottery.Lottery;
import com.mrgregles.bsp_core.lottery.LotteryDraw;
import com.mrgregles.bsp_core.lottery.LotteryPage;
import com.mrgregles.bsp_core.lottery.LotteryWin;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The lottery for other mods (BSP-Server Utilities request SU-BC1: its staff web panel): the pot, who is in, the last winners,
 * and an admin's draw. Amounts are pieces; times are epoch milliseconds.
 *
 * <p>Who is in and the past wins come from the copy the LOTTERY page and the Score Screen boards share, read again when it is
 * more than five seconds old (on a network from the database; a read that fails keeps the last copy, and before the first
 * good read there are no names and no wins). The pot is this server's copy of the pot's bank account. Answers come through a
 * callback on the server thread: at once when the copy is fresh, a read is already under way (the older copy) or on a single
 * server, else once it is read. While a network server's database is not connected ({@link #connected} false) they answer a
 * pot of 0, nobody in and no wins, never this world's own records in the network's place, and a draw answers "unreachable".
 *
 * <p>Server thread only: from another thread (a web request, say) hand the call to the server with
 * {@code server.execute(...)}. No callback may be null. Since 1.16.0.
 */
public final class BspLottery {
    private BspLottery() {}

    /**
     * Where the draw stands as this server sees it. {@code IDLE}: no draw. {@code COUNTING}: from a draw's start until its result
     * is shown on this server, whichever server runs the draw (the winner is picked and paid as the count ends). {@code DRAWING}:
     * a draw asked for on this server is being started (the claim, who is in and the pot are checked), or is still being paid
     * a minute after its countdown began.
     */
    public enum Phase { IDLE, COUNTING, DRAWING }

    /** One past draw: the winner's name, what they won and when. */
    public record Win(String name, long pieces, long atMs) {}

    /** The pot, how many players are in, where the draw stands and the past wins, newest first (at most ten). */
    public record Summary(long pot, int entrants, Phase phase, List<Win> lastWins) {
        public Summary {
            lastWins = List.copyOf(lastWins);
        }
    }

    /** False on a server meant to keep its records on the network whose database is not connected (yet, or any more). */
    public static boolean connected() {
        return !Lottery.offline();
    }

    /** Where the draw stands on this server now. */
    public static Phase phase() {
        if (LotteryDraw.pageState(System.currentTimeMillis()).phase() == LotteryDraw.Phase.COUNTING) {
            return Phase.COUNTING;
        }
        return LotteryDraw.busy() ? Phase.DRAWING : Phase.IDLE;
    }

    /** The lottery's {@link Summary}. */
    public static void summary(MinecraftServer server, Consumer<Summary> then) {
        Objects.requireNonNull(then, "then");
        if (!connected()) {
            then.accept(new Summary(0, 0, phase(), List.of()));
            return;
        }
        LotteryPage.inAndWins(server, (names, wins) -> {
            List<Win> out = new ArrayList<>(wins.size());
            for (LotteryWin w : wins) {
                out.add(new Win(w.name(), w.pieces(), w.ms()));
            }
            then.accept(new Summary(Market.pot(server), names.size(), phase(), out));
        });
    }

    /**
     * The names of the players in, in alphabetical order (ignoring case): seen within the lottery's days, and neither an operator
     * nor holding a rank left out as last recorded (at each login and logout, and every five minutes while online). A draw
     * checks again as it starts: anyone on the drawing server's operator list then is out, online or not.
     */
    public static void entrants(MinecraftServer server, Consumer<List<String>> then) {
        Objects.requireNonNull(then, "then");
        if (!connected()) {
            then.accept(List.of());
            return;
        }
        LotteryPage.inAndWins(server, (names, wins) -> then.accept(names));
    }

    /**
     * Draws the lottery now, exactly as {@code /bsp lottery draw} does: the countdown in chat on every server, then the winner
     * takes the whole pot through the bank and is added to the past wins. {@code by} (a name, such as the staff member's) is
     * written in the server log as the one who started it (one line, at most 64 characters; "api" when null or blank).
     * {@code then} hears "ok" (the countdown has begun), "busy" (a draw is running here or elsewhere), "unreachable" (the
     * network could not be reached; nothing was drawn), "nobody" (no player is in) or "empty" (the pot holds nothing): at once
     * on a single server, after the database answers on a network (never, if the server stops first). BSP-Core checks no
     * permission here: the caller decides who may draw.
     */
    public static void draw(MinecraftServer server, String by, Consumer<String> then) {
        Objects.requireNonNull(then, "then");
        LotteryDraw.start(server, byName(by), then);
    }

    /** {@code by} as written in the log: one line of at most 64 characters, "api" when null or blank. */
    static String byName(String by) {
        String s = by == null ? "" : by.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ").strip();
        if (s.isEmpty()) {
            return "api";
        }
        return s.codePointCount(0, s.length()) > 64 ? s.substring(0, s.offsetByCodePoints(0, 64)) : s;
    }
}
