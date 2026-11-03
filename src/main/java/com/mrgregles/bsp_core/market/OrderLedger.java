package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This world's buy order records: on a single server the open orders themselves (through {@link LocalOrders}); on a network
 * only the fills whose answer was lost ({@link Unsure}), holding the seller's items until the database can say whether the
 * fill went in. A ledger made with {@code new OrderLedger()} and not taken from the world is blank, for GameTests.
 */
public class OrderLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_orders";

    /** A fill whose answer was lost: {@code attempt} names it in the database; the items are the seller's unless it went in. */
    public record Unsure(String attempt, UUID seller, List<ItemStack> items) {}

    /** Open orders by id, in order of placing (oldest first). */
    private final Map<String, BuyOrder> orders = new LinkedHashMap<>();
    private final List<Unsure> unsure = new ArrayList<>();

    public static OrderLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OrderLedger::load, OrderLedger::new, DATA_NAME);
    }

    public void put(BuyOrder order) {
        orders.put(order.id, order);
        setDirty();
    }

    @Nullable
    public BuyOrder get(String id) {
        return orders.get(id);
    }

    @Nullable
    public BuyOrder remove(String id) {
        BuyOrder o = orders.remove(id);
        if (o != null) {
            setDirty();
        }
        return o;
    }

    /** The open orders, newest first. */
    public List<BuyOrder> open() {
        List<BuyOrder> out = new ArrayList<>(orders.values());
        java.util.Collections.reverse(out);
        return out;
    }

    public void addUnsure(Unsure u) {
        unsure.add(u);
        setDirty();
    }

    public List<Unsure> unsure() {
        return new ArrayList<>(unsure);
    }

    public void dropUnsure(String attempt) {
        if (unsure.removeIf(u -> u.attempt().equals(attempt))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag os = new ListTag();
        orders.values().forEach(o -> os.add(o.save()));
        tag.put("Orders", os);
        ListTag us = new ListTag();
        for (Unsure u : unsure) {
            CompoundTag t = new CompoundTag();
            t.putString("Attempt", u.attempt());
            t.putUUID("Seller", u.seller());
            ListTag items = new ListTag();
            u.items().forEach(s -> items.add(s.save(new CompoundTag())));
            t.put("Items", items);
            us.add(t);
        }
        tag.put("Unsure", us);
        return tag;
    }

    public static OrderLedger load(CompoundTag tag) {
        OrderLedger ledger = new OrderLedger();
        ListTag os = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < os.size(); i++) {
            BuyOrder o = BuyOrder.load(os.getCompound(i));
            ledger.orders.put(o.id, o);
        }
        ListTag us = tag.getList("Unsure", Tag.TAG_COMPOUND);
        for (int i = 0; i < us.size(); i++) {
            CompoundTag t = us.getCompound(i);
            List<ItemStack> items = new ArrayList<>();
            ListTag it = t.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < it.size(); j++) {
                ItemStack s = ItemStack.of(it.getCompound(j));
                if (!s.isEmpty()) {
                    items.add(s);
                }
            }
            ledger.unsure.add(new Unsure(t.getString("Attempt"), t.getUUID("Seller"), items));
        }
        return ledger;
    }
}
