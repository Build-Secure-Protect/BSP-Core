package com.mrgregles.bsp_core.shop;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /bsp shop export <id|all> <file> [overwrite]} (spawn shops request SS-1): writes shops to
 * {@code config/bsp_core/shops/<file>.json} in the import's format 1, every key written, so a shop
 * changed in the Workbench can be saved, edited and imported back. One shop comes with its
 * sub-sections (their cards point at them). Counts and draws are never written: only the shops as
 * set up. An existing file is kept unless {@code overwrite} is given.
 */
public final class ShopExport {
    private ShopExport() {}

    /** What an export did: the message key ("exported", "no_shop", "bad_name", "exists", "unwritable"), the shops and cards written, and the file's name. */
    public record Result(String key, int shops, int cards, String file) {
        public boolean ok() {
            return key.equals("exported");
        }
    }

    public static Result run(MinecraftServer server, String which, String name, boolean overwrite) {
        Path path = ShopImport.file(name);
        if (path == null) {
            return new Result("bad_name", 0, 0, name);
        }
        List<Shop> shops = shops(Shops.ledger(server), which);
        if (shops.isEmpty()) {
            return new Result("no_shop", 0, 0, which);
        }
        if (Files.exists(path) && !overwrite) {
            return new Result("exists", 0, 0, path.getFileName().toString());
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(write(shops)) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core shops: could not write the export {}: {}", path, e.toString());
            return new Result("unwritable", 0, 0, path.getFileName().toString());
        }
        return new Result("exported", shops.size(), shops.stream().mapToInt(s -> s.all().size()).sum(), path.getFileName().toString());
    }

