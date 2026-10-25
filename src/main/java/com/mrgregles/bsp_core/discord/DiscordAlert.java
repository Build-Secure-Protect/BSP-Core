package com.mrgregles.bsp_core.discord;

import com.google.gson.JsonObject;
import net.minecraft.core.GlobalPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One message for the Discord bot, as the facts of a steal; the bot words and draws it. Sent as one JSON object on
 * {@code discord.redisChannel}. A {@link Type#CHANNEL} alert is the public line and carries the server's name and nothing
 * else: no player, thief, place or totem.
 *
 * @param player     the Minecraft UUID the bot looks its Discord link up by; null on the public line
 * @param reason     why a steal failed ({@link Type#SAVED}): left, interrupted, totem_gone, cancelled or ended
 * @param sentAt     epoch milliseconds
 */
public record DiscordAlert(Type type, @Nullable UUID player, String server, String world, int x, int y, int z, String thief, int secondsLeft, String reason, long sentAt) {
    public static final int VERSION = 1;

    public enum Type {
        /** To the owner: their totem is being stolen (sent when the game warns them, after the thief's Shroud). */
        WARNING("warning"),
        /** To the owner: the steal failed. */
        SAVED("saved"),
        /** To the owner: the steal worked; the thief owns the totem now. */
        STOLEN("stolen"),
        /** The public line in the alert channel: a totem is being stolen on this server. */
        CHANNEL("channel");

        public final String key;

        Type(String key) {
            this.key = key;
        }
    }

    public static DiscordAlert channel(String server, long now) {
        return new DiscordAlert(Type.CHANNEL, null, server, "", 0, 0, 0, "", 0, "", now);
    }

    public static DiscordAlert warning(UUID owner, String server, GlobalPos where, String thief, int secondsLeft, long now) {
        return new DiscordAlert(Type.WARNING, owner, server, where.dimension().location().toString(), where.pos().getX(), where.pos().getY(), where.pos().getZ(), thief, secondsLeft, "", now);
    }

    public static DiscordAlert outcome(UUID owner, String server, GlobalPos where, String thief, boolean stolen, String reasonKey, long now) {
        return new DiscordAlert(stolen ? Type.STOLEN : Type.SAVED, owner, server, where.dimension().location().toString(), where.pos().getX(), where.pos().getY(), where.pos().getZ(),
                thief, 0, stolen ? "" : reason(reasonKey), now);
    }

    /** The owner's side of why a steal failed, from the key the thief is shown. */
    static String reason(String reasonKey) {
        return switch (reasonKey) {
            case "message.bsp_core.steal.failed_left" -> "left"; // the thief stayed outside the radius too long
            case "message.bsp_core.steal.failed_thief_gone" -> "interrupted"; // the thief logged out, died or changed world
            case "message.bsp_core.steal.totem_gone" -> "totem_gone";
            case "message.bsp_core.steal.cancelled_admin" -> "cancelled";
            default -> "ended";
        };
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("type", type.key);
        o.addProperty("server", server);
        o.addProperty("sentAt", sentAt);
        if (type == Type.CHANNEL) {
            return o.toString(); // the public line: nothing more
        }
        o.addProperty("player", String.valueOf(player));
        o.addProperty("world", world);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("thief", thief);
        if (type == Type.WARNING) {
            o.addProperty("secondsLeft", secondsLeft);
        }
        if (type == Type.SAVED) {
            o.addProperty("reason", reason);
        }
        return o.toString();
    }
}
