package com.mrgregles.bsp_core.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * Recall across servers. When a totem with the Recall upgrade is being stolen while its owner is
 * on another server of the network, BSP-Core sends the owner a clickable offer; clicking it posts
 * {@link CrossServer} on the owner's server. A teleport mod that can move players between servers
 * takes them to {@link CrossServer#getTargetServer()} and calls {@link #setHandled()}; on arrival
 * BSP-Core finishes the Recall by itself (the offer waits there). Unhandled, BSP-Core tells the
 * owner no teleport mod can take them.
 */
public abstract class RecallEvent extends Event {
    private final Player owner;

    protected RecallEvent(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return owner;
    }

    public static class CrossServer extends RecallEvent {
        private final String targetServer;
        private boolean handled;

        public CrossServer(Player owner, String targetServer) {
            super(owner);
            this.targetServer = targetServer;
        }

        /** The {@code storage.serverId} of the server the totem stands on. */
        public String getTargetServer() {
            return targetServer;
        }

        /** The listener will move the player (it may call {@code BspTransfer.beginTransfer} itself). */
        public void setHandled() {
            handled = true;
        }

        public boolean isHandled() {
            return handled;
        }
    }
}