    /** "all", or one shop with its sub-sections (in the ledger's order, parents first). */
    static List<Shop> shops(ShopLedger ledger, String which) {
        List<Shop> out = new ArrayList<>();
        if (which.equalsIgnoreCase("all")) {
            out.addAll(ledger.all().values());
            return out;
        }
        Shop top = ledger.shop(which.toLowerCase(Locale.ROOT));
        if (top == null) {
            return out;
        }
        out.add(top);
        for (int i = 0; i < out.size(); i++) { // breadth first: each shop's children after it
            String parent = out.get(i).id;
            for (Shop s : ledger.all().values()) {
                if (s.parent.equals(parent) && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The file's JSON: format 1, with allowCoinPayouts when a card pays out coins (so it imports back). */
    public static JsonObject write(List<Shop> shops) {
        JsonObject root = new JsonObject();
        root.addProperty("format", ShopImport.FORMAT);
        boolean coins = shops.stream().flatMap(s -> s.all().stream()).anyMatch(it -> it.sell >= 0 && it.currency >= 0 && it.section.isEmpty());
        if (coins) {
            root.addProperty("allowCoinPayouts", true);
        }
        JsonArray list = new JsonArray();
        shops.forEach(s -> list.add(shop(s)));
        root.add("shops", list);
        return root;
    }

    private static JsonObject shop(Shop s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id);
        o.addProperty("name", s.name);
        o.addProperty("kind", s.kind.name().toLowerCase(Locale.ROOT));
        o.addProperty("parent", s.parent);
        o.addProperty("remote", s.remote);
        o.addProperty("buyOnly", s.buyOnly);
        o.addProperty("style", s.style);
        o.addProperty("columns", s.columns);
        o.addProperty("rows", s.rows);
        o.add("hours", hours(s.hours));
        o.addProperty("displaySlots", s.displaySlots);
        o.addProperty("intervalHours", Math.max(1, s.intervalMs / 3_600_000L));
        if (s.rotateAt.fixedTime()) {
            o.add("rotateAt", rule(s.rotateAt));
        }
        o.addProperty("noRepeat", s.noRepeat);
        o.addProperty("broadcast", s.broadcast);
        if (s.requires.any()) {
            o.add("requires", requires(s.requires));
        }
        o.addProperty("offerPercent", s.offerPercent);
        o.addProperty("offerEnd", s.offerEndMs == 0 ? "" : LocalDateTime.ofInstant(Instant.ofEpochMilli(s.offerEndMs), Shops.zone()).truncatedTo(ChronoUnit.MINUTES).toString());
        JsonArray pages = new JsonArray();
        for (List<ShopItem> page : s.pages) {
            JsonArray cards = new JsonArray();
            page.forEach(it -> cards.add(card(it)));
            pages.add(cards);
        }
        o.add("pages", pages);
        return o;
    }

    private static JsonObject card(ShopItem it) {
        JsonObject o = new JsonObject();
        var key = ForgeRegistries.ITEMS.getKey(it.stack.getItem());
        o.addProperty("item", key == null ? "minecraft:air" : key.toString());
        if (!it.section.isEmpty()) {
            o.addProperty("section", it.section); // a section card: its item is only the picture
            return o;
        }
        o.addProperty("count", it.stack.getCount());
        if (it.stack.getTag() != null && !it.stack.getTag().isEmpty()) {
            o.addProperty("nbt", it.stack.getTag().toString());
        }
        o.addProperty("buy", it.buy);
        o.addProperty("sell", it.sell);
        o.addProperty("currency", it.currency < 0 ? "pieces" : com.mrgregles.bsp_core.coin.CoinTier.values()[it.currency].name().toLowerCase(Locale.ROOT));
        o.addProperty("stockGlobal", it.stockGlobal);
        o.addProperty("stockPlayer", it.stockPlayer);
        o.add("stockRefill", rule(it.stockRefill));
        o.add("stockPlayerRefill", rule(it.stockPlayerRefill));
        o.addProperty("sellGlobal", it.sellGlobal);
        o.addProperty("sellPlayer", it.sellPlayer);
        o.add("sellRefill", rule(it.sellRefill));
        o.add("sellPlayerRefill", rule(it.sellPlayerRefill));
        o.addProperty("instant", it.instant);
        o.addProperty("hidden", it.hidden);
        o.addProperty("match", it.matchItem ? "item" : "exact");
        o.addProperty("weight", it.weight);
        JsonArray lore = new JsonArray();
        it.note.forEach(lore::add);
        o.add("lore", lore);
        if (it.requires.any()) {
            o.add("requires", requires(it.requires));
        }
        return o;
    }

    private static JsonObject requires(Requirement r) {
        JsonObject o = new JsonObject();
        if (!r.permission().isEmpty()) {
            o.addProperty("permission", r.permission());
        }
        if (r.playtimeHours() > 0) {
            o.addProperty("playtimeHours", r.playtimeHours());
        }
        o.addProperty("whenLocked", r.hide() ? "hide" : "show");
        if (!r.reason().isEmpty()) {
            o.addProperty("reason", r.reason());
        }
        return o;
    }

    /** A rule in the object form: {"after": minutes}, {"every": "day"|weekday, "at": "HH:MM"}; "rotation", "same" and "never" as text. */
    static JsonElement rule(Refill r) {
        return switch (r.kind()) {
            case AFTER -> {
                JsonObject o = new JsonObject();
                o.addProperty("after", r.minutes());
                yield o;
            }
            case DAILY, WEEKLY -> {
                JsonObject o = new JsonObject();
                o.addProperty("every", r.kind() == Refill.Kind.DAILY ? "day" : r.day().name().toLowerCase(Locale.ROOT));
                o.addProperty("at", r.clock());
                yield o;
            }
            default -> new JsonPrimitive(r.text());
        };
    }

    @Nullable
    private static JsonObject hours(OpenHours h) {
        JsonObject o = new JsonObject();
        o.addProperty("mode", switch (h.mode()) {
            case ALWAYS -> "always";
            case REAL -> "real";
            case GAME -> "game";
        });
        if (h.mode() == OpenHours.Mode.REAL) {
            o.addProperty("open", String.format(Locale.ROOT, "%02d:%02d", h.openMin() / 60, h.openMin() % 60));
            o.addProperty("close", String.format(Locale.ROOT, "%02d:%02d", h.closeMin() / 60, h.closeMin() % 60));
        } else if (h.mode() == OpenHours.Mode.GAME) {
            o.addProperty("open", h.openMin());
            o.addProperty("close", h.closeMin());
        }
        return o;
    }
}
