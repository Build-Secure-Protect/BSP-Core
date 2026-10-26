package com.mrgregles.bsp_core.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /bsp shop import <file> [test]} (spawn shops request SS-1): shops written as JSON (format 1)
 * in the server's {@code config/bsp_core/shops/}, checked in full first. Any error refuses the whole
 * file and nothing changes; otherwise every shop in it replaces the shop of that id whole (its posts
 * and stalls keep it, its counts and draw start again) and shops not in the file are untouched. The
 * report goes to chat in short and to {@code <file>-report.txt} beside the file in full. {@code test}
 * checks and reports without changing anything.
 */
public final class ShopImport {
    private ShopImport() {}

    public static final int FORMAT = 1;
    private static final Set<String> SHOP_KEYS = Set.of("id", "name", "kind", "parent", "remote", "buyOnly", "style", "columns", "rows", "displaySlots", "intervalHours",
            "rotateAt", "noRepeat", "broadcast", "offerPercent", "offerEnd", "hours", "pages", "requires", "note");
    private static final Set<String> CARD_KEYS = Set.of("item", "count", "nbt", "buy", "sell", "currency", "stockGlobal", "stockPlayer", "restockMinutes", "stockRefill",
            "stockPlayerRefill", "sellGlobal", "sellPlayer", "sellRefill", "sellPlayerRefill", "instant", "hidden", "section", "match", "weight", "lore", "requires", "note");
    private static final List<String> CURRENCIES = List.of("pieces", "copper", "gold", "diamond", "netherite", "illyrium");
    private static final List<String> STYLES = List.of("cash_machine", "passbook", "market_board");

    /** What an import did or would do: the shops (in file order), whether each replaces one, the problems, and where the full report is. */
    /** {@code kept}: replaced shops with the same cards in the same places, which keep their counts and draw. */
    public record Result(boolean read, List<Shop> shops, Set<String> replaced, Set<String> kept, List<String> errors, List<String> warnings, String reportFile) {
        public boolean ok() {
            return read && errors.isEmpty();
        }

        public int cards() {
            return shops.stream().mapToInt(s -> s.all().size()).sum();
        }
    }

    /** The folder import and export use: {@code config/bsp_core/shops/} of this server. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("bsp_core").resolve("shops");
    }

    /** A file name as typed: letters, digits, "_", "-" and ".", no path; ".json" is added when missing. Null when it is not one. */
    @Nullable
    public static Path file(String name) {
        String n = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        if (!n.matches("[A-Za-z0-9_.-]{1,64}") || n.contains("..") || n.startsWith(".")) {
            return null;
        }
        return folder().resolve(n + ".json");
    }

