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

    /** The season ended. With {@link #isFullReset()} every Shatter Coin was wiped too. */
    public static class Ended extends SeasonEvent {
        private final int endedNumber;
        private final boolean fullReset;
        private final String by;

        public Ended(MinecraftServer server, int endedNumber, boolean fullReset, String by) {
            super(server);
            this.endedNumber = endedNumber;
            this.fullReset = fullReset;
            this.by = by;
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
}
