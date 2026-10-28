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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This world's marketplace records: the open lots and every player's Collect box. On a single server the record itself
 * (through {@link LocalMarket}); on a network only what is held here until the database takes it (a Collect box that could
 * not be written, lots from before the server joined the network, listings whose answer was lost, Collect box takes whose
 * rows still wait to be deleted), and the batch being written, so a write whose answer is lost is never made twice. A
 * ledger made with {@code new MarketLedger()} and not taken from the world is blank, for GameTests.
 */
public class MarketLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_market";

    /** Open lots by id, in listing order (oldest first). */
    private final Map<String, MarketLot> lots = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> collect = new HashMap<>();
    /** Network only: listings whose answer was lost and could not be asked about yet. */
    private final Map<String, MarketLot> unsure = new LinkedHashMap<>();
    /** Network only: Collect box takes delivered whose rows are still to be deleted. */
    private final List<String> deletes = new ArrayList<>();
    /** Network only: the batch being written, kept until the database has it. */
    @Nullable
    private Batch batch;

    /** What one write to the network carries; {@code attempt} makes a second try of the same batch do nothing. */
    public record Batch(String attempt, List<MarketLot> lots, List<MarketLot> unsure, Map<UUID, List<ItemStack>> boxes) {
        public boolean isEmpty() {
            return lots.isEmpty() && unsure.isEmpty() && boxes.isEmpty();
        }
    }

    public static MarketLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketLedger::load, MarketLedger::new, DATA_NAME);
    }

    public void put(MarketLot lot) {
        lots.put(lot.id, lot);
        setDirty();
    }

    @Nullable
    public MarketLot get(String id) {
        return lots.get(id);
    }

    @Nullable
    public MarketLot remove(String id) {
        MarketLot lot = lots.remove(id);
        if (lot != null) {
            setDirty();
        }
        return lot;
    }

    /** The open lots, newest first. */
    public List<MarketLot> open() {
        List<MarketLot> out = new ArrayList<>(lots.values());
        java.util.Collections.reverse(out);
        return out;
    }

    public void addCollect(UUID player, List<ItemStack> items) {
        List<ItemStack> box = collect.computeIfAbsent(player, k -> new ArrayList<>());
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                box.add(s.copy());
            }
        }
        if (box.isEmpty()) {
            collect.remove(player);
        }
        setDirty();
    }

    /** Empties the box and returns what was in it. */
    public List<ItemStack> takeCollect(UUID player) {
        List<ItemStack> box = collect.remove(player);
        if (box != null) {
            setDirty();
        }
        return box == null ? new ArrayList<>() : box;
    }

    public List<ItemStack> peekCollect(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        collect.getOrDefault(player, List.of()).forEach(s -> out.add(s.copy()));
        return out;
    }

    /** Drops the first {@code n} stacks of a player's box (the ones written elsewhere; later ones stay). */
    public void dropCollect(UUID player, int n) {
        List<ItemStack> box = collect.get(player);
        if (box == null) {
            return;
        }
        box.subList(0, Math.min(n, box.size())).clear();
        if (box.isEmpty()) {
            collect.remove(player);
        }
        setDirty();
    }

    /** The players with something in their Collect box here. */
    public List<UUID> collectors() {
        return new ArrayList<>(collect.keySet());
    }

    public void putUnsure(MarketLot lot) {
        unsure.put(lot.id, lot);
        setDirty();
    }

    public void addDelete(String attempt) {
        deletes.add(attempt);
        setDirty();
    }

    public List<String> deletes() {
        return new ArrayList<>(deletes);
    }

    public void dropDeletes(List<String> done) {
        deletes.removeAll(done);
        setDirty();
    }

    /** The batch being written: the one left from before (its answer was lost or it failed), else everything held now, moved out of the ledger into it; null when nothing is held. */
    @Nullable
    public Batch batch(java.util.function.Supplier<String> newAttempt) {
        if (batch == null && !(lots.isEmpty() && unsure.isEmpty() && collect.isEmpty())) {
            Map<UUID, List<ItemStack>> boxes = new HashMap<>();
            collect.forEach((p, items) -> boxes.put(p, new ArrayList<>(items)));
            batch = new Batch(newAttempt.get(), new ArrayList<>(lots.values()), new ArrayList<>(unsure.values()), boxes);
            lots.clear();
            unsure.clear();
            collect.clear();
            setDirty();
        }
        return batch;
    }

    /** The database has the batch. */
    public void batchWritten() {
        batch = null;
        setDirty();
    }

    /** What the batch being written holds for a player's Collect box (for the box's display). */
    public List<ItemStack> batchFor(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        if (batch != null) {
            batch.boxes().getOrDefault(player, List.of()).forEach(s -> out.add(s.copy()));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag ls = new ListTag();
        lots.values().forEach(l -> ls.add(l.save()));
        tag.put("Lots", ls);
        tag.put("Collect", boxesTag(collect));
        ListTag us = new ListTag();
        unsure.values().forEach(l -> us.add(l.save()));
        tag.put("Unsure", us);
        ListTag ds = new ListTag();
        deletes.forEach(d -> ds.add(net.minecraft.nbt.StringTag.valueOf(d)));
        tag.put("Deletes", ds);
        if (batch != null) {
            CompoundTag b = new CompoundTag();
            b.putString("Attempt", batch.attempt());
            ListTag bl = new ListTag();
            batch.lots().forEach(l -> bl.add(l.save()));
            b.put("Lots", bl);
            ListTag bu = new ListTag();
            batch.unsure().forEach(l -> bu.add(l.save()));
            b.put("Unsure", bu);
            b.put("Collect", boxesTag(batch.boxes()));
            tag.put("Batch", b);
        }
        return tag;
    }

    private static ListTag boxesTag(Map<UUID, List<ItemStack>> map) {
        ListTag boxes = new ListTag();
        map.forEach((id, items) -> {
            CompoundTag b = new CompoundTag();
            b.putUUID("Player", id);
            ListTag it = new ListTag();
            items.forEach(s -> it.add(s.save(new CompoundTag())));
            b.put("Items", it);
            boxes.add(b);
        });
        return boxes;
    }

    private static Map<UUID, List<ItemStack>> boxesOf(ListTag boxes) {
        Map<UUID, List<ItemStack>> out = new HashMap<>();
        for (int i = 0; i < boxes.size(); i++) {
            CompoundTag b = boxes.getCompound(i);
            List<ItemStack> items = new ArrayList<>();
            ListTag it = b.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < it.size(); j++) {
                ItemStack s = ItemStack.of(it.getCompound(j));
                if (!s.isEmpty()) {
                    items.add(s);
                }
            }
            if (!items.isEmpty()) {
                out.put(b.getUUID("Player"), items);
            }
        }
        return out;
    }

    private static List<MarketLot> lotsOf(ListTag ls) {
        List<MarketLot> out = new ArrayList<>();
        for (int i = 0; i < ls.size(); i++) {
            out.add(MarketLot.load(ls.getCompound(i)));
        }
        return out;
    }

    public static MarketLedger load(CompoundTag tag) {
        MarketLedger ledger = new MarketLedger();
        ListTag ls = tag.getList("Lots", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            MarketLot lot = MarketLot.load(ls.getCompound(i));
            ledger.lots.put(lot.id, lot);
        }
        ledger.collect.putAll(boxesOf(tag.getList("Collect", Tag.TAG_COMPOUND)));
        lotsOf(tag.getList("Unsure", Tag.TAG_COMPOUND)).forEach(l -> ledger.unsure.put(l.id, l));
        ListTag ds = tag.getList("Deletes", Tag.TAG_STRING);
        for (int i = 0; i < ds.size(); i++) {
            ledger.deletes.add(ds.getString(i));
        }
        if (tag.contains("Batch")) {
            CompoundTag b = tag.getCompound("Batch");
            ledger.batch = new Batch(b.getString("Attempt"), lotsOf(b.getList("Lots", Tag.TAG_COMPOUND)), lotsOf(b.getList("Unsure", Tag.TAG_COMPOUND)),
                    boxesOf(b.getList("Collect", Tag.TAG_COMPOUND)));
        }
        return ledger;
    }
}
