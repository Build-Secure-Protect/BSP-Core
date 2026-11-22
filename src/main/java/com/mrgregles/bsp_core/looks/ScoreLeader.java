package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.score.ScoreService;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Who the Score Leader is: the one player with the most points on the score board (the board every client already gets), with at
 * least one point. Nobody leads while two or more players share the top points; the board's own tie-breaks (more totems, the higher
 * tier, the name) do not count for this.
 */
public final class ScoreLeader {
    private ScoreLeader() {}

    @Nullable
    public static UUID leader(List<ScoreService.Entry> board) {
        ScoreService.Entry top = null;
        boolean shared = false;
        for (ScoreService.Entry e : board) {
            if (top == null || e.points() > top.points()) {
                top = e;
                shared = false;
            } else if (e.points() == top.points()) {
                shared = true;
            }
        }
        return top == null || shared || top.points() < 1 ? null : top.id();
    }
}
