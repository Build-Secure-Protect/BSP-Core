package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.score.ScoreService;

import java.util.List;

/** The leaderboard as last sent by the server. Read by Score Screens and the admin panel. */
public final class ClientScores {
    private static volatile List<ScoreService.Entry> board = List.of();

    private ClientScores() {}

    private static volatile java.util.UUID leader;

    public static void accept(List<ScoreService.Entry> entries) {
        board = List.copyOf(entries);
        leader = com.mrgregles.bsp_core.looks.ScoreLeader.leader(board);
    }

    /** The Score Leader: the one player with the most points (none on a tie, or with no points). */
    @javax.annotation.Nullable
    public static java.util.UUID leader() {
        return leader;
    }

    private static volatile List<net.minecraft.world.item.ItemStack> prizes = List.of();
    private static volatile int season = 1;

    public static void acceptPrizes(int seasonNumber, List<net.minecraft.world.item.ItemStack> stacks) {
        season = seasonNumber;
        prizes = List.copyOf(stacks);
    }

    /** This season's prize slots: ten for first place, then ten for second, then ten for third. */
    public static List<net.minecraft.world.item.ItemStack> prizes() {
        return prizes;
    }

    public static int season() {
        return season;
    }

    public static List<ScoreService.Entry> board() {
        return board;
    }
}
