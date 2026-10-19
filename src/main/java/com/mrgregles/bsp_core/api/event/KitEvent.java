package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Posted on the Forge bus ({@code MinecraftForge.EVENT_BUS}) on the server thread when a kit
 * changes hands on this server. Notifications only: nothing can be cancelled, and listeners must
 * not give or take kits from inside them. Read kits with {@code BspKits}.
 */
public abstract class KitEvent extends Event {
    private final MinecraftServer server;
    private final String kitId;
    private final String kitName;

    protected KitEvent(MinecraftServer server, String kitId, String kitName) {
        this.server = server;
        this.kitId = kitId;
        this.kitName = kitName;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public String getKitId() {
        return kitId;
    }

    /** The kit's name at the time of the event. */
    public String getKitName() {
        return kitName;
    }

    /** Why a kit went to a player. */
    public enum How {
        /** Handed out at their first login (the kit's grant rule). */
        FIRST_JOIN,
        /** Redeemed from the kit browser, the kit's price paid. */
        REDEEMED,
        /** Given by another mod through {@code BspKits.give}. */
        REWARD,
        /** Given by an admin ({@code /bsp kit give} or GIVE TO in the panel). */
        ADMIN,
        /** TEST ON ME in the editor: no record is kept. */
        TEST
    }

    /** The kit's items landed in the player's inventory. */
    public static class Given extends KitEvent {
        private final UUID player;
        private final How how;

        public Given(MinecraftServer server, String kitId, String kitName, UUID player, How how) {
            super(server, kitId, kitName);
            this.player = player;
            this.how = how;
        }

        public UUID getPlayer() {
            return player;
        }

        public How getHow() {
            return how;
        }
    }

    /** A kit became owed to a player (it will be delivered when it fits and they are settled in). */
    public static class Owed extends KitEvent {
        private final UUID player;
        private final How how;

        public Owed(MinecraftServer server, String kitId, String kitName, UUID player, How how) {
            super(server, kitId, kitName);
            this.player = player;
            this.how = how;
        }

        public UUID getPlayer() {
            return player;
        }

        public How getHow() {
            return how;
        }
    }
}
