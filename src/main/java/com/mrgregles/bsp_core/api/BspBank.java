package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * What other mods (BSP-Region Guard first) may do with a player's bank account. Amounts are Shatter
 * Pieces. Every call that moves money answers through a callback on the server thread: on a single
 * server at once, on a network when the database has answered. Nothing here is stable until
 * BSP-Core 1.1; names may still change.
 */
public final class BspBank {
    private BspBank() {}

    /** The balance this server knows of (on a network: the last value read from the database). */
    public static long balance(MinecraftServer server, UUID player) {
        return Bank.pieces(server, player);
    }

    /**
     * Takes {@code pieces} from the player for {@code reason} (shown in their log as bought from {@code from}).
     * {@code then} gets true when the money was taken; false means they could not afford it or the bank was unreachable, and nothing was taken.
     */
    public static void charge(MinecraftServer server, UUID player, long pieces, String from, String reason, Consumer<Boolean> then) {
        Bank.take(server, player, pieces, BankTransaction.Type.BOUGHT, from, reason, r -> then.accept(r.ok()));
    }

    /** Gives {@code pieces} to the player (shown in their log as sold to {@code to}). */
    public static void credit(MinecraftServer server, UUID player, long pieces, String to, String reason, Consumer<Boolean> then) {
        Bank.give(server, player, pieces, BankTransaction.Type.SOLD, to, reason, r -> then.accept(r.ok()));
    }

    public static String format(long pieces) {
        return Bank.format(pieces) + " pieces";
    }
}
