package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Staff refunds of bank log rows (the Bank &amp; Market admin page and the staff web panel through {@code BspBank}). Any row can be
 * reversed, in either direction, in full or in part: what the player lost comes back, what they gained is taken back (refused when
 * they no longer have it). Each refund writes a REFUND row naming the row, and the row remembers how much of it is refunded, so the
 * whole of a row is refunded at most once however many parts it comes in. A payment can also be reversed on the other player.
 * The player (and the other player) are told in chat, at once or at their next login. Nobody's permission is checked here: the
 * caller checks it.
 */
public final class Refunds {
    private Refunds() {}

    /**
     * A refund staff asked for: of row {@code rowId} in {@code player}'s log, {@code pieces} of its pieces and {@code coins} of its
     * coins (each no more than what is left of the row). {@code other}: also reverse the pieces on the other player of a payment
     * (PAID or RECEIVED). {@code by}: who did it, written in the REFUND row (at most 64 characters).
     */
    public record Ask(UUID player, long rowId, long pieces, int coins, boolean other, String by) {}

    /**
     * {@code key} "ok", or why nothing changed: {@code unreachable} (the network's database), {@code no_row} (not in the log, or
     * trimmed), {@code amount} (a negative amount), {@code nothing} (both 0), {@code too_much} (more than is left of the row),
     * {@code no_coins} (the row moved no coins), {@code not_payment} ({@code other} on a row that is no payment),
     * {@code other_unknown} (the other player cannot be found), {@code other_row} (the other player's own row of the payment is not
     * in their log any more, or not enough of it is left: reverse it on them by hand), {@code own_coins} (a deposit or withdrawal
     * only moved the player's own coins between their pocket and the bank), {@code already} (someone refunded it meanwhile),
     * {@code short} (the player no longer has what would be taken back), {@code other_short} (the other player does not).
     */
    public record Result(String key, long piecesAfter, int[] coinsAfter, @Nullable BankTransaction refundRow) {
        public boolean ok() {
            return key.equals("ok");
        }

        static Result fail(String key) {
            return new Result(key, 0, new int[CoinTier.values().length], null);
        }
    }

    /** Whether the row is a payment between two players. */
    public static boolean isPayment(BankTransaction t) {
        return t.type() == BankTransaction.Type.PAID || t.type() == BankTransaction.Type.RECEIVED;
    }

    /** Whether the row moved coins (so its coins can be refunded). */
    public static boolean hasCoins(BankTransaction t) {
        return t.coinCount() != 0 && t.coinTier() >= 0 && t.coinTier() < CoinTier.values().length;
    }

    /** Refunds as asked. {@code then} runs on the server thread. */
    public static void refund(MinecraftServer server, Ask ask, Consumer<Result> then) {
        if (NetworkStorage.down()) {
            then.accept(Result.fail("unreachable"));
            return;
        }
        Bank.logFresh(server, ask.player(), list -> { // on a network, read fresh: the rows and their refunded amounts as the database has them
            if (list == null) {
                then.accept(Result.fail("unreachable"));
                return;
            }
            BankTransaction row = null;
            for (BankTransaction t : list) {
                if (t.id() == ask.rowId()) {
                    row = t;
                    break;
                }
            }
            String why = check(row, ask);
            if (why != null) {
                then.accept(Result.fail(why));
                return;
            }
            BankTransaction r = row;
            if (!ask.other() || ask.pieces() == 0) {
                apply(server, ask, r, null, null, then);
                return;
            }
            Bank.resolve(server, r.with(), hit -> {
                if (hit == null || hit.getKey().equals(ask.player())) {
                    then.accept(Result.fail("other_unknown"));
                    return;
                }
                UUID otherId = hit.getKey();
                Bank.logFresh(server, otherId, otherLog -> {
                    if (otherLog == null) {
                        then.accept(Result.fail("unreachable"));
                        return;
                    }
                    // the other player's own row of the payment is what shows it is the right player, and it is marked with ours, so the
                    // payment is never reversed on them twice: without it, nothing is done
                    BankTransaction theirs = counterpart(otherLog, r, Bank.nameOf(server, ask.player()), ask.pieces());
                    if (theirs == null) {
                        then.accept(Result.fail("other_row"));
                        return;
                    }
                    apply(server, ask, r, otherId, theirs, then);
                });
            });
        });
    }

