package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This world's bounties: the open ones and a history of closed ones (newest last, at most {@link #HISTORY}). On a single server
 * the record itself (through {@link LocalBounties}). A ledger made with {@code new BountyLedger()} and not taken from the world
 * is blank, for GameTests.
 */
public class BountyLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_bounties";
    public static final int HISTORY = 500;

    /** Open bounties by id, oldest first. */
    private final Map<String, Bounty> open = new LinkedHashMap<>();
    private final List<BountyStore.Closed> closed = new ArrayList<>();
    /** Network only: coin bounties whose placing's answer was lost (their coins are held here until the database says). */
    private final Map<String, Bounty> unsure = new LinkedHashMap<>();
    /** Kills that could not be claimed yet (the network could not be reached): tried again for a while. */
    private final List<PendingKill> kills = new ArrayList<>();

    /** A kill waiting to be claimed. */
    public record PendingKill(UUID target, String targetName, UUID hunter, String hunterName, long killMs) {}

    public static BountyLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BountyLedger::load, BountyLedger::new, DATA_NAME);
    }

    public void put(Bounty b) {
        open.put(b.id, b);
        setDirty();
    }

    @Nullable
    public Bounty get(String id) {
        return open.get(id);
    }

    @Nullable
    public Bounty remove(String id) {
        Bounty b = open.remove(id);
        if (b != null) {
            setDirty();
        }
        return b;
    }

    /** The open bounties, newest first. */
    public List<Bounty> open() {
        List<Bounty> out = new ArrayList<>(open.values());
        Collections.reverse(out);
        return out;
    }

    public void addClosed(BountyStore.Closed c) {
        closed.add(c);
        while (closed.size() > HISTORY) {
            closed.remove(0);
        }
        setDirty();
    }

    /** The closed bounties, newest first. */
    public List<BountyStore.Closed> closed() {
        List<BountyStore.Closed> out = new ArrayList<>(closed);
        Collections.reverse(out);
        return out;
    }

    public void putUnsure(Bounty b) {
        unsure.put(b.id, b);
        setDirty();
    }

    @Nullable
    public Bounty dropUnsure(String id) {
        Bounty b = unsure.remove(id);
        if (b != null) {
            setDirty();
        }
        return b;
    }

    public List<Bounty> unsure() {
        return new ArrayList<>(unsure.values());
    }

    /** Keeps a kill to claim later (once per hunter and target). */
    public void addKill(PendingKill k) {
        if (kills.stream().noneMatch(x -> x.target().equals(k.target()) && x.hunter().equals(k.hunter()))) {
            kills.add(k);
            setDirty();
        }
    }

    /** Every kept kill, taken out of the ledger. */
    public List<PendingKill> takeKills() {
        List<PendingKill> out = new ArrayList<>(kills);
        if (!kills.isEmpty()) {
            kills.clear();
            setDirty();
        }
        return out;
    }

    public List<PendingKill> kills() {
        return new ArrayList<>(kills);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag os = new ListTag();
        open.values().forEach(b -> os.add(b.save()));
        tag.put("Open", os);
        ListTag cs = new ListTag();
        for (BountyStore.Closed c : closed) {
            CompoundTag t = new CompoundTag();
            t.put("Bounty", c.bounty().save());
            t.putString("Ending", c.ending().name());
            t.putLong("At", c.closedMs());
            if (c.hunter() != null) {
                t.putUUID("Hunter", c.hunter());
            }
            t.putString("HunterName", c.hunterName());
            t.putString("By", c.by());
            t.putBoolean("Bonus", c.bonusPaid());
            cs.add(t);
        }
        tag.put("Closed", cs);
        ListTag us = new ListTag();
        unsure.values().forEach(b -> us.add(b.save()));
        tag.put("Unsure", us);
        ListTag ks = new ListTag();
        for (PendingKill k : kills) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Target", k.target());
            t.putString("TargetName", k.targetName());
            t.putUUID("Hunter", k.hunter());
            t.putString("HunterName", k.hunterName());
            t.putLong("At", k.killMs());
            ks.add(t);
        }
        tag.put("Kills", ks);
        return tag;
    }

    public static BountyLedger load(CompoundTag tag) {
        BountyLedger l = new BountyLedger();
        for (Tag t : tag.getList("Open", Tag.TAG_COMPOUND)) {
            Bounty b = Bounty.load((CompoundTag) t);
            l.open.put(b.id, b);
        }
        for (Tag t : tag.getList("Closed", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BountyStore.Ending ending;
            try {
                ending = BountyStore.Ending.valueOf(c.getString("Ending"));
            } catch (IllegalArgumentException e) {
                ending = BountyStore.Ending.REMOVED;
            }
            UUID hunter = c.hasUUID("Hunter") ? c.getUUID("Hunter") : null;
            l.closed.add(new BountyStore.Closed(Bounty.load(c.getCompound("Bounty")), ending, c.getLong("At"), hunter, c.getString("HunterName"),
                    c.getString("By"), c.getBoolean("Bonus")));
        }
        for (Tag t : tag.getList("Unsure", Tag.TAG_COMPOUND)) {
            Bounty b = Bounty.load((CompoundTag) t);
            l.unsure.put(b.id, b);
        }
        for (Tag t : tag.getList("Kills", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            l.kills.add(new PendingKill(c.getUUID("Target"), c.getString("TargetName"), c.getUUID("Hunter"), c.getString("HunterName"), c.getLong("At")));
        }
        return l;
    }
}
