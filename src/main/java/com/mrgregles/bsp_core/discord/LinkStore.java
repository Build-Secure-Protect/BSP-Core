package com.mrgregles.bsp_core.discord;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where link codes wait for the bot and where the bot says who is linked: Redis in play, a map in the GameTests. The game
 * never learns a Discord id, only "linked" or not. Every answer comes back on the server thread.
 */
public interface LinkStore {
    /** Saves {@code code} for {@code seconds}, replacing {@code previous} (the player's last code, or null). Answers whether it was saved. */
    void putCode(String code, @Nullable String previous, UUID player, String name, int seconds, Consumer<Boolean> then);

    /** Whether the bot has linked {@code player}: null when that cannot be read now. */
    void isLinked(UUID player, Consumer<Boolean> then);

    /** Ends the link and drops {@code code} if one is waiting; tells the bot to forget the player. Answers whether it went through. */
    void unlink(UUID player, @Nullable String code, Consumer<Boolean> then);

    /** Takes the bot's note that a message to {@code player} could not be delivered: true if there was one (it is gone now), null when unreadable. */
    void takeUndelivered(UUID player, Consumer<Boolean> then);
}
