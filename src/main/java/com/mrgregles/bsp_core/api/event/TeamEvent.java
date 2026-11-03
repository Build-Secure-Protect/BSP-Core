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

    /**
     * A member pressed TELEPORT on a teammate's row (or a mod asked the same through the api). A
     * teleport mod moves {@link #getPlayer()} to {@link #getTarget()} under its own rules (no price
     * for a team teleport; its cooldown and warm-up, destination access, zone flags, and its raid
     * rule, which refuses a landing within 150 blocks of any running steal on the network) and calls
     * {@link #setHandled()} on every request, refusals included, telling the player why in its own
     * words. {@link #getTargetServer()} names the network server the target is online on, "" when
     * they are on this one. Unhandled, BSP-Core tells the player no teleport mod answered, which
     * happens only in a pack without one.
     */
    public static class TeleportRequest extends TeamEvent {
        private final net.minecraft.server.level.ServerPlayer player;
        private final UUID target;
        private final String targetName, targetServer;
        private boolean handled;

        public TeleportRequest(MinecraftServer server, UUID teamId, String teamName, net.minecraft.server.level.ServerPlayer player, UUID target, String targetName, String targetServer) {
            super(server, teamId, teamName);
            this.player = player;
            this.target = target;
            this.targetName = targetName;
            this.targetServer = targetServer;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return player;
        }

        public UUID getTarget() {
            return target;
        }

        public String getTargetName() {
            return targetName;
        }

        public String getTargetServer() {
            return targetServer;
        }

        public void setHandled() {
            handled = true;
        }

        public boolean isHandled() {
            return handled;
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

    /** An event about the team's place in an alliance; {@link #getAllianceName()} is the alliance's name at the time. */
    public abstract static class AllianceEvent extends TeamEvent {
        private final UUID allianceId;
        private final String allianceName;

        protected AllianceEvent(MinecraftServer server, UUID teamId, String teamName, UUID allianceId, String allianceName) {
            super(server, teamId, teamName);
            this.allianceId = allianceId;
            this.allianceName = allianceName;
        }

        public UUID getAllianceId() {
            return allianceId;
        }

        public String getAllianceName() {
            return allianceName;
        }
    }

    /** The team made an alliance (it is then the only team in it) or joined one. */
    public static class Allied extends AllianceEvent {
        public Allied(MinecraftServer server, UUID teamId, String teamName, UUID allianceId, String allianceName) {
            super(server, teamId, teamName, allianceId, allianceName);
        }
    }

    /**
     * The team is out of the alliance: its leader left, the team was disbanded, an admin ended the alliance, or a member broke it
     * (then an {@link AllianceBroken} follows). {@link #isEnded()} when fewer than two teams were left, so the alliance is gone.
     */
    public static class AllianceLeft extends AllianceEvent {
        private final boolean ended;

        public AllianceLeft(MinecraftServer server, UUID teamId, String teamName, UUID allianceId, String allianceName, boolean ended) {
            super(server, teamId, teamName, allianceId, allianceName);
            this.ended = ended;
        }

        public boolean isEnded() {
            return ended;
        }
    }

    /**
     * A member of the team started a steal on an ally's totem, so the team was thrown out of the alliance. The steal goes ahead.
     * {@link #getThief()} started it on {@link #getOwner()}'s totem; {@link #isEnded()} when the alliance is gone.
     */
    public static class AllianceBroken extends AllianceEvent {
        private final UUID thief, owner;
        private final boolean ended;

        public AllianceBroken(MinecraftServer server, UUID teamId, String teamName, UUID allianceId, String allianceName, UUID thief, UUID owner, boolean ended) {
            super(server, teamId, teamName, allianceId, allianceName);
            this.thief = thief;
            this.owner = owner;
            this.ended = ended;
        }

        public UUID getThief() {
            return thief;
        }

        public UUID getOwner() {
            return owner;
        }

        public boolean isEnded() {
            return ended;
        }
    }

    /**
     * A player pressed TELEPORT on an ally's row on the Team screen's ALLIES page (or a mod asked the same through the api). Allies
     * trust each other less than teammates: a teleport mod asks the target first and moves {@link #getPlayer()} only when they
     * accept, under its own rules, and calls {@link #setHandled()} on every request, refusals included. {@link #getTargetServer()}
     * names the network server the target is online on, "" when they are on this one. The team is the player's.
     */
    public static class AllyTeleportRequest extends AllianceEvent {
        private final net.minecraft.server.level.ServerPlayer player;
        private final UUID target;
        private final String targetName, targetServer;
        private boolean handled;

        public AllyTeleportRequest(MinecraftServer server, UUID teamId, String teamName, UUID allianceId, String allianceName, net.minecraft.server.level.ServerPlayer player,
                                   UUID target, String targetName, String targetServer) {
            super(server, teamId, teamName, allianceId, allianceName);
            this.player = player;
            this.target = target;
            this.targetName = targetName;
            this.targetServer = targetServer;
        }

        public net.minecraft.server.level.ServerPlayer getPlayer() {
            return player;
        }

        public UUID getTarget() {
            return target;
        }

        public String getTargetName() {
            return targetName;
        }

        public String getTargetServer() {
            return targetServer;
        }

        public void setHandled() {
            handled = true;
        }

        public boolean isHandled() {
            return handled;
        }
    }
}
