package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /bsp shop report [days] [shop]} (spawn shops request SS-11): from the bank log's BOUGHT and
 * SOLD rows, per shop and per item: pieces paid out to players (their sales), pieces taken in (their
 * purchases), the net faucet (out minus in), items moved, and coins apart; the busiest cards; the
 * players who sold most. On a network the rows come from MySQL (every server); alone, from this
 * server's log. The full table goes to a CSV beside the import files.
 */
public final class ShopReport {
    private ShopReport() {}

    /** One logged trade and whose it was. */
    public record Row(UUID player, String name, BankTransaction tx) {}

    /** One shop and item's figures (shop "" with a name: rows from before rows named the shop). */
    public static final class Line {
        public final String shop, shopName, item;
        public long out, in, itemsSold, itemsBought;
        public final long[] coinsOut = new long[5], coinsIn = new long[5];

        Line(String shop, String shopName, String item) {
            this.shop = shop;
            this.shopName = shopName;
            this.item = item;
        }

        public long net() {
            return out - in;
        }
    }

    public record Report(int days, boolean capped, List<Line> shops, List<Line> items, List<Map.Entry<String, Long>> sellers, Line total, int rows, String file) {}

    /** The rows since {@code since}: MySQL's on a network, this server's log alone; {@code then} gets null when the database could not be read. */
    public static void rows(MinecraftServer server, long since, Consumer<List<Row>> then) {
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            com.mrgregles.bsp_core.storage.NetworkStorage.shopTrades(since, then);
            return;
        }
        List<Row> out = new ArrayList<>();
        var ledger = com.mrgregles.bsp_core.bank.BankLedger.get(server);
        for (var e : ledger.all().entrySet()) {
            for (BankTransaction tx : ledger.log(e.getKey())) {
                if (tx.ms() >= since && (tx.type() == BankTransaction.Type.BOUGHT || tx.type() == BankTransaction.Type.SOLD)) {
                    out.add(new Row(e.getKey(), e.getValue().name, tx));
                }
            }
        }
        then.accept(out);
    }

    /** The figures from the rows, for one shop (its id; older rows by its name) or all ({@code shop} null). */
    public static Report build(MinecraftServer server, List<Row> rows, int days, boolean capped, @Nullable Shop shop) {
        Map<String, Line> byShop = new LinkedHashMap<>(), byItem = new LinkedHashMap<>();
        Map<String, Long> sold = new HashMap<>();
        Line total = new Line("", "all shops", "");
        int counted = 0;
        for (Row r : rows) {
            BankTransaction tx = r.tx();
            if (shop != null && !(tx.shopId().equals(shop.id) || tx.shopId().isEmpty() && tx.with().equals(shop.name))) {
                continue;
            }
            counted++;
            String shopKey = tx.shopId().isEmpty() ? "name:" + tx.with() : tx.shopId();
            Shop now = tx.shopId().isEmpty() ? null : Shops.ledger(server).shop(tx.shopId());
            String shopName = now != null ? now.name : tx.shopId().isEmpty() ? tx.with() + " (older rows)" : tx.with();
            String item = tx.itemId().isEmpty() ? itemText(tx.detail()) : tx.itemId();
            Line s = byShop.computeIfAbsent(shopKey, k -> new Line(tx.shopId(), shopName, ""));
            Line i = byItem.computeIfAbsent(shopKey + "|" + item, k -> new Line(tx.shopId(), shopName, item));
            for (Line l : List.of(s, i, total)) {
                add(l, tx);
            }
            if (tx.type() == BankTransaction.Type.SOLD && tx.pieces() > 0) {
                sold.merge(r.name().isEmpty() ? r.player().toString().substring(0, 8) : r.name(), tx.pieces(), Long::sum);
            }
        }
        List<Line> shops = new ArrayList<>(byShop.values());
        shops.sort(Comparator.comparingLong((Line l) -> l.out + l.in).reversed());
        List<Line> items = new ArrayList<>(byItem.values());
        items.sort(Comparator.comparingLong((Line l) -> l.out + l.in).reversed());
        List<Map.Entry<String, Long>> sellers = new ArrayList<>(sold.entrySet());
        sellers.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        String file = csv(days, shop, items, total);
        return new Report(days, capped, shops, items, sellers.subList(0, Math.min(10, sellers.size())), total, counted, file);
    }

    private static void add(Line l, BankTransaction tx) {
        long items = leadingCount(tx.detail());
        if (tx.type() == BankTransaction.Type.SOLD) {
            l.out += Math.max(0, tx.pieces());
            l.itemsSold += items;
            if (tx.coinTier() >= 0 && tx.coinTier() < 5) {
                l.coinsOut[tx.coinTier()] += Math.max(0, tx.coinCount());
            }
        } else {
            l.in += Math.max(0, -tx.pieces());
            l.itemsBought += items;
            if (tx.coinTier() >= 0 && tx.coinTier() < 5) {
                l.coinsIn[tx.coinTier()] += Math.max(0, -tx.coinCount());
            }
        }
    }

    /** "64 Oak Planks" (or "64 Oak Planks, 3 Gold coins") → 64. */
    public static long leadingCount(String detail) {
        int end = 0;
        while (end < detail.length() && Character.isDigit(detail.charAt(end))) {
            end++;
        }
        try {
            return end == 0 ? 0 : Long.parseLong(detail.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** An older row's item: its detail without the count and any coin price ("64 Oak Planks, 3 Gold coins" → "Oak Planks"). */
    public static String itemText(String detail) {
        String s = detail.replaceFirst("^\\d+\\s*", "");
        int comma = s.lastIndexOf(", ");
        return comma > 0 && s.endsWith(" coins") ? s.substring(0, comma) : s;
    }

    /** Writes the per-item table to config/bsp_core/shops/report-<days>d-<date>[-shop].csv; returns its name, or "" when it could not. */
    private static String csv(int days, @Nullable Shop shop, List<Line> items, Line total) {
        String name = "report-" + days + "d-" + LocalDate.now(Shops.zone()) + (shop == null ? "" : "-" + shop.id) + ".csv";
        List<String> lines = new ArrayList<>();
        lines.add("shop,shop_name,item,items_bought,items_sold,pieces_in,pieces_out,net_out,coins_in,coins_out");
        for (Line l : items) {
            lines.add(String.join(",", q(l.shop), q(l.shopName), q(l.item), "" + l.itemsBought, "" + l.itemsSold, "" + l.in, "" + l.out, "" + l.net(), q(coins(l.coinsIn)), q(coins(l.coinsOut))));
        }
        lines.add(String.join(",", q(""), q("all shops"), q(""), "" + total.itemsBought, "" + total.itemsSold, "" + total.in, "" + total.out, "" + total.net(), q(coins(total.coinsIn)), q(coins(total.coinsOut))));
        Path path = ShopImport.folder().resolve(name);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
            return name;
        } catch (IOException e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core shops: could not write the report {}: {}", path, e.toString());
            return "";
        }
    }

    /** "3 copper 1 gold", or "" with none. */
    public static String coins(long[] c) {
        StringBuilder b = new StringBuilder();
        var tiers = com.mrgregles.bsp_core.coin.CoinTier.values();
        for (int t = 0; t < c.length && t < tiers.length; t++) {
            if (c[t] > 0) {
                b.append(b.length() > 0 ? " " : "").append(c[t]).append(' ').append(tiers[t].key);
            }
        }
        return b.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** The days asked for, kept within the bank log's own keeping time. */
    public static int days(int asked) {
        return Math.max(1, Math.min(asked, BSPConfig.getOr(BSPConfig.BANK_LOG_DAYS, 90)));
    }

    /** "12,000". */
    public static String n(long v) {
        return Bank.format(v);
    }
}