    /** Why the ask cannot be done on this row, or null. */
    @Nullable
    static String check(@Nullable BankTransaction row, Ask ask) {
        if (row == null) {
            return "no_row";
        }
        if (ask.pieces() < 0 || ask.coins() < 0) {
            return "amount";
        }
        if (ask.pieces() == 0 && ask.coins() == 0) {
            return "nothing";
        }
        if (row.type() == BankTransaction.Type.DEPOSIT || row.type() == BankTransaction.Type.WITHDRAW) {
            return "own_coins"; // coins moved between the player's pocket and their bank: nothing was gained or lost
        }
        if (ask.coins() > 0 && !hasCoins(row)) {
            return "no_coins";
        }
        if (ask.pieces() > row.piecesLeft() || ask.coins() > row.coinsLeft()) {
            return "too_much";
        }
        if (ask.other() && !isPayment(row)) {
            return "not_payment";
        }
        return null;
    }

    /** The other player's row of the same payment: the opposite kind, naming this player, the same amount, within a minute, with enough left. */
    @Nullable
    static BankTransaction counterpart(List<BankTransaction> otherLog, BankTransaction row, String playerName, long pieces) {
        BankTransaction.Type want = row.type() == BankTransaction.Type.PAID ? BankTransaction.Type.RECEIVED : BankTransaction.Type.PAID;
        BankTransaction best = null;
        for (BankTransaction t : otherLog) {
            if (t.type() == want && t.with().equalsIgnoreCase(playerName) && t.pieces() == -row.pieces() && Math.abs(t.ms() - row.ms()) <= 60_000
                    && t.piecesLeft() >= pieces && (best == null || Math.abs(t.ms() - row.ms()) < Math.abs(best.ms() - row.ms()))) {
                best = t;
            }
        }
        return best;
    }

    private static void apply(MinecraftServer server, Ask ask, BankTransaction row, @Nullable UUID otherId, @Nullable BankTransaction otherRow, Consumer<Result> then) {
        long now = System.currentTimeMillis();
        long dP = -Long.signum(row.pieces()) * ask.pieces();
        int tier = row.coinTier(), dC = ask.coins() > 0 ? -Integer.signum(row.coinCount()) * ask.coins() : 0;
        String by = ask.by().length() > 64 ? ask.by().substring(0, 64) : ask.by();
        String playerName = Bank.nameOf(server, ask.player());
        BankTransaction mine = new BankTransaction(now, BankTransaction.Type.REFUND, by, "refund of " + when(row.ms()) + " " + kindWord(row) + (row.detail().isEmpty() ? "" : " (" + row.detail() + ")"),
                dP, dC != 0 ? tier : -1, dC, Bank.serverId()).refundOf(row.id());
        BankTransaction theirs = otherId == null ? null : new BankTransaction(now, BankTransaction.Type.REFUND, by, "refund of " + when(row.ms()) + " payment with " + playerName,
                -dP, -1, 0, Bank.serverId()).refundOf(otherRow == null ? 0 : otherRow.id());
        BankLedger ledger = BankLedger.get(server);
        if (!NetworkStorage.enabled()) {
            BankAccount a = ledger.account(ask.player());
            if (a.pieces + dP < 0 || dC != 0 && a.coins[tier] + dC < 0) {
                then.accept(Result.fail("short"));
                return;
            }
            BankAccount b = otherId == null ? null : ledger.account(otherId);
            if (b != null && b.pieces - dP < 0) {
                then.accept(Result.fail("other_short"));
                return;
            }
            BankTransaction now1 = ledger.find(ask.player(), row.id());
            if (now1 == null || now1.piecesLeft() < ask.pieces() || now1.coinsLeft() < ask.coins()) {
                then.accept(Result.fail("already"));
                return;
            }
            a.pieces += dP;
            if (dC < 0) {
                BankStamps.remove(a, CoinTier.values()[tier], BankStamps.pick(a, CoinTier.values()[tier], -dC, null)); // read before the count goes down
            }
            if (dC != 0) {
                a.coins[tier] += dC;
            }
            ledger.replace(ask.player(), now1.refunded(ask.pieces(), ask.coins()));
            BankTransaction kept = ledger.record(ask.player(), mine);
            if (b != null) {
                b.pieces -= dP;
                if (otherRow != null) {
                    BankTransaction o = ledger.find(otherId, otherRow.id());
                    if (o != null) {
                        ledger.replace(otherId, o.refunded(ask.pieces(), 0));
                    }
                }
                ledger.record(otherId, theirs);
            }
            ledger.setDirty();
            tell(server, ask.player(), row, dP, tier, dC, otherId, playerName, theirs);
            then.accept(new Result("ok", a.pieces, a.coins.clone(), kept));
            return;
        }
        List<NetworkStorage.RefundMark> marks = new ArrayList<>();
        marks.add(new NetworkStorage.RefundMark(ask.player(), row.id(), ask.pieces(), ask.coins()));
        if (otherRow != null) {
            marks.add(new NetworkStorage.RefundMark(otherId, otherRow.id(), ask.pieces(), 0));
        }
        int[] dCoins = new int[CoinTier.values().length];
        if (dC != 0) {
            dCoins[tier] = dC;
        }
        List<NetworkStorage.RefundMove> moves = new ArrayList<>();
        moves.add(new NetworkStorage.RefundMove(ask.player(), dP, dCoins, dC < 0 ? CoinTier.values()[tier] : null, dC < 0 ? -dC : 0, mine));
        if (otherId != null) {
            moves.add(new NetworkStorage.RefundMove(otherId, -dP, new int[CoinTier.values().length], null, 0, theirs));
        }
        NetworkStorage.bankRefund(marks, moves, out -> {
            if (out == null) {
                then.accept(Result.fail("unreachable"));
                return;
            }
            if (!out.key().equals("ok")) {
                // "short" cannot say whose account it was: check the other player's here, as far as this server knows
                BankAccount mineNow = ledger.account(ask.player());
                boolean mineOk = mineNow.pieces + dP >= 0 && (dC == 0 || mineNow.coins[tier] + dC >= 0);
                then.accept(Result.fail(out.key().equals("short") && otherId != null && mineOk ? "other_short" : out.key()));
                return;
            }
            BankTransaction kept = mine.withId(out.logIds().get(0));
            ledger.account(ask.player()).copyMoneyFrom(out.after().get(0));
            ledger.replace(ask.player(), row.refunded(ask.pieces(), ask.coins()));
            ledger.record(ask.player(), kept);
            if (otherId != null) {
                ledger.account(otherId).copyMoneyFrom(out.after().get(1));
                if (otherRow != null) {
                    ledger.replace(otherId, otherRow.refunded(ask.pieces(), 0));
                }
                ledger.record(otherId, theirs.withId(out.logIds().get(1)));
            }
            ledger.setDirty();
            tell(server, ask.player(), row, dP, tier, dC, otherId, playerName, theirs);
            BankAccount after = out.after().get(0);
            then.accept(new Result("ok", after.pieces, after.coins.clone(), kept));
        });
    }

