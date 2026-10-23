package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.score.ScoreService;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The leaderboard for other mods (BSP-RankUp request RU-R1). A player's points are the sum of their totems' tier points
 * ({@code scoring.tierPoints}, 1, 2, 4, 7 and 10 for Tier I to V by default); a totem counts for whoever owns it while placed
 * and whoever carries it while in an inventory. With network storage the board is every server's combined. Read from the last
 * board, so cheap to call; it is rebuilt every {@code scoring.refreshSeconds} (30), and on a single server within a second of
 * a change. {@link com.mrgregles.bsp_core.api.event.ScoreEvent.Changed} tells you when a player's points move.
 */
public final class BspScores {
    private BspScores() {}

    /** One player's line: {@code rank} is 1 for the leader; {@code bestTier} is 1 (Tier I) to 5 (Tier V). */
    public record Score(UUID player, String name, int rank, int points, int totems, int bestTier) {}

    /** The player's line on the board; empty when they hold no totem. */
    public static Optional<Score> score(MinecraftServer server, UUID player) {
        List<ScoreService.Entry> board = ScoreService.board();
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).id().equals(player)) {
                return Optional.of(of(board.get(i), i + 1));
            }
        }
        return Optional.empty();
    }

    /** The player's points; 0 when they hold no totem. */
    public static int points(MinecraftServer server, UUID player) {
        return score(server, player).map(Score::points).orElse(0);
    }

    /** The totems the player holds (placed and owned, or carried). */
    public static int totemsHeld(MinecraftServer server, UUID player) {
        return score(server, player).map(Score::totems).orElse(0);
    }

    /** The tier of the player's best totem, 1 (Tier I) to 5 (Tier V); 0 when they hold none. */
    public static int bestTier(MinecraftServer server, UUID player) {
        return score(server, player).map(Score::bestTier).orElse(0);
    }

    /** The whole board, best first. */
    public static List<Score> board(MinecraftServer server) {
        List<ScoreService.Entry> board = ScoreService.board();
        List<Score> out = new ArrayList<>(board.size());
        for (int i = 0; i < board.size(); i++) {
            out.add(of(board.get(i), i + 1));
        }
        return out;
    }

    private static Score of(ScoreService.Entry e, int rank) {
        return new Score(e.id(), e.name(), rank, e.points(), e.totems(), e.bestTier() + 1);
    }
}
