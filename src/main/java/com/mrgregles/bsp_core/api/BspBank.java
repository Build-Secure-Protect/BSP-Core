package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        // while the Land Office runs a quote or purchase, the player's tickets for that place count too (Store Credit task 5)
        return Bank.pieces(server, player) + com.mrgregles.bsp_core.credit.CreditLand.extra(server, player);
    }

    /**
     * Takes {@code pieces} from the player for {@code reason} (shown in their log as bought from {@code from}).
     * {@code then} gets true when the money was taken; false means they could not afford it or the bank was unreachable, and nothing was taken.
     */
    public static void charge(MinecraftServer server, UUID player, long pieces, String from, String reason, Consumer<Boolean> then) {
        if (com.mrgregles.bsp_core.credit.CreditLand.charge(server, player, pieces, from, reason, then)) {
            return; // a Land Office purchase: store credit pays first, the bank the rest
        }
        Bank.take(server, player, pieces, BankTransaction.Type.BOUGHT, from, reason, r -> then.accept(r.ok()));
    }

    /** Gives {@code pieces} to the player (shown in their log as sold to {@code to}). */
    public static void credit(MinecraftServer server, UUID player, long pieces, String to, String reason, Consumer<Boolean> then) {
        if (com.mrgregles.bsp_core.credit.CreditLand.credit(server, player, pieces, to, reason, then)) {
            return; // a Land Office purchase paid back: the credit goes back on the ticket
        }
        Bank.give(server, player, pieces, BankTransaction.Type.SOLD, to, reason, r -> then.accept(r.ok()));
    }

    public static String format(long pieces) {
        return Bank.format(pieces) + " pieces";
    }

    // ------------------------------------------------------------------ staff: accounts, logs and refunds (the Bank & Market page)

    /**
     * A player's bank account as staff see it. {@code coins} are the coins kept in the bank, by tier, cheapest first (named by
     * {@link BspCoins#tiers()}). {@code known}: the bank has an account for them (false: every figure is 0).
     */
    public record Account(UUID player, String name, long pieces, List<Integer> coins, boolean known) {
        public Account {
            coins = List.copyOf(coins);
        }
    }

    /**
     * One row of a player's bank log. {@code pieces} and {@code coinCount} are signed from the player's side (money in is
     * positive); {@code coinTier} is an index into {@link BspCoins#tiers()}, or -1 when the row moved no coins. {@code type} is
     * one of BOUGHT, SOLD, PAID, RECEIVED, DEPOSIT, CONVERT, WITHDRAW, GRANT, ADMIN, RENT, FEE, MARKET, TAX, LOTTERY, REFUND
     * (more may come). {@code with} is the other side (a player, a shop, the bank, the staff member of a REFUND). A row knows
     * how much of it staff have refunded; a REFUND row names the row it refunds in {@code refundOf} (0 otherwise).
     */
    public record Row(long id, long ms, String type, String with, String detail, long pieces, int coinTier, int coinCount, String serverId,
                      long refundedPieces, int refundedCoins, long refundOf) {
        /** Pieces of the row that can still be refunded. */
        public long piecesLeft() {
            return Math.max(0, Math.abs(pieces) - refundedPieces);
        }

        /** Coins of the row that can still be refunded. */
        public int coinsLeft() {
            return Math.max(0, Math.abs(coinCount) - refundedCoins);
        }

        /** A payment between two players (PAID or RECEIVED): it can also be reversed on the other player. */
        public boolean payment() {
            return type.equals("PAID") || type.equals("RECEIVED");
        }
    }

    /** A player found by name. */
    public record Named(UUID id, String name) {}

    /**
     * The player's account. On a network it is read from the database first (this server's copy if that one read fails);
     * {@code then} gets null when the database is not connected. Server thread only; {@code then} runs on it.
     */
    public static void account(MinecraftServer server, UUID player, Consumer<Account> then) {
        Objects.requireNonNull(then, "then");
        if (com.mrgregles.bsp_core.storage.NetworkStorage.down()) {
            then.accept(null);
            return;
        }
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            then.accept(accountOf(server, player));
            return;
        }
        com.mrgregles.bsp_core.storage.NetworkStorage.bankRead(player, row -> {
            if (row != null) {
                Bank.account(server, player).copyMoneyFrom(row);
                com.mrgregles.bsp_core.bank.BankLedger.get(server).setDirty();
            }
            then.accept(accountOf(server, player));
        });
    }

    private static Account accountOf(MinecraftServer server, UUID player) {
        var ledger = com.mrgregles.bsp_core.bank.BankLedger.get(server);
        List<Integer> coins = new ArrayList<>();
        if (!ledger.has(player)) { // looking makes no account
            for (int i = 0; i < com.mrgregles.bsp_core.coin.CoinTier.values().length; i++) {
                coins.add(0);
            }
            return new Account(player, Bank.nameOf(server, player), 0, coins, false);
        }
        var a = ledger.account(player);
        for (int c : a.coins) {
            coins.add(c);
        }
        return new Account(player, Bank.nameOf(server, player), a.pieces, coins, true);
    }

    /**
     * The player's bank log, newest first: what the bank keeps ({@code bank.logDays}, {@code bank.logEntries}). On a network it is
     * read fresh from the database, and {@code then} gets null when that read fails (row ids are the database's: a copy could
     * not be refunded by). Server thread only; {@code then} runs on it.
     */
    public static void log(MinecraftServer server, UUID player, Consumer<List<Row>> then) {
        Objects.requireNonNull(then, "then");
        Bank.logFresh(server, player, list -> {
            if (list == null) {
                then.accept(null);
                return;
            }
            List<Row> out = new ArrayList<>(list.size());
            for (BankTransaction t : list) {
                out.add(row(t));
            }
            then.accept(out);
        });
    }

    static Row row(BankTransaction t) {
        return new Row(t.id(), t.ms(), t.type().name(), t.with(), t.detail(), t.pieces(), com.mrgregles.bsp_core.bank.Refunds.hasCoins(t) ? t.coinTier() : -1,
                t.coinCount(), t.server(), t.refundedPieces(), t.refundedCoins(), t.refundOf());
    }

    /**
     * Refunds row {@code rowId} of the player's log: {@code pieces} of its pieces and {@code coins} of its coins (each at most what
     * is left of the row; either may be 0, not both), reversing the row's direction: what the player lost comes back, what they
     * gained is taken back. {@code alsoOtherPlayer} reverses a payment's pieces on the other player too. {@code by} names who did it
     * in the REFUND row (at most 64 characters; e.g. "Name (panel)"). The player, and the other player, are told in chat, at once
     * or at their next login. No permission is checked here: the caller checks who may refund.
     *
     * <p>{@code then} gets "ok", or why nothing changed: "unreachable", "no_row", "amount", "nothing", "too_much", "no_coins",
     * "not_payment", "other_unknown", "other_row" (the other player's own row of the payment is gone or has too little left),
     * "own_coins" (a deposit or withdrawal: the player's own coins only moved), "already" (refunded meanwhile), "short" (the player
     * no longer has what would be taken back), "other_short". Server thread only; {@code then} runs on it.
     */
    public static void refund(MinecraftServer server, UUID player, long rowId, long pieces, int coins, boolean alsoOtherPlayer, String by, Consumer<String> then) {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(then, "then");
        com.mrgregles.bsp_core.bank.Refunds.refund(server, new com.mrgregles.bsp_core.bank.Refunds.Ask(player, rowId, pieces, coins, alsoOtherPlayer, by), r -> then.accept(r.key()));
    }

    /** Finds a player by name: online here, known to the bank (on a network, anywhere on it), or in the server's profile cache; null when nobody. Server thread only. */
    public static void findPlayer(MinecraftServer server, String name, Consumer<Named> then) {
        Objects.requireNonNull(then, "then");
        Bank.resolve(server, name, hit -> then.accept(hit == null ? null : new Named(hit.getKey(), hit.getValue())));
    }
}
