package com.mrgregles.bsp_core.discord;

/** Where Discord alerts go: the bot over Redis in play, a recorder in the GameTests. Called on the server thread; must not block. */
@FunctionalInterface
public interface AlertSender {
    void send(DiscordAlert alert);
}
