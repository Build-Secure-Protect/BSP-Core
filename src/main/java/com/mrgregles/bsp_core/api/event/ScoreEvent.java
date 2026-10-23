package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Posted on the Forge bus on the server thread when the leaderboard is rebuilt and a player's points differ from the last
 * board (BSP-RankUp request RU-R1). The board is rebuilt every {@code scoring.refreshSeconds} (30), and on a single server
 * within a second of a totem changing; with network storage it is the combined board of every server. Nothing is posted
 * for the first board after the server starts (there is nothing to compare it with).
 */
public abstract class ScoreEvent extends Event {
    private final MinecraftServer server;

    protected ScoreEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** A player's points changed; {@code points} is 0 when their last totem left the board. */
    public static class Changed extends ScoreEvent {
        private final UUID player;
        private final String name;
        private final int points;
        private final int delta;
        private final int totems;
        private final int bestTier;

        public Changed(MinecraftServer server, UUID player, String name, int points, int delta, int totems, int bestTier) {
            super(server);
            this.player = player;
            this.name = name;
            this.points = points;
            this.delta = delta;
            this.totems = totems;
            this.bestTier = bestTier;
        }

        public UUID getPlayer() {
            return player;
        }

        public String getName() {
            return name;
        }

        /** Their points now. */
        public int getPoints() {
            return points;
        }

        /** Points now minus points on the last board (negative when they lost totems). */
        public int getDelta() {
            return delta;
        }

        /** Totems they hold now (placed and owned, or carried). */
        public int getTotems() {
            return totems;
        }

        /** Their best totem's tier, 1 (Tier I) to 5 (Tier V); 0 when they hold none. */
        public int getBestTier() {
            return bestTier;
        }
    }
}
