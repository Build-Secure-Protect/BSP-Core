package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The marketplace on a single server: this world's {@link MarketLedger} and the local bank. Everything runs at once on the
 * server thread, so each call is whole by itself; a purchase takes the lot off before the money moves and puts it back if
 * the payment fails. {@code new LocalMarket(new MarketLedger())} is a blank market for GameTests.
 */
public class LocalMarket implements MarketStore {
    @Nullable
    private final MarketLedger fixed;

    /** The world's ledger with null, or the given one. */
    public LocalMarket(@Nullable MarketLedger fixed) {
        this.fixed = fixed;
    }

    protected MarketLedger ledger(MinecraftServer server) {
        return fixed != null ? fixed : MarketLedger.get(server);
    }

    @Override
    public void list(MinecraftServer server, MarketLot lot, long tax, Consumer<String> then) {
        Runnable add = () -> {
            ledger(server).put(lot);
            toPot(server, tax, lot.sellerName, "listing tax, lot " + lot.id);
            then.accept("ok");
        };
        if (tax <= 0) {
            add.run();
            return;
        }
        Bank.take(server, lot.seller, tax, BankTransaction.Type.TAX, "Market", "listing tax: " + lot.describe(), r -> {
            if (r.ok()) {
                add.run();
            } else {
                then.accept(r.key());
            }
        });
    }

    @Override
    public List<MarketLot> open(MinecraftServer server) {
        return ledger(server).open();
    }

    @Override
    public void buy(MinecraftServer server, String id, UUID buyer, String buyerName, long tax, Consumer<Answer> then) {
        MarketLedger ledger = ledger(server);
        MarketLot lot = ledger.remove(id);
        if (lot == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        Bank.take(server, buyer, lot.price + tax, BankTransaction.Type.MARKET, lot.sellerName, "bought " + lot.describe() + " (tax " + Bank.format(tax) + ")", r -> {
            if (!r.ok()) {
                ledger.put(lot);
                then.accept(new Answer(r.key(), null));
                return;
            }
            Bank.give(server, lot.seller, lot.price, BankTransaction.Type.MARKET, buyerName, "sold " + lot.describe(), x -> {});
            toPot(server, tax, buyerName, "purchase tax, lot " + lot.id);
            then.accept(new Answer("ok", lot));
        });
    }

    @Override
    public void close(MinecraftServer server, String id, Consumer<Answer> then) {
        MarketLedger ledger = ledger(server);
        MarketLot lot = ledger.remove(id);
        if (lot == null) {
            then.accept(new Answer("gone", null));
            return;
        }
        ledger.addCollect(lot.seller, lot.items());
        then.accept(new Answer("ok", lot));
    }

    @Override
    public void addCollect(MinecraftServer server, UUID player, List<ItemStack> items) {
        ledger(server).addCollect(player, items);
    }

    @Override
    public void takeCollect(MinecraftServer server, UUID player, Consumer<Optional<List<ItemStack>>> then) {
        then.accept(Optional.of(ledger(server).takeCollect(player)));
    }

    @Override
    public List<ItemStack> peekCollect(MinecraftServer server, UUID player) {
        return ledger(server).peekCollect(player);
    }

    private static void toPot(MinecraftServer server, long tax, String from, String detail) {
        Market.toPot(server, tax, from, detail);
    }
}
