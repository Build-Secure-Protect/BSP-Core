package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This server's shops and their running state: the shop records (the record on a single server, a
 * copy of the database on a network), each card's stock and sell-limit counts (for everyone and
 * per player) with their refill clocks, and each rotating shop's current draw.
 */
public class ShopLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_shops";

    /**
     * One count of a limit: lots left (-1 not counted yet, so full) and its clock: when a countdown
     * ends (0 none running), or the refill time or draw it was last filled for.
     */
    public static final class Count {
        public int left = -1;
        public long at;

        public Count() {}

        public Count(int left, long at) {
            this.left = left;
            this.at = at;
        }

        public Count copy() {
            return new Count(left, at);
        }
    }

    /** The counts of one shop item: stock to buy and room to sell, for everyone and per player. */
    public static final class Stock {
        public final Count stock = new Count(), sell = new Count();
        public final Map<UUID, Count> buyers = new HashMap<>(), sellers = new HashMap<>();

        /** Every count full again (after the card is edited or an admin resets it). */
        public void reset() {
            stock.left = -1;
            stock.at = 0;
            sell.left = -1;
            sell.at = 0;
            buyers.clear();
            sellers.clear();
        }
    }

    /** A rotating shop's draw: the item indexes shown, when it was drawn and when the next draw is due. */
    public static final class Draw {
        public final List<Integer> shown = new ArrayList<>();
        public long drawnAt, nextAt;
        /** On a network, not saved: a new draw has been asked of the database; and no asking again before this time after a failure. */
        public boolean asking;
        public long askAfter;

        public Draw copy() {
            Draw d = new Draw();
            d.shown.addAll(shown);
            d.drawnAt = drawnAt;
            d.nextAt = nextAt;
            return d;
        }

        /** Takes another draw's cards and times (the network's answer). */
        public void set(Draw from) {
            shown.clear();
            shown.addAll(from.shown);
            drawnAt = from.drawnAt;
            nextAt = from.nextAt;
        }
    }

    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<String, Stock> stock = new HashMap<>();
    private final Map<String, Draw> draws = new HashMap<>();
    /** On a network: this server's counts have been offered to the database once (the first server to start keeps its counts there). */
    public boolean countsMoved;

    public static ShopLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShopLedger::load, ShopLedger::new, DATA_NAME);
    }

    @Nullable
    public Shop shop(String id) {
        return shops.get(id);
    }

    public Map<String, Shop> all() {
        return shops;
    }

    public void put(Shop shop) {
        shops.put(shop.id, shop);
        setDirty();
    }

    public void remove(String id) {
        shops.remove(id);
        stock.keySet().removeIf(k -> k.startsWith(id + ":"));
        draws.remove(id);
        setDirty();
    }

    public Stock stock(String shopId, int index) {
        return stock.computeIfAbsent(shopId + ":" + index, k -> new Stock());
    }

    /** Every card's counts on this server, keyed "shop:index" (on a network, a copy of the database's). */
    public Map<String, Stock> counts() {
        return stock;
    }

    /** Forgets this server's counts of one shop (before the database's are copied in). */
    public void clearCounts(String shopId) {
        stock.keySet().removeIf(k -> k.startsWith(shopId + ":"));
        setDirty();
    }

    public Draw draw(String shopId) {
        return draws.computeIfAbsent(shopId, k -> new Draw());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag sl = new ListTag();
        shops.values().forEach(s -> sl.add(s.save()));
        tag.put("Shops", sl);
        ListTag st = new ListTag();
        for (var e : stock.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Key", e.getKey());
            t.putInt("Left", e.getValue().stock.left);
            t.putLong("RestockAt", e.getValue().stock.at);
            t.put("Players", saveCounts(e.getValue().buyers));
            t.putInt("SellLeft", e.getValue().sell.left);
            t.putLong("SellAt", e.getValue().sell.at);
            t.put("Sellers", saveCounts(e.getValue().sellers));
            st.add(t);
        }
        tag.put("Stock", st);
        ListTag dl = new ListTag();
        for (var e : draws.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Shop", e.getKey());
            t.putIntArray("Shown", e.getValue().shown.stream().mapToInt(Integer::intValue).toArray());
            t.putLong("DrawnAt", e.getValue().drawnAt);
            t.putLong("NextAt", e.getValue().nextAt);
            dl.add(t);
        }
        tag.put("Draws", dl);
        tag.putBoolean("CountsMoved", countsMoved);
        return tag;
    }

    public static ShopLedger load(CompoundTag tag) {
        ShopLedger l = new ShopLedger();
        l.countsMoved = tag.getBoolean("CountsMoved");
        for (Tag t : tag.getList("Shops", Tag.TAG_COMPOUND)) {
            Shop s = Shop.load((CompoundTag) t);
            l.shops.put(s.id, s);
        }
        for (Tag t : tag.getList("Stock", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Stock s = new Stock();
            s.stock.left = c.getInt("Left");
            s.stock.at = c.getLong("RestockAt");
            loadCounts(c.getList("Players", Tag.TAG_COMPOUND), s.buyers);
            s.sell.left = c.contains("SellLeft") ? c.getInt("SellLeft") : -1;
            s.sell.at = c.getLong("SellAt");
            loadCounts(c.getList("Sellers", Tag.TAG_COMPOUND), s.sellers);
            l.stock.put(c.getString("Key"), s);
        }
        for (Tag t : tag.getList("Draws", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Draw d = new Draw();
            for (int i : c.getIntArray("Shown")) {
                d.shown.add(i);
            }
            d.drawnAt = c.getLong("DrawnAt");
            d.nextAt = c.getLong("NextAt");
            l.draws.put(c.getString("Shop"), d);
        }
        return l;
    }

    private static ListTag saveCounts(Map<UUID, Count> counts) {
        ListTag out = new ListTag();
        for (var p : counts.entrySet()) {
            CompoundTag q = new CompoundTag();
            q.putUUID("Id", p.getKey());
            q.putInt("Left", p.getValue().left);
            q.putLong("At", p.getValue().at);
            out.add(q);
        }
        return out;
    }

    private static void loadCounts(ListTag list, Map<UUID, Count> into) {
        for (Tag p : list) {
            Count n = new Count();
            n.left = ((CompoundTag) p).getInt("Left");
            n.at = ((CompoundTag) p).getLong("At");
            into.put(((CompoundTag) p).getUUID("Id"), n);
        }
    }
}
