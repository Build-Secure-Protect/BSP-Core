package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketDb;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The bounties on a network: the database holds them, and every placing, claim and closing is one transaction there
 * ({@link BountyDb}), so a bounty is claimed or closed once whichever servers try at the same moment, and money never moves
 * halfway. This server keeps a copy of the open bounties and the history for its screens, re-read at start, on another server's
 * notice and every minute; after its own changes it updates the copy, the cached bank balances and logs and the Collect boxes,
 * and announces the change.
 *
 * <p>When the database cannot be reached nothing moves. A placing whose answer was lost asks whether the bounty went in; when even
 * that cannot be asked, a coin bounty's coins wait here as "unsure" (never handed back twice): the next refresh puts them in the
 * placer's Collect box if the bounty never went in.
 */
public final class NetworkBounties implements BountyStore {
    private static final int HISTORY = BountyLedger.HISTORY;
    /** Closed bounties older than this are forgotten by the database. */
    public static final long KEEP_CLOSED_MS = 90L * 24 * 60 * 60 * 1000;

    private final BountyDb db;
    private final Executor worker;
    private final Consumer<Runnable> onServer;
    private final Runnable announce;
    private final Function<MinecraftServer, BountyLedger> held;
    /** A player's Collect box gained coins in the database: this server's copy is read again and the other servers told. */
    private final Consumer<UUID> boxChanged;
    /** Server thread only. */
    private List<Bounty> open = new ArrayList<>();
    private List<Closed> closed = new ArrayList<>();
    private boolean refreshing, refreshAgain;
    /** While a refresh reads: bounties this server changed or closed itself, which the read may not have seen. */
    private final Map<String, Bounty> changedDuring = new HashMap<>();
    private final Set<String> removedDuring = new HashSet<>();
    private final List<Closed> closedDuring = new ArrayList<>();

    public NetworkBounties(BountyDb db, Executor worker, Consumer<Runnable> onServer, Runnable announce, Function<MinecraftServer, BountyLedger> held,
                           Consumer<UUID> boxChanged) {
        this.db = db;
        this.worker = worker;
        this.onServer = onServer;
        this.announce = announce;
        this.held = held;
        this.boxChanged = boxChanged;
    }

