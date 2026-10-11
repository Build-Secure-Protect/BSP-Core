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
 * copy of the database on a network), global stock left and restock times, per-player stock, and
 * each rotating shop's current draw.
 */
public class ShopLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_shops";

    /** Global stock of one shop item: what is left and when it refills (0 = not counting). */
    public static final class Stock {
        public int left = -1;
        public long restockAt;
        public final Map<UUID, Integer> perPlayer = new HashMap<>();
    }

    /** A rotating shop's draw: the item indexes shown and when the next draw is due. */
    public static final class Draw {
        public final List<Integer> shown = new ArrayList<>();
        public long nextAt;
    }

    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<String, Stock> stock = new HashMap<>();
    private final Map<String, Draw> draws = new HashMap<>();

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
            t.putInt("Left", e.getValue().left);
            t.putLong("RestockAt", e.getValue().restockAt);
            ListTag pp = new ListTag();
            for (var p : e.getValue().perPlayer.entrySet()) {
                CompoundTag q = new CompoundTag();
                q.putUUID("Id", p.getKey());
                q.putInt("Left", p.getValue());
                pp.add(q);
            }
            t.put("Players", pp);
            st.add(t);
        }
        tag.put("Stock", st);
        ListTag dl = new ListTag();
        for (var e : draws.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Shop", e.getKey());
            t.putIntArray("Shown", e.getValue().shown.stream().mapToInt(Integer::intValue).toArray());
            t.putLong("NextAt", e.getValue().nextAt);
            dl.add(t);
        }
        tag.put("Draws", dl);
        return tag;
    }

    public static ShopLedger load(CompoundTag tag) {
        ShopLedger l = new ShopLedger();
        for (Tag t : tag.getList("Shops", Tag.TAG_COMPOUND)) {
            Shop s = Shop.load((CompoundTag) t);
            l.shops.put(s.id, s);
        }
        for (Tag t : tag.getList("Stock", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Stock s = new Stock();
            s.left = c.getInt("Left");
            s.restockAt = c.getLong("RestockAt");
            for (Tag p : c.getList("Players", Tag.TAG_COMPOUND)) {
                s.perPlayer.put(((CompoundTag) p).getUUID("Id"), ((CompoundTag) p).getInt("Left"));
            }
            l.stock.put(c.getString("Key"), s);
        }
        for (Tag t : tag.getList("Draws", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Draw d = new Draw();
            for (int i : c.getIntArray("Shown")) {
                d.shown.add(i);
            }
            d.nextAt = c.getLong("NextAt");
            l.draws.put(c.getString("Shop"), d);
        }
        return l;
    }
}
