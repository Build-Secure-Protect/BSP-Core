package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The marketplace on a network: the database holds the lots and the Collect boxes, and every purchase, listing and closing
 * is one transaction there ({@link MarketDb}), so a lot is sold once whichever servers try at the same moment, and money and
 * items never move halfway. This server keeps a copy of the open lots for its screens, re-read at start, on another server's
 * notice and every minute; after its own changes it updates the copy, the cached bank balances and the bank log, and
 * announces the change.
 *
 * <p>When the database cannot be reached nothing moves: a listing hands its stacks back, a purchase stays unbought. Items
 * for a Collect box wait in this world's {@link MarketLedger} and are written at the next refresh, as are lots and boxes
 * left in the ledger from before the server joined the network.
 *
 * <p>An answer can also be lost after the database changed (the link dropped at the commit). So nothing is handed back on
 * an error without knowing: a listing asks whether its lot id went in (and when even that cannot be asked, its stacks wait
 * as "unsure" and the next write settles them); a purchase puts the items into the buyer's Collect box inside its own
 * transaction; emptying a box marks its rows first and deletes them only once the items are in the inventory; and what is
 * held here goes up as one batch whose attempt id makes a second try do nothing.
 */
public final class NetworkMarket implements MarketStore {
    private final MarketDb db;
    private final Executor worker;
    private final Consumer<Runnable> onServer;
    private final Runnable announce;
    private final Function<MinecraftServer, MarketLedger> held;
    /** Tells the other servers a player's Collect box changed here (a lot closed into it), so a screen there shows it. */
    private final Consumer<UUID> boxChanged;
    /** Server thread only. */
    private List<MarketLot> open = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> boxes = new HashMap<>();
    private boolean flushing;
    /** A refresh is reading; notices meanwhile ask for one more afterwards (many notices, one more read). */
    private boolean refreshing, refreshAgain;
    /** While a refresh reads: lots this server added or removed itself, which the read may not have seen. */
    private final java.util.Set<String> addedDuring = new java.util.HashSet<>(), removedDuring = new java.util.HashSet<>();

    /**
     * @param worker   runs the database calls (the storage thread)
     * @param onServer runs the answers on the server thread
     * @param announce tells the other servers the market changed
     * @param held     where items wait while the database cannot be reached (the world's ledger)
     */
    public NetworkMarket(MarketDb db, Executor worker, Consumer<Runnable> onServer, Runnable announce, Function<MinecraftServer, MarketLedger> held) {
        this(db, worker, onServer, announce, held, id -> {});
    }

    public NetworkMarket(MarketDb db, Executor worker, Consumer<Runnable> onServer, Runnable announce, Function<MinecraftServer, MarketLedger> held, Consumer<UUID> boxChanged) {
        this.db = db;
        this.worker = worker;
        this.onServer = onServer;
        this.announce = announce;
        this.held = held;
        this.boxChanged = boxChanged;
    }

    /** The copy changed here: a refresh under way must not undo it. */
    private void added(MarketLot lot) {
        open.removeIf(l -> l.id.equals(lot.id));
        open.add(0, lot);
        if (refreshing) {
            addedDuring.add(lot.id);
        }
    }

    private void removed(String id) {
        open.removeIf(l -> l.id.equals(id));
        if (refreshing) {
            removedDuring.add(id);
        }
    }

    private <T> void run(String what, Callable<T> work, Consumer<T> done, Runnable failed) {
        runOr(what, work, done, untouched -> failed.run());
    }

