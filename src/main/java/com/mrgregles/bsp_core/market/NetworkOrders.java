package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Buy orders on a network: the database holds them, and every placing, fill and closing is one transaction there
 * ({@link OrderDb}), so two sellers on two servers filling the same order at once never send more than it wants, and money
 * and items never move halfway. A fill's items go to the buyer's Collect box inside its transaction, and any it did not
 * need back to the seller's, so nothing is ever left in the caller's hands to lose with an answer. This server keeps a copy
 * of the open orders for its screens, re-read whole at start, on another server's notice and every minute (orders change
 * as they fill); after its own changes it updates the copy, the cached bank balances and the bank log, and announces it.
 *
 * <p>When the database cannot be reached nothing moves. When an answer is lost after the database may have changed, a
 * placing or closing is read back at the next refresh (the bank and the orders agree either way); a fill's items wait here
 * as unsure ({@link OrderLedger.Unsure}) until the database can say whether the fill went in, and go to the seller's
 * Collect box if it did not.
 */
public final class NetworkOrders implements OrderStore {
    private final OrderDb db;
    private final Executor worker;
    private final Consumer<Runnable> onServer;
    private final Runnable announce;
    private final Function<MinecraftServer, OrderLedger> held;
    /** A player's Collect box changed in the database (a fill sent them items): this server's copy and the other servers re-read it. */
    private final Consumer<UUID> boxChanged;
    /** Server thread only. */
    private List<BuyOrder> open = new ArrayList<>();
    private boolean refreshing, refreshAgain, settling;

