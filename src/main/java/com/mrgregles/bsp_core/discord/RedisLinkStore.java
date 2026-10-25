package com.mrgregles.bsp_core.discord;

import com.google.gson.JsonObject;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.storage.NetworkStorage;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Link codes and link flags on Redis, all under the alert channel's name (default {@code bsp_core:discord}), so the bot's
 * Redis user can be limited to that channel and the keys {@code <channel>:*}:
 * <ul>
 * <li>{@code <channel>:code:<CODE>}: the code's player, set with an expiry; the bot takes it with GETDEL when someone runs /link.</li>
 * <li>{@code <channel>:linked:<uuid>}: set by the bot while the player is linked; holds no Discord id.</li>
 * <li>{@code <channel>:undelivered:<uuid>}: set by the bot when a direct message could not reach the player; taken at their next login.</li>
 * </ul>
 * An unlink deletes the flag and publishes {@code {"v":1,"type":"unlink",...}} on the channel so the bot forgets the player.
 */
public final class RedisLinkStore implements LinkStore {
    static String channel() {
        return BSPConfig.getOr(BSPConfig.DISCORD_REDIS_CHANNEL, "bsp_core:discord");
    }

    static String codeKey(String code) {
        return channel() + ":code:" + code;
    }

    static String linkedKey(UUID player) {
        return channel() + ":linked:" + player;
    }

    static String undeliveredKey(UUID player) {
        return channel() + ":undelivered:" + player;
    }

    /** What the bot reads from a code: whose it is. */
    public static String codeValue(UUID player, String name, long expiresAt) {
        JsonObject o = new JsonObject();
        o.addProperty("v", DiscordAlert.VERSION);
        o.addProperty("player", player.toString());
        o.addProperty("name", name);
        o.addProperty("server", DiscordAlerts.serverName());
        o.addProperty("expiresAt", expiresAt);
        return o.toString();
    }

    public static String unlinkMessage(UUID player, long now) {
        JsonObject o = new JsonObject();
        o.addProperty("v", DiscordAlert.VERSION);
        o.addProperty("type", "unlink");
        o.addProperty("player", player.toString());
        o.addProperty("sentAt", now);
        return o.toString();
    }

    @Override
    public void putCode(String code, @Nullable String previous, UUID player, String name, int seconds, Consumer<Boolean> then) {
        String value = codeValue(player, name, System.currentTimeMillis() + seconds * 1000L);
        NetworkStorage.redis("save a Discord link code", r -> {
            if (previous != null) {
                r.call("DEL", codeKey(previous));
            }
            r.call("SET", codeKey(code), value, "EX", String.valueOf(seconds));
            return Boolean.TRUE;
        }, ok -> then.accept(ok != null));
    }

    @Override
    public void isLinked(UUID player, Consumer<Boolean> then) {
        NetworkStorage.redis("read a Discord link", r -> "1".equals(String.valueOf(r.call("EXISTS", linkedKey(player)))), then);
    }

    @Override
    public void unlink(UUID player, @Nullable String code, Consumer<Boolean> then) {
        String message = unlinkMessage(player, System.currentTimeMillis());
        NetworkStorage.redis("end a Discord link", r -> {
            r.call("DEL", linkedKey(player));
            if (code != null) {
                r.call("DEL", codeKey(code));
            }
            r.call("PUBLISH", channel(), message);
            return Boolean.TRUE;
        }, ok -> then.accept(ok != null));
    }

    @Override
    public void takeUndelivered(UUID player, Consumer<Boolean> then) {
        // DEL answers how many keys it removed: 1 means the note was there (works on any Redis version, unlike GETDEL)
        NetworkStorage.redis("read a Discord delivery note", r -> "1".equals(String.valueOf(r.call("DEL", undeliveredKey(player)))), then);
    }
}
