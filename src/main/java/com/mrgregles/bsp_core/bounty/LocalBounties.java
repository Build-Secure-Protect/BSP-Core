package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The bounties on a single server: this world's {@link BountyLedger}, the local bank and the market's Collect boxes. Everything
 * runs at once on the server thread, so each call is whole by itself. {@code new LocalBounties(new BountyLedger())} is a blank
 * board for GameTests.
 */
public class LocalBounties implements BountyStore {
    @Nullable
    private final BountyLedger fixed;

    /** The world's ledger with null, or the given one. */
    public LocalBounties(@Nullable BountyLedger fixed) {
        this.fixed = fixed;
    }

    protected BountyLedger ledger(MinecraftServer server) {
        return fixed != null ? fixed : BountyLedger.get(server);
    }

    @Override
    public void place(MinecraftServer server, Bounty b, Consumer<String> then) {
        Runnable add = () -> {
            ledger(server).put(b);
            Market.toPot(server, b.fee, b.placerName, "bounty fee, bounty " + b.id);
            then.accept("ok");
        };
        if (b.coins() || b.placer.equals(Bounties.SERVER)) {
            add.run();
            return;
        }
        Bank.take(server, b.placer, b.held() + b.fee, BankTransaction.Type.BOUNTY, b.targetName,
                "bounty on " + b.targetName + ": " + b.describe(b.amount) + (b.bonus > 0 ? ", bonus " + Bank.format(b.bonus) : "") + ", fee " + Bank.format(b.fee), r -> {
                    if (r.ok()) {
                        add.run();
                    } else {
                        then.accept(r.key());
                    }
                });
    }

    @Override
    public List<Bounty> open(MinecraftServer server) {
        return ledger(server).open();
    }

    @Override
    public List<Closed> closed(MinecraftServer server) {
        return ledger(server).closed();
    }

    @Override
    public void accept(MinecraftServer server, String id, Bounty.Hunter hunter, int maxPerHunter, Consumer<Answer> then) {
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        if (b.hasHunter(hunter.id())) {
            then.accept(new Answer("already", b));
            return;
        }
        long held = l.open().stream().filter(x -> x.hasHunter(hunter.id())).count();
        if (held >= maxPerHunter) {
            then.accept(new Answer("too_many", b));
            return;
        }
        Bounty after = b.withHunter(hunter);
        l.put(after);
        then.accept(new Answer("ok", after));
    }

    @Override
    public void abandon(MinecraftServer server, String id, UUID hunter, Consumer<Answer> then) {
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        if (!b.hasHunter(hunter)) {
            then.accept(new Answer("not_hunter", b));
            return;
        }
        Bounty after = b.withoutHunter(hunter);
        l.put(after);
        then.accept(new Answer("ok", after));
    }

    @Override
    public void end(MinecraftServer server, String id, Ending ending, String by, Consumer<Answer> then) {
        if (ending == Ending.CLAIMED) {
            throw new IllegalArgumentException("a claim goes through claim()");
        }
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        // checked against the record itself, not a caller's copy: a cancel never takes a bounty a hunter holds, an expiry never one with time left
        if (ending == Ending.CANCELLED && !b.hunters().isEmpty()) {
            then.accept(new Answer("accepted", b));
            return;
        }
        if (ending == Ending.EXPIRED && !b.endedBy(Bounties.now())) {
            then.accept(new Answer("not_ended", b));
            return;
        }
        l.remove(id);
        if (ending != Ending.REMOVED_NO_REFUND) {
            giveBack(server, b, 0, b.held(), word(ending));
        }
        if (ending == Ending.TARGET_STAFF && b.fee > 0 && !b.placer.equals(Bounties.SERVER)) {
            // the fee comes out of the lottery pot again (as far as the pot holds it) and back to the placer
            long back = Math.min(Math.max(0, Bank.pieces(server, Market.POT)), b.fee); // only what the pot still holds: no pieces from nowhere
            if (back > 0) {
                Bank.take(server, Market.POT, back, BankTransaction.Type.BOUNTY, b.placerName, "bounty fee returned, bounty " + b.id, r -> {});
                Bank.give(server, b.placer, back, BankTransaction.Type.BOUNTY, b.targetName, "bounty fee returned: " + b.targetName + " is staff", r -> {});
            }
        }
        l.addClosed(new Closed(b, ending, Bounties.now(), null, "", by, false));
        then.accept(new Answer("ok", b));
    }

    @Override
    public void claim(MinecraftServer server, String id, UUID hunter, String hunterName, long killMs, Consumer<Answer> then) {
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        if (!b.hasHunter(hunter)) {
            then.accept(new Answer("not_hunter", b));
            return;
        }
        if (b.endedBy(killMs)) {
            then.accept(new Answer("ended", b));
            return;
        }
        l.remove(id);
        boolean bonus = b.bonusLive(killMs);
        long pay = b.payFor(killMs);
        String detail = "bounty on " + b.targetName + (bonus ? " with the bonus" : "");
        if (b.coins()) {
            if (b.tier() != null) {
                Market.store(server).addCollect(server, hunter, b.coinPart(0, pay)); // the coins as taken, with their team stamps
            }
        } else {
            Bank.give(server, hunter, pay, BankTransaction.Type.BOUNTY, b.placerName, detail, r -> {
                if (!r.ok()) {
                    BSPCore.LOGGER.warn("BSP-Core bounty {}: {} pieces could not reach {} ({})", b.id, pay, hunterName, r.key());
                }
            });
        }
        if (b.bonus > 0 && !bonus) {
            giveBack(server, b, b.amount, b.bonus, "bonus not earned");
        }
        l.addClosed(new Closed(b, Ending.CLAIMED, Bounties.now(), hunter, hunterName, hunterName, bonus));
        then.accept(new Answer("ok", b, bonus));
    }

    @Override
    public void extend(MinecraftServer server, String id, long endsMs, Consumer<Answer> then) {
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        Bounty after = b.withEnds(endsMs);
        l.put(after);
        then.accept(new Answer("ok", after));
    }

    @Override
    public void setNotes(MinecraftServer server, String id, String notes, Consumer<Answer> then) {
        BountyLedger l = ledger(server);
        Bounty b = l.get(id);
        if (b == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        Bounty after = b.withNotes(notes);
        l.put(after);
        then.accept(new Answer("ok", after));
    }

    /**
     * {@code n} of the bounty's kind back to its placer: pieces to the bank, coins (from {@code skip} coins in, with their stamps) to the
     * Collect box; nothing for a staff bounty.
     */
    static void giveBack(MinecraftServer server, Bounty b, long skip, long n, String why) {
        if (n <= 0 || b.placer.equals(Bounties.SERVER)) {
            return;
        }
        if (b.coins()) {
            if (b.tier() != null) {
                Market.store(server).addCollect(server, b.placer, b.coinPart(skip, n));
            }
            return;
        }
        Bank.give(server, b.placer, n, BankTransaction.Type.BOUNTY, b.targetName, "bounty on " + b.targetName + " " + why, r -> {
            if (!r.ok()) {
                BSPCore.LOGGER.warn("BSP-Core bounty {}: {} pieces could not go back to {} ({})", b.id, n, b.placerName, r.key());
            }
        });
    }

    static String word(Ending e) {
        return switch (e) {
            case CANCELLED -> "cancelled";
            case EXPIRED -> "ran out";
            case REMOVED, REMOVED_NO_REFUND -> "removed by staff";
            case TARGET_STAFF -> "ended: the target is staff";
            case CLAIMED -> "claimed";
        };
    }
}
