package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyDb;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.NetworkBounties;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketDb;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameTests for the bounties on a network ({@link NetworkBounties}) over an in-memory database that keeps the rules the MySQL one
 * keeps, run at once on the server thread. Batch {@code bounty}. The SQL itself never runs here: it needs a real MySQL.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyNetworkGameTests {
    private static final String EMPTY = "empty";
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS;

    /** What the next call does: work, fail before changing anything, or change and then lose the answer. */
    enum Fail { NONE, UNTOUCHED, LOST }

    /** The database in memory: open bounties, history, bank balances and Collect boxes, with failures on demand. */
    static final class MemDb implements BountyDb {
        final Map<String, Bounty> open = new LinkedHashMap<>();
        final List<BountyStore.Closed> closed = new ArrayList<>();
        final Map<UUID, Long> bank = new HashMap<>();
        final Map<UUID, List<ItemStack>> boxes = new HashMap<>();
        Fail next = Fail.NONE;
        boolean askFails;

        private void before() throws Exception {
            if (next == Fail.UNTOUCHED) {
                next = Fail.NONE;
                throw new MarketDb.Untouched("gametest");
            }
        }

        private Done after(Done d) throws Exception {
            if (next == Fail.LOST) {
                next = Fail.NONE;
                throw new java.sql.SQLException("answer lost (gametest)");
            }
            return d;
        }

        private boolean move(UUID id, long delta, List<Moved> moved) {
            long have = bank.getOrDefault(id, 0L);
            if (have + delta < 0) {
                return false;
            }
            bank.put(id, have + delta);
            moved.add(new Moved(id, have + delta, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.BOUNTY, "test", "test", delta, -1, 0, "test")));
            return true;
        }

        private void give(Bounty b, UUID to, long n, List<Moved> moved, List<UUID> got) {
            if (n <= 0 || to.equals(Bounties.SERVER)) {
                return;
            }
            if (b.coins()) {
                boxes.computeIfAbsent(to, k -> new ArrayList<>()).addAll(Bounty.coinStacks(b.tier(), n));
                got.add(to);
            } else {
                move(to, n, moved);
            }
        }

        @Override
        public Done place(Bounty b, int maxOpen) throws Exception {
            before();
            if (open.values().stream().filter(x -> x.placer.equals(b.placer)).count() >= maxOpen) {
                return Done.no("too_many");
            }
            List<Moved> moved = new ArrayList<>();
            if (!b.coins() && !b.placer.equals(Bounties.SERVER) && !move(b.placer, -(b.held() + b.fee), moved)) {
                return Done.no("short");
            }
            move(Market.POT, b.fee, moved);
            open.put(b.id, b);
            return after(new Done("ok", b, false, moved, List.of()));
        }

        @Override
        public boolean placed(String id) throws Exception {
            if (askFails) {
                throw new java.sql.SQLException("cannot ask (gametest)");
            }
            return open.containsKey(id) || closed.stream().anyMatch(c -> c.bounty().id.equals(id));
        }

        @Override
        public List<Bounty> open() throws Exception {
            before();
            List<Bounty> out = new ArrayList<>(open.values());
            java.util.Collections.reverse(out);
            return out;
        }

        @Override
        public List<BountyStore.Closed> closed(int limit) {
            return new ArrayList<>(closed);
        }

        @Override
        public Done accept(String id, Bounty.Hunter hunter, int maxPerHunter, long now) throws Exception {
            before();
            Bounty b = open.get(id);
            if (b == null || b.endedBy(now)) {
                return Done.no("gone");
            }
            if (b.hasHunter(hunter.id())) {
                return Done.no("already", b);
            }
            if (open.values().stream().filter(x -> x.hasHunter(hunter.id())).count() >= maxPerHunter) {
                return Done.no("too_many", b);
            }
            Bounty after = b.withHunter(hunter);
            open.put(id, after);
            return after(new Done("ok", after, false, List.of(), List.of()));
        }

        @Override
        public Done abandon(String id, UUID hunter) throws Exception {
            before();
            Bounty b = open.get(id);
            if (b == null) {
                return Done.no("gone");
            }
            Bounty after = b.withoutHunter(hunter);
            open.put(id, after);
            return after(new Done("ok", after, false, List.of(), List.of()));
        }

        @Override
        public Done end(String id, BountyStore.Ending ending, String by, long now) throws Exception {
            before();
            Bounty b = open.remove(id);
            if (b == null) {
                return Done.no("gone");
            }
            List<Moved> moved = new ArrayList<>();
            List<UUID> got = new ArrayList<>();
            if (ending != BountyStore.Ending.REMOVED_NO_REFUND) {
                give(b, b.placer, b.held(), moved, got);
            }
            closed.add(0, new BountyStore.Closed(b, ending, now, null, "", by, false));
            return after(new Done("ok", b, false, moved, got));
        }

        @Override
        public Done claim(String id, UUID hunter, String hunterName, long killMs, long now) throws Exception {
            before();
            Bounty b = open.get(id);
            if (b == null) {
                return Done.no("gone");
            }
            if (!b.hasHunter(hunter)) {
                return Done.no("not_hunter", b);
            }
            if (b.endedBy(killMs)) {
                return Done.no("ended", b);
            }
            open.remove(id);
            List<Moved> moved = new ArrayList<>();
            List<UUID> got = new ArrayList<>();
            boolean bonus = b.bonusLive(killMs);
            give(b, hunter, b.payFor(killMs), moved, got);
            if (b.bonus > 0 && !bonus) {
                give(b, b.placer, b.bonus, moved, got);
            }
            closed.add(0, new BountyStore.Closed(b, BountyStore.Ending.CLAIMED, now, hunter, hunterName, hunterName, bonus));
            return after(new Done("ok", b, bonus, moved, got));
        }

        @Override
        public Done extend(String id, long addMs, long now, long capMs) throws Exception {
            before();
            Bounty b = open.get(id);
            if (b == null) {
                return Done.no("gone");
            }
            long ends = Bounties.extendedEnd(b.endsMs, addMs, now, capMs);
            if (ends < 0) {
                return Done.no("max");
            }
            open.put(id, b.withEnds(ends));
            return after(new Done("ok", open.get(id), false, List.of(), List.of(), b.endsMs));
        }

        @Override
        public Done setNotes(String id, String notes) throws Exception {
            before();
            Bounty b = open.get(id);
            if (b == null) {
                return Done.no("gone");
            }
            open.put(id, b.withNotes(notes));
            return after(new Done("ok", open.get(id), false, List.of(), List.of()));
        }

        @Override
        public void sweep(long beforeMs) {
            closed.removeIf(c -> c.closedMs() < beforeMs);
        }
    }

    private interface Body {
        void run(MinecraftServer server, MemDb db, NetworkBounties net, BountyLedger ledger, List<UUID> boxNotices);
    }

    /** A network store over a fresh in-memory database, run at once, with a fixed clock and no staff or protected players. */
    private static void run(GameTestHelper h, long now, Body body) {
        MinecraftServer server = h.getLevel().getServer();
        MemDb db = new MemDb();
        BountyLedger ledger = new BountyLedger();
        List<UUID> notices = new ArrayList<>();
        NetworkBounties net = new NetworkBounties(db, Runnable::run, Runnable::run, () -> {}, s -> ledger, notices::add);
        BountyStore oldB = Bounties.useStore(net);
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        Bounties.useLedger(ledger);
        Bounties.useClock(() -> now);
        Bounties.staffCheck = (id, name) -> false;
        Bounties.protectedCheck = id -> false;
        try {
            body.run(server, db, net, ledger, notices);
        } finally {
            Bounties.useStore(oldB);
            Market.useStore(oldM);
            Bounties.useLedger(null);
            Bounties.useClock(null);
            Bounties.staffCheck = null;
            Bounties.protectedCheck = null;
        }
        h.succeed();
    }

    /** A player with the same pieces in this server's bank and in the database. */
    private static ServerPlayer player(GameTestHelper h, MemDb db, long pieces) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        Bank.ensure(server, p.getUUID(), p.getGameProfile().getName(), g -> {});
        Bank.set(server, p.getUUID(), pieces, "gametest", r -> {});
        db.bank.put(p.getUUID(), pieces);
        return p;
    }

    /** Another server's extension, straight into the database. */
    private static BountyDb.Done dbExtend(MemDb db, String id, long addMs, long now, long capMs) {
        try {
            return db.extend(id, addMs, now, capMs);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    private static Bounties.Result place(MinecraftServer server, ServerPlayer placer, Bounties.Ask ask) {
        Bounties.Result[] r = new Bounties.Result[1];
        Bounties.place(server, placer, ask, x -> r[0] = x);
        return r[0];
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void an_extension_adds_to_the_end_in_the_database_even_when_this_servers_copy_is_behind(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        run(h, start, (server, db, net, ledger, notices) -> {
            ServerPlayer placer = player(h, db, 100_000);
            UUID target = UUID.randomUUID();
            Bounties.Result placed = place(server, placer, new Bounties.Ask(target, "Wanted", Bounty.PIECES, 10_000, 0, 0, 2 * D, ""));
            h.assertTrue(placed != null && placed.key().equals("ok"), "placed: " + (placed == null ? null : placed.key()));
            String id = placed.bounty().id;
            long cap = start + Bounties.maxDays() * D;
            // another server gives it a day more: the database has it, this server's copy does not yet
            h.assertTrue(dbExtend(db, id, D, start, cap).ok() && db.open.get(id).endsMs == start + 3 * D, "the other server's day is in the database");
            h.assertTrue(net.open(server).get(0).endsMs == start + 2 * D, "this server's copy is still behind");
            List<String> logged = new ArrayList<>();
            Bounties.extendLogWatch = logged::add;
            try {
                com.mrgregles.bsp_core.api.BspBounties.Result[] r = new com.mrgregles.bsp_core.api.BspBounties.Result[1];
                com.mrgregles.bsp_core.api.BspBounties.extend(server, id, H, "Mod (panel)", x -> r[0] = x);
                h.assertTrue(r[0] != null && r[0].ok() && r[0].bounty().endsMs() == start + 3 * D + H && db.open.get(id).endsMs == start + 3 * D + H,
                        "an hour on top of the other server's day, none lost: " + (r[0] == null ? null : r[0].bounty()));
                h.assertTrue(net.open(server).get(0).endsMs == start + 3 * D + H, "the copy follows the database");
                h.assertTrue(logged.size() == 1 && logged.get(0).startsWith("by 1 h 0 m, to end "), "the log says an hour was added: " + logged);
                // a bounty placed on another server that this server has not read yet is extended all the same
                Bounty elsewhere = new Bounty("bzzelse1", target, "Wanted", Bounties.SERVER, "Server", Bounty.PIECES, 5_000, 0, 0, 0, start, start + D, "",
                        "other", List.of());
                db.open.put(elsewhere.id, elsewhere);
                com.mrgregles.bsp_core.api.BspBounties.extend(server, elsewhere.id, H, "Mod (panel)", x -> r[0] = x);
                h.assertTrue(r[0].ok() && db.open.get(elsewhere.id).endsMs == start + D + H, "extended from the database's record: " + r[0].key());
                // the other server already took it to the longest: the database says max, whatever the copy shows
                h.assertTrue(dbExtend(db, id, 60 * D, start, cap).ok() && db.open.get(id).endsMs == cap, "the other server reached the cap");
                com.mrgregles.bsp_core.api.BspBounties.extend(server, id, H, "Mod (panel)", x -> r[0] = x);
                h.assertTrue(r[0].key().equals("max") && db.open.get(id).endsMs == cap, "already at the cap: " + r[0].key());
                h.assertTrue(dbExtend(db, "bnothere", H, start, cap).key().equals("gone"), "no such bounty: gone");
            } finally {
                Bounties.extendLogWatch = null;
            }
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void on_a_network_the_database_moves_the_money_and_this_server_follows_it(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, db, net, ledger, notices) -> {
            ServerPlayer placer = player(h, db, 100_000), hunter = player(h, db, 0), target = player(h, db, 0);
            Bounties.Result r = place(server, placer, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 20_000, 4_000, H, D, "notes"));
            h.assertTrue(r != null && r.ok(), "placed through the database: " + r);
            long fee = r.bounty().fee;
            h.assertTrue(db.bank.get(placer.getUUID()) == 100_000 - 24_000 - fee, "the database took reward, bonus and fee");
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == db.bank.get(placer.getUUID()), "and this server's copy of the bank follows it");
            h.assertTrue(Bounties.open(server).size() == 1 && db.open.size() == 1, "the copy holds the bounty");
            Bounties.Result[] a = new Bounties.Result[1];
            Bounties.accept(server, hunter.getUUID(), "H", r.bounty().id, x -> a[0] = x);
            h.assertTrue(a[0].ok() && Bounties.open(server).get(0).hasHunter(hunter.getUUID()), "the copy shows the hunter");
            int[] n = {0};
            Bounties.claimKill(server, target.getUUID(), "T", hunter.getUUID(), "H", Bounties.now() + 30 * 60_000L, x -> n[0] = x);
            h.assertTrue(n[0] == 1 && db.bank.get(hunter.getUUID()) == 24_000 && Bank.pieces(server, hunter.getUUID()) == 24_000, "the claim pays reward and bonus");
            h.assertTrue(Bounties.open(server).isEmpty() && net.closed(server).get(0).ending() == BountyStore.Ending.CLAIMED, "off the board, in the history");
            // a coin bounty: the payout lands in the database's Collect box, and this server is told to read the box again
            placer.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 10));
            Bounties.Result c = place(server, placer, new Bounties.Ask(target.getUUID(), "T", CoinTier.GOLD.ordinal(), 3, 0, 0, D, ""));
            Bounties.accept(server, hunter.getUUID(), "H", c.bounty().id, x -> a[0] = x);
            Bounties.claimKill(server, target.getUUID(), "T", hunter.getUUID(), "H", Bounties.now(), x -> n[0] = x);
            h.assertTrue(n[0] == 1 && db.boxes.get(hunter.getUUID()).stream().mapToInt(ItemStack::getCount).sum() == 3 && notices.contains(hunter.getUUID()),
                    "3 Gold coins in the hunter's Collect box in the database, and a box notice: " + notices);
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_placing_that_failed_or_lost_its_answer_never_takes_twice_or_hands_back_twice(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, db, net, ledger, notices) -> {
            ServerPlayer placer = player(h, db, 100_000);
            UUID target = UUID.randomUUID();
            // failed before the database changed anything
            db.next = Fail.UNTOUCHED;
            Bounties.Result r = place(server, placer, new Bounties.Ask(target, "T", Bounty.PIECES, 5_000, 0, 0, D, ""));
            h.assertTrue(r.key().equals("unreachable") && db.open.isEmpty() && db.bank.get(placer.getUUID()) == 100_000, "nothing placed, nothing taken");
            // placed, then the answer was lost: the store asks and finds it on the board
            db.next = Fail.LOST;
            r = place(server, placer, new Bounties.Ask(target, "T", Bounty.PIECES, 5_000, 0, 0, D, ""));
            h.assertTrue(r.ok() && db.open.size() == 1 && Bounties.open(server).size() == 1, "the lost answer is settled as placed: " + r.key());
            // a coin bounty whose answer was lost and could not even be asked about: the coins wait, not handed back
            placer.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 10));
            int before = CoinWallet.totalValue(placer);
            db.next = Fail.LOST;
            db.askFails = true;
            r = place(server, placer, new Bounties.Ask(target, "T", CoinTier.GOLD.ordinal(), 2, 0, 0, D, ""));
            h.assertTrue(r.key().equals("unsure") && ledger.unsure().size() == 1, "unsure, the bounty held here: " + r.key());
            h.assertTrue(CoinWallet.totalValue(placer) < before, "the coins stay taken (not handed back while unsure)");
            // it did go in: the next refresh finds it and lets it be
            db.askFails = false;
            net.refresh(server);
            h.assertTrue(ledger.unsure().isEmpty() && Market.store(server).peekCollect(server, placer.getUUID()).isEmpty(), "found on the board: nothing comes back");
            // one that never went in: its coins and fee go to the Collect box
            Bounty never = new Bounty("bnever1", target, "T", placer.getUUID(), "P", CoinTier.GOLD.ordinal(), 2, 1, Bounties.now() + H, 1000, Bounties.now(),
                    Bounties.now() + D, "", Bank.serverId(), List.of());
            ledger.putUnsure(never);
            net.refresh(server);
            long gold = Market.store(server).peekCollect(server, placer.getUUID()).stream().filter(s -> s.is(CoinTier.GOLD.coin())).mapToInt(ItemStack::getCount).sum();
            long copper = Market.store(server).peekCollect(server, placer.getUUID()).stream().filter(s -> s.is(CoinTier.COPPER.coin())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(ledger.unsure().isEmpty() && gold == 3 && copper == 1, "never placed: 3 Gold and the 1 Copper fee wait in the Collect box: " + gold + ", " + copper);
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_kill_the_database_could_not_take_is_claimed_later_by_the_time_of_the_kill(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, start, (server, db, net, ledger, notices) -> {
            Bounties.useClock(() -> now[0]);
            ServerPlayer placer = player(h, db, 100_000), hunter = player(h, db, 0), target = player(h, db, 0);
            Bounty b = place(server, placer, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 8_000, 2_000, 5 * 60_000L, D, "")).bounty();
            Bounties.accept(server, hunter.getUUID(), "H", b.id, x -> {});
            long kill = now[0] + 60_000L;
            db.next = Fail.UNTOUCHED;
            Bounties.claimKill(server, target.getUUID(), "T", hunter.getUUID(), "H", kill, x -> {});
            h.assertTrue(db.bank.get(hunter.getUUID()) == 0 && ledger.kills().size() == 1, "not paid yet; the kill is kept");
            now[0] = start + 8 * 60_000L; // past the bonus window, still inside the grace for retries
            h.assertTrue(Bounties.retryKills(server) == 1, "the kept kill is tried again");
            h.assertTrue(db.bank.get(hunter.getUUID()) == 10_000 && ledger.kills().isEmpty(), "paid with the bonus, judged by the kill's own time: " + db.bank.get(hunter.getUUID()));
            // a kill kept too long is given up
            ledger.addKill(new BountyLedger.PendingKill(target.getUUID(), "T", hunter.getUUID(), "H", now[0] - Bounties.SWEEP_GRACE_MS - 1));
            h.assertTrue(Bounties.retryKills(server) == 0 && ledger.kills().isEmpty(), "an old kill is dropped");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_refresh_keeps_this_servers_own_changes_made_while_it_read(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, db, net, ledger, notices) -> {
            ServerPlayer placer = player(h, db, 100_000);
            UUID target = UUID.randomUUID();
            Bounty mine = place(server, placer, new Bounties.Ask(target, "T", Bounty.PIECES, 2_000, 0, 0, D, "")).bounty();
            // another server's bounty, only in the database: a refresh brings it in
            Bounty theirs = new Bounty("btheirs1", target, "T", UUID.randomUUID(), "Other", Bounty.PIECES, 3_000, 0, 0, 150, Bounties.now(), Bounties.now() + D, "",
                    "other", List.of());
            db.open.put(theirs.id, theirs);
            net.refresh(server);
            h.assertTrue(Bounties.open(server).size() == 2, "the other server's bounty shows after a refresh");
            // the database dropped it (closed elsewhere): the next refresh drops it too
            db.open.remove(theirs.id);
            net.refresh(server);
            h.assertTrue(Bounties.open(server).size() == 1 && Bounties.open(server).get(0).id.equals(mine.id), "and goes when it closed elsewhere");
            // a failed read keeps the copy as it was
            db.next = Fail.UNTOUCHED;
            net.refresh(server);
            h.assertTrue(Bounties.open(server).size() == 1, "a failed read changes nothing");
        });
    }
}
