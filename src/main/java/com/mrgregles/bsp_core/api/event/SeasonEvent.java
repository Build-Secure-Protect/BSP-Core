package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Posted on the Forge bus on the server thread when a season ends on this server (every totem wiped, prizes paid). */
public abstract class SeasonEvent extends Event {
    private final MinecraftServer server;

    protected SeasonEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /**
     * The season ended. With {@link #isFullReset()} every Shatter Coin was wiped too. With {@link #isSeasonReset()} it was a season
     * reset ({@code /bsp season reset}): every player starts the new season over (each BSP mod resets what it keeps of them: ranks,
     * homes, claims, graves...), and every player was sent off the network. A season reset is posted on every server once that
     * server's own records are cleared: {@link #isOrigin()} on the server it was typed on (which already reset what the network
     * shares, BSP-Core's rows and the bank), not on the others, which forget their own copies only.
     */
    public static class Ended extends SeasonEvent {
        private final int endedNumber;
        private final boolean fullReset;
        private final String by;
        private final boolean seasonReset;
        private final boolean origin;
        private final long resetMs;

        public Ended(MinecraftServer server, int endedNumber, boolean fullReset, String by) {
            this(server, endedNumber, fullReset, by, false, true, 0L);
        }

        public Ended(MinecraftServer server, int endedNumber, boolean fullReset, String by, boolean seasonReset, boolean origin) {
            this(server, endedNumber, fullReset, by, seasonReset, origin, 0L);
        }

        public Ended(MinecraftServer server, int endedNumber, boolean fullReset, String by, boolean seasonReset, boolean origin, long resetMs) {
            super(server);
            this.resetMs = seasonReset ? resetMs : 0L;
            this.endedNumber = endedNumber;
            this.fullReset = fullReset || seasonReset;
            this.by = by;
            this.seasonReset = seasonReset;
            this.origin = origin;
        }

        /** A season reset: every player starts the new season over in every BSP mod. Implies {@link #isFullReset()}. */
        public boolean isSeasonReset() {
            return seasonReset;
        }

        /** Whether the season ended on this server (a season reset typed here), not heard from another server of the network. */
        public boolean isOrigin() {
            return origin;
        }

        /**
         * When the season reset was made (epoch milliseconds, the new season's start on the server it was typed on), the same on every
         * server however late it hears, even after later season ends; 0 when this is not a season reset. Something a player gained here after it belongs to the new season.
         */
        public long getResetMs() {
            return resetMs;
        }

        /** The number of the season that just ended. */
        public int getEndedNumber() {
            return endedNumber;
        }

        public boolean isFullReset() {
            return fullReset;
        }

        /** Who ended it (an operator's name, or "console"). */
        public String getBy() {
            return by;
        }
    }

    /**
     * A player was wiped for a season reset on this server, at their first login since it (after an inventory sync mod's restore):
     * what they carry is gone and their stamp written. Posted once per player per season reset, on the server they logged in to; also
     * for a player who had never played ({@link #isNewPlayer()}: nothing was taken, only the stamp written), so every player hears it.
     */
    public static class Wiped extends SeasonEvent {
        private final net.minecraft.server.level.ServerPlayer player;
        private final int endedNumber;
        private final boolean newPlayer;

        public Wiped(MinecraftServer server, net.minecraft.server.level.ServerPlayer player, int endedNumber) {
            this(server, player, endedNumber, false);
        }

        public Wiped(MinecraftServer server, net.minecraft.server.level.ServerPlayer player, int endedNumber, boolean newPlayer) {
            super(server);
            this.player = player;
            this.endedNumber = endedNumber;
            this.newPlayer = newPlayer;
        }

        /** The player had never played before the reset (no totem yet): nothing of theirs was wiped, they were only marked. */
        public boolean isNewPlayer() {
            return newPlayer;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return player;
        }

        /** The number of the season that ended with the reset. */
        public int getEndedNumber() {
            return endedNumber;
        }
    }
}
