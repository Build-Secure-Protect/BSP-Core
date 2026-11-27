package com.mrgregles.bsp_core.api;

import com.google.gson.GsonBuilder;
import com.mrgregles.bsp_core.shop.OpenHours;
import com.mrgregles.bsp_core.shop.Requirement;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopExport;
import com.mrgregles.bsp_core.shop.ShopItem;
import com.mrgregles.bsp_core.shop.ShopLedger;
import com.mrgregles.bsp_core.shop.ShopRules;
import com.mrgregles.bsp_core.shop.Shops;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shops, for other mods (BSP-Server Utilities requests SU-BC5 to SU-BC10: its staff web panel's SHOPS tab). A shop is one
 * record for the whole network, so any one server answers for every shop; its Market Stalls, Vendor Posts and Stall
 * Displays are blocks, so each server answers only for its own. Field names follow the panel's (Server Utilities'
 * {@code docs/API.md}, "Shops").
 *
 * <p>Server thread only: from another thread hand the call to the server with {@code server.execute(...)}. No argument may
 * be null unless marked. Changes are told by {@link com.mrgregles.bsp_core.api.event.ShopEvent}. Gate on {@link #VERSION}.
 */
public final class BspShops {
    private BspShops() {}

    /** The shop api's version: 1 has the reads ({@link #list}, {@link #read}, {@link #export}) and the change event. Raised as the api grows. */
    public static final int VERSION = 1;

    /** Opening hours: {@code mode} {@code ALWAYS}, {@code REAL} (minutes of the server's clock, 0 to 1,439) or {@code GAME} (ticks of the Minecraft day, 0 to 23,999). A window may wrap past midnight. */
    public record Hours(String mode, int open, int close) {
        static Hours of(OpenHours h) {
            return new Hours(h.mode().name(), h.openMin(), h.closeMin());
        }
    }

    /** What a player needs: a permission node (empty for none), hours played (0 for none); hidden from those without it, or shown locked; the reason shown. */
    public record Needs(String permission, int playtimeHours, boolean hide, String reason) {
        static Needs of(Requirement r) {
            return new Needs(r.permission(), r.playtimeHours(), r.hide(), r.reason());
        }
    }

    /** A card's item for a picture: its registry id and, for a potion (or a tipped arrow), the potion's id in {@code v} (empty otherwise). */
    public record Peek(String item, String v) {}

    /**
     * A shop or section as the list shows it. {@code kind} is {@code shop}, {@code sub}, {@code rotating} or {@code offer};
     * {@code parent} is empty for a top shop. {@code enabled} is the top shop's switch (a section follows it). {@code cards}
     * counts every card, section cards too. {@code openNow} is false while the shop is off or outside its own hours (a
     * stall or post may keep other hours); {@code opensAt} says when it opens next in words ("09:00", "06:00 (game)", empty
     * when always open). {@code offerEnd} and {@code nextChange} are epoch milliseconds, 0 for none; {@code rotateAt} is
     * "none" or a fixed change time ("day 06:00", "monday 00:00"). {@code edited} is the record's stamp, which only grows.
     * {@code peek} is the first four item cards on show that are not hidden.
     */
    public record Summary(String id, String name, String kind, String parent, boolean enabled, boolean remote, boolean buyOnly, String style,
                          int columns, int rows, int cards, Hours hours, boolean openNow, String opensAt, Needs requires, int offerPercent, long offerEnd,
                          int displaySlots, long intervalHours, String rotateAt, boolean noRepeat, boolean broadcast, long nextChange, long edited, List<Peek> peek) {
        public Summary {
            peek = List.copyOf(peek);
        }
    }

    /**
     * One card, at place {@code i} (from 0, across the pages). {@code nbt} is the item's data as SNBT (empty for none), as the
     * import file's {@code nbt}. {@code count} is the lot size. Prices are -1 when that side is off; {@code currency} -1 is
     * pieces, 0 to 4 a coin tier (copper, gold, diamond, netherite, illyrium). Limits are lots, -1 for none. Refill rules are
     * text: {@code never}, {@code same} (per player: as the shop-wide rule), {@code rotation}, {@code 90m}, {@code 12h},
     * {@code 2d}, {@code day 06:00}, {@code monday 00:00}. {@code section} is the section a section card opens (empty for an
     * item card; its item is only the picture). {@code match} is {@code exact} or {@code item}. {@code stockLeft} and
     * {@code sellLeft} are what is left now for everyone (-1 for no limit).
     */
    public record Card(int i, String item, String v, String nbt, int count, long buy, long sell, int currency,
                       int stockGlobal, int stockPlayer, int sellGlobal, int sellPlayer,
                       String stockRefill, String stockPlayerRefill, String sellRefill, String sellPlayerRefill,
                       boolean instant, boolean hidden, String section, String match, int weight, List<String> note, Needs requires,
                       int stockLeft, int sellLeft) {
        public Card {
            note = List.copyOf(note);
        }
    }

    /** A rotating shop's draw: when it was made (epoch ms) and the places of the cards it put on show. */
    public record Draw(long at, List<Integer> cards) {
        public Draw {
            cards = List.copyOf(cards);
        }
    }

    /**
     * One shop whole: the list's summary and every card. For a rotating shop, {@code onShow} is the places on show now,
     * {@code cycleLeft} how many cards are still to show in a no-repeat cycle (-1 without no repeats), and {@code draws}
     * the draw on show (BSP-Core keeps only the current one); for other shops they are empty, -1 and empty.
     */
    public record Detail(Summary shop, List<Card> cards, List<Integer> onShow, int cycleLeft, List<Draw> draws) {
        public Detail {
            cards = List.copyOf(cards);
            onShow = List.copyOf(onShow);
            draws = List.copyOf(draws);
        }
    }

    /**
     * True when this server can answer for every shop now: alone, always; on a network, while its database is connected
     * (its copy of the shops is then the network's, read again at every change and every five minutes).
     */
    public static boolean ready(MinecraftServer server) {
        return !NetworkStorage.down();
    }

    /** The time zone fixed times are read in ({@code shop.timeZone}, or the server's own), as an id ("Europe/London"). */
    public static String timeZone() {
        return Shops.zone().getId();
    }

    /** Every shop and section, sorted by id. */
    public static List<Summary> list(MinecraftServer server) {
        List<Summary> out = new ArrayList<>();
        long now = Shops.now();
        ShopLedger ledger = Shops.ledger(server);
        ledger.all().values().stream().sorted(java.util.Comparator.comparing(s -> s.id)).forEach(s -> out.add(summary(server, s, now)));
        return out;
    }

    /** One shop or section whole, or null when there is none. */
    @Nullable
    public static Detail read(MinecraftServer server, String id) {
        Shop s = Shops.ledger(server).shop(id.toLowerCase(Locale.ROOT));
        if (s == null) {
            return null;
        }
        long now = Shops.now();
        Summary sum = summary(server, s, now);
        List<Card> cards = new ArrayList<>();
        List<ShopItem> all = s.all();
        for (int i = 0; i < all.size(); i++) {
            ShopItem it = all.get(i);
            boolean item = it.section.isEmpty();
            cards.add(new Card(i, itemId(it), potion(it), it.stack.hasTag() ? it.stack.getTag().toString() : "", it.stack.getCount(), it.buy, it.sell, it.currency,
                    it.stockGlobal, it.stockPlayer, it.sellGlobal, it.sellPlayer,
                    it.stockRefill.text(), it.stockPlayerRefill.text(), it.sellRefill.text(), it.sellPlayerRefill.text(),
                    it.instant, it.hidden, it.section, it.matchItem ? "item" : "exact", it.weight, it.note, Needs.of(it.requires),
                    item ? Shops.everyoneLeft(server, s, i, false, now) : -1, item ? Shops.everyoneLeft(server, s, i, true, now) : -1));
        }
        List<Integer> onShow = List.of();
        int cycleLeft = -1;
        List<Draw> draws = List.of();
        if (s.kind == Shop.Kind.ROTATING) {
            onShow = Shops.visible(server, s, now);
            ShopLedger.Draw d = Shops.ledger(server).draw(s.id);
            if (s.noRepeat) {
                int left = 0;
                for (int i = 0; i < all.size(); i++) {
                    if (!all.get(i).hidden && all.get(i).weight > 0 && all.get(i).section.isEmpty() && !d.used.contains(i)) {
                        left++;
                    }
                }
                cycleLeft = left;
            }
            draws = d.drawnAt > 0 ? List.of(new Draw(d.drawnAt, d.shown)) : List.of();
        }
        return new Detail(sum, cards, onShow, cycleLeft, draws);
    }

    /**
     * The shop and every section under it in the import file's format 1, as {@code /bsp shop export} writes it (pretty
     * printed), without writing a file; null when there is no such shop. {@code /bsp shop import} reads it back.
     */
    @Nullable
    public static String export(MinecraftServer server, String id) {
        List<Shop> shops = ShopExport.shops(Shops.ledger(server), id.toLowerCase(Locale.ROOT));
        if (shops.isEmpty() || id.equalsIgnoreCase("all")) {
            return null;
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(ShopExport.write(shops)) + "\n";
    }

    // ------------------------------------------------------------------ venues (SU-BC9)

    /**
     * A Market Stall ({@code stall}), Vendor Post ({@code post}) or Stall Display ({@code display}) on this server: its
     * dimension ({@code minecraft:overworld}) and block, the shop it sells (empty when unassigned), its own opening hours
     * (null: the shop's count), and a display's pinned item id (empty for none).
     */
    public record Venue(String kind, String dim, int x, int y, int z, String shop, @Nullable Hours hours, String pin) {}

    /**
     * This server's venues, as placed, assigned, changed and broken, and learned as their chunks load (one from before this
     * version is known the first time its chunk loads). Each server answers only for its own; ask each.
     * {@link com.mrgregles.bsp_core.api.event.ShopEvent.VenuesChanged} tells of a change.
     */
    public static List<Venue> venues(MinecraftServer server) {
        List<Venue> out = new ArrayList<>();
        for (var v : com.mrgregles.bsp_core.shop.VenueRegister.get(server).all()) {
            out.add(new Venue(v.kind(), v.dim(), v.pos().getX(), v.pos().getY(), v.pos().getZ(), v.shop(), v.hours() == null ? null : Hours.of(v.hours()), v.pin()));
        }
        return out;
    }

    /** When this server's venues last changed (epoch ms), 0 when not since the server started. */
    public static long venuesChangedAt(MinecraftServer server) {
        return com.mrgregles.bsp_core.shop.VenueRegister.get(server).changedAt;
    }

    // ------------------------------------------------------------------ the report (SU-BC10)

    /**
     * One shop and item's trades: items players bought and sold, pieces taken in (their purchases) and paid out (their
     * sales), and coins in and out per tier (copper, gold, diamond, netherite, illyrium). {@code shop} is empty for rows
     * from before trades named their shop (then {@code shopName} is the name they carried).
     */
    public record ReportRow(String shop, String shopName, String item, long bought, long sold, long piecesIn, long piecesOut, List<Long> coinsIn, List<Long> coinsOut) {
        public ReportRow {
            coinsIn = List.copyOf(coinsIn);
            coinsOut = List.copyOf(coinsOut);
        }
    }

    /** A player and the pieces the shops paid them. */
    public record Seller(String name, long pieces) {}

    /**
     * {@code /bsp shop report}'s figures. {@code ok} is false when the trades could not be read (on a network, the database
     * not connected or not answering: never this server's rows in the network's place). {@code days} is the span used (the
     * bank log keeps rows {@code bank.logDays}; {@code capped} when fewer than asked). {@code rows} counts the trades;
     * {@code shops} has one line a shop (item empty), {@code items} one a card's item, both busiest first; {@code total}
     * every one; {@code sellers} the ten players paid most.
     */
    public record Report(boolean ok, int days, boolean capped, int rows, ReportRow total, List<ReportRow> shops, List<ReportRow> items, List<Seller> sellers) {
        public Report {
            shops = List.copyOf(shops);
            items = List.copyOf(items);
            sellers = List.copyOf(sellers);
        }
    }

    /**
     * Adds up the shop trades of the last {@code days} days (1 or more) for a shop and every section under it, or for every
     * shop ({@code shopId} null), as {@code /bsp shop report} does, without writing a CSV. {@code then} runs on the server
     * thread, at once alone and a moment later on a network (the trades come from the database). An unknown shop answers
     * an empty report with {@code ok} true and no rows.
     */
    public static void report(MinecraftServer server, int days, @Nullable String shopId, java.util.function.Consumer<Report> then) {
        int span = com.mrgregles.bsp_core.shop.ShopReport.days(days);
        List<Shop> shops = shopId == null ? null : ShopExport.shops(Shops.ledger(server), shopId.toLowerCase(Locale.ROOT));
        if (shopId != null && (shops.isEmpty() || shopId.equalsIgnoreCase("all"))) {
            ReportRow none = new ReportRow("", "", "", 0, 0, 0, 0, List.of(0L, 0L, 0L, 0L, 0L), List.of(0L, 0L, 0L, 0L, 0L));
            then.accept(new Report(true, span, days > span, 0, none, List.of(), List.of(), List.of()));
            return;
        }
        com.mrgregles.bsp_core.shop.ShopReport.rows(server, Shops.now() - span * 86_400_000L, rows -> {
            if (rows == null) {
                ReportRow none = new ReportRow("", "", "", 0, 0, 0, 0, List.of(0L, 0L, 0L, 0L, 0L), List.of(0L, 0L, 0L, 0L, 0L));
                then.accept(new Report(false, span, days > span, 0, none, List.of(), List.of(), List.of()));
                return;
            }
            var r = com.mrgregles.bsp_core.shop.ShopReport.build(server, rows, span, days > span, shops, null, false);
            List<Seller> sellers = new ArrayList<>();
            r.sellers().forEach(e -> sellers.add(new Seller(e.getKey(), e.getValue())));
            then.accept(new Report(true, span, r.capped(), r.rows(), line(r.total()), r.shops().stream().map(BspShops::line).toList(), r.items().stream().map(BspShops::line).toList(), sellers));
        });
    }

    private static ReportRow line(com.mrgregles.bsp_core.shop.ShopReport.Line l) {
        List<Long> in = new ArrayList<>(), out = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
            in.add(l.coinsIn[t]);
            out.add(l.coinsOut[t]);
        }
        return new ReportRow(l.shop, l.shopName, l.item, l.itemsBought, l.itemsSold, l.in, l.out, in, out);
    }

    // ------------------------------------------------------------------ changes (SU-BC6, SU-BC8)

    /**
     * What a change came to. {@code key} is {@code created}, {@code saved} or {@code deleted} when {@code ok}; otherwise
     * why not: {@code stale} (the shop changed since {@code edited} was read: read it again), {@code no_shop},
     * {@code bad_id}, {@code taken}, {@code bad_field}, {@code bad_value}, {@code bad_item}, {@code no_parent},
     * {@code parent_loop}, {@code not_top}, {@code rotation_cards}. {@code shop} and {@code edited} are the shop changed and
     * its new stamp (the stamp it has now after a refusal; 0 when there is no such shop). {@code parent} and
     * {@code parentEdited} name a parent whose page changed too (a section card put on it or taken off), else empty and 0.
     * {@code deleted} lists the shops a delete took, the shop first. {@code index} is where a card added or moved now
     * stands (-1 otherwise). {@code ids} maps each shop a copy took to its new id, the top first (empty otherwise).
     */
    public record Answer(boolean ok, String key, String shop, long edited, String parent, long parentEdited, List<String> deleted, int index, java.util.Map<String, String> ids) {
        public Answer {
            deleted = List.copyOf(deleted);
            ids = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(ids));
        }

        static Answer no(String key, @Nullable Shop s) {
            return new Answer(false, key, s == null ? "" : s.id, s == null ? 0 : s.edited, "", 0, List.of(), -1, java.util.Map.of());
        }

        static Answer done(String key, Shop s, @Nullable Shop parent) {
            return new Answer(true, key, s.id, s.edited, parent == null ? "" : parent.id, parent == null ? 0 : parent.edited, List.of(), -1, java.util.Map.of());
        }

        Answer at(int index) {
            return new Answer(ok, key, shop, edited, parent, parentEdited, deleted, index, ids);
        }
    }

    /** The shop if it exists and still carries {@code edited}; otherwise the refusal ({@code no_shop}, {@code stale}) in {@code why[0]}. */
    @Nullable
    private static Shop stamped(MinecraftServer server, String id, long edited, Answer[] why) {
        Shop s = Shops.ledger(server).shop(id.toLowerCase(Locale.ROOT));
        if (s == null) {
            why[0] = Answer.no("no_shop", null);
            return null;
        }
        if (s.edited != edited) {
            why[0] = Answer.no("stale", s);
            return null;
        }
        return s;
    }

    private static void log(String by, String what, String id, String detail) {
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("BSP-Core shops: {} {} {}{}", ShopRules.clean(by), what, id, detail.isEmpty() ? "" : " (" + detail + ")");
    }

    /**
     * Makes a top shop, switched off: {@code kind} {@code shop}, {@code rotating} or {@code offer}. {@code name} is cleaned
     * (control characters and colour codes become spaces) and must be 1 to 64 characters. {@code by} is the staff member's
     * name, for BSP-Core's log.
     */
    public static Answer create(MinecraftServer server, String id, String name, String kind, String by) {
        id = id.toLowerCase(Locale.ROOT);
        if (!id.matches(ShopRules.ID)) {
            return Answer.no("bad_id", null);
        }
        if (Shops.ledger(server).shop(id) != null) {
            return Answer.no("taken", Shops.ledger(server).shop(id));
        }
        Shop.Kind k = topKind(kind);
        String n = ShopRules.name(name);
        if (k == null || n == null) {
            return Answer.no("bad_value", null);
        }
        Shop s = Shops.create(server, id, n, k);
        log(by, "created", id, k.name().toLowerCase(Locale.ROOT));
        return Answer.done("created", s, null);
    }

    /**
     * Makes a section under {@code parent} (which must still carry {@code parentEdited}), with its section card at the end of
     * the parent's page in the same step: the card's picture is {@code pictureItem} (a registry id) with {@code pictureNbt}
     * (SNBT, empty for none, at most 4,096 characters; a potion's {@code Potion}, say). The section takes the parent's
     * network flag and style. {@code parent} in the answer carries the parent's new stamp.
     */
    public static Answer createSection(MinecraftServer server, String id, String name, String parent, String pictureItem, String pictureNbt, long parentEdited, String by) {
        id = id.toLowerCase(Locale.ROOT);
        if (!id.matches(ShopRules.ID)) {
            return Answer.no("bad_id", null);
        }
        if (Shops.ledger(server).shop(id) != null) {
            return Answer.no("taken", Shops.ledger(server).shop(id));
        }
        Answer[] why = new Answer[1];
        Shop p = stamped(server, parent, parentEdited, why);
        if (p == null) {
            return why[0];
        }
        String n = ShopRules.name(name);
        if (n == null) {
            return Answer.no("bad_value", p);
        }
        if (Shops.parents(server, p).size() + 1 > Shops.MAX_DEPTH) {
            return Answer.no("parent_loop", p);
        }
        net.minecraft.world.item.ItemStack picture = stack(pictureItem, pictureNbt);
        if (picture == null) {
            return Answer.no("bad_item", p);
        }
        Shop s = Shops.create(server, id, n, Shop.Kind.SUB, made -> { // set up before its one save: one event, never a top-level record on the network
            made.parent = p.id;
            made.enabled = true; // a section follows its top shop's switch
            made.remote = p.remote;
            made.style = p.style;
        });
        ShopItem card = new ShopItem();
        card.stack = picture;
        card.section = s.id;
        p.add(card);
        Shops.save(server, p);
        log(by, "created section", id, "in " + p.id);
        return Answer.done("created", s, p);
    }

    /**
     * Copies a top shop and every section under it: the copy is {@code newId} named {@code name} (cleaned, 1 to 64), each
     * section {@code <newId>_1}, {@code _2}... in tree order (parents before their sections), skipping ids already taken
     * and cut to fit 32 characters. Parents and the section cards inside the copy point at the new ids; every setting and
     * card is copied; counts and draws are not (the copy starts full). Made off. Refused without making anything:
     * {@code bad_id}, {@code taken} (the new id), {@code no_shop}, {@code not_top} (a section: copy its top shop),
     * {@code bad_value} (the name). No stamp: nothing is changed. The answer's {@code ids} maps each old id to its new one.
     */
    public static Answer copy(MinecraftServer server, String id, String newId, String name, String by) {
        Shop from = Shops.ledger(server).shop(id.toLowerCase(Locale.ROOT));
        if (from == null) {
            return Answer.no("no_shop", null);
        }
        if (Shops.top(server, from) != from) {
            return Answer.no("not_top", from);
        }
        newId = newId.toLowerCase(Locale.ROOT);
        if (!newId.matches(ShopRules.ID)) {
            return Answer.no("bad_id", from);
        }
        if (Shops.ledger(server).shop(newId) != null) {
            return Answer.no("taken", Shops.ledger(server).shop(newId));
        }
        String n = ShopRules.name(name);
        if (n == null) {
            return Answer.no("bad_value", from);
        }
        var ids = Shops.copy(server, from, newId, n);
        Shop made = Shops.ledger(server).shop(newId);
        log(by, "copied", from.id, "as " + newId + (ids.size() > 1 ? " with " + (ids.size() - 1) + " sections" : ""));
        return new Answer(true, "created", made.id, made.edited, "", 0, List.of(), -1, ids);
    }

    /** Deletes a shop (which must still carry {@code edited}) with every section under it, and its section card on its parent's page. */
    public static Answer delete(MinecraftServer server, String id, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        List<String> gone = Shops.delete(server, s.id);
        Shop p = s.parent.isEmpty() ? null : Shops.ledger(server).shop(s.parent);
        log(by, "deleted", s.id, gone.size() > 1 ? "with " + (gone.size() - 1) + " sections" : "");
        return new Answer(true, "deleted", s.id, 0, p == null ? "" : p.id, p == null ? 0 : p.edited, gone, -1, java.util.Map.of());
    }

    /** Turns a top shop on or off (its sections follow; players in it have the screen closed); {@code not_top} for a section. */
    public static Answer setEnabled(MinecraftServer server, String id, boolean on, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        String key = Shops.setEnabled(server, s, on);
        if (!key.equals("saved")) {
            return Answer.no(key, s);
        }
        log(by, on ? "turned on" : "turned off", s.id, "");
        return Answer.done("saved", s, null);
    }

    /**
     * Changes one setting of a shop that still carries {@code edited}. Fields and values (text):
     * <ul>
     * <li>{@code name}: 1 to 64 characters after cleaning;</li>
     * <li>{@code kind}: {@code shop}, {@code rotating}, {@code offer}, or {@code sub} for a section ({@code rotation_cards}
     * when leaving rotating while a card refills each rotation);</li>
     * <li>{@code parent}: a shop id, never a loop or more than 16 deep ({@code parent_loop}); the section card moves with it
     * (taken off the old parent's page, put at the end of the new one's). A section never moves to the top level
     * ({@code bad_value}); a top shop given a parent keeps its kind;</li>
     * <li>{@code remote}, {@code buyOnly}, {@code noRepeat}, {@code broadcast}: {@code true} or {@code false};</li>
     * <li>{@code style}: {@code cash_machine}, {@code passbook}, {@code market_board};</li>
     * <li>{@code columns}: 0 or 4 to 7; {@code rows}: 2 or 3; {@code displaySlots}: 1 to 54; {@code intervalHours}: 1 to 8,760;</li>
     * <li>{@code rotateAt}: {@code none}, {@code day HH:MM} or {@code <weekday> HH:MM};</li>
     * <li>{@code offerPercent}: 0 to 99; {@code offerEnd}: epoch ms within the next ten years, or 0 for none.</li>
     * </ul>
     * Opening hours and requirements have their own calls ({@link #setHours}, {@link #setRequires}).
     */
    public static Answer set(MinecraftServer server, String id, String field, String value, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        String v = value.trim();
        Shop movedFrom = null, movedTo = null;
        try {
            switch (field) {
                case "name" -> {
                    String n = ShopRules.name(value);
                    if (n == null) {
                        return Answer.no("bad_value", s);
                    }
                    s.name = n;
                }
                case "kind" -> {
                    Shop.Kind k = v.equalsIgnoreCase("sub") ? Shop.Kind.SUB : topKind(v);
                    if (k == null || k == Shop.Kind.SUB && s.parent.isEmpty()) {
                        return Answer.no("bad_value", s);
                    }
                    if (k != Shop.Kind.ROTATING && s.all().stream().anyMatch(ShopItem::refillsEachRotation)) {
                        return Answer.no("rotation_cards", s);
                    }
                    s.kind = k;
                }
                case "parent" -> {
                    if (v.isEmpty() && !s.parent.isEmpty()) {
                        return Answer.no("bad_value", s); // a section stays a section
                    }
                    movedFrom = s.parent.isEmpty() ? null : Shops.ledger(server).shop(s.parent);
                    String key = Shops.setParent(server, s, v);
                    if (!key.equals("saved")) {
                        return Answer.no(key, s);
                    }
                    movedTo = s.parent.isEmpty() ? null : Shops.ledger(server).shop(s.parent);
                }
                case "remote" -> s.remote = bool(v);
                case "buyOnly" -> s.buyOnly = bool(v);
                case "noRepeat" -> s.noRepeat = bool(v);
                case "broadcast" -> s.broadcast = bool(v);
                case "style" -> {
                    if (!List.of("cash_machine", "passbook", "market_board").contains(v)) {
                        return Answer.no("bad_value", s);
                    }
                    s.style = v;
                }
                case "columns" -> s.columns = range(Integer.parseInt(v), 0, Shop.MAX_COLUMNS, Integer.parseInt(v) == 0 || Integer.parseInt(v) >= Shop.MIN_COLUMNS);
                case "rows" -> s.rows = range(Integer.parseInt(v), 2, 3, true);
                case "displaySlots" -> s.displaySlots = range(Integer.parseInt(v), 1, ShopRules.SLOTS_MAX, true);
                case "intervalHours" -> s.intervalMs = range(Integer.parseInt(v), 1, ShopRules.INTERVAL_HOURS_MAX, true) * 3_600_000L;
                case "rotateAt" -> {
                    com.mrgregles.bsp_core.shop.Refill at = v.equalsIgnoreCase("none") ? com.mrgregles.bsp_core.shop.Refill.NEVER : com.mrgregles.bsp_core.shop.Refill.parse(v);
                    if (at == null || !at.fixedTime() && at.kind() != com.mrgregles.bsp_core.shop.Refill.Kind.NEVER) {
                        return Answer.no("bad_value", s);
                    }
                    s.rotateAt = at;
                    s.rotateSince = Shops.now(); // the draw on show stays until the next change time
                }
                case "offerPercent" -> s.offerPercent = range(Integer.parseInt(v), 0, 99, true);
                case "offerEnd" -> {
                    long end = Long.parseLong(v), now = System.currentTimeMillis();
                    if (end != 0 && (end <= now || end > now + ShopRules.OFFER_YEARS_MS)) {
                        return Answer.no("bad_value", s);
                    }
                    s.offerEndMs = end;
                }
                default -> {
                    return Answer.no("bad_field", s);
                }
            }
        } catch (IllegalArgumentException e) {
            return Answer.no("bad_value", s);
        }
        Shops.save(server, s);
        log(by, "set " + field, s.id, field.equals("name") ? s.name : v.length() > 64 ? v.substring(0, 64) : v);
        Shop changedParent = movedTo != null ? movedTo : movedFrom;
        return Answer.done("saved", s, changedParent == null ? null : Shops.ledger(server).shop(changedParent.id));
    }

    /** Sets a shop's own opening hours: {@code mode} {@code ALWAYS}, {@code REAL} (minutes 0 to 1,439) or {@code GAME} (ticks 0 to 23,999). */
    public static Answer setHours(MinecraftServer server, String id, Hours hours, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        OpenHours h;
        try {
            OpenHours.Mode mode = OpenHours.Mode.valueOf(hours.mode().toUpperCase(Locale.ROOT));
            h = mode == OpenHours.Mode.ALWAYS ? OpenHours.ALWAYS : new OpenHours(mode, hours.open(), hours.close());
        } catch (IllegalArgumentException e) {
            return Answer.no("bad_value", s);
        }
        if (!ShopRules.hours(h)) {
            return Answer.no("bad_value", s);
        }
        s.hours = h;
        Shops.save(server, s);
        log(by, "set hours", s.id, h.mode() + " " + h.openMin() + "-" + h.closeMin());
        return Answer.done("saved", s, null);
    }

    /**
     * Sets what a player needs for the whole shop (and so everything under it): a permission node (letters, digits and
     * {@code . _ - *}, up to 128; empty for none), hours played (0 to 100,000), hidden or shown locked, a reason (up to 64,
     * cleaned). A permission of "" with 0 hours clears it.
     */
    public static Answer setRequires(MinecraftServer server, String id, Needs needs, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        Requirement r = requirement(needs);
        if (r == null) {
            return Answer.no("bad_value", s);
        }
        s.requires = r;
        Shops.save(server, s);
        log(by, "set requires", s.id, r.text());
        return Answer.done("saved", s, null);
    }

    // ------------------------------------------------------------------ cards (SU-BC6)

    /** The card fields {@link #setItem} takes, by the panel's names; a section card takes only {@code hidden} and {@code requires}. */
    public static final List<String> CARD_FIELDS = List.of("count", "buy", "sell", "currency", "stockGlobal", "stockPlayer", "sellGlobal", "sellPlayer",
            "stockRefill", "stockPlayerRefill", "sellRefill", "sellPlayerRefill", "instant", "hidden", "section", "match", "weight", "note", "requires");

    /**
     * Adds a card of {@code item} (a registry id) with {@code snbt} (the item's data, empty for none, at most 4,096
     * characters) and a lot of {@code count} (1 to 64, kept to the item's stack) at place {@code at} (past the end: at the
     * end). The cards after it move up a place and their counts start again. The card is made with no prices (both sides
     * off) until {@code buy} or {@code sell} is set. The answer's {@code index} is where it stands.
     */
    public static Answer addItem(MinecraftServer server, String id, String item, String snbt, int count, int at, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        if (count < 1 || count > 64 || at < 0) {
            return Answer.no("bad_value", s);
        }
        net.minecraft.world.item.ItemStack stack = stack(item, snbt);
        if (stack == null) {
            return Answer.no("bad_item", s);
        }
        if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(stack)) {
            return Answer.no("bad_item", s);
        }
        stack.setCount(Math.min(stack.getMaxStackSize(), count)); // a lot never more than a stack of the item
        ShopItem it = new ShopItem();
        it.stack = stack;
        int place = Math.min(at, s.all().size());
        com.mrgregles.bsp_core.network.ShopConfigActionPacket.place(server, s, it, place);
        Shops.save(server, s);
        log(by, "added a card to", s.id, item + " x" + stack.getCount() + " at " + place);
        return Answer.done("saved", s, null).at(place);
    }

    /**
     * Changes one field of card {@code index}, by the panel's names ({@link #CARD_FIELDS}), with the Workbench's rules: a
     * change to a limit, a refill rule, the lot size or the match rule starts the card's counts again (a price or a note
     * leaves them). Values (text):
     * <ul>
     * <li>{@code count}: 1 to 64, kept to the item's stack;</li>
     * <li>{@code buy}, {@code sell}: -1 (off) or 0 to 10^12 pieces; in coins at most 10,000 ({@code bad_price}, also when
     * {@code currency} changes);</li>
     * <li>{@code currency}: -1 (pieces) to 4 (illyrium);</li>
     * <li>{@code stockGlobal}, {@code stockPlayer}, {@code sellGlobal}, {@code sellPlayer}: -1 (none) or 0 to 10^9 lots;</li>
     * <li>the four refill rules: as {@link Card}'s; {@code same} only for the per-player two, {@code rotation} only in a
     * rotating shop ({@code rotation_only});</li>
     * <li>{@code instant}, {@code hidden}: {@code true} or {@code false};</li>
     * <li>{@code section}: a shop id to open, or empty for an item card (a section card takes this and {@code hidden} only);</li>
     * <li>{@code match}: {@code exact} or {@code item}; {@code weight}: 0 to 100;</li>
     * <li>{@code note}: up to three lines of up to 60 characters, separated by a new line (a {@code |} in a line becomes a
     * space), cleaned; empty clears it;</li>
     * <li>{@code requires}: use {@link #setItemRequires}.</li>
     * </ul>
     */
    public static Answer setItem(MinecraftServer server, String id, int index, String field, String value, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        ShopItem it = s.item(index);
        if (it == null) {
            return Answer.no("no_item", s);
        }
        if (!CARD_FIELDS.contains(field) || field.equals("requires")) {
            return Answer.no("bad_field", s);
        }
        if (!it.section.isEmpty() && !field.equals("hidden") && !field.equals("section")) {
            return Answer.no("bad_field", s); // a section card is only a picture and a way in (its section may change, or empty make it an item card)
        }
        String v = value.trim(), wb;
        try {
            switch (field) {
                case "count" -> range(Integer.parseInt(v), 1, 64, true);
                case "buy", "sell" -> {
                    long p = Long.parseLong(v);
                    if (p < -1 || p > ShopRules.PRICE_MAX) {
                        return Answer.no("bad_value", s);
                    }
                }
                case "currency" -> range(Integer.parseInt(v), -1, 4, true);
                case "stockGlobal", "stockPlayer", "sellGlobal", "sellPlayer" -> {
                    long n = Long.parseLong(v);
                    if (n < -1 || n > ShopRules.LIMIT_MAX) {
                        return Answer.no("bad_value", s);
                    }
                }
                case "instant", "hidden" -> v = String.valueOf(bool(v));
                case "section" -> {
                    v = v.toLowerCase(Locale.ROOT);
                    if (!v.isEmpty() && (Shops.ledger(server).shop(v) == null || v.equals(s.id))) {
                        return Answer.no("bad_value", s);
                    }
                }
                case "match" -> {
                    if (!v.equals("exact") && !v.equals("item")) {
                        return Answer.no("bad_value", s);
                    }
                }
                case "weight" -> range(Integer.parseInt(v), 0, 100, true);
                case "note" -> {
                    String[] lines = value.split("\r?\n", -1);
                    if (lines.length > ShopItem.NOTE_LINES) {
                        return Answer.no("bad_value", s);
                    }
                    StringBuilder b = new StringBuilder();
                    for (String line : lines) {
                        String t = ShopRules.clean(line.replace('|', ' '));
                        if (t.length() > ShopItem.NOTE_CHARS) {
                            return Answer.no("bad_value", s);
                        }
                        b.append(b.length() > 0 ? "|" : "").append(t);
                    }
                    v = b.toString();
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException e) {
            return Answer.no("bad_value", s);
        }
        wb = field.toLowerCase(Locale.ROOT); // the Workbench's names are the panel's in lower case
        String key = com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null,
                new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET_ITEM, wb, v, index, 0), s.id);
        if (!key.equals("saved")) {
            return Answer.no(key, s);
        }
        log(by, "set card " + index + " " + field + " in", s.id, v.length() > 64 ? v.substring(0, 64) : v);
        return Answer.done("saved", s, null).at(index);
    }

    /** Sets what a player needs for card {@code index} (the rules of {@link #setRequires}); a section card takes it too. */
    public static Answer setItemRequires(MinecraftServer server, String id, int index, Needs needs, long edited, String by) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        ShopItem it = s.item(index);
        if (it == null) {
            return Answer.no("no_item", s);
        }
        Requirement r = requirement(needs);
        if (r == null) {
            return Answer.no("bad_value", s);
        }
        it.requires = r;
        Shops.save(server, s);
        log(by, "set card " + index + " requires in", s.id, r.text());
        return Answer.done("saved", s, null).at(index);
    }

    /** Moves card {@code from} to place {@code to}; every card between them starts its counts again. */
    public static Answer moveItem(MinecraftServer server, String id, int from, int to, long edited, String by) {
        return card(server, id, com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.MOVE_ITEM, from, to, edited, by, "moved card " + from + " to " + to + " in");
    }

    /** Removes card {@code index}; the cards after it move up a place and start their counts again. */
    public static Answer removeItem(MinecraftServer server, String id, int index, long edited, String by) {
        return card(server, id, com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.REMOVE_ITEM, index, 0, edited, by, "removed card " + index + " from");
    }

    /** Fills every count of card {@code index} again now (everyone's and each player's). The record itself is not changed, so its stamp stays. */
    public static Answer refill(MinecraftServer server, String id, int index, long edited, String by) {
        return card(server, id, com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.REFILL_ITEM, index, 0, edited, by, "refilled card " + index + " in");
    }

    private static Answer card(MinecraftServer server, String id, com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action action, int i, int j, long edited, String by, String what) {
        Answer[] why = new Answer[1];
        Shop s = stamped(server, id, edited, why);
        if (s == null) {
            return why[0];
        }
        String key = com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(action, "", "", i, j), s.id);
        if (!key.equals("saved") && !key.equals("refilled")) {
            return Answer.no(key, s);
        }
        log(by, what, s.id, "");
        return Answer.done("saved", s, null).at(action == com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.MOVE_ITEM ? j : action == com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.REMOVE_ITEM ? -1 : i);
    }

    // ------------------------------------------------------------------ parts

    @Nullable
    static Requirement requirement(Needs n) {
        Requirement r = ShopRules.requirement(n.permission().trim(), n.playtimeHours(), n.hide(), n.reason());
        return r == null ? null : r.any() ? r : Requirement.NONE;
    }

    @Nullable
    private static Shop.Kind topKind(String kind) {
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "shop" -> Shop.Kind.SHOP;
            case "rotating" -> Shop.Kind.ROTATING;
            case "offer" -> Shop.Kind.OFFER;
            default -> null;
        };
    }

    private static boolean bool(String v) {
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(v);
    }

    private static int range(int v, int min, int max, boolean also) {
        if (v < min || v > max || !also) {
            throw new IllegalArgumentException(String.valueOf(v));
        }
        return v;
    }

    /** A stack of {@code item} with {@code snbt} (empty for none, at most 4,096 characters), or null when the item or the data is not one. */
    @Nullable
    static net.minecraft.world.item.ItemStack stack(String item, String snbt) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(item.trim());
        var it = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        if (it == null || it == net.minecraft.world.item.Items.AIR || !ForgeRegistries.ITEMS.containsKey(key) || snbt.length() > ShopRules.SNBT_MAX) {
            return null;
        }
        var stack = new net.minecraft.world.item.ItemStack(it);
        if (!snbt.isBlank()) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(snbt));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                return null;
            }
        }
        return stack;
    }

    private static Summary summary(MinecraftServer server, Shop s, long now) {
        var level = server.overworld();
        boolean off = Shops.off(server, s);
        boolean open = !off && s.hours.isOpen(level);
        List<Peek> peek = new ArrayList<>();
        List<Integer> shown = s.kind == Shop.Kind.ROTATING ? Shops.visible(server, s, now) : null;
        List<ShopItem> all = s.all();
        for (int i = 0; i < all.size() && peek.size() < 4; i++) {
            ShopItem it = all.get(i);
            if (it.section.isEmpty() && !it.hidden && !it.stack.isEmpty() && (shown == null || shown.contains(i))) {
                peek.add(new Peek(itemId(it), potion(it)));
            }
        }
        return new Summary(s.id, s.name, s.kind.name().toLowerCase(Locale.ROOT), s.parent, !off, s.remote, s.buyOnly, s.style,
                s.columns, s.rows(), all.size(), Hours.of(s.hours), open, open ? "" : opensAt(s.hours), Needs.of(s.requires), s.offerPercent, s.offerEndMs,
                s.displaySlots, Math.max(1, s.intervalMs / 3_600_000L), s.rotateAt.fixedTime() ? s.rotateAt.text() : "none", s.noRepeat, s.broadcast,
                s.kind == Shop.Kind.ROTATING ? Shops.nextChange(server, s, now) : 0, s.edited, peek);
    }

    /** When the hours open, in words: "09:00" on the clock, "06:00 (game)" on the Minecraft day (tick 0 is 06:00); empty when always open. */
    static String opensAt(OpenHours h) {
        return switch (h.mode()) {
            case ALWAYS -> "";
            case REAL -> String.format(Locale.ROOT, "%02d:%02d", h.openMin() / 60, h.openMin() % 60);
            case GAME -> {
                int minutes = (int) ((h.openMin() + 6_000L) % 24_000L * 60 / 1_000);
                yield String.format(Locale.ROOT, "%02d:%02d (game)", minutes / 60, minutes % 60);
            }
        };
    }

    private static String itemId(ShopItem it) {
        var key = ForgeRegistries.ITEMS.getKey(it.stack.getItem());
        return key == null ? "minecraft:air" : key.toString();
    }

    private static String potion(ShopItem it) {
        return it.stack.hasTag() && it.stack.getTag().contains("Potion", net.minecraft.nbt.Tag.TAG_STRING) ? it.stack.getTag().getString("Potion") : "";
    }
}
