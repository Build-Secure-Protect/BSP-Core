package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankStamps;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.team.Alliances;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import com.mrgregles.bsp_core.totem.NewPlayerProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The bounty rules. A player puts a price on another player's head at a Bounty Board: a reward in pieces (from the bank) or in
 * Shatter Coins of one tier (from the inventory), an optional bonus of the same kind paid only if the kill comes within a window,
 * notes only hunters see, and an end from an hour to {@code bounty.maxDays} days away. The placer pays the market tax on reward
 * and bonus into the lottery pot. Hunters accept bounties (not on themselves, their own, or their teammates' and allies'); a
 * hunter who accepted and then kills the target is paid. A bounty nobody claims gives the reward and bonus back when it ends; the
 * placer can cancel only while nobody has accepted. Staff and players under new-player protection cannot be targets.
 *
 * <p>Every call answers through {@code then} on the server thread (at once on a single server). The money and the bounty move in
 * one step of the {@link BountyStore}.
 */
public final class Bounties {
    private Bounties() {}

    /** The placer of a staff bounty: paid by the server, nothing goes back to it. Shown as {@link #SERVER_NAME}. */
    public static final UUID SERVER = UUID.nameUUIDFromBytes((BSPCore.MODID + ":bounty_server").getBytes(StandardCharsets.UTF_8));
    public static final String SERVER_NAME = "Server";
    public static final long HOUR_MS = 60L * 60 * 1000, DAY_MS = 24 * HOUR_MS;
    /** The biggest reward or bonus in pieces, and in coins, so totals stay far from overflow. */
    public static final long MAX_PIECES = 1_000_000_000_000L, MAX_COINS = 100_000;

    /** What an answer says: "ok" or why not (a lang key under {@code message.bsp_core.bounty.}), and the bounty when there is one. */
    public record Result(String key, @Nullable Bounty bounty) {
        public boolean ok() {
            return key.equals("ok");
        }

        static Result fail(String key) {
            return new Result(key, null);
        }
    }

    /**
     * What a placer asks for. {@code coinTier} {@link Bounty#PIECES} or a coin tier's ordinal; {@code amount} and {@code bonus} in
     * that kind; {@code bonusWithinMs} how long after placing the bonus is paid (0 with no bonus); {@code durationMs} how long the
     * bounty runs.
     */
    public record Ask(UUID target, String targetName, int coinTier, long amount, long bonus, long bonusWithinMs, long durationMs, String notes) {}

    // ------------------------------------------------------------------ hooks

    @Nullable
    private static BountyStore storeOverride;
    private static LongSupplier clock = System::currentTimeMillis;
    private static final LocalBounties LOCAL = new LocalBounties(null);
    /** GameTests: who counts as staff and who is protected, in place of the config lists and new-player protection. */
    @Nullable
    public static BiPredicate<UUID, String> staffCheck;
    @Nullable
    public static Predicate<UUID> protectedCheck;

    /** The test's store, else the network's while network storage is on, else this world's. */
    public static BountyStore store(MinecraftServer server) {
        if (storeOverride != null) {
            return storeOverride;
        }
        NetworkBounties net = NetworkStorage.bounties();
        return net != null ? net : LOCAL;
    }

    /** Where kills waiting to be claimed and unsure coin bounties are kept: the test's ledger with its store, else the world's. */
    @Nullable
    private static BountyLedger testLedger;

    /** GameTests: keep held kills in this ledger (null for the world's again). Set and put back within one tick. */
    public static void useLedger(@Nullable BountyLedger ledger) {
        testLedger = ledger;
    }

    static BountyLedger ledger(MinecraftServer server) {
        return testLedger != null ? testLedger : BountyLedger.get(server);
    }

    /** GameTests: use this store (null for the usual one again); returns the one it replaced. Set and put back within one tick. */
    @Nullable
    public static BountyStore useStore(@Nullable BountyStore store) {
        BountyStore old = storeOverride;
        storeOverride = store;
        return old;
    }

