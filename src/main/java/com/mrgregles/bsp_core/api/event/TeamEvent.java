package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Posted on the Forge bus ({@code MinecraftForge.EVENT_BUS}) on the server thread when a team
 * changes on this server. Notifications only: none can be cancelled, and listeners must not change
 * the team from inside them. Read the team with {@code BspTeams}.
 */
public abstract class TeamEvent extends Event {
    private final MinecraftServer server;
    private final UUID teamId;
    private final String teamName;

    protected TeamEvent(MinecraftServer server, UUID teamId, String teamName) {
        this.server = server;
        this.teamId = teamId;
        this.teamName = teamName;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public UUID getTeamId() {
        return teamId;
    }

    /** The team's name at the time of the event. */
    public String getTeamName() {
        return teamName;
    }

    /** A team was made; its leader is its only member. */
    public static class Created extends TeamEvent {
        private final UUID leader;

        public Created(MinecraftServer server, UUID teamId, String teamName, UUID leader) {
            super(server, teamId, teamName);
            this.leader = leader;
        }

        public UUID getLeader() {
            return leader;
        }
    }

    /** A player accepted an invite and is now a member. */
    public static class Joined extends TeamEvent {
        private final UUID player;

        public Joined(MinecraftServer server, UUID teamId, String teamName, UUID player) {
            super(server, teamId, teamName);
            this.player = player;
        }

        public UUID getPlayer() {
            return player;
        }
    }

    /** A player is no longer a member: they left, were removed, or the team was disbanded. */
    public static class Left extends TeamEvent {
        public enum Reason { LEFT, KICKED, DISBANDED }

        private final UUID player;
        private final Reason reason;

        public Left(MinecraftServer server, UUID teamId, String teamName, UUID player, Reason reason) {
            super(server, teamId, teamName);
            this.player = player;
            this.reason = reason;
        }

        public UUID getPlayer() {
            return player;
        }

        public Reason getReason() {
            return reason;
        }
    }

    /** The lead passed to another member (handed over, or moved by an admin). */
    public static class LeaderChanged extends TeamEvent {
        private final UUID previous, leader;

        public LeaderChanged(MinecraftServer server, UUID teamId, String teamName, UUID previous, UUID leader) {
            super(server, teamId, teamName);
            this.previous = previous;
            this.leader = leader;
        }

        public UUID getPrevious() {
            return previous;
        }

        public UUID getLeader() {
            return leader;
        }
    }

    /** The team is gone; a {@link Left} with reason DISBANDED was posted for every member first. {@code getBy} is null for an admin command. */
    public static class Disbanded extends TeamEvent {
        @Nullable
        private final UUID by;

        public Disbanded(MinecraftServer server, UUID teamId, String teamName, @Nullable UUID by) {
            super(server, teamId, teamName);
            this.by = by;
        }

        @Nullable
        public UUID getBy() {
            return by;
        }
    }
}
