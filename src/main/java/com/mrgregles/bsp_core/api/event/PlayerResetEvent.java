package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * An admin reset a player to a brand-new player ({@code /bsp player reset <name>}). Posted on {@code MinecraftForge.EVENT_BUS} on
 * the server thread; notifications only, never cancelable. Sister mods forget their own records of the player here, so the one
 * BSP-Core command starts the player over in every mod. A mod that does not compile against this BSP-Core may listen by class name
 * ({@code com.mrgregles.bsp_core.api.event.PlayerResetEvent$Forgotten} and {@code $Cleared}) and read the getters by reflection.
 *
 * <p>Two events:
 * <ul>
 * <li>{@link Forgotten} on every server of the network, once per reset: on the server the admin typed it on at once, on the others
 * when the reset reaches them (a Redis notice at once, or at the latest at their next read of the database's reset records, at a
 * start or every few minutes). Forget the player's records kept on this server; when {@link Forgotten#isOrigin()} is true, also the
 * records shared by the whole network (the database rows), which no other server's event should do again. The player may be online
 * here, on another server or nowhere.
 * <li>{@link Cleared} when what the player carries is wiped on this server: at once when they are online and settled here at the
 * reset, otherwise at their next login (on any server, after an inventory sync mod's restore), and again on each server whose own
 * copy of the player's files was not moved away. The inventory, ender chest, Curios, XP, effects, advancements and statistics are
 * already empty and BSP-Core's own keys of the player's persistent data are reset. Clear what your mod keeps on the player entity.
 * </ul>
 * Both carry {@link #getResetMs()}, the same for every event of one reset, so a mod can recognise a repeat.
 */
public abstract class PlayerResetEvent extends Event {
    private final MinecraftServer server;
    private final UUID player;
    private final String name;
    private final long resetMs;

    protected PlayerResetEvent(MinecraftServer server, UUID player, String name, long resetMs) {
        this.server = server;
        this.player = player;
        this.name = name;
        this.resetMs = resetMs;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public UUID getPlayer() {
        return player;
    }

    /** The player's name as the admin's lookup found it. */
    public String getName() {
        return name;
    }

    /** When the reset was made (wall clock, ms): one value for every event of one reset, on every server. */
    public long getResetMs() {
        return resetMs;
    }

    /** The reset reached this server: forget the player's records here (and, on the origin, the network's). */
    public static class Forgotten extends PlayerResetEvent {
        private final boolean origin;
        private final String by;

        public Forgotten(MinecraftServer server, UUID player, String name, long resetMs, boolean origin, String by) {
            super(server, player, name, resetMs);
            this.origin = origin;
            this.by = by;
        }

        /** True only on the server the reset was typed on: the one place records shared by the network are forgotten. */
        public boolean isOrigin() {
            return origin;
        }

        /** Who ran the reset (a player's name, or "Server" for the console). */
        public String getBy() {
            return by;
        }
    }

    /**
     * What the player carries was wiped on this server; {@link #getEntity()} is them, online (at a login, possibly still waiting for
     * the rest of the settle: posted before kits and owed items are handed over).
     */
    public static class Cleared extends PlayerResetEvent {
        private final ServerPlayer entity;

        public Cleared(MinecraftServer server, ServerPlayer entity, String name, long resetMs) {
            super(server, entity.getUUID(), name, resetMs);
            this.entity = entity;
        }

        public ServerPlayer getEntity() {
            return entity;
        }
    }
}
