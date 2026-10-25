package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.discord.DiscordLinks;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.DiscordActionPacket;
import com.mrgregles.bsp_core.network.DiscordStatusPacket;

import javax.annotation.Nullable;

/** The player's own Discord link as the server last said, for the line on Herald's card. */
public final class DiscordClient {
    private DiscordClient() {}

    private static final long ASK_EVERY_MS = 5_000;
    @Nullable
    private static DiscordLinks.State state;
    private static String code = "";
    private static long codeUntil, askedAt;
    private static boolean stale = true;

    public static void receive(DiscordStatusPacket msg) {
        state = msg.state();
        if (state == DiscordLinks.State.CODE) {
            code = msg.code();
            codeUntil = System.currentTimeMillis() + msg.secondsLeft() * 1000L;
        } else if (state == DiscordLinks.State.LINKED || state == DiscordLinks.State.NOT_LINKED) {
            code = "";
        }
    }

    /** A totem screen opened: the link may have changed on Discord's side since. */
    public static void opened() {
        stale = true;
    }

    /** Asks the server when the state is unknown or old, and every few seconds while a code waits (the bot takes it when the player links). */
    public static void refresh() {
        long now = System.currentTimeMillis();
        if ((stale || state == DiscordLinks.State.CODE) && now - askedAt >= ASK_EVERY_MS) {
            send(DiscordLinks.Action.STATUS);
        }
    }

    public static void send(DiscordLinks.Action action) {
        askedAt = System.currentTimeMillis();
        stale = false;
        BSPNetwork.CHANNEL.sendToServer(new DiscordActionPacket(action));
    }

    /** What the card shows: NOT_LINKED, CODE (while it lasts), LINKED, UNKNOWN (cannot be checked), or null before the first answer. A refused new code leaves the last one showing. */
    @Nullable
    public static DiscordLinks.State shown() {
        boolean codeLasts = !code.isEmpty() && secondsLeft() > 0;
        if (state == DiscordLinks.State.TOO_MANY || state == DiscordLinks.State.FAILED || state == DiscordLinks.State.CODE) {
            return codeLasts ? DiscordLinks.State.CODE : DiscordLinks.State.NOT_LINKED;
        }
        return state;
    }

    public static String code() {
        return code;
    }

    public static int secondsLeft() {
        return (int) Math.max(0, (codeUntil - System.currentTimeMillis() + 999) / 1000);
    }
}