    private <T> void run(String what, Callable<T> work, Consumer<T> done, Consumer<Boolean> failed) {
        worker.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core bounty: could not {}: {}", what, e.toString());
                boolean untouched = e instanceof MarketDb.Untouched;
                onServer.accept(() -> failed.accept(untouched));
                return;
            }
            onServer.accept(() -> done.accept(result));
        });
    }

    // ------------------------------------------------------------------ the copy

    private void put(Bounty b) {
        boolean found = false;
        for (int i = 0; i < open.size(); i++) {
            if (open.get(i).id.equals(b.id)) {
                open.set(i, b);
                found = true;
                break;
            }
        }
        if (!found) {
            open.add(0, b);
        }
        if (refreshing) {
            changedDuring.put(b.id, b);
        }
    }

    private void drop(String id) {
        open.removeIf(b -> b.id.equals(id));
        if (refreshing) {
            removedDuring.add(id);
            changedDuring.remove(id);
        }
    }

    private void addClosed(Closed c) {
        closed.add(0, c);
        while (closed.size() > HISTORY) {
            closed.remove(closed.size() - 1);
        }
        if (refreshing) {
            closedDuring.add(c);
        }
    }

    private record Fresh(List<Bounty> open, List<Closed> closed) {}

    /**
     * Re-reads the open bounties and the history, after settling coin bounties whose answer was lost. A refresh asked for while one
     * reads runs once afterwards.
     */
    public void refresh(MinecraftServer server) {
        settleHeld(server);
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        changedDuring.clear();
        removedDuring.clear();
        closedDuring.clear();
        run("read the bounties", () -> new Fresh(db.open(), db.closed(HISTORY)), f -> {
            List<Bounty> next = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Bounty b : f.open()) {
                if (removedDuring.contains(b.id)) {
                    continue;
                }
                next.add(changedDuring.getOrDefault(b.id, b));
                seen.add(b.id);
            }
            for (Bounty b : changedDuring.values()) {
                if (!seen.contains(b.id)) {
                    next.add(0, b);
                }
            }
            open = next;
            List<Closed> hist = new ArrayList<>(f.closed());
            for (Closed c : closedDuring) {
                if (hist.stream().noneMatch(x -> x.bounty().id.equals(c.bounty().id))) {
                    hist.add(0, c);
                }
            }
            closed = hist;
            refreshDone(server);
        }, untouched -> refreshDone(server));
    }

    private void refreshDone(MinecraftServer server) {
        refreshing = false;
        changedDuring.clear();
        removedDuring.clear();
        closedDuring.clear();
        if (refreshAgain) {
            refreshAgain = false;
            refresh(server);
        }
    }

    /** Once a minute: forgets closed bounties older than {@link #KEEP_CLOSED_MS}. */
    public void sweep() {
        long before = Bounties.now() - KEEP_CLOSED_MS;
        run("forget old bounties", () -> {
            db.sweep(before);
            return true;
        }, ok -> {}, untouched -> {});
    }

    /** Coin bounties whose placing's answer was lost: up on the board, nothing to do; never placed, the coins go to the placer's Collect box. */
    private void settleHeld(MinecraftServer server) {
        BountyLedger ledger = held.apply(server);
        for (Bounty b : ledger.unsure()) {
            run("ask whether a bounty went in", () -> db.placed(b.id), in -> {
                if (ledger.dropUnsure(b.id) == null) {
                    return; // settled by an earlier answer
                }
                if (in) {
                    BSPCore.LOGGER.info("BSP-Core bounty: bounty {} on {} is on the board after all", b.id, b.targetName);
                    return;
                }
                CoinTier tier = b.tier();
                if (tier != null) {
                    Market.store(server).addCollect(server, b.placer, b.coinPart(0, b.held()));
                    Market.store(server).addCollect(server, b.placer, Bounty.coinStacks(CoinTier.COPPER, b.fee / Math.max(1, Bounties.piecesPerCopper()) / Math.max(1, CoinTier.COPPER.value())));
                }
                BSPCore.LOGGER.info("BSP-Core bounty: bounty {} on {} never went in: its coins went to {}'s Collect box", b.id, b.targetName, b.placerName);
            }, untouched -> {});
        }
    }

    /** What the database changed for this answer: cached balances and logs, and Collect boxes. */
    private void apply(MinecraftServer server, BountyDb.Done d) {
        for (BountyDb.Moved m : d.moved()) {
            Bank.changedAndLoggedInDatabase(server, m.id(), m.piecesAfter(), m.row().type(), m.row().with(), m.row().detail(), m.row().pieces());
        }
        d.boxes().forEach(boxChanged);
    }

    // ------------------------------------------------------------------ the store

    @Override
    public void place(MinecraftServer server, Bounty b, Consumer<String> then) {
        int maxOpen = Bounties.maxOpen();
        run("place a bounty", () -> db.place(b, maxOpen), d -> {
            if (!d.ok()) {
                then.accept(d.key());
                return;
            }
            put(b);
            apply(server, d);
            announce.run();
            then.accept("ok");
        }, untouched -> {
            if (untouched) {
                then.accept("unreachable");
            } else {
                settlePlace(server, b, then);
            }
        });
    }

    /** A placing's call failed after it may have reached the database: did it? Only a clear no lets the caller give the coins back. */
    private void settlePlace(MinecraftServer server, Bounty b, Consumer<String> then) {
        run("ask whether a bounty went in", () -> db.placed(b.id), in -> {
            if (!in) {
                then.accept("unreachable");
                return;
            }
            BSPCore.LOGGER.warn("BSP-Core bounty: the answer to placing bounty {} was lost, but it is on the board", b.id);
            put(b);
            Bank.refresh(server, b.placer);
            announce.run();
            then.accept("ok");
        }, untouched -> {
            BSPCore.LOGGER.warn("BSP-Core bounty: the answer to placing bounty {} was lost; {}", b.id,
                    b.coins() ? "its coins wait here until the database can say" : "the placer's bank shows whether it went in");
            if (b.coins()) {
                held.apply(server).putUnsure(b);
            } else {
                Bank.refresh(server, b.placer);
            }
            then.accept("unsure");
        });
    }

    @Override
    public List<Bounty> open(MinecraftServer server) {
        return new ArrayList<>(open);
    }

    @Override
    public List<Closed> closed(MinecraftServer server) {
        return new ArrayList<>(closed);
    }

    /** One answer that changes a bounty but closes nothing: the copy follows it. */
    private void changed(BountyDb.Done d, String id, Consumer<Answer> then) {
        if (d.ok() && d.bounty() != null) {
            put(d.bounty());
            announce.run();
        } else if (d.key().equals("gone")) {
            drop(id);
        }
        then.accept(new Answer(d.key(), d.bounty()));
    }

    @Override
    public void accept(MinecraftServer server, String id, Bounty.Hunter hunter, int maxPerHunter, Consumer<Answer> then) {
        long now = Bounties.now();
        run("accept a bounty", () -> db.accept(id, hunter, maxPerHunter, now), d -> changed(d, id, then), u -> then.accept(new Answer("unreachable", null)));
    }

    @Override
    public void abandon(MinecraftServer server, String id, UUID hunter, Consumer<Answer> then) {
        run("drop a bounty", () -> db.abandon(id, hunter), d -> changed(d, id, then), u -> then.accept(new Answer("unreachable", null)));
    }

    @Override
    public void extend(MinecraftServer server, String id, long endsMs, Consumer<Answer> then) {
        run("extend a bounty", () -> db.extend(id, endsMs), d -> changed(d, id, then), u -> then.accept(new Answer("unreachable", null)));
    }

    @Override
    public void setNotes(MinecraftServer server, String id, String notes, Consumer<Answer> then) {
        run("change a bounty's notes", () -> db.setNotes(id, notes), d -> changed(d, id, then), u -> then.accept(new Answer("unreachable", null)));
    }

    @Override
    public void end(MinecraftServer server, String id, Ending ending, String by, Consumer<Answer> then) {
        if (ending == Ending.CLAIMED) {
            throw new IllegalArgumentException("a claim goes through claim()");
        }
        long now = Bounties.now();
        run("close a bounty", () -> db.end(id, ending, by, now), d -> {
            if (d.ok() || d.key().equals("gone")) {
                drop(id);
            } else if (d.bounty() != null) {
                put(d.bounty()); // the database's own record: this server's copy was behind
            }
            if (d.ok()) {
                addClosed(new Closed(d.bounty(), ending, now, null, "", by, false));
                apply(server, d);
                announce.run();
            }
            then.accept(new Answer(d.key(), d.bounty()));
        }, u -> then.accept(new Answer("unreachable", null)));
    }

    @Override
    public void claim(MinecraftServer server, String id, UUID hunter, String hunterName, long killMs, Consumer<Answer> then) {
        long now = Bounties.now();
        run("claim a bounty", () -> db.claim(id, hunter, hunterName, killMs, now), d -> {
            if (d.ok() || d.key().equals("gone")) {
                drop(id);
            }
            if (d.ok()) {
                addClosed(new Closed(d.bounty(), Ending.CLAIMED, now, hunter, hunterName, hunterName, d.bonusPaid()));
                apply(server, d);
                announce.run();
            }
            then.accept(new Answer(d.key(), d.bounty(), d.bonusPaid()));
        }, u -> then.accept(new Answer("unreachable", null)));
    }
}