    /** Reads, checks and (unless {@code test} or anything is wrong) puts the file's shops in place. */
    public static Result run(MinecraftServer server, String name, boolean test) {
        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Path path = file(name);
        if (path == null) {
            errors.add("\"" + name + "\" is not a file name here: letters, digits, _ - and . only, no folders.");
            return new Result(false, List.of(), Set.of(), Set.of(), errors, warnings, "");
        }
        Path report = path.resolveSibling(path.getFileName().toString().replace(".json", "-report.txt"));
        JsonObject root;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(r);
            root = e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            errors.add(Files.exists(path) ? "The file is not readable JSON: " + e.getMessage() : "No file " + folder().relativize(path) + " in config/bsp_core/shops/.");
            return finish(server, test, new Result(false, List.of(), Set.of(), Set.of(), errors, warnings, Files.exists(path) ? write(report, path, test, List.of(), Set.of(), Set.of(), errors, warnings) : ""));
        }
        if (root == null) {
            errors.add("The file must hold one JSON object with \"format\" and \"shops\".");
            return finish(server, test, new Result(false, List.of(), Set.of(), Set.of(), errors, warnings, write(report, path, test, List.of(), Set.of(), Set.of(), errors, warnings)));
        }
        if (lng(root, "format", -1, "The top", errors) != FORMAT) {
            errors.add("\"format\" must be " + FORMAT + ".");
        }
        boolean coinPayouts = bool(root, "allowCoinPayouts", false, "The top", errors);
        for (String k : root.keySet()) {
            if (!Set.of("format", "shops", "allowCoinPayouts", "note").contains(k)) {
                warnings.add("Unknown key \"" + k + "\" at the top: ignored.");
            }
        }
        JsonArray shops = root.has("shops") && root.get("shops").isJsonArray() ? root.getAsJsonArray("shops") : null;
        if (shops == null) {
            errors.add("\"shops\" must be a list.");
            return finish(server, test, new Result(false, List.of(), Set.of(), Set.of(), errors, warnings, write(report, path, test, List.of(), Set.of(), Set.of(), errors, warnings)));
        }
        // the ids first, so sections and parents may name a shop further down the file
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement e : shops) {
            if (e.isJsonObject() && e.getAsJsonObject().has("id") && e.getAsJsonObject().get("id").isJsonPrimitive()) {
                ids.add(e.getAsJsonObject().get("id").getAsString());
            }
        }
        ShopLedger ledger = Shops.ledger(server);
        List<Shop> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(), replaced = new LinkedHashSet<>();
        int n = 0;
        for (JsonElement e : shops) {
            n++;
            if (!e.isJsonObject()) {
                errors.add("Shop " + n + ": not an object.");
                continue;
            }
            Shop shop;
            try {
                shop = readShop(e.getAsJsonObject(), n, ids, ledger, coinPayouts, seen, errors, warnings);
            } catch (RuntimeException ex) { // a value of a shape no check foresaw: reported, never thrown
                errors.add("Shop " + n + ": could not be read (" + ex.getMessage() + ").");
                shop = null;
            }
            if (shop != null) {
                out.add(shop);
                if (ledger.shop(shop.id) != null) {
                    replaced.add(shop.id);
                }
            }
        }
        // parents as they would be after the import: the file's over the server's; none may go round in a loop
        Map<String, String> parents = new HashMap<>();
        ledger.all().values().forEach(s -> parents.put(s.id, s.parent));
        out.forEach(s -> parents.put(s.id, s.parent));
        for (Shop s : out) {
            if (Shops.loops(s.id, s.parent, parents::get)) {
                errors.add("Shop " + s.id + ": its parents go round in a loop back to it (or more than " + Shops.MAX_DEPTH + " deep): " + s.id + " > " + s.parent + " > ...");
            }
        }
        Set<String> kept = new LinkedHashSet<>();
        for (Shop s : out) {
            Shop old = ledger.shop(s.id);
            if (old != null && sameCards(old, s)) {
                kept.add(s.id);
            }
        }
        return finish(server, test, new Result(true, out, replaced, kept, errors, warnings, write(report, path, test, out, replaced, kept, errors, warnings)));
    }

    /** True when a replaced shop keeps its cards: as many, each the same item, data, lot size and section in the same place (prices, notes and switches may change). */
    static boolean sameCards(Shop old, Shop now) {
        List<ShopItem> a = old.all(), b = now.all();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).sameCard(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Puts the shops in place when the file is clean and this is not a test. */
    private static Result finish(MinecraftServer server, boolean test, Result r) {
        if (test || !r.ok()) {
            return r;
        }
        for (Shop shop : r.shops()) {
            Shop old = Shops.ledger(server).shop(shop.id);
            if (old != null) {
                shop.edited = old.edited; // the new record's stamp goes on from the old one's
                if (old.rotateAt.equals(shop.rotateAt)) {
                    shop.rotateSince = old.rotateSince; // the change time as before: the draw changes when it would have
                }
            }
            Shops.save(server, shop);
            if (old != null && r.kept().contains(shop.id)) {
                // the same cards in the same places (owner's pick 14a): counts and draw stay; a card whose limits or rules changed starts its counts again
                for (int i = 0; i < shop.all().size(); i++) {
                    if (!old.item(i).countsAlike(shop.item(i))) {
                        Shops.resetCounts(server, shop, i, i);
                    }
                }
            } else if (old != null) {
                Shops.restart(server, shop); // cards changed: counts and draw start again
            }
            ShopOpener.resyncOpen(server, shop.id);
        }
        return r;
    }

    @Nullable
    private static Shop readShop(JsonObject o, int n, Set<String> ids, ShopLedger ledger, boolean coinPayouts, Set<String> seen, List<String> errors, List<String> warnings) {
        String id = str(o, "id", "");
        String where = "Shop " + n + (id.isEmpty() ? "" : " (" + id + ")");
        if (!id.matches("[a-z0-9_]{1,32}")) {
            errors.add(where + ": \"id\" must be 1 to 32 of a-z, 0-9 and _.");
            return null;
        }
        if (!seen.add(id)) {
            errors.add(where + ": the id is in the file twice.");
            return null;
        }
        for (String k : o.keySet()) {
            if (!SHOP_KEYS.contains(k)) {
                warnings.add(where + ": unknown key \"" + k + "\": ignored.");
            }
        }
        int before = errors.size();
        Shop s = new Shop(id);
        s.pages.clear();
        s.name = str(o, "name", id.replace('_', ' '));
        if (s.name.length() > Shop.NAME_MAX) {
            errors.add(where + ": \"name\" is " + s.name.length() + " characters; a shop's name holds " + Shop.NAME_MAX + ".");
        }
        try {
            s.kind = Shop.Kind.valueOf(str(o, "kind", "shop").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(where + ": \"kind\" must be shop, sub, rotating or offer.");
        }
        s.parent = str(o, "parent", "");
        if (s.kind == Shop.Kind.SUB && s.parent.isEmpty()) {
            errors.add(where + ": a sub-shop needs a \"parent\".");
        } else if (!s.parent.isEmpty() && !ids.contains(s.parent) && ledger.shop(s.parent) == null) {
            errors.add(where + ": its parent \"" + s.parent + "\" is neither in the file nor on the server.");
        }
        s.remote = bool(o, "remote", false, where, errors);
        s.buyOnly = bool(o, "buyOnly", false, where, errors);
        s.style = str(o, "style", "cash_machine").toLowerCase(Locale.ROOT);
        if (!STYLES.contains(s.style)) {
            errors.add(where + ": \"style\" must be cash_machine, passbook or market_board.");
        }
        s.columns = num(o, "columns", 0, where, errors);
        if (s.columns != 0 && (s.columns < Shop.MIN_COLUMNS || s.columns > Shop.MAX_COLUMNS)) {
            errors.add(where + ": \"columns\" must be 0 (automatic) or 4 to 7.");
        }
        s.rows = num(o, "rows", 3, where, errors);
        if (s.rows < 2 || s.rows > 3) {
            errors.add(where + ": \"rows\" must be 2 or 3.");
        }
        s.displaySlots = Math.max(1, num(o, "displaySlots", 4, where, errors));
        s.intervalMs = Math.max(1, num(o, "intervalHours", 12, where, errors)) * 3_600_000L;
        if (o.has("rotateAt")) {
            Refill at = rule(o.get("rotateAt"));
            if (at == null || !at.fixedTime()) {
                errors.add(where + ": \"rotateAt\" must be a day or weekday time, such as {\"every\": \"monday\", \"at\": \"00:00\"}.");
            } else {
                s.rotateAt = at;
                s.rotateSince = Shops.now();
            }
        }
        s.noRepeat = bool(o, "noRepeat", false, where, errors);
        s.requires = requires(o, where, errors);
        s.broadcast = bool(o, "broadcast", false, where, errors);
        s.offerPercent = Math.max(0, Math.min(99, num(o, "offerPercent", 0, where, errors)));
        String end = str(o, "offerEnd", "");
        if (!end.isEmpty()) {
            try {
                s.offerEndMs = LocalDateTime.parse(end).atZone(Shops.zone()).toInstant().toEpochMilli();
            } catch (RuntimeException ex) {
                errors.add(where + ": \"offerEnd\" must be a date and time such as 2026-12-31T23:59, or empty.");
            }
        }
        if (o.has("hours")) {
            OpenHours h = hours(o.get("hours"));
            if (h == null) {
                errors.add(where + ": \"hours\" must be {\"mode\": \"always\"}, {\"mode\": \"real\", \"open\": \"09:00\", \"close\": \"21:00\"} or {\"mode\": \"game\", \"open\": 0, \"close\": 12000}.");
            } else {
                s.hours = h;
            }
        }
        JsonArray pages = o.has("pages") && o.get("pages").isJsonArray() ? o.getAsJsonArray("pages") : new JsonArray();
        if (o.has("pages") && !o.get("pages").isJsonArray()) {
            errors.add(where + ": \"pages\" must be a list of pages.");
        }
        Map<String, Integer> sellable = new HashMap<>();
        int p = 0;
        for (JsonElement pe : pages) {
            p++;
            if (!pe.isJsonArray()) {
                errors.add(where + ", page " + p + ": not a list of cards.");
                continue;
            }
            if (pe.getAsJsonArray().size() > Shop.PER_PAGE) {
                errors.add(where + ", page " + p + ": " + pe.getAsJsonArray().size() + " cards; a page holds " + Shop.PER_PAGE + ".");
            }
            List<ShopItem> page = new ArrayList<>();
            int c = 0;
            for (JsonElement ce : pe.getAsJsonArray()) {
                c++;
                String at = where + ", page " + p + " card " + c;
                if (!ce.isJsonObject()) {
                    errors.add(at + ": not an object.");
                    continue;
                }
                ShopItem it = readCard(ce.getAsJsonObject(), at, s, ids, ledger, coinPayouts, errors, warnings);
                if (it != null) {
                    page.add(it);
                    if (it.sellable() && !it.matchItem) {
                        String key = it.stack.getItem() + "|" + it.stack.getTag();
                        if (sellable.containsKey(key)) {
                            warnings.add(at + ": the same item as card " + sellable.get(key) + " is sold to this shop twice; the first card takes it in Sell all.");
                        } else {
                            sellable.put(key, s.all().size() + page.size());
                        }
                    }
                }
            }
            s.pages.add(page);
        }
        if (s.pages.isEmpty()) {
            s.pages.add(new ArrayList<>());
        }
        return errors.size() == before ? s : null;
    }

    @Nullable
    private static ShopItem readCard(JsonObject o, String at, Shop shop, Set<String> ids, ShopLedger ledger, boolean coinPayouts, List<String> errors, List<String> warnings) {
        for (String k : o.keySet()) {
            if (!CARD_KEYS.contains(k)) {
                warnings.add(at + ": unknown key \"" + k + "\": ignored.");
            }
        }
        int before = errors.size();
        ShopItem it = new ShopItem();
        String itemId = str(o, "item", "");
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        Item item = rl == null || !ForgeRegistries.ITEMS.containsKey(rl) ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            errors.add(at + ": unknown item \"" + itemId + "\"" + (rl == null ? "" : " (is the mod \"" + rl.getNamespace() + "\" on this server?)") + ".");
            return null;
        }
        int count = num(o, "count", 1, at, errors);
        it.stack = new ItemStack(item, Math.max(1, count));
        if (count < 1 || count > item.getMaxStackSize(it.stack)) {
            errors.add(at + ": \"count\" must be 1 to " + item.getMaxStackSize(it.stack) + " for " + itemId + ".");
        }
        if (o.has("nbt")) {
            try {
                CompoundTag tag = TagParser.parseTag(o.get("nbt").getAsString());
                it.stack.setTag(tag.isEmpty() ? null : tag);
            } catch (Exception ex) {
                errors.add(at + ": \"nbt\" is not readable: " + ex.getMessage());
            }
        }
        if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(it.stack)) {
            errors.add(at + ": a placeholder for a missing mod's item; shops never take or sell one.");
        }
        it.section = str(o, "section", "").toLowerCase(Locale.ROOT);
        if (!it.section.isEmpty()) {
            if (!ids.contains(it.section) && ledger.shop(it.section) == null) {
                errors.add(at + ": its section \"" + it.section + "\" is neither in the file nor on the server.");
            }
            List<String> unused = o.keySet().stream().filter(k -> CARD_KEYS.contains(k) && !Set.of("item", "count", "nbt", "section", "note").contains(k)).toList();
            if (!unused.isEmpty()) {
                warnings.add(at + ": a section card only opens its section, so " + String.join(", ", unused) + " " + (unused.size() == 1 ? "is" : "are") + " ignored.");
            }
            return errors.size() == before ? it : null; // a section card only opens the sub-shop: its item is the picture
        }
        it.buy = lng(o, "buy", -1, at, errors);
        it.sell = lng(o, "sell", -1, at, errors);
        String currency = str(o, "currency", "pieces").toLowerCase(Locale.ROOT);
        it.currency = CURRENCIES.indexOf(currency) - 1;
        if (!CURRENCIES.contains(currency)) {
            errors.add(at + ": \"currency\" must be one of " + String.join(", ", CURRENCIES) + ".");
        }
        if (it.sell >= 0 && it.currency >= 0 && !coinPayouts) {
            errors.add(at + ": a sell price in " + currency + " coins pays out coins made outside the Coin Factory; use pieces, or put \"allowCoinPayouts\": true at the top.");
        }
        it.stockGlobal = num(o, "stockGlobal", -1, at, errors);
        it.stockPlayer = num(o, "stockPlayer", -1, at, errors);
        it.sellGlobal = num(o, "sellGlobal", -1, at, errors);
        it.sellPlayer = num(o, "sellPlayer", -1, at, errors);
        if (o.has("restockMinutes")) {
            it.stockRefill = Refill.after(num(o, "restockMinutes", 0, at, errors));
        }
        it.stockRefill = ruleKey(o, "stockRefill", it.stockRefill, false, shop, at, errors);
        it.stockPlayerRefill = ruleKey(o, "stockPlayerRefill", Refill.SAME, true, shop, at, errors);
        it.sellRefill = ruleKey(o, "sellRefill", Refill.NEVER, false, shop, at, errors);
        it.sellPlayerRefill = ruleKey(o, "sellPlayerRefill", Refill.SAME, true, shop, at, errors);
        it.instant = bool(o, "instant", false, at, errors);
        it.requires = requires(o, at, errors);
        it.hidden = bool(o, "hidden", false, at, errors);
        String match = str(o, "match", "exact").toLowerCase(Locale.ROOT);
        if (!match.equals("exact") && !match.equals("item")) {
            errors.add(at + ": \"match\" must be exact or item.");
        }
        it.matchItem = match.equals("item");
        it.weight = num(o, "weight", 1, at, errors);
        if (it.weight < 0 || it.weight > 100) {
            errors.add(at + ": \"weight\" must be 0 to 100.");
        }
        if (o.has("lore")) {
            if (!o.get("lore").isJsonArray()) {
                errors.add(at + ": \"lore\" must be a list of up to three lines.");
            } else {
                List<String> lines = new ArrayList<>();
                o.getAsJsonArray("lore").forEach(l -> lines.add(l.isJsonPrimitive() ? l.getAsString().replace("|", "/") : ""));
                it.setNote(String.join("|", lines));
                if (lines.size() > ShopItem.NOTE_LINES || lines.stream().anyMatch(l -> l.strip().length() > ShopItem.NOTE_CHARS)) {
                    warnings.add(at + ": the note is cut to " + ShopItem.NOTE_LINES + " lines of " + ShopItem.NOTE_CHARS + " characters.");
                }
            }
        }
        if (it.buy >= 0 && it.sell >= 0 && it.currency < 0) {
            if (it.sell >= it.buy) {
                warnings.add(at + ": it sells back at " + it.sell + ", at or above its buy price " + it.buy + ": a buy-and-sell-back loop.");
            } else if (it.sell * 3 > it.buy) {
                warnings.add(at + ": its sell price " + it.sell + " is above a third of its buy price " + it.buy + ".");
            }
        }
        return errors.size() == before ? it : null;
    }

    /** {"permission": "node", "playtimeHours": 10, "whenLocked": "show"|"hide", "reason": "Gold rank"}, all optional; absent for none. */
    private static Requirement requires(JsonObject o, String at, List<String> errors) {
        if (!o.has("requires")) {
            return Requirement.NONE;
        }
        if (!o.get("requires").isJsonObject()) {
            errors.add(at + ": \"requires\" must be an object such as {\"permission\": \"bsp.shop.gold\", \"playtimeHours\": 10, \"whenLocked\": \"show\"}.");
            return Requirement.NONE;
        }
        JsonObject r = o.getAsJsonObject("requires");
        String when = str(r, "whenLocked", "show").toLowerCase(Locale.ROOT);
        if (!when.equals("show") && !when.equals("hide")) {
            errors.add(at + ": \"whenLocked\" must be show or hide.");
        }
        int hours = num(r, "playtimeHours", 0, at, errors);
        if (hours < 0) {
            errors.add(at + ": \"playtimeHours\" cannot be below 0.");
        }
        String node = str(r, "permission", "").trim(), reason = str(r, "reason", "").trim();
        if (node.length() > Requirement.NODE_MAX || reason.length() > Requirement.REASON_MAX) {
            errors.add(at + ": a requirement's \"permission\" holds up to " + Requirement.NODE_MAX + " characters and its \"reason\" up to " + Requirement.REASON_MAX + ".");
        }
        return new Requirement(node, Math.max(0, hours), when.equals("hide"), reason);
    }

    /** A refill rule key: object or short text; SAME only for the per-player rules; "rotation" only in a rotating shop. */
    private static Refill ruleKey(JsonObject o, String key, Refill otherwise, boolean perPlayer, Shop shop, String at, List<String> errors) {
        if (!o.has(key)) {
            return otherwise;
        }
        Refill r = rule(o.get(key));
        if (r == null || !perPlayer && r.kind() == Refill.Kind.SAME) {
            errors.add(at + ": \"" + key + "\" is not a refill rule: {\"after\": 720}, {\"every\": \"day\", \"at\": \"06:00\"}, {\"every\": \"monday\", \"at\": \"00:00\"}, \"rotation\""
                    + (perPlayer ? ", \"same\"" : "") + ", or text such as \"12h\".");
            return otherwise;
        }
        if (r.kind() == Refill.Kind.ROTATION && shop.kind != Shop.Kind.ROTATING) {
            errors.add(at + ": \"" + key + "\" refills each rotation, but this shop does not rotate.");
            return otherwise;
        }
        return r;
    }

    /** A rule in either form: {"after": minutes}, {"every": "day"|weekday, "at": "HH:MM"}, or text Refill reads ("rotation", "12h", "monday 00:00"). */
    @Nullable
    static Refill rule(JsonElement e) {
        try {
            if (e.isJsonPrimitive()) {
                return Refill.parse(e.getAsString());
            }
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("after")) {
                    return Refill.after(o.get("after").getAsLong());
                }
                if (o.has("every") && o.has("at")) {
                    return Refill.parse(o.get("every").getAsString() + " " + o.get("at").getAsString());
                }
            }
        } catch (RuntimeException ignored) {
            // not a rule
        }
        return null;
    }

    @Nullable
    private static OpenHours hours(JsonElement e) {
        try {
            JsonObject o = e.getAsJsonObject();
            OpenHours.Mode mode = switch (o.get("mode").getAsString().toLowerCase(Locale.ROOT)) {
                case "always" -> OpenHours.Mode.ALWAYS;
                case "real" -> OpenHours.Mode.REAL;
                case "game" -> OpenHours.Mode.GAME;
                default -> null;
            };
            if (mode == null) {
                return null;
            }
            if (mode == OpenHours.Mode.ALWAYS) {
                return OpenHours.ALWAYS;
            }
            if (mode == OpenHours.Mode.GAME) {
                return new OpenHours(mode, Math.max(0, Math.min(23999, o.get("open").getAsInt())), Math.max(0, Math.min(23999, o.get("close").getAsInt())));
            }
            return new OpenHours(mode, clock(o.get("open").getAsString()), clock(o.get("close").getAsString()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int clock(String hhmm) {
        String[] p = hhmm.trim().split(":");
        int h = Integer.parseInt(p[0]), m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new IllegalArgumentException(hhmm);
        }
        return h * 60 + m;
    }

    private static String str(JsonObject o, String key, String otherwise) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : otherwise;
    }

    private static boolean bool(JsonObject o, String key, boolean otherwise, String at, List<String> errors) {
        if (!o.has(key)) {
            return otherwise;
        }
        if (!o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isBoolean()) {
            errors.add(at + ": \"" + key + "\" must be true or false.");
            return otherwise;
        }
        return o.get(key).getAsBoolean();
    }

    private static int num(JsonObject o, String key, int otherwise, String at, List<String> errors) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, lng(o, key, otherwise, at, errors)));
    }

    private static long lng(JsonObject o, String key, long otherwise, String at, List<String> errors) {
        if (!o.has(key)) {
            return otherwise;
        }
        if (!o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isNumber()) {
            errors.add(at + ": \"" + key + "\" must be a number.");
            return otherwise;
        }
        return o.get(key).getAsLong();
    }

    /** Writes the full report beside the file; returns its name (empty when it could not be written). */
    private static String write(Path report, Path file, boolean test, List<Shop> shops, Set<String> replaced, Set<String> kept, List<String> errors, List<String> warnings) {
        List<String> lines = new ArrayList<>();
        lines.add("BSP-Core shop import report: " + file.getFileName() + (test ? " (test: nothing changed)" : ""));
        lines.add("Written " + LocalDateTime.now().withNano(0));
        lines.add("");
        lines.add(errors.isEmpty() ? (test ? "The file is clean: an import would" : "Imported:") + " create " + (shops.size() - replaced.size()) + " shop(s) and replace " + replaced.size() + "."
                : "Refused: " + errors.size() + " error(s). Nothing was changed.");
        lines.add("");
        Map<String, Integer> cards = new LinkedHashMap<>();
        shops.forEach(s -> cards.put(s.id, s.all().size()));
        if (!cards.isEmpty()) {
            lines.add("Shops (cards):");
            cards.forEach((id, c) -> lines.add("  " + id + (!replaced.contains(id) ? " (new)" : kept.contains(id) ? " (replaces the shop on the server; the same cards: counts and draw kept)"
                    : " (replaces the shop on the server; cards changed: counts and draw start again)") + ": " + c));
            lines.add("");
        }
        if (!errors.isEmpty()) {
            lines.add("Errors (" + errors.size() + "):");
            errors.forEach(e -> lines.add("  " + e));
            lines.add("");
        }
        if (!warnings.isEmpty()) {
            lines.add("Warnings (" + warnings.size() + "):");
            warnings.forEach(w -> lines.add("  " + w));
        }
        try {
            Files.createDirectories(report.getParent());
            Files.write(report, lines, StandardCharsets.UTF_8);
            return report.getFileName().toString();
        } catch (IOException e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core shops: could not write the import report {}: {}", report, e.toString());
            return "";
        }
    }
}
