package com.mrgregles.bsp_core.looks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one player has for dressing totems, as last checked while they were online, and the looks they saved. Kept for players
 * who have, or once had, dressing or a hat; shared across the network so a server knows it while the player is elsewhere.
 */
public final class LookOwner {
    /** Looks an owner may keep to put on any of their totems with one click. */
    public static final int MAX_SAVED = 5;
    /** {@link #hatChoice} for "no hat": the player took the hat off. */
    public static final String NO_HAT = "-";

    /** Whether the player had dressing ({@code bsp_core.looks}) when last checked. */
    public boolean dressing;
    /** Every hat they had when last checked, in the tab's order. */
    public final List<String> owned = new ArrayList<>();
    /** Hats they have by the hat's own permission (bought or given), and when each was first seen (ms). */
    public final Map<String, Long> bought = new LinkedHashMap<>();
    /** The hat a player without dressing picked for their totems: "" for the newest bought, {@link #NO_HAT}, or a hat id. */
    public String hatChoice = "";
    /** Looks the owner saved, at most {@link #MAX_SAVED}. */
    public final List<TotemLook> saved = new ArrayList<>();
    /** Looks of this player's made before this (ms) were taken off by staff, wherever their totems are. */
    public long clearedBefore;
    /** When the record last changed (ms): between servers the newest record wins. */
    public long updatedMs;

    /** Drops the saved looks made before a staff clear (one may arrive from a server that had not heard of the clear yet). */
    public void dropCleared() {
        saved.removeIf(l -> l.made() < clearedBefore);
    }

    /** The hat bought most recently that the player still has, or "" for none. */
    public String newestBought() {
        String best = "";
        long at = Long.MIN_VALUE;
        for (Map.Entry<String, Long> e : bought.entrySet()) {
            if (owned.contains(e.getKey()) && e.getValue() > at) {
                best = e.getKey();
                at = e.getValue();
            }
        }
        return best;
    }

    /** The hat this player's totems wear when no look of theirs chooses one (they have no dressing, or the totem has no look). */
    public String autoHat() {
        if (NO_HAT.equals(hatChoice)) {
            return "";
        }
        if (!hatChoice.isEmpty() && owned.contains(hatChoice)) {
            return hatChoice;
        }
        return newestBought();
    }

    /** Whether there is anything worth keeping: a record for every player would only fill the database. */
    public boolean worthKeeping() {
        return dressing || !owned.isEmpty() || !bought.isEmpty() || !saved.isEmpty() || clearedBefore > 0 || !hatChoice.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Dressing", dressing);
        ListTag o = new ListTag();
        owned.forEach(h -> o.add(StringTag.valueOf(h)));
        t.put("Owned", o);
        CompoundTag b = new CompoundTag();
        bought.forEach(b::putLong);
        t.put("Bought", b);
        t.putString("HatChoice", hatChoice);
        ListTag s = new ListTag();
        saved.forEach(l -> s.add(l.save()));
        t.put("Saved", s);
        t.putLong("ClearedBefore", clearedBefore);
        t.putLong("Updated", updatedMs);
        return t;
    }

    public static LookOwner load(CompoundTag t) {
        LookOwner r = new LookOwner();
        r.dressing = t.getBoolean("Dressing");
        for (Tag h : t.getList("Owned", Tag.TAG_STRING)) {
            if (TotemHats.known(h.getAsString())) {
                r.owned.add(h.getAsString());
            }
        }
        CompoundTag b = t.getCompound("Bought");
        for (String k : b.getAllKeys()) {
            if (TotemHats.known(k)) {
                r.bought.put(k, b.getLong(k));
            }
        }
        r.hatChoice = t.getString("HatChoice");
        r.clearedBefore = t.getLong("ClearedBefore");
        for (Tag l : t.getList("Saved", Tag.TAG_COMPOUND)) {
            TotemLook look = TotemLook.load((CompoundTag) l);
            if (look != null && r.saved.size() < MAX_SAVED && look.made() >= r.clearedBefore) {
                r.saved.add(look); // a saved look older than a staff clear is gone for good
            }
        }
        r.updatedMs = t.getLong("Updated");
        return r;
    }

    public LookOwner copy() {
        return load(save());
    }
}