    /** As {@link #run}, but {@code failed} is told whether the database surely did not change ({@link MarketDb.Untouched}) or might have. */
    private <T> void runOr(String what, Callable<T> work, Consumer<T> done, Consumer<Boolean> failed) {
        worker.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core market: could not {}: {}", what, e.toString());
                boolean untouched = e instanceof MarketDb.Untouched;
                onServer.accept(() -> failed.accept(untouched));
                return;
            }
            onServer.accept(() -> done.accept(result));
        });
    }

    // ------------------------------------------------------------------ the copy

    private record Fresh(List<String> ids, List<MarketLot> fetched) {}

    /**
     * Re-reads the open lots, after first writing anything held on this server: the ids only, then the data of the lots this
     * server does not have yet (a lot never changes once listed). A refresh asked for while one reads runs once afterwards.
     */
    public void refresh(MinecraftServer server) {
        flushHeld(server);
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        addedDuring.clear();
        removedDuring.clear();
        java.util.Set<String> known = new java.util.HashSet<>();
        open.forEach(l -> known.add(l.id));
        run("read the market", () -> {
            List<String> ids = db.openIds();
            List<String> missing = new ArrayList<>();
            for (String id : ids) {
                if (!known.contains(id)) {
                    missing.add(id);
                }
            }
            return new Fresh(ids, missing.isEmpty() ? List.of() : db.lots(missing));
        }, f -> {
            Map<String, MarketLot> by = new HashMap<>();
            open.forEach(l -> by.put(l.id, l));
            f.fetched().forEach(l -> by.put(l.id, l));
            List<MarketLot> next = new ArrayList<>();
            for (String id : f.ids()) {
                MarketLot l = by.get(id);
                if (l != null && !removedDuring.contains(id)) {
                    next.add(l);
                }
            }
            for (String id : addedDuring) {
                if (!f.ids().contains(id) && by.containsKey(id)) {
                    next.add(0, by.get(id));
                }
            }
            open = next;
            refreshDone(server);
        }, () -> refreshDone(server));
    }

    private void refreshDone(MinecraftServer server) {
        refreshing = false;
        addedDuring.clear();
        removedDuring.clear();
        if (refreshAgain) {
            refreshAgain = false;
            refresh(server);
        }
    }

    /** Once a minute: also puts back stacks left half taken by a server that stopped, and forgets old attempt records. */
    public void sweep() {
        run("sweep the Collect boxes", () -> {
            db.sweep(Market.now());
            return true;
        }, ok -> {}, () -> {});
    }

    /** Forgets this server's copy of a player's Collect box (they logged out), so the copies of players long gone do not pile up. */
    public void forget(UUID player) {
        boxes.remove(player);
    }

    /** Re-reads what waits in one player's Collect box (at login, before a screen shows it). */
    public void refreshBox(UUID player) {
        run("read a Collect box", () -> db.peekCollect(player), items -> boxes.put(player, new ArrayList<>(items)), () -> {});
    }

    private void flushHeld(MinecraftServer server) {
        MarketLedger ledger = held.apply(server);
        if (flushing) {
            return;
        }
        List<String> deletes = ledger.deletes();
        if (!deletes.isEmpty()) {
            run("finish emptying Collect boxes", () -> {
                for (String d : deletes) {
                    db.taken(d);
                }
                return true;
            }, ok -> ledger.dropDeletes(deletes), () -> {});
        }
        // the batch leaves the ledger's open part at once but is kept, under its attempt id, until the database has it
        MarketLedger.Batch batch = ledger.batch(() -> "f-" + UUID.randomUUID());
        if (batch == null) {
            return;
        }
        flushing = true;
        run("write the market items held on this server", () -> {
            db.flush(batch.attempt(), batch.lots(), batch.unsure(), batch.boxes());
            return true;
        }, ok -> {
            flushing = false;
            ledger.batchWritten();
            batch.boxes().keySet().forEach(this::refreshBox);
            batch.boxes().keySet().forEach(boxChanged);
            batch.unsure().forEach(l -> boxChanged.accept(l.seller));
            BSPCore.LOGGER.info("BSP-Core market: wrote {} held lot(s), {} unsure listing(s) and {} held Collect box(es) to the network",
                    batch.lots().size(), batch.unsure().size(), batch.boxes().size());
            announce.run();
        }, () -> flushing = false);
    }

    // ------------------------------------------------------------------ the store

    @Override
    public void list(MinecraftServer server, MarketLot lot, long tax, Consumer<String> then) {
        int maxLots = Market.maxLots();
        runOr("list a lot", () -> db.list(lot, tax, maxLots), d -> {
            if (!d.ok()) {
                then.accept(d.key());
                return;
            }
            added(lot);
            if (tax > 0) {
                Bank.changedInDatabase(server, lot.seller, d.pieces(), BankTransaction.Type.TAX, "Market", "listing tax: " + lot.describe(), -tax);
                Bank.changedInDatabase(server, Market.POT, d.potPieces(), BankTransaction.Type.TAX, lot.sellerName, "listing tax, lot " + lot.id, tax);
            }
            announce.run();
            then.accept("ok");
        }, untouched -> {
            if (untouched) {
                then.accept("unreachable");
            } else {
                settleListing(server, lot, then);
            }
        });
    }

    /** A listing's call failed: did it reach the database? Only a clear no hands the stacks back. */
    private void settleListing(MinecraftServer server, MarketLot lot, Consumer<String> then) {
        run("ask whether a listing went in", () -> db.committed(lot.id), in -> {
            if (!in) {
                then.accept("unreachable");
                return;
            }
            BSPCore.LOGGER.warn("BSP-Core market: the answer to listing lot {} was lost, but the lot is on the market", lot.id);
            added(lot);
            Bank.refresh(server, lot.seller);
            announce.run();
            then.accept("ok");
        }, () -> {
            BSPCore.LOGGER.warn("BSP-Core market: the answer to listing lot {} was lost; its stacks wait here until the database can say", lot.id);
            held.apply(server).putUnsure(lot);
            then.accept("unsure");
        });
    }

    @Override
    public List<MarketLot> open(MinecraftServer server) {
        return new ArrayList<>(open);
    }

    @Override
    public void buy(MinecraftServer server, String id, UUID buyer, String buyerName, long tax, Consumer<Answer> then) {
        runOr("buy a lot", () -> db.buy(id, buyer, tax, Market.now()), d -> {
            if (!d.ok()) {
                if (d.key().equals("gone")) {
                    removed(id);
                }
                then.accept(new Answer(d.key(), null));
                return;
            }
            MarketLot lot = d.lot();
            removed(id);
            boxes.computeIfAbsent(buyer, k -> new ArrayList<>()).addAll(lot.items());
            Bank.changedInDatabase(server, buyer, d.pieces(), BankTransaction.Type.MARKET, lot.sellerName, "bought " + lot.describe() + " (tax " + Bank.format(tax) + ")", -(lot.price + tax));
            Bank.changedInDatabase(server, lot.seller, d.otherPieces(), BankTransaction.Type.MARKET, buyerName, "sold " + lot.describe(), lot.price);
            if (tax > 0) {
                Bank.changedInDatabase(server, Market.POT, d.potPieces(), BankTransaction.Type.TAX, buyerName, "purchase tax, lot " + lot.id, tax);
            }
            announce.run();
            then.accept(new Answer("ok", lot, true));
        }, untouched -> {
            if (untouched) {
                then.accept(new Answer("unreachable", null));
                return;
            }
            // the purchase may have gone through: if so its items are in the buyer's box, so read the box and balance again
            Bank.refresh(server, buyer);
            refreshBox(buyer);
            refresh(server);
            then.accept(new Answer("unsure_buy", null));
        });
    }

    @Override
    public void close(MinecraftServer server, String id, Consumer<Answer> then) {
        run("take a lot off the market", () -> db.close(id), d -> {
            if (closed(d, id, then)) {
                announce.run();
            }
        }, () -> then.accept(new Answer("unreachable", null)));
    }

    /** One closing's answer, here; true when it closed the lot. */
    private boolean closed(MarketDb.Done d, String id, Consumer<Answer> then) {
        removed(id);
        if (!d.ok()) {
            then.accept(new Answer(d.key(), null));
            return false;
        }
        boxes.computeIfAbsent(d.lot().seller, k -> new ArrayList<>()).addAll(d.lot().items());
        boxChanged.accept(d.lot().seller);
        then.accept(new Answer("ok", d.lot()));
        return true;
    }

    /** The expiry sweep: every lot closed in one go on the storage thread (each its own transaction), and one notice at the end. */
    @Override
    public void closeAll(MinecraftServer server, List<String> ids, Consumer<Answer> each) {
        run("close the lots that ran out", () -> {
            List<MarketDb.Done> out = new ArrayList<>();
            for (String id : ids) {
                try {
                    out.add(db.close(id));
                } catch (Exception e) {
                    BSPCore.LOGGER.error("BSP-Core market: could not close lot {}: {}", id, e.toString());
                    out.add(MarketDb.Done.no("unreachable"));
                }
            }
            return out;
        }, out -> {
            boolean any = false;
            for (int i = 0; i < ids.size(); i++) {
                MarketDb.Done d = out.get(i);
                if (d.key().equals("unreachable")) {
                    each.accept(new Answer("unreachable", null));
                } else {
                    any |= closed(d, ids.get(i), each);
                }
            }
            if (any) {
                announce.run();
            }
        }, () -> ids.forEach(id -> each.accept(new Answer("unreachable", null))));
    }

    @Override
    public void addCollect(MinecraftServer server, UUID player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> copy = new ArrayList<>();
        items.forEach(s -> copy.add(s.copy()));
        boxes.computeIfAbsent(player, k -> new ArrayList<>()).addAll(copy);
        run("fill a Collect box", () -> {
            db.addCollect(player, copy);
            return true;
        }, ok -> {}, () -> held.apply(server).addCollect(player, copy));
    }

    /**
     * Two steps: the box's rows are marked with an attempt and read; once {@code then} has put the items in the inventory the
     * rows are deleted (or, if that fails, deleted at the next refresh). A lost answer to the first step unmarks them, so they
     * stay in the box; marks no server finished go back after ten minutes ({@link #sweep}).
     */
    @Override
    public void takeCollect(MinecraftServer server, UUID player, Consumer<Optional<List<ItemStack>>> then) {
        String attempt = "t-" + UUID.randomUUID();
        runOr("empty a Collect box", () -> db.takeCollect(player, attempt), items -> {
            boxes.remove(player);
            then.accept(Optional.of(new ArrayList<>(items)));
            run("finish emptying a Collect box", () -> {
                db.taken(attempt);
                return true;
            }, ok -> {}, () -> held.apply(server).addDelete(attempt));
        }, untouched -> {
            then.accept(Optional.empty());
            if (!untouched) {
                run("put a Collect box back", () -> {
                    db.untake(attempt);
                    return true;
                }, ok -> {}, () -> {});
            }
        });
    }

    @Override
    public List<ItemStack> peekCollect(MinecraftServer server, UUID player) {
        List<ItemStack> out = new ArrayList<>();
        boxes.getOrDefault(player, List.of()).forEach(s -> out.add(s.copy()));
        MarketLedger ledger = held.apply(server);
        out.addAll(ledger.peekCollect(player));
        out.addAll(ledger.batchFor(player));
        return out;
    }
}