    /** The chat line for the player (and the other player of a payment), at once or at their next login. */
    private static void tell(MinecraftServer server, UUID player, BankTransaction row, long dP, int tier, int dC, @Nullable UUID otherId, String playerName,
                             @Nullable BankTransaction theirs) {
        String key = dP >= 0 && dC >= 0 ? "refund_gave" : dP <= 0 && dC <= 0 ? "refund_took" : "refund_changed";
        NetworkStorage.tellOrKeepSafely(server, player, Component.translatable("message.bsp_core.bank." + key, kind(row), when(row.ms()), amounts(dP, tier, dC)));
        if (otherId != null && theirs != null) {
            NetworkStorage.tellOrKeepSafely(server, otherId, Component.translatable("message.bsp_core.bank.refund_other", playerName, when(row.ms()), amounts(-dP, -1, 0)));
        }
    }

    /** What the row was, for the player: "purchase (Oak Log x64)", "payment to Kestrel". */
    public static MutableComponent kind(BankTransaction row) {
        MutableComponent k = Component.translatable("message.bsp_core.bank.kind." + row.type().name().toLowerCase(Locale.ROOT), row.with());
        return row.detail().isEmpty() || isPayment(row) ? k : k.append(" (" + row.detail() + ")");
    }

    private static String kindWord(BankTransaction row) {
        return row.type().name().toLowerCase(Locale.ROOT);
    }

    /** Signed amounts: "+320 pieces", "+2 Gold coins, -2,000 pieces". */
    public static MutableComponent amounts(long dP, int tier, int dC) {
        MutableComponent out = Component.empty();
        boolean first = true;
        if (dC != 0) {
            out.append(Component.translatable("message.bsp_core.bank.amount_coins", (dC > 0 ? "+" : "-") + Math.abs(dC), Bank.tierName(CoinTier.values()[tier])));
            first = false;
        }
        if (dP != 0) {
            if (!first) {
                out.append(", ");
            }
            out.append(Component.translatable("message.bsp_core.bank.amount_pieces", (dP > 0 ? "+" : "-") + Bank.format(Math.abs(dP))));
        }
        return out;
    }

    /** "18 Nov 14:02", the server's own time zone. */
    public static String when(long ms) {
        return new SimpleDateFormat("d MMM HH:mm", Locale.ROOT).format(new Date(ms));
    }
}
