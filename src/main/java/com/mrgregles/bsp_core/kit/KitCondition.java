package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.totem.TotemCount;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A condition a kit can ask of the player who redeems it, on top of its rank, server, price and once or cooldown rules. A kit
 * holds any number of them and every one must be true. They are saved by name, so a new condition goes anywhere in the list, but
 * the editor shows them in this order: add new ones last. Each has its words under {@code gui.bsp_core.kit.cond.<key>} (the
 * editor's sentence) and {@code message.bsp_core.kit.condition.<key>} (the refusal).
 */
public enum KitCondition {
    /** The player owns no live Shatter Totem: none carried, placed, dropped, travelling or put away, here or on the network's other servers. */
    NO_TOTEM("no_totem") {
        @Override
        public Result test(ServerPlayer player) {
            int owned = TotemCount.of(player);
            return new Result(owned == 0, owned);
        }

        /** Asks the network afresh for the other servers' placed totems and the totems waiting for the player. */
        @Override
        public void confirm(ServerPlayer player, Consumer<Result> then) {
            Kits.totemsElsewhere.ask(player, elsewhere -> {
                if (elsewhere == null) {
                    then.accept(null);
                    return;
                }
                int owned = TotemCount.local(player) + elsewhere;
                then.accept(new Result(owned == 0, owned));
            });
        }
    };

    /** Whether the condition holds, and a number for the player's message (for {@link #NO_TOTEM}: how many totems they own). */
    public record Result(boolean ok, long number) {}

    /** The name in lang keys and in the saved kit. */
    public final String key;

    KitCondition(String key) {
        this.key = key;
    }

    /** The condition now, from what this server knows (the kit card, the first check on a redeem). Server thread. */
    public abstract Result test(ServerPlayer player);

    /**
     * The condition checked again just before a redeem hands the kit over, with whatever it needs from the network asked fresh.
     * The answer comes on the server thread, at once or after a database read; null means it cannot be told now (the network
     * storage is down), and the redeem is refused with "try again shortly". By default the same as {@link #test}.
     */
    public void confirm(ServerPlayer player, Consumer<Result> then) {
        then.accept(test(player));
    }

    /** The condition with this key or enum name, or null for one this version does not know (a newer server saved it). */
    @Nullable
    public static KitCondition of(String name) {
        for (KitCondition c : values()) {
            if (c.key.equals(name) || c.name().equals(name.toUpperCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }
}
