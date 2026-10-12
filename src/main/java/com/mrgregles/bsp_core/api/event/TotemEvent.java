package com.mrgregles.bsp_core.api.event;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Posted on the Forge bus ({@code MinecraftForge.EVENT_BUS}) on the server thread when a Shatter
 * Totem changes hands or state on this server. Notifications only: none can be cancelled, and
 * listeners must not change the totem from inside them.
 */
public abstract class TotemEvent extends Event {
    private final MinecraftServer server;
    private final GlobalPos where;
    @Nullable
    private final UUID owner;

    protected TotemEvent(MinecraftServer server, GlobalPos where, @Nullable UUID owner) {
        this.server = server;
        this.where = where;
        this.owner = owner;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** The totem's dimension and position. */
    public GlobalPos getWhere() {
        return where;
    }

    /** The totem's owner at the time of the event (for an owner change, the new owner); null when unclaimed. */
    @Nullable
    public UUID getOwner() {
        return owner;
    }

    /** A totem with an owner now stands here: placed by its owner, or claimed where none stood. */
    public static class Placed extends TotemEvent {
        public Placed(MinecraftServer server, GlobalPos where, UUID owner) {
            super(server, where, owner);
        }
    }

    /** The totem here is gone: picked up, mined, reissued or wiped. {@link #getOwner()} is who owned it. */
    public static class Removed extends TotemEvent {
        public Removed(MinecraftServer server, GlobalPos where, @Nullable UUID owner) {
            super(server, where, owner);
        }
    }

    /** The totem's owner changed while it stood (a completed steal, or an operator's change). {@link #getOwner()} is the new owner, null when it became unclaimed. */
    public static class OwnerChanged extends TotemEvent {
        @Nullable
        private final UUID previous;

        public OwnerChanged(MinecraftServer server, GlobalPos where, @Nullable UUID previous, @Nullable UUID owner) {
            super(server, where, owner);
            this.previous = previous;
        }

        @Nullable
        public UUID getPrevious() {
            return previous;
        }
    }

    /** A player began stealing the totem. */
    public static class StealStarted extends TotemEvent {
        private final UUID thief;
        private final String thiefName;
        private final int seconds;

        public StealStarted(MinecraftServer server, GlobalPos where, @Nullable UUID owner, UUID thief, String thiefName, int seconds) {
            super(server, where, owner);
            this.thief = thief;
            this.thiefName = thiefName;
            this.seconds = seconds;
        }

        public UUID getThief() {
            return thief;
        }

        public String getThiefName() {
            return thiefName;
        }

        /** How long the steal takes if nothing stops it. */
        public int getSeconds() {
            return seconds;
        }
    }

    /** The steal ended: the thief took the totem ({@link #isSuccess()}, and an {@link OwnerChanged} follows), or it failed for {@link #getReasonKey()}. {@link #getOwner()} is the owner the steal was against. */
    public static class StealEnded extends TotemEvent {
        private final UUID thief;
        private final String thiefName;
        private final boolean success;
        private final String reasonKey;

        public StealEnded(MinecraftServer server, GlobalPos where, @Nullable UUID owner, UUID thief, String thiefName, boolean success, String reasonKey) {
            super(server, where, owner);
            this.thief = thief;
            this.thiefName = thiefName;
            this.success = success;
            this.reasonKey = reasonKey;
        }

        public UUID getThief() {
            return thief;
        }

        public String getThiefName() {
            return thiefName;
        }

        public boolean isSuccess() {
            return success;
        }

        /** A translation key for why it failed ("message.bsp_core.steal.failed_dead" and the like); empty on success. */
        public String getReasonKey() {
            return reasonKey;
        }
    }
}