    public static long now() {
        return clock.getAsLong();
    }

    /** GameTests: run the bounties at a chosen time (null for the wall clock again). Set and put back within one tick. */
    public static void useClock(@Nullable LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    // ------------------------------------------------------------------ rules read by the screens too

    public static int maxDays() {
        return BSPConfig.getOr(BSPConfig.BOUNTY_MAX_DAYS, 30);
    }

    public static long minPieces() {
        return BSPConfig.getOr(BSPConfig.BOUNTY_MIN_PIECES, 1000);
    }

    public static int maxOpen() {
        return BSPConfig.getOr(BSPConfig.BOUNTY_MAX_OPEN, 5);
    }

    public static int maxAccepted() {
        return BSPConfig.getOr(BSPConfig.BOUNTY_MAX_ACCEPTED, 5);
    }

    public static int notesMax() {
        return BSPConfig.getOr(BSPConfig.BOUNTY_NOTES_MAX, 256);
    }

    /** The fee on a reward and bonus: the market tax, in this bounty's kind (pieces, or copper-coin units for coins). */
    public static long fee(long value) {
        return Market.tax(value);
    }

    /** Pieces one copper-coin unit is worth at a bank. */
    public static long piecesPerCopper() {
        return BSPConfig.getOr(BSPConfig.BANK_PIECES_PER_COPPER, 1000);
    }

    /** Whether the player is staff: an admin or a moderator as BSP-Core counts them, online or not. */
    public static boolean isStaff(MinecraftServer server, UUID player, String name) {
        if (staffCheck != null) {
            return staffCheck.test(player, name);
        }
        return Admins.isStaff(server, player, name);
    }

    public static boolean isProtected(MinecraftServer server, UUID player) {
        if (protectedCheck != null) {
            return protectedCheck.test(player);
        }
        return NewPlayerProtection.get(server).protects(server, player);
    }

    /** Why {@code target} cannot have a bounty placed on them, or null when they can. */
    @Nullable
    public static String targetRefusal(MinecraftServer server, UUID placer, UUID target, String targetName) {
        if (placer.equals(target)) {
            return "self";
        }
        if (target.equals(SERVER) || isStaff(server, target, targetName)) {
            return "staff";
        }
        if (isProtected(server, target)) {
            return "protected";
        }
        return null;
    }

    /** Why {@code hunter} may not accept {@code b}, or null when they may. */
    @Nullable
    public static String hunterRefusal(MinecraftServer server, Bounty b, UUID hunter) {
        if (hunter.equals(b.target)) {
            return "target";
        }
        if (hunter.equals(b.placer)) {
            return "own";
        }
        Team th = Teams.teamOf(server, hunter), tt = Teams.teamOf(server, b.target);
        if (th != null && tt != null && th.id.equals(tt.id)) {
            return "team";
        }
        if (Alliances.allied(server, hunter, b.target)) {
            return "ally";
        }
        return null;
    }

    /** Notes as kept: formatting codes and control characters out, trimmed, cut to {@link #notesMax()}. */
    public static String cleanNotes(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++; // the code letter after it goes too
            } else if (c == '\n' || !Character.isISOControl(c)) {
                b.append(c);
            }
        }
        String s = b.toString().trim();
        return s.length() > notesMax() ? s.substring(0, notesMax()) : s;
    }

    // ------------------------------------------------------------------ reading

    /** Every open bounty, newest first, ended ones included until the sweep closes them. */
    public static List<Bounty> open(MinecraftServer server) {
        return store(server).open(server);
    }

    public static Optional<Bounty> find(MinecraftServer server, String id) {
        return open(server).stream().filter(b -> b.id.equals(id)).findFirst();
    }

    /** The bounties a player placed that are still open. */
    public static List<Bounty> placedBy(MinecraftServer server, UUID placer) {
        return open(server).stream().filter(b -> b.placer.equals(placer)).toList();
    }

    /** The open bounties a player accepted. */
    public static List<Bounty> huntedBy(MinecraftServer server, UUID hunter) {
        return open(server).stream().filter(b -> b.hasHunter(hunter)).toList();
    }

    /** The open bounties on a player. */
    public static List<Bounty> on(MinecraftServer server, UUID target) {
        return open(server).stream().filter(b -> b.target.equals(target)).toList();
    }

    // ------------------------------------------------------------------ placing

    /** Why {@code ask} is not a bounty anyone may place, or null. Checks the numbers only, not the target or the money. */
    @Nullable
    public static String askRefusal(Ask ask) {
        boolean coins = ask.coinTier() != Bounty.PIECES;
        if (coins && CoinTier.byOrdinal(ask.coinTier()) == null) {
            return "price";
        }
        if (ask.durationMs() < HOUR_MS || ask.durationMs() > maxDays() * DAY_MS) {
            return "time";
        }
        long max = coins ? MAX_COINS : MAX_PIECES;
        long min = coins ? 1 : minPieces();
        if (ask.amount() < min || ask.amount() > max) {
            return "price";
        }
        if (ask.bonus() < 0 || ask.bonus() > max) {
            return "bonus";
        }
        if (ask.bonus() > 0 && (ask.bonusWithinMs() <= 0 || ask.bonusWithinMs() >= ask.durationMs())) {
            return "bonus_time";
        }
        return null;
    }

    /**
     * Places a bounty for {@code placer} (who is at a Bounty Board: coins come out of their inventory). Fails with "unreachable",
     * "time", "price", "bonus", "bonus_time", "self", "staff", "protected", "too_many" (they have {@code bounty.maxOpen} open) or
     * "short"; nothing is taken then. The target is told.
     */
    public static void place(MinecraftServer server, Player placer, Ask ask, Consumer<Result> then) {
        UUID pid = placer.getUUID();
        String pname = placer.getGameProfile().getName();
        String fail = NetworkStorage.down() ? "unreachable" : askRefusal(ask);
        if (fail == null) {
            fail = targetRefusal(server, pid, ask.target(), ask.targetName());
        }
        if (fail == null && placedBy(server, pid).size() >= maxOpen()) {
            fail = "too_many";
        }
        if (fail != null) {
            then.accept(Result.fail(fail));
            return;
        }
        long now = now();
        boolean coins = ask.coinTier() != Bounty.PIECES;
        long held = ask.amount() + ask.bonus();
        long feePieces;
        int feeCopper = 0;
        if (coins) {
            // the reward and bonus are coins of the tier itself, kept as they were taken (with their team stamps); the fee is paid with
            // any coins, with change, and reaches the pot as pieces
            CoinTier tier = CoinTier.byOrdinal(ask.coinTier());
            long fc = fee(held * tier.value());
            if (held > Integer.MAX_VALUE || BankStamps.count(placer, tier) < held || CoinWallet.totalValue(placer) - held * tier.value() < fc) {
                then.accept(Result.fail("short"));
                return;
            }
            feeCopper = (int) fc;
            feePieces = fc * piecesPerCopper();
        } else {
            feePieces = fee(held);
            if (Bank.pieces(server, pid) < held + feePieces) {
                then.accept(Result.fail("short"));
                return;
            }
        }
        List<BankStamps.Lot> lots = List.of();
        if (coins) {
            CoinTier tier = CoinTier.byOrdinal(ask.coinTier());
            lots = BankStamps.takeFromInventory(placer, tier, (int) held);
            if (!CoinWallet.pay(placer, feeCopper)) {
                giveCoins(server, placer, BankStamps.stacks(tier, lots), 0);
                then.accept(Result.fail("short"));
                return;
            }
        }
        Bounty b = new Bounty(newId(server), ask.target(), ask.targetName(), pid, pname, ask.coinTier(), ask.amount(), ask.bonus(),
                ask.bonus() > 0 ? now + ask.bonusWithinMs() : 0, feePieces, now, now + ask.durationMs(), cleanNotes(ask.notes()), Bank.serverId(), List.of(), lots);
        int paidFee = feeCopper;
        store(server).place(server, b, key -> {
            if (!key.equals("ok")) {
                if (coins && !key.equals("unsure")) {
                    // nothing was placed: the same coins come back (unsure: they wait until the network says)
                    giveCoins(server, placer, b.coinPart(0, b.held()), paidFee);
                }
                then.accept(Result.fail(key));
                return;
            }
            BSPCore.LOGGER.info("BSP-Core bounty: {} placed bounty {} on {}: {}{}, fee {} pieces, ends in {} h", pname, b.id, b.targetName,
                    b.describe(b.amount), b.bonus > 0 ? " + bonus " + b.describe(b.bonus) : "", b.fee, ask.durationMs() / HOUR_MS);
            NetworkStorage.tellOrKeep(server, b.target, Component.translatable("message.bsp_core.bounty.placed_on_you", b.describe(b.amount), span(ask.durationMs())));
            then.accept(new Result("ok", b));
        });
    }

    /**
     * Coins back to a placer: into their inventory while they are here, otherwise (they left while the network answered) and for what
     * does not fit, into their Collect box. {@code feeCopper} more is given as new coins worth that.
     */
    static void giveCoins(MinecraftServer server, Player player, List<net.minecraft.world.item.ItemStack> stacks, int feeCopper) {
        boolean gone = player instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected();
        List<net.minecraft.world.item.ItemStack> left = gone ? new ArrayList<>(stacks) : Market.intoInventory(player, stacks);
        if (feeCopper > 0) {
            if (gone) {
                left.addAll(Bounty.coinStacks(CoinTier.COPPER, feeCopper / Math.max(1, CoinTier.COPPER.value())));
            } else {
                CoinWallet.give(player, feeCopper);
            }
        }
        if (!left.isEmpty()) {
            Market.store(server).addCollect(server, player.getUUID(), left);
        }
    }

    // ------------------------------------------------------------------ hunting

    /** {@code hunter} accepts a bounty. Fails with "unreachable", "gone", "target", "own", "team", "ally", "already" or "too_many". */
    public static void accept(MinecraftServer server, UUID hunter, String name, String id, Consumer<Result> then) {
        Bounty seen = find(server, id).orElse(null);
        String fail = NetworkStorage.down() ? "unreachable" : seen == null || seen.endedBy(now()) ? "gone" : hunterRefusal(server, seen, hunter);
        if (fail != null) {
            then.accept(Result.fail(fail));
            return;
        }
        store(server).accept(server, id, new Bounty.Hunter(hunter, name, now()), maxAccepted(), a -> then.accept(new Result(a.key(), a.bounty())));
    }

    /** {@code hunter} drops a bounty they accepted. Fails with "unreachable", "gone" or "not_hunter". */
    public static void abandon(MinecraftServer server, UUID hunter, String id, Consumer<Result> then) {
        if (NetworkStorage.down()) {
            then.accept(Result.fail("unreachable"));
            return;
        }
        store(server).abandon(server, id, hunter, a -> then.accept(new Result(a.key(), a.bounty())));
    }

    // ------------------------------------------------------------------ cancelling and running out

    /** The placer takes a bounty back while nobody has accepted it. Fails with "unreachable", "gone", "not_yours" or "accepted". */
    public static void cancel(MinecraftServer server, UUID by, String id, Consumer<Result> then) {
        Bounty seen = find(server, id).orElse(null);
        String fail = NetworkStorage.down() ? "unreachable" : seen == null ? "gone" : !seen.placer.equals(by) ? "not_yours" : !seen.hunters().isEmpty() ? "accepted" : null;
        if (fail != null) {
            then.accept(Result.fail(fail));
            return;
        }
        store(server).end(server, id, BountyStore.Ending.CANCELLED, seen.placerName, a -> {
            if (a.ok()) {
                BSPCore.LOGGER.info("BSP-Core bounty: {} cancelled bounty {} on {}", seen.placerName, id, seen.targetName);
                NetworkStorage.tellOrKeep(server, seen.target, Component.translatable("message.bsp_core.bounty.ended_on_you"));
            }
            then.accept(new Result(a.key(), a.bounty()));
        });
    }

    /** How long past its end a bounty placed on another server waits for that server to close it before any server does. */
    public static final long SWEEP_GRACE_MS = 10 * 60_000L;

    /**
     * Ends every bounty past its end time, giving the reward and bonus back, and tells the placer and the target. On a network each
     * server closes the bounties placed on it, and any server those more than {@link #SWEEP_GRACE_MS} past their end. Returns how
     * many this call closed at once.
     */
    public static int expireDue(MinecraftServer server) {
        long now = now();
        String here = Bank.serverId();
        List<Bounty> due = new ArrayList<>();
        for (Bounty b : open(server)) {
            if (b.endedBy(now) && (b.server.equals(here) || now >= b.endsMs + SWEEP_GRACE_MS)) {
                due.add(b);
            }
        }
        int[] n = {0};
        for (Bounty b : due) {
            store(server).end(server, b.id, BountyStore.Ending.EXPIRED, "", a -> {
                if (!a.ok()) {
                    return;
                }
                n[0]++;
                BSPCore.LOGGER.info("BSP-Core bounty: bounty {} on {} by {} ran out", b.id, b.targetName, b.placerName);
                if (!b.placer.equals(SERVER)) {
                    NetworkStorage.tellOrKeep(server, b.placer, Component.translatable(b.coins() ? "message.bsp_core.bounty.expired_coins" : "message.bsp_core.bounty.expired",
                            b.targetName, b.describe(b.held())));
                }
                NetworkStorage.tellOrKeep(server, b.target, Component.translatable("message.bsp_core.bounty.ended_on_you"));
            });
        }
        return n[0];
    }

    // ------------------------------------------------------------------ staff

    /**
     * Staff put up a bounty paid by the server ({@link #SERVER}, shown as "Server"): no money is taken, no fee, no limit on how many;
     * the same target rules (never on staff or protected players) and numbers as a player's. {@code by} (who did it) goes in the log.
     * Fails with "unreachable", "time", "price", "bonus", "bonus_time", "staff" or "protected".
     */
    public static void addByStaff(MinecraftServer server, String by, Ask ask, Consumer<Result> then) {
        String fail = NetworkStorage.down() ? "unreachable" : askRefusal(ask);
        if (fail == null) {
            fail = targetRefusal(server, SERVER, ask.target(), ask.targetName());
        }
        if (fail != null) {
            then.accept(Result.fail(fail));
            return;
        }
        long now = now();
        Bounty b = new Bounty(newId(server), ask.target(), ask.targetName(), SERVER, SERVER_NAME, ask.coinTier(), ask.amount(), ask.bonus(),
                ask.bonus() > 0 ? now + ask.bonusWithinMs() : 0, 0, now, now + ask.durationMs(), cleanNotes(ask.notes()), Bank.serverId(), List.of());
        store(server).place(server, b, key -> {
            if (!key.equals("ok")) {
                then.accept(Result.fail(key));
                return;
            }
            BSPCore.LOGGER.info("BSP-Core bounty: staff {} placed bounty {} on {}: {}", by, b.id, b.targetName, b.describe(b.amount));
            NetworkStorage.tellOrKeep(server, b.target, Component.translatable("message.bsp_core.bounty.placed_on_you", b.describe(b.amount), span(ask.durationMs())));
            then.accept(new Result("ok", b));
        });
    }

    /**
     * Staff take a bounty off the board: with {@code refund} the placer gets the reward and bonus back (the fee stays paid), without
     * it nothing goes back. The placer, the hunters and the target are told. Fails with "unreachable" or "gone".
     */
    public static void removeByStaff(MinecraftServer server, String id, boolean refund, String by, Consumer<Result> then) {
        if (NetworkStorage.down()) {
            then.accept(Result.fail("unreachable"));
            return;
        }
        store(server).end(server, id, refund ? BountyStore.Ending.REMOVED : BountyStore.Ending.REMOVED_NO_REFUND, by, a -> {
            Bounty b = a.bounty();
            if (a.ok() && b != null) {
                BSPCore.LOGGER.info("BSP-Core bounty: staff {} removed bounty {} on {} by {}{}", by, b.id, b.targetName, b.placerName, refund ? " with a refund" : ", no refund");
                if (!b.placer.equals(SERVER)) {
                    NetworkStorage.tellOrKeep(server, b.placer, Component.translatable(!refund ? "message.bsp_core.bounty.removed_no_refund"
                            : b.coins() ? "message.bsp_core.bounty.removed_coins" : "message.bsp_core.bounty.removed", b.targetName, b.describe(b.held())));
                }
                for (Bounty.Hunter hu : b.hunters()) {
                    NetworkStorage.tellOrKeep(server, hu.id(), Component.translatable("message.bsp_core.bounty.removed_hunter", b.targetName));
                }
                NetworkStorage.tellOrKeep(server, b.target, Component.translatable("message.bsp_core.bounty.ended_on_you"));
            }
            then.accept(new Result(a.key(), b));
        });
    }

    /**
     * Staff give a bounty more time: {@code addMs} more, but never past {@code bounty.maxDays} from now. Fails with "unreachable", "gone",
     * "time" ({@code addMs} not positive) or "max" (it already ends as late as it may). The placer and the hunters are told.
     */
    public static void extendByStaff(MinecraftServer server, String id, long addMs, String by, Consumer<Result> then) {
        Bounty seen = find(server, id).orElse(null);
        long cap = now() + maxDays() * DAY_MS;
        String fail = NetworkStorage.down() ? "unreachable" : seen == null ? "gone" : addMs <= 0 ? "time" : seen.endsMs >= cap ? "max" : null;
        if (fail != null) {
            then.accept(Result.fail(fail));
            return;
        }
        long ends = Math.min(cap, Math.max(seen.endsMs, now()) + addMs);
        store(server).extend(server, id, ends, a -> {
            Bounty b = a.bounty();
            if (a.ok() && b != null) {
                BSPCore.LOGGER.info("BSP-Core bounty: staff {} extended bounty {} on {} to end in {}", by, b.id, b.targetName, span(ends - now()));
                if (!b.placer.equals(SERVER)) {
                    NetworkStorage.tellOrKeep(server, b.placer, Component.translatable("message.bsp_core.bounty.extended", b.targetName, span(ends - now())));
                }
                for (Bounty.Hunter hu : b.hunters()) {
                    NetworkStorage.tellOrKeep(server, hu.id(), Component.translatable("message.bsp_core.bounty.extended", b.targetName, span(ends - now())));
                }
            }
            then.accept(new Result(a.key(), b));
        });
    }

    /** Staff empty a bounty's notes. Fails with "unreachable" or "gone". */
    public static void blankNotes(MinecraftServer server, String id, String by, Consumer<Result> then) {
        if (NetworkStorage.down()) {
            then.accept(Result.fail("unreachable"));
            return;
        }
        store(server).setNotes(server, id, "", a -> {
            if (a.ok()) {
                BSPCore.LOGGER.info("BSP-Core bounty: staff {} blanked the notes of bounty {}", by, id);
            }
            then.accept(new Result(a.key(), a.bounty()));
        });
    }

    /** The closed bounties, newest first (on a network the copy, at most {@link BountyLedger#HISTORY}). */
    public static List<BountyStore.Closed> history(MinecraftServer server) {
        return store(server).closed(server);
    }

    // ------------------------------------------------------------------ the kill

    /**
     * {@code hunter} killed {@code target} at {@code killMs} ({@link BountyKills} decides who counts): every open bounty on the target
     * that this hunter accepted is claimed and paid; other hunters' bounties on the target stay open. The hunter, each placer and the
     * target are told. {@code then} hears how many were claimed (on a network, later).
     */
    public static void claimKill(MinecraftServer server, UUID target, String targetName, UUID hunter, String hunterName, long killMs, Consumer<Integer> then) {
        List<Bounty> mine = on(server, target).stream().filter(b -> b.hasHunter(hunter) && !b.endedBy(killMs)).toList();
        if (mine.isEmpty()) {
            then.accept(0);
            return;
        }
        BountyLedger.PendingKill kill = new BountyLedger.PendingKill(target, targetName, hunter, hunterName, killMs);
        if (NetworkStorage.down()) {
            BSPCore.LOGGER.warn("BSP-Core bounty: {} killed {} but the network cannot be reached: the claim waits", hunterName, targetName);
            ledger(server).addKill(kill);
            then.accept(0);
            return;
        }
        int[] done = {0, 0};
        for (Bounty b : mine) {
            store(server).claim(server, b.id, hunter, hunterName, killMs, a -> {
                if (a.ok()) {
                    done[0]++;
                    told(server, b, hunter, hunterName, a.bonusPaid(), killMs);
                } else if (a.key().equals("unreachable")) {
                    ledger(server).addKill(kill); // tried again by the sweep, judged by the kill's own time
                }
                if (++done[1] == mine.size()) {
                    then.accept(done[0]);
                }
            });
        }
    }

    private static void told(MinecraftServer server, Bounty b, UUID hunter, String hunterName, boolean bonus, long killMs) {
        long paid = b.payFor(killMs);
        BSPCore.LOGGER.info("BSP-Core bounty: {} claimed bounty {} on {} placed by {}: {}{}", hunterName, b.id, b.targetName, b.placerName, b.describe(paid),
                bonus ? " (bonus included)" : "");
        NetworkStorage.tell(server, hunter, Component.translatable(b.coins() ? "message.bsp_core.bounty.claimed_hunter_coins" : "message.bsp_core.bounty.claimed_hunter",
                b.placerName, b.targetName, b.describe(paid)));
        if (!b.placer.equals(SERVER)) {
            if (b.bonus > 0 && !bonus) {
                NetworkStorage.tellOrKeep(server, b.placer, Component.translatable(b.coins() ? "message.bsp_core.bounty.claimed_placer_late_coins" : "message.bsp_core.bounty.claimed_placer_late",
                        hunterName, b.targetName, b.describe(b.bonus)));
            } else {
                NetworkStorage.tellOrKeep(server, b.placer, Component.translatable("message.bsp_core.bounty.claimed_placer", hunterName, b.targetName));
            }
        }
        NetworkStorage.tellOrKeep(server, b.target, Component.translatable("message.bsp_core.bounty.claimed_target", hunterName, b.placerName));
    }

    /**
     * Claims again the kills that could not be claimed (the network could not be reached), judged by the time of the kill. A kill
     * older than {@link #SWEEP_GRACE_MS} is given up (a bounty may have run out and closed meanwhile). Returns how many it tried.
     */
    public static int retryKills(MinecraftServer server) {
        if (NetworkStorage.down()) {
            return 0;
        }
        long now = now();
        int n = 0;
        for (BountyLedger.PendingKill k : ledger(server).takeKills()) {
            if (now - k.killMs() > SWEEP_GRACE_MS) {
                BSPCore.LOGGER.warn("BSP-Core bounty: gave up claiming {}'s kill of {} from {} min ago", k.hunterName(), k.targetName(), (now - k.killMs()) / 60_000L);
                continue;
            }
            n++;
            claimKill(server, k.target(), k.targetName(), k.hunter(), k.hunterName(), k.killMs(), x -> {});
        }
        return n;
    }

    // ------------------------------------------------------------------ the players to choose from

    /** A player who can be picked as a target: known to the bank here or on the network, and whether they are online now. */
    public record Known(UUID id, String name, boolean online) {}

    /** Most players a search answers with. */
    public static final int KNOWN_MAX = 200;

    /**
     * Every player the bank knows (anyone who has joined), whose name contains {@code search} (any case; empty for all), online ones
     * first, then by name, at most {@link #KNOWN_MAX}. On a network the database's accounts; on a single server this world's.
     * Staff and protected players are in the list; placing on them is refused with the reason.
     */
    public static void knownPlayers(MinecraftServer server, String search, Consumer<List<Known>> then) {
        String q = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        Consumer<List<java.util.Map.Entry<UUID, String>>> sort = all -> {
            List<Known> out = new ArrayList<>();
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (var e : all) {
                UUID id = e.getKey();
                String name = e.getValue();
                if (name == null || name.isEmpty() || id.equals(Market.POT) || id.equals(SERVER) || !seen.add(id)
                        || !name.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    continue;
                }
                boolean online = server.getPlayerList().getPlayer(id) != null || NetworkStorage.onlineElsewhere(id).isPresent();
                out.add(new Known(id, name, online));
            }
            out.sort(java.util.Comparator.comparing((Known k) -> !k.online()).thenComparing(k -> k.name().toLowerCase(java.util.Locale.ROOT)));
            then.accept(out.size() > KNOWN_MAX ? List.copyOf(out.subList(0, KNOWN_MAX)) : out);
        };
        if (NetworkStorage.enabled()) {
            NetworkStorage.bankNames(q, KNOWN_MAX * 2, rows -> {
                List<java.util.Map.Entry<UUID, String>> all = new ArrayList<>(rows);
                server.getPlayerList().getPlayers().forEach(p -> all.add(java.util.Map.entry(p.getUUID(), p.getGameProfile().getName())));
                sort.accept(all);
            });
            return;
        }
        List<java.util.Map.Entry<UUID, String>> all = new ArrayList<>();
        server.getPlayerList().getPlayers().forEach(p -> all.add(java.util.Map.entry(p.getUUID(), p.getGameProfile().getName())));
        com.mrgregles.bsp_core.bank.BankLedger.get(server).all().forEach((id, acct) -> all.add(java.util.Map.entry(id, acct.name)));
        sort.accept(all);
    }

    // ------------------------------------------------------------------ targets who are staff

    /**
     * Ends every open bounty on {@code player} if they are staff now (checked at login): each placer gets everything back, the fee
     * too, and is told. Returns how many it ended at once.
     */
    public static int endIfStaff(MinecraftServer server, UUID player, String name) {
        List<Bounty> on = on(server, player);
        if (on.isEmpty() || NetworkStorage.down() || !isStaff(server, player, name)) {
            return 0;
        }
        int[] n = {0};
        for (Bounty b : on) {
            store(server).end(server, b.id, BountyStore.Ending.TARGET_STAFF, "", a -> {
                if (!a.ok()) {
                    return;
                }
                n[0]++;
                BSPCore.LOGGER.info("BSP-Core bounty: bounty {} on {} ended: {} is staff", b.id, b.targetName, b.targetName);
                if (!b.placer.equals(SERVER)) {
                    NetworkStorage.tellOrKeep(server, b.placer, Component.translatable("message.bsp_core.bounty.target_staff", b.targetName));
                }
            });
        }
        return n[0];
    }

    // ------------------------------------------------------------------ inside

    /** "3 d 4 h", "5 h 20 m" or "12 m": a length of time as the screens and messages write it. */
    public static String span(long ms) {
        long m = Math.max(0, ms) / 60_000L;
        long d = m / (60 * 24), h = m / 60 % 24, mm = m % 60;
        return d > 0 ? d + " d " + h + " h" : h > 0 ? h + " h " + mm + " m" : mm + " m";
    }

    private static String newId(MinecraftServer server) {
        List<Bounty> open = open(server);
        while (true) {
            String id = "b" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36 * 36 * 36), 36);
            if (open.stream().noneMatch(b -> b.id.equals(id))) {
                return id;
            }
        }
    }
}
