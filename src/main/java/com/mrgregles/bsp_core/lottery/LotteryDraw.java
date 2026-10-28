package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * An admin's draw of the lottery. The server the admin is on runs it: it marks everyone online as seen, claims the draw (on a
 * network, the shared state key {@code lottery_draw}, so only one draw runs at a time), takes the players who are in and
 * checks the pot holds something, and only then starts the countdown. Every server (told over Redis, kind {@code lottery})
 * says in chat that the lottery is being drawn, counts 5, 4, 3, 2, 1 a second apart, and then names the winner and the
 * amount. As the count reaches its end the drawing server picks the winner at random from the players who were in when it
 * began (a seed from {@link SecureRandom}, written to the log), and pays them the whole pot in one step: on a network in one
 * database transaction that also records the win. A winner who is offline is told at their next login (the bank's "while
 * you were away"). Payload lines: {@code start|draw|pot|players}, {@code won|draw|uuid|name|pieces|players},
 * {@code fail|draw|reason}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LotteryDraw {
    private LotteryDraw() {}

    /** Real milliseconds between the countdown's lines; GameTests set 0. */
    public static long stepMs = 1000;
    /** The numbers counted down. */
    private static final int STEPS = 5;
    /** A claim on the draw older than this is taken over (its server stopped mid-draw). */
    private static final long STALE_MS = 120_000;
    /** A countdown whose result never arrives is dropped after this long. */
    private static final long SHOW_LIMIT_MS = 60_000;

    private static final class Running {
        final String id, by;
        final List<LotteryPlayer> entrants;
        final long start;
        boolean paying;

        Running(String id, String by, List<LotteryPlayer> entrants, long start) {
            this.id = id;
            this.by = by;
            this.entrants = entrants;
            this.start = start;
        }
    }

    /** A countdown shown on this server: the lines sent so far (0 the opening line, then one per number) and, once known, the result. */
    private static final class Show {
        final long pot, start;
        final int players;
        int sent;
        @Nullable
        Component result;
        @Nullable
        UUID winner;
        String winnerName = "";
        long pieces;

        Show(long pot, int players, long start) {
            this.pot = pot;
            this.players = players;
            this.start = start;
        }
    }

    /** The draw this server runs, if any. */
    @Nullable
    private static Running running;
    /** True from an admin's DRAW until the draw runs or is refused. */
    private static boolean starting;
    private static final Map<String, Show> shows = new LinkedHashMap<>();
    /** The LOTTERY page's view of the draw here: when the newest countdown began (0 none), and the last result shown. */
    private static long countingSince;
    @Nullable
    private static String lastWinner;
    private static long lastPieces, lastShownAt;

    /** What the LOTTERY page shows of the draw: IDLE, COUNTING (since {@code sinceMs}), or WON (shown at {@code sinceMs}). */
    public enum Phase { IDLE, COUNTING, WON }

    public record PageState(Phase phase, long sinceMs, String winner, long pieces) {}

    /** How long the LOTTERY page keeps showing the last winner after the result. */
    private static final long WON_SHOWN_MS = 30_000;

    public static PageState pageState(long now) {
        if (countingSince > 0) {
            return new PageState(Phase.COUNTING, countingSince, "", 0);
        }
        if (lastWinner != null && now - lastShownAt < WON_SHOWN_MS) {
            return new PageState(Phase.WON, lastShownAt, lastWinner, lastPieces);
        }
        return new PageState(Phase.IDLE, 0, "", 0);
    }

    /** The real milliseconds of the whole countdown, for the page's drum. */
    public static long countdownMs() {
        return (STEPS + 1) * stepMs;
    }

    /** True while this server is starting or running a draw. */
    public static boolean busy() {
        return starting || running != null;
    }

    /**
     * Starts a draw. {@code then} hears "ok" (the countdown has begun), "busy" (a draw is running here or elsewhere),
     * "unreachable", "nobody" (no player is in) or "empty" (the pot holds nothing).
     */
    public static void start(MinecraftServer server, String by, Consumer<String> then) {
        if (busy()) {
            then.accept("busy");
            return;
        }
        if (Lottery.offline()) {
            then.accept("unreachable"); // a network server without its database must never draw from its own old copy
            return;
        }
        starting = true;
        long now = System.currentTimeMillis();
        String id = Long.toString(now, 36) + Integer.toString(ThreadLocalRandom.current().nextInt(46_656), 36);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Lottery.seen(p); // so someone online for longer than the days is still in
        }
        Consumer<String> refuse = key -> {
            release(id);
            starting = false;
            then.accept(key);
        };
        claim(id, now, got -> {
            if (got == null || !got) {
                starting = false;
                then.accept(got == null ? "unreachable" : "busy");
                return;
            }
            Lottery.entrants(server, now, entrants -> {
                if (entrants == null) {
                    refuse.accept("unreachable");
                    return;
                }
                if (entrants.isEmpty()) {
                    refuse.accept("nobody");
                    return;
                }
                pot(server, pot -> {
                    if (pot < 0) {
                        refuse.accept("unreachable");
                        return;
                    }
                    if (pot == 0) {
                        refuse.accept("empty");
                        return;
                    }
                    // an operator here now is out, whatever they were at their last login (an op made while they were away)
                    List<LotteryPlayer> sorted = new ArrayList<>(entrants.stream()
                            .filter(p -> !server.getPlayerList().isOp(new com.mojang.authlib.GameProfile(p.id(), p.name()))).toList());
                    if (sorted.isEmpty()) {
                        refuse.accept("nobody");
                        return;
                    }
                    sorted.sort(Comparator.comparing(p -> p.id().toString()));
                    running = new Running(id, by, sorted, System.currentTimeMillis());
                    starting = false;
                    BSPCore.LOGGER.info("Lottery draw {} started by {}: {} pieces, {} players in", id, by, pot, sorted.size());
                    tell(server, "start|" + id + "|" + pot + "|" + sorted.size());
                    then.accept("ok");
                });
            });
        });
    }

    /** Whether the {@code lottery_draw} value {@code held} ("id@ms", "" free) is a draw still running, not one abandoned. */
    public static boolean claimLive(String held, long now) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        int at = held.lastIndexOf('@');
        try {
            return at >= 0 && now - Long.parseLong(held.substring(at + 1)) < STALE_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The index of the winner among {@code entrants} (sorted by id) for {@code seed}. */
    public static int pick(int entrants, long seed) {
        return new java.util.Random(seed).nextInt(entrants);
    }

    private static void claim(String id, long now, Consumer<Boolean> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.lotteryClaim(id, now, STALE_MS, then);
        } else {
            then.accept(true);
        }
    }

    private static void release(String id) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.lotteryRelease(id);
        }
    }

    /** The pot as it is now (read from the database on a network); -1 when unreachable. */
    private static void pot(MinecraftServer server, Consumer<Long> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.bankRead(Market.POT, row -> then.accept(row == null ? -1 : row.pieces));
        } else {
            then.accept(Market.pot(server));
        }
    }

    /** Shows {@code payload} here and sends it to the other servers. */
    private static void tell(MinecraftServer server, String payload) {
        NetworkStorage.publishLottery(payload);
        onMessage(server, payload);
    }

    // ------------------------------------------------------------------ paying

    private static void pay(MinecraftServer server, Running r) {
        long seed = new SecureRandom().nextLong();
        LotteryPlayer w = r.entrants.get(pick(r.entrants.size(), seed));
        int n = r.entrants.size();
        BSPCore.LOGGER.info("Lottery draw {} by {}: {} players in, seed {}, picked {} ({})", r.id, r.by, n, seed, w.name(), w.id());
        String won = wonDetail(n);
        if (NetworkStorage.enabled()) {
            NetworkStorage.lotteryPayout(new LotteryWin(w.id(), w.name(), 0, System.currentTimeMillis(), n, r.id), p -> {
                if (p.key().equals("unsure")) {
                    settleUnsure(server, r, w);
                    return;
                }
                if (!p.key().equals("ok")) {
                    end(server, r, "fail|" + r.id + "|" + p.key());
                    return;
                }
                // the payout wrote both log rows in its own transaction: here only this server's copies follow
                Bank.changedAndLoggedInDatabase(server, Market.POT, 0, BankTransaction.Type.LOTTERY, w.name(), potDetail(r.id), -p.pieces());
                Bank.changedAndLoggedInDatabase(server, w.id(), p.winnerPieces(), BankTransaction.Type.LOTTERY, Market.POT_NAME, won, p.pieces());
                end(server, r, "won|" + r.id + "|" + w.id() + "|" + w.name() + "|" + p.pieces() + "|" + n);
            });
            return;
        }
        long amount = Market.pot(server);
        if (amount <= 0) {
            end(server, r, "fail|" + r.id + "|empty");
            return;
        }
        Bank.take(server, Market.POT, amount, BankTransaction.Type.LOTTERY, w.name(), potDetail(r.id), t -> {
            if (!t.ok()) {
                end(server, r, "fail|" + r.id + "|untouched");
                return;
            }
            Bank.give(server, w.id(), amount, BankTransaction.Type.LOTTERY, Market.POT_NAME, won, g -> {
                LotteryLedger.get(server).won(new LotteryWin(w.id(), w.name(), amount, System.currentTimeMillis(), n, r.id));
                end(server, r, "won|" + r.id + "|" + w.id() + "|" + w.name() + "|" + amount + "|" + n);
            });
        });
    }

    /** The pot's log line for a draw. */
    public static String potDetail(String draw) {
        return "draw " + draw;
    }

    /** The winner's log line for a draw of {@code n} players. */
    public static String wonDetail(int n) {
        return "won the lottery draw of " + n + " players";
    }

    /**
     * The payout's answer was lost after it may have committed: the shared list of wins says whether it did (the payout writes
     * the win in the same transaction). Either way the pot and the winner are read again.
     */
    private static void settleUnsure(MinecraftServer server, Running r, LotteryPlayer w) {
        NetworkStorage.readStateKey(LotteryWin.STATE_KEY, text -> {
            Bank.refresh(server, Market.POT);
            Bank.refresh(server, w.id());
            LotteryWin paid = text == null ? null : LotteryWin.fromText(text).stream().filter(x -> x.draw().equals(r.id)).findFirst().orElse(null);
            if (paid != null) {
                end(server, r, "won|" + r.id + "|" + paid.id() + "|" + paid.name() + "|" + paid.pieces() + "|" + paid.entrants());
            } else {
                end(server, r, "fail|" + r.id + "|unsure");
            }
        });
    }

    /** Stopping mid-draw: the claim is freed and the other servers told, before the network storage closes. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        Running r = running;
        if (r != null && !r.paying) {
            BSPCore.LOGGER.warn("Lottery draw {} stopped with the server before it was paid; nothing was paid", r.id);
            release(r.id);
            NetworkStorage.publishLottery("fail|" + r.id + "|stopped");
        }
    }

    private static void end(MinecraftServer server, Running r, String payload) {
        BSPCore.LOGGER.info("Lottery draw {} ended: {}", r.id, payload);
        release(r.id);
        running = null;
        tell(server, payload);
    }

    // ------------------------------------------------------------------ the countdown, on every server

    /** A draw's line from this or another server. */
    public static void onMessage(MinecraftServer server, String payload) {
        String[] p = payload.split("\\|");
        try {
            switch (p[0]) {
                case "start" -> {
                    shows.put(p[1], new Show(Long.parseLong(p[2]), Integer.parseInt(p[3]), System.currentTimeMillis()));
                    countingSince = System.currentTimeMillis();
                }
                case "won" -> {
                    Show s = arrived(p[1]);
                    s.winner = UUID.fromString(p[2]);
                    s.pieces = Long.parseLong(p[4]);
                    s.result = line("won", ChatFormatting.AQUA, p[3], Bank.format(s.pieces));
                    s.winnerName = p[3];
                }
                case "fail" -> arrived(p[1]).result = line(p[2].equals("unsure") ? "unsure" : "failed", ChatFormatting.RED);
                default -> BSPCore.LOGGER.warn("BSP-Core lottery: unknown message {}", payload);
            }
        } catch (RuntimeException e) {
            BSPCore.LOGGER.warn("BSP-Core lottery: unreadable message {}", payload);
        }
    }

    /** The countdown a result belongs to; one that never started here (this server came up mid-draw) shows the result at once. */
    private static Show arrived(String id) {
        return shows.computeIfAbsent(id, k -> {
            Show s = new Show(0, 0, System.currentTimeMillis() - (STEPS + 1) * stepMs);
            s.sent = STEPS + 1;
            return s;
        });
    }

    private static MutableComponent line(String key, ChatFormatting colour, Object... args) {
        return Component.translatable("message.bsp_core.lottery.prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.bsp_core.lottery." + key, args).withStyle(colour));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (running == null && shows.isEmpty())) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        Running r = running;
        if (r != null && !r.paying && now - r.start >= STEPS * stepMs) {
            r.paying = true;
            pay(server, r);
        }
        for (Iterator<Map.Entry<String, Show>> it = shows.entrySet().iterator(); it.hasNext(); ) {
            Show s = it.next().getValue();
            long since = now - s.start;
            int due = stepMs <= 0 ? STEPS + 1 : (int) Math.min(STEPS + 1, since / stepMs + 1);
            while (s.sent < due) {
                server.getPlayerList().broadcastSystemMessage(s.sent == 0
                        ? line("drawing", ChatFormatting.WHITE, Bank.format(s.pot), s.players)
                        : line("count", ChatFormatting.YELLOW, STEPS + 1 - s.sent), false);
                s.sent++;
            }
            if (s.result != null && s.sent > STEPS && since >= (STEPS + 1) * stepMs) {
                server.getPlayerList().broadcastSystemMessage(s.result, false);
                ServerPlayer winner = s.winner == null ? null : server.getPlayerList().getPlayer(s.winner);
                if (winner != null) {
                    winner.displayClientMessage(line("you_won", ChatFormatting.GREEN, Bank.format(s.pieces)), false);
                }
                countingSince = 0;
                if (s.winner != null) {
                    lastWinner = s.winnerName;
                    lastPieces = s.pieces;
                    lastShownAt = now;
                }
                LotteryPage.invalidate();
                it.remove();
            } else if (since > SHOW_LIMIT_MS) {
                BSPCore.LOGGER.warn("BSP-Core lottery: a draw's result never arrived here; its countdown was dropped");
                countingSince = 0;
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        running = null;
        starting = false;
        shows.clear();
        countingSince = 0;
        lastWinner = null;
    }
}
