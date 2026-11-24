package com.mrgregles.bsp_core.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's machine tracking: the tag slots they bought and the Remote Control unlock (each counted only in the season it was bought
 * in), their tags, and their Holo Grid layout (pages, presets, the pinned box), which no reset touches. Kept per server in
 * {@link TrackerOwners} and in the network database, where the newer record wins and a player reset is never lost.
 */
public final class TrackerRecord {
    /** Tag slots bought on top of the free ones, and the season they were bought in. */
    public int bought;
    public int boughtSeason;
    /** When each bought slot was bought (ms), so a reset reaching a copy late drops exactly the purchases made before it. */
    public final List<Long> boughtTimes = new ArrayList<>();
    /** The Remote Control unlock, the season it was bought in, and when. */
    public boolean remote;
    public int remoteSeason;
    public long remoteMs;
    public final List<TrackedTag> tags = new ArrayList<>();
    /** The Holo Grid's pages, presets and pinned box, as the grid stores them. Kept through resets and season ends. */
    public CompoundTag layout = new CompoundTag();
    /** When the record last changed (ms); the newer copy wins on the network. */
    public long updatedMs;
    /** The latest player reset applied to it (ms): a copy from before that reset is reset again wherever it turns up. */
    public long resetMs;

    /** Bought slots that count this season. */
    public int boughtIn(int season) {
        return boughtSeason == season ? bought : 0;
    }

    /** Whether the Remote Control unlock counts this season. */
    public boolean remoteIn(int season) {
        return remote && remoteSeason == season;
    }

    /** A slot bought now, in {@code season}. */
    public void addPurchase(int season, long ms) {
        if (boughtSeason != season) {
            bought = 0;
            boughtTimes.clear();
            boughtSeason = season;
        }
        bought++;
        boughtTimes.add(ms);
    }

    /**
     * Drops what an earlier season left: tags from an earlier season, and slots and the unlock bought in an earlier season. Never what a
     * later season made (a server that has not heard of a season end yet must not destroy the new season's tags and purchases). Returns
     * whether anything changed.
     */
    public boolean prune(int season) {
        boolean changed = tags.removeIf(t -> t.season() < season);
        if (boughtSeason < season) {
            changed |= bought != 0;
            bought = 0;
            boughtTimes.clear();
            boughtSeason = season;
        }
        if (remote && remoteSeason < season) {
            remote = false;
            changed = true;
        }
        return changed;
    }

    /** The player reset at {@code ms}, on the server that ran it: tags, bought slots and the unlock go, the layout stays. */
    public boolean reset(long ms) {
        if (ms <= resetMs) {
            return false;
        }
        resetMs = ms;
        tags.clear();
        bought = 0;
        boughtTimes.clear();
        remote = false;
        return true;
    }

    /**
     * The player reset at {@code ms}, reaching a copy late: everything made at or before the reset goes (tags by when they were made, slots
     * by when they were bought, the unlock by when it was bought); what the player made after it stays, as their new start. The layout
     * stays. Returns whether anything was dropped.
     */
    public boolean resetIfOlder(long ms) {
        if (ms <= resetMs) {
            return false;
        }
        resetMs = ms;
        boolean changed = tags.removeIf(t -> t.madeMs() <= ms);
        int before = bought;
        boughtTimes.removeIf(b -> b <= ms);
        bought = boughtTimes.size();
        changed |= bought != before;
        if (remote && remoteMs <= ms) {
            remote = false;
            changed = true;
        }
        return changed;
    }

    public TrackerRecord copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("bought", bought);
        t.putInt("boughtSeason", boughtSeason);
        t.putLongArray("boughtTimes", boughtTimes.stream().mapToLong(Long::longValue).toArray());
        t.putBoolean("remote", remote);
        t.putInt("remoteSeason", remoteSeason);
        t.putLong("remoteMs", remoteMs);
        ListTag list = new ListTag();
        tags.forEach(tag -> list.add(tag.save()));
        t.put("tags", list);
        t.put("layout", layout.copy());
        t.putLong("updated", updatedMs);
        t.putLong("reset", resetMs);
        return t;
    }

    public static TrackerRecord load(CompoundTag t) {
        TrackerRecord r = new TrackerRecord();
        r.bought = Math.max(0, t.getInt("bought"));
        r.boughtSeason = t.getInt("boughtSeason");
        for (long ms : t.getLongArray("boughtTimes")) {
            r.boughtTimes.add(ms);
        }
        while (r.boughtTimes.size() < r.bought) {
            r.boughtTimes.add(0L); // bought with no time kept: treated as old
        }
        while (r.boughtTimes.size() > r.bought) {
            r.boughtTimes.remove(r.boughtTimes.size() - 1);
        }
        r.remote = t.getBoolean("remote");
        r.remoteSeason = t.getInt("remoteSeason");
        r.remoteMs = t.getLong("remoteMs");
        ListTag list = t.getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TrackedTag tag = TrackedTag.load(list.getCompound(i));
            if (tag != null && r.tags.stream().noneMatch(o -> o.id().equals(tag.id()))) {
                r.tags.add(tag);
            }
        }
        r.layout = t.getCompound("layout").copy();
        r.updatedMs = t.getLong("updated");
        r.resetMs = t.getLong("reset");
        return r;
    }

    /** Whether the record holds anything worth keeping (a player who never bought, tagged or laid out anything needs no record). */
    public boolean worthKeeping() {
        return bought > 0 || remote || !tags.isEmpty() || !layout.isEmpty() || resetMs > 0;
    }
}