    public NetworkOrders(OrderDb db, Executor worker, Consumer<Runnable> onServer, Runnable announce, Function<MinecraftServer, OrderLedger> held, Consumer<UUID> boxChanged) {
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
                BSPCore.LOGGER.error("BSP-Core orders: could not {}: {}", what, e.toString());
                boolean untouched = e instanceof MarketDb.Untouched;
                onServer.accept(() -> failed.accept(untouched));
                return;
            }
            onServer.accept(() -> done.accept(result));
        });
    }

    /**
     * The copy changed here: {@code now} the order as it stands, or null when it closed. A refresh under way cannot undo it: the
     * database calls run one at a time on the storage thread and their answers come back in order, so a read that answers after
     * this change was made after it too.
     */
    private void changed(String id, @Nullable BuyOrder now) {
        List<BuyOrder> next = new ArrayList<>(open.size() + 1);
        boolean placed = false;
        for (BuyOrder o : open) {
            if (!o.id.equals(id)) {
                next.add(o);
            } else if (now != null) {
                next.add(now);
                placed = true;
            }
        }
        if (now != null && !placed) {
            next.add(0, now);
        }
        open = next;
    }

    // ------------------------------------------------------------------ the copy

    /** Re-reads every open order, after first settling any fill whose answer was lost. A refresh asked for while one reads runs once afterwards. */
    public void refresh(MinecraftServer server) {
        settle(server);
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        run("read the buy orders", db::orders, read -> {
            List<BuyOrder> next = new ArrayList<>(read);
            next.sort(java.util.Comparator.comparingLong((BuyOrder o) -> o.placedMs).reversed().thenComparing(o -> o.id));
            open = next;
            refreshDone(server);
        }, untouched -> refreshDone(server));
    }

    private void refreshDone(MinecraftServer server) {
        refreshing = false;
        if (refreshAgain) {
            refreshAgain = false;
            refresh(server);
        }
    }

    /** Fills whose answer was lost: each settled once the database can say (the items to the seller's box unless the fill went in). */
    private void settle(MinecraftServer server) {
        OrderLedger ledger = held.apply(server);
        List<OrderLedger.Unsure> waiting = ledger.unsure();
        if (waiting.isEmpty() || settling) {
            return;
        }
        settling = true;
        run("settle the fills whose answer was lost", () -> {
            List<String> done = new ArrayList<>();
            for (OrderLedger.Unsure u : waiting) {
                db.settleFill(u.attempt(), u.seller(), u.items());
                done.add(u.attempt());
            }
            return done;
        }, done -> {
            settling = false;
            done.forEach(ledger::dropUnsure);
            waiting.forEach(u -> boxChanged.accept(u.seller()));
            BSPCore.LOGGER.info("BSP-Core orders: settled {} fill(s) whose answer was lost", done.size());
        }, untouched -> settling = false);
    }

    // ------------------------------------------------------------------ the store

    @Override
    public void place(MinecraftServer server, BuyOrder order, int maxOrders, Consumer<Answer> then) {
        run("place a buy order", () -> db.place(order, maxOrders), d -> {
            if (!d.ok()) {
                then.accept(Answer.no(d.key()));
                return;
            }
            changed(order.id, order);
            Bank.changedInDatabase(server, order.buyer, d.pieces(), BankTransaction.Type.MARKET, "Market",
                    "order placed: " + order.describe() + " at " + Bank.format(order.each) + " each", -order.held());
            announce.run();
            then.accept(new Answer("ok", order, 0, 0, 0, 0, 0, false));
        }, untouched -> {
            if (untouched) {
                then.accept(Answer.no("unreachable"));
                return;
            }
            // placed or not, the bank and the orders agree: ask, and read both again
            run("ask whether a buy order went in", () -> db.committed(order.id), in -> {
                Bank.refresh(server, order.buyer);
                refresh(server);
                if (in) {
                    changed(order.id, order);
                    announce.run();
                    then.accept(new Answer("ok", order, 0, 0, 0, 0, 0, false));
                } else {
                    then.accept(Answer.no("unreachable"));
                }
            }, u -> {
                Bank.refresh(server, order.buyer);
                refresh(server);
                then.accept(Answer.no("unsure"));
            });
        });
    }

    @Override
    public List<BuyOrder> open(MinecraftServer server) {
        return new ArrayList<>(open);
    }

    @Override
    public void fill(MinecraftServer server, BuyOrder seen, UUID seller, String sellerName, int count, Consumer<Answer> then) {
        String attempt = "o-" + UUID.randomUUID();
        run("fill a buy order", () -> db.fill(seen.id, seller, count, attempt, Market.now()), d -> {
            if (!d.ok()) {
                if (d.key().equals("gone")) {
                    changed(seen.id, null);
                }
                then.accept(Answer.no(d.key()));
                return;
            }
            BuyOrder after = d.order();
            changed(after.id, d.closed() ? null : after);
            Bank.changedInDatabase(server, seller, d.pieces(), BankTransaction.Type.MARKET, after.buyerName,
                    "filled an order: " + d.sent() + " " + after.item().getHoverName().getString() + " (tax " + Bank.format(d.tax()) + ")", d.paid());
            if (d.tax() > 0) {
                Bank.changedInDatabase(server, Market.POT, d.potPieces(), BankTransaction.Type.TAX, sellerName, "order tax, order " + after.id, d.tax());
            }
            boxChanged.accept(after.buyer);
            if (d.sent() < count) {
                boxChanged.accept(seller); // what the order did not need went back into the seller's box
            }
            announce.run();
            then.accept(new Answer("ok", after, d.sent(), 0, d.paid(), d.tax(), 0, d.closed()));
        }, untouched -> {
            if (untouched) {
                then.accept(Answer.no("unreachable"));
                return;
            }
            // the fill may have gone in: the items wait here until the database can say, and go to the seller's box if it did not
            held.apply(server).addUnsure(new OrderLedger.Unsure(attempt, seller, BuyOrders.stacks(seen.item(), count)));
            BSPCore.LOGGER.warn("BSP-Core orders: the answer to {}'s fill of order {} ({} items) was lost; the items wait here until the database can say", sellerName, seen.id, count);
            Bank.refresh(server, seller);
            refresh(server);
            then.accept(Answer.no("unsure"));
        });
    }

    @Override
    public void close(MinecraftServer server, String id, Consumer<Answer> then) {
        UUID buyer = open.stream().filter(o -> o.id.equals(id)).map(o -> o.buyer).findFirst().orElse(null);
        run("close a buy order", () -> db.close(id), d -> {
            if (!d.ok()) {
                if (d.key().equals("gone")) {
                    changed(id, null);
                }
                then.accept(Answer.no(d.key())); // "buyer": the order stays open in the database, and here
                return;
            }
            changed(id, null);
            BuyOrder o = d.order();
            if (d.refund() > 0) {
                Bank.changedInDatabase(server, o.buyer, d.pieces(), BankTransaction.Type.MARKET, "Market",
                        "order closed: " + o.toGo() + " " + o.item().getHoverName().getString() + " not sent", d.refund());
            }
            announce.run();
            then.accept(new Answer("ok", o, 0, 0, 0, 0, d.refund(), false));
        }, untouched -> {
            if (!untouched) {
                // closed or not, the bank and the orders agree: read both again
                if (buyer != null) {
                    Bank.refresh(server, buyer);
                }
                refresh(server);
            }
            then.accept(Answer.no(untouched ? "unreachable" : "unsure"));
        });
    }
}
