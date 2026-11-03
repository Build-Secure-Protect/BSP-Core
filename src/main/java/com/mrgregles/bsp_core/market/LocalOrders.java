package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Buy orders on a single server: this world's {@link OrderLedger}, the local bank and the market's Collect boxes. Everything
 * runs at once on the server thread, so each call is whole by itself; a fill changes the order before the money moves and
 * puts it back if the payment fails. {@code new LocalOrders(new OrderLedger())} is a blank one for GameTests.
 */
public class LocalOrders implements OrderStore {
    @Nullable
    private final OrderLedger fixed;

    /** The world's ledger with null, or the given one. */
    public LocalOrders(@Nullable OrderLedger fixed) {
        this.fixed = fixed;
    }

    protected OrderLedger ledger(MinecraftServer server) {
        return fixed != null ? fixed : OrderLedger.get(server);
    }

    @Override
    public void place(MinecraftServer server, BuyOrder order, int maxOrders, Consumer<Answer> then) {
        OrderLedger ledger = ledger(server);
        if (ledger.open().stream().filter(o -> o.buyer.equals(order.buyer)).count() >= maxOrders) {
            then.accept(Answer.no("orders"));
            return;
        }
        Bank.take(server, order.buyer, order.held(), BankTransaction.Type.MARKET, "Market", "order placed: " + order.describe() + " at " + Bank.format(order.each) + " each", r -> {
            if (!r.ok()) {
                then.accept(Answer.no(r.key()));
                return;
            }
            ledger.put(order);
            then.accept(new Answer("ok", order, 0, 0, 0, 0, 0, false));
        });
    }

    @Override
    public List<BuyOrder> open(MinecraftServer server) {
        return ledger(server).open();
    }

    @Override
    public void fill(MinecraftServer server, BuyOrder seen, UUID seller, String sellerName, int count, Consumer<Answer> then) {
        OrderLedger ledger = ledger(server);
        BuyOrder o = ledger.get(seen.id);
        if (o == null || o.endedBy(Market.now()) || o.toGo() <= 0) {
            then.accept(Answer.no("gone"));
            return;
        }
        if (o.buyer.equals(seller)) {
            then.accept(Answer.no("own"));
            return;
        }
        int n = Math.min(count, o.toGo());
        long pay = n * o.each, tax = BuyOrders.tax(pay);
        BuyOrder after = o.filledBy(n);
        boolean closed = after.toGo() <= 0;
        if (closed) {
            ledger.remove(o.id);
        } else {
            ledger.put(after);
        }
        Bank.give(server, seller, pay - tax, BankTransaction.Type.MARKET, o.buyerName, "filled an order: " + n + " " + o.item().getHoverName().getString()
                + " (tax " + Bank.format(tax) + ")", r -> {
            if (!r.ok()) {
                ledger.put(o); // nobody was paid: the order is as it was
                then.accept(Answer.no(r.key()));
                return;
            }
            Market.toPot(server, tax, sellerName, "order tax, order " + o.id);
            Market.store(server).addCollect(server, o.buyer, BuyOrders.stacks(o.item(), n));
            then.accept(new Answer("ok", after, n, count - n, pay - tax, tax, 0, closed));
        });
    }

    @Override
    public void close(MinecraftServer server, String id, Consumer<Answer> then) {
        OrderLedger ledger = ledger(server);
        BuyOrder o = ledger.remove(id);
        if (o == null) {
            then.accept(Answer.no("gone"));
            return;
        }
        long refund = o.held();
        if (refund <= 0) {
            then.accept(new Answer("ok", o, 0, 0, 0, 0, 0, false));
            return;
        }
        Bank.give(server, o.buyer, refund, BankTransaction.Type.MARKET, "Market", "order closed: " + o.toGo() + " " + o.item().getHoverName().getString() + " not sent", r -> {
            if (!r.ok()) {
                ledger.put(o); // the buyer was not paid back: the order stays open, and its pieces with it
                then.accept(Answer.no(r.key()));
                return;
            }
            then.accept(new Answer("ok", o, 0, 0, 0, 0, refund, false));
        });
    }
}
