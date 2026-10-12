package com.mrgregles.bsp_core.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop: a tree node. Plain shops and sub-sections hold pages of items; a rotating shop draws a
 * few of its pool each interval; a special offer is a section with a discount until a time. The
 * whole shop is one record, saved as NBT.
 */
public final class Shop {
    public enum Kind { SHOP, SUB, ROTATING, OFFER }

    /** The storage page size (how items are grouped in the record); what players see per page follows the shop's layout. */
    public static final int COLUMNS = 7, ROWS = 3, PER_PAGE = COLUMNS * ROWS;
    /** The card grid's width on the Cash Machine screen and the gap between cards, in GUI pixels. */
    public static final int GRID_WIDTH = 194, GRID_GAP = 1, MIN_COLUMNS = 4, MAX_COLUMNS = 7;

    public final String id;
    public String name = "";
    public Kind kind = Kind.SHOP;
    public String parent = "";
    /** Reachable through the Portable Banker's Shopping Network. */
    public boolean remote;
    public boolean buyOnly;
    public String style = "cash_machine";
    public final List<List<ShopItem>> pages = new ArrayList<>();
    /** Rotating: how many of the pool show, and how often a new draw happens. */
    public int displaySlots = 4;
    public long intervalMs = 12 * 3_600_000L;
    /** Special offer: the discount and when it ends (0 = no end). */
    public int offerPercent;
    public long offerEndMs;
    /** The shop's own opening hours: used on the Shopping Network, and at a post or stall that has none of its own. */
    public OpenHours hours = OpenHours.ALWAYS;
    /** The card layout players see: cards per row (0 = automatic, from the longest price label) and rows per page. */
    public int columns, rows = 3;

    public Shop(String id) {
        this.id = id;
        pages.add(new ArrayList<>());
    }

    /** Every item in order, across pages (the pool, for a rotating shop). */
    public List<ShopItem> all() {
        List<ShopItem> out = new ArrayList<>();
        pages.forEach(out::addAll);
        return out;
    }

    public ShopItem item(int index) {
        List<ShopItem> all = all();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    /** Appends an item to the last page with room, opening a new page when full; returns its index. */
    public int add(ShopItem item) {
        List<ShopItem> last = pages.get(pages.size() - 1);
        if (last.size() >= PER_PAGE) {
            last = new ArrayList<>();
            pages.add(last);
        }
        last.add(item);
        return all().size() - 1;
    }

    public boolean remove(int index) {
        for (List<ShopItem> page : pages) {
            if (index < page.size()) {
                page.remove(index);
                return true;
            }
            index -= page.size();
        }
        return false;
    }

    /** The text under a card: "buy 2,000 G", "sell 15". Shared so the server can size cards the way the client draws them. */
    public static String priceLabel(String verb, long price, int currency) {
        return verb + " " + com.mrgregles.bsp_core.bank.Bank.format(price) + (currency < 0 ? "" : " " + com.mrgregles.bsp_core.bank.Bank.tierName(com.mrgregles.bsp_core.coin.CoinTier.values()[currency]).charAt(0));
    }

    /** Cards per row for these items: the set number, or, when automatic, as many as the longest label leaves room for (4 to 7). */
    public int effectiveColumns(List<ShopItem> items) {
        if (columns >= MIN_COLUMNS) {
            return Math.min(MAX_COLUMNS, columns);
        }
        int longest = 6; // "> open"
        for (ShopItem it : items) {
            if (it == null || !it.section.isEmpty()) {
                continue;
            }
            if (it.buy >= 0) {
                longest = Math.max(longest, priceLabel("buy", it.buy, it.currency).length());
            }
            if (it.sell >= 0) {
                longest = Math.max(longest, priceLabel("sell", it.sell, it.currency).length());
            }
        }
        int need = (int) Math.ceil(longest * 6 * 0.75) + 4; // small text is three quarters of 6 px a character, plus padding
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (GRID_WIDTH + GRID_GAP) / (need + GRID_GAP)));
    }

    public int rows() {
        return Math.max(2, Math.min(3, rows));
    }

    /** The hours that count at a vendor or stall: its own when it has any, otherwise the shop's. */
    public OpenHours effectiveHours(@javax.annotation.Nullable OpenHours venue) {
        return venue != null && venue.mode() != OpenHours.Mode.ALWAYS ? venue : hours;
    }

    public boolean offerActive(long now) {
        return kind == Kind.OFFER && offerPercent > 0 && (offerEndMs == 0 || now < offerEndMs);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Name", name);
        t.putString("Kind", kind.name());
        t.putString("Parent", parent);
        t.putBoolean("Remote", remote);
        t.putBoolean("BuyOnly", buyOnly);
        t.putString("Style", style);
        ListTag pl = new ListTag();
        for (List<ShopItem> page : pages) {
            ListTag il = new ListTag();
            page.forEach(i -> il.add(i.save()));
            CompoundTag p = new CompoundTag();
            p.put("Items", il);
            pl.add(p);
        }
        t.put("Pages", pl);
        t.putInt("DisplaySlots", displaySlots);
        t.putInt("Columns", columns);
        t.putInt("Rows", rows);
        t.putLong("Interval", intervalMs);
        t.putInt("OfferPercent", offerPercent);
        t.putLong("OfferEnd", offerEndMs);
        t.put("Hours", hours.save());
        return t;
    }

    public static Shop load(CompoundTag t) {
        Shop s = new Shop(t.getString("Id"));
        s.name = t.getString("Name");
        try {
            s.kind = Kind.valueOf(t.getString("Kind"));
        } catch (IllegalArgumentException e) {
            s.kind = Kind.SHOP;
        }
        s.parent = t.getString("Parent");
        s.remote = t.getBoolean("Remote");
        s.buyOnly = t.getBoolean("BuyOnly");
        s.style = t.contains("Style") ? t.getString("Style") : "cash_machine";
        s.pages.clear();
        for (Tag p : t.getList("Pages", Tag.TAG_COMPOUND)) {
            List<ShopItem> page = new ArrayList<>();
            for (Tag i : ((CompoundTag) p).getList("Items", Tag.TAG_COMPOUND)) {
                page.add(ShopItem.load((CompoundTag) i));
            }
            s.pages.add(page);
        }
        if (s.pages.isEmpty()) {
            s.pages.add(new ArrayList<>());
        }
        s.displaySlots = t.contains("DisplaySlots") ? t.getInt("DisplaySlots") : 4;
        s.columns = t.getInt("Columns");
        s.rows = t.contains("Rows") ? t.getInt("Rows") : 3;
        s.intervalMs = t.contains("Interval") ? t.getLong("Interval") : 12 * 3_600_000L;
        s.offerPercent = t.getInt("OfferPercent");
        s.offerEndMs = t.getLong("OfferEnd");
        s.hours = OpenHours.load(t.getCompound("Hours"));
        return s;
    }
}
