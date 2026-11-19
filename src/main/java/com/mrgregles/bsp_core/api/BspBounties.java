package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyStore;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bounties, for a staff web page (BSP-Server Utilities): everything the admin panel's BOUNTIES tab reads (the notes included) and
 * everything it does. <b>Server thread only</b>; every callback runs on the server thread. <b>No permission is checked here</b>: the
 * caller decides who may, and passes who did it in {@code by} (at most 64 characters). In game, admins may, and moderators holding
 * the LuckPerms permission {@code bsp_core.admin.bounty}.
 */
public final class BspBounties {
    private BspBounties() {}

    /** A hunter who accepted a bounty, and when (epoch ms). */
    public record Hunter(UUID id, String name, long acceptedMs) {}

    /**
     * One bounty. {@code coinTier} -1 for pieces, else an index into {@link BspCoins#tiers()}; {@code amount} and {@code bonus} in that
     * kind (pieces, or a count of coins); {@code bonusEndsMs} 0 with no bonus; {@code fee} pieces the placer paid into the lottery
     * pot; {@code placer} {@link #SERVER} for a staff bounty ({@code placerName} "Server"); times epoch ms; {@code serverId} where it
     * was placed.
     */
    public record Info(String id, UUID target, String targetName, UUID placer, String placerName, int coinTier, long amount, long bonus, long bonusEndsMs,
                       long fee, long placedMs, long endsMs, String notes, String serverId, List<Hunter> hunters) {
        public boolean coins() {
            return coinTier >= 0;
        }
    }

    /**
     * A closed bounty: how it ended ({@code CLAIMED}, {@code CANCELLED}, {@code EXPIRED}, {@code REMOVED}, {@code REMOVED_NO_REFUND},
     * {@code TARGET_STAFF}; more may come), when, who claimed it ({@code hunter} null otherwise), who closed it, and whether the bonus
     * was paid.
     */
    public record Closed(Info bounty, String ending, long closedMs, @Nullable UUID hunter, String hunterName, String by, boolean bonusPaid) {}

    /** A player who can be picked as a target, and whether they are online on any server now. */
    public record Player(UUID id, String name, boolean online) {}

    /** An answer: {@code key} "ok" or why not; the bounty as it is after the call (or as it was, for a removal). */
    public record Result(String key, @Nullable Info bounty) {
        public boolean ok() {
            return key.equals("ok");
        }
    }

    /** The placer of a staff bounty. */
    public static final UUID SERVER = Bounties.SERVER;

    static Info info(Bounty b) {
        List<Hunter> hs = new ArrayList<>();
        b.hunters().forEach(h -> hs.add(new Hunter(h.id(), h.name(), h.acceptedMs())));
        return new Info(b.id, b.target, b.targetName, b.placer, b.placerName, b.coinTier, b.amount, b.bonus, b.bonusEndsMs, b.fee, b.placedMs, b.endsMs,
                b.notes, b.server, List.copyOf(hs));
    }

    private static Result result(Bounties.Result r) {
        return new Result(r.key(), r.bounty() == null ? null : info(r.bounty()));
    }

    // ------------------------------------------------------------------ reading

    /** Every open bounty, newest first, those past their end left out (the sweep closes them within 30 seconds). On a network the copy, at most a minute old. */
    public static List<Info> open(MinecraftServer server) {
        long now = Bounties.now();
        return Bounties.open(server).stream().filter(b -> !b.endedBy(now)).map(BspBounties::info).toList();
    }

    public static Optional<Info> find(MinecraftServer server, String id) {
        return Bounties.find(server, id).map(BspBounties::info);
    }

    /** The open bounties on a player. */
    public static List<Info> on(MinecraftServer server, UUID target) {
        return Bounties.on(server, target).stream().map(BspBounties::info).toList();
    }

    /** The open bounties a player placed. */
    public static List<Info> placedBy(MinecraftServer server, UUID placer) {
        return Bounties.placedBy(server, placer).stream().map(BspBounties::info).toList();
    }

    /** The open bounties a player accepted. */
    public static List<Info> huntedBy(MinecraftServer server, UUID hunter) {
        return Bounties.huntedBy(server, hunter).stream().map(BspBounties::info).toList();
    }

    /** Closed bounties, newest first (kept 90 days on a network; the newest 500 either way). */
    public static List<Closed> history(MinecraftServer server) {
        List<Closed> out = new ArrayList<>();
        for (BountyStore.Closed c : Bounties.history(server)) {
            out.add(new Closed(info(c.bounty()), c.ending().name(), c.closedMs(), c.hunter(), c.hunterName(), c.by(), c.bonusPaid()));
        }
        return out;
    }

    /** Every player the bank knows whose name contains {@code search} (any case; "" for all), online first, at most 200. */
    public static void players(MinecraftServer server, String search, Consumer<List<Player>> then) {
        Bounties.knownPlayers(server, search, list -> then.accept(list.stream().map(k -> new Player(k.id(), k.name(), k.online())).toList()));
    }

    /** Whether a bounty may be placed on this player: null when it may, else "staff" or "protected". */
    @Nullable
    public static String targetRefusal(MinecraftServer server, UUID target, String name) {
        return Bounties.targetRefusal(server, SERVER, target, name);
    }

    // ------------------------------------------------------------------ staff actions

    /**
     * A staff bounty, paid by the server (nothing is taken from anyone; no fee). {@code coinTier} -1 for pieces; {@code bonus} 0 for
     * none, else with {@code bonusWithinMs} (shorter than {@code durationMs}); {@code durationMs} from 1 hour to {@code bounty.maxDays}.
     * Keys: ok, unreachable, time, price, bonus, bonus_time, staff, protected.
     */
    public static void add(MinecraftServer server, String by, UUID target, String targetName, int coinTier, long amount, long bonus, long bonusWithinMs,
                           long durationMs, String notes, Consumer<Result> then) {
        Bounties.addByStaff(server, by, new Bounties.Ask(target, targetName, coinTier < 0 ? Bounty.PIECES : coinTier, amount, bonus, bonusWithinMs, durationMs,
                notes == null ? "" : notes), r -> then.accept(result(r)));
    }

    /** Takes a bounty off the board, giving the placer the reward and bonus back ({@code refund}) or nothing. Keys: ok, unreachable, gone. */
    public static void remove(MinecraftServer server, String id, boolean refund, String by, Consumer<Result> then) {
        Bounties.removeByStaff(server, id, refund, by, r -> then.accept(result(r)));
    }

    /** {@code addMs} more time, never past {@code bounty.maxDays} from now. Keys: ok, unreachable, gone, time, max. */
    public static void extend(MinecraftServer server, String id, long addMs, String by, Consumer<Result> then) {
        Bounties.extendByStaff(server, id, addMs, by, r -> then.accept(result(r)));
    }

    /** Empties a bounty's notes. Keys: ok, unreachable, gone. */
    public static void blankNotes(MinecraftServer server, String id, String by, Consumer<Result> then) {
        Bounties.blankNotes(server, id, by, r -> then.accept(result(r)));
    }
}
