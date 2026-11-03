package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This server's alliances, saved with the overworld: every alliance, which alliance each team is in, and the waits a team that
 * broke an alliance serves before it may ally again with the teams that were in it. The record on a single server; with network
 * storage it becomes the local copy of the shared tables.
 */
public class AllianceLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_alliances";

    /** {@code team} may not ally with {@code other} before {@code untilMs} (it broke an alliance they shared). */
    public record Wait(UUID team, UUID other, long untilMs) {}

    /**
     * {@code team} and {@code other} were allied in {@code allianceName} until one of them left: until {@code untilMs} a steal by
     * either on the other still counts as breaking that alliance.
     */
    public record Former(UUID team, UUID other, UUID allianceId, String allianceName, long untilMs) {
        boolean between(UUID a, UUID b) {
            return team.equals(a) && other.equals(b) || team.equals(b) && other.equals(a);
        }
    }

    private final Map<UUID, Alliance> alliances = new HashMap<>();
    private final List<Former> formers = new ArrayList<>();
    private final Map<UUID, UUID> byTeam = new HashMap<>();
    private final List<Wait> waits = new ArrayList<>();

    public static AllianceLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AllianceLedger::load, AllianceLedger::new, DATA_NAME);
    }

    @Nullable
    public Alliance alliance(UUID id) {
        return alliances.get(id);
    }

    /** The alliance the team is in, or null. */
    @Nullable
    public Alliance allianceOf(UUID teamId) {
        UUID id = byTeam.get(teamId);
        return id == null ? null : alliances.get(id);
    }

    /** An alliance by name, case-insensitively; null when none has it. */
    @Nullable
    public Alliance byName(String name) {
        for (Alliance a : alliances.values()) {
            if (a.name.equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    public Collection<Alliance> all() {
        return alliances.values();
    }

    void add(Alliance a) {
        alliances.put(a.id, a);
        for (UUID t : a.teams().keySet()) {
            byTeam.put(t, a.id);
        }
        setDirty();
    }

    void joined(Alliance a, UUID teamId) {
        byTeam.put(teamId, a.id);
        setDirty();
    }

    void left(UUID teamId) {
        byTeam.remove(teamId);
        setDirty();
    }

    void remove(Alliance a) {
        alliances.remove(a.id);
        byTeam.values().removeIf(a.id::equals);
        setDirty();
    }

    /**
     * Puts an alliance read from the database in place of the local copy (same id), fixing which teams are in it. A team it holds
     * comes out of any other alliance the local copy still had it in (it left that one; the notice was missed).
     */
    void replace(Alliance a) {
        for (UUID t : a.teams().keySet()) {
            UUID before = byTeam.get(t);
            Alliance other = before == null || before.equals(a.id) ? null : alliances.get(before);
            if (other != null) {
                other.remove(t);
            }
        }
        Alliance old = alliances.put(a.id, a);
        if (old != null) {
            byTeam.values().removeIf(a.id::equals);
        }
        for (UUID t : a.teams().keySet()) {
            byTeam.put(t, a.id);
        }
        setDirty();
    }

    void changed() {
        setDirty();
    }

    // ------------------------------------------------------------------ waits after a break

    /** When the team may ally with {@code other} again; 0 when it may now. */
    public long waitUntil(UUID team, UUID other, long nowMs) {
        long until = 0;
        for (Wait w : waits) {
            if (w.untilMs() > nowMs && (w.team().equals(team) && w.other().equals(other) || w.team().equals(other) && w.other().equals(team))) {
                until = Math.max(until, w.untilMs());
            }
        }
        return until;
    }

    public List<Wait> waits() {
        return waits;
    }

    void addWait(Wait w) {
        long longest = w.untilMs();
        for (Wait x : waits) {
            if (x.team().equals(w.team()) && x.other().equals(w.other())) {
                longest = Math.max(longest, x.untilMs());
            }
        }
        waits.removeIf(x -> x.team().equals(w.team()) && x.other().equals(w.other()));
        waits.add(new Wait(w.team(), w.other(), longest));
        setDirty();
    }

    /** Adds the waits read from the database to the local ones (a read may predate a wait made here), dropping those that are over. */
    void mergeWaits(List<Wait> fresh) {
        fresh.forEach(this::addWait);
        dropOldWaits(System.currentTimeMillis());
        setDirty();
    }

    void dropOldWaits(long nowMs) {
        if (waits.removeIf(w -> w.untilMs() <= nowMs) | formers.removeIf(f -> f.untilMs() <= nowMs)) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------ teams that left lately

    /** The record that the two teams were allied lately, if one stands; null when none. */
    @Nullable
    public Former former(UUID a, UUID b, long nowMs) {
        Former best = null;
        for (Former f : formers) {
            if (f.untilMs() > nowMs && f.between(a, b) && (best == null || f.untilMs() > best.untilMs())) {
                best = f;
            }
        }
        return best;
    }

    /** Every standing record the team is in. */
    public List<Former> formersOf(UUID team, long nowMs) {
        List<Former> out = new ArrayList<>();
        for (Former f : formers) {
            if (f.untilMs() > nowMs && (f.team().equals(team) || f.other().equals(team))) {
                out.add(f);
            }
        }
        return out;
    }

    public List<Former> formers() {
        return formers;
    }

    void addFormer(Former f) {
        formers.removeIf(x -> x.between(f.team(), f.other()));
        formers.add(f);
        setDirty();
    }

    /** The team's records are used up (its steal counted as a break). */
    void spendFormers(UUID team) {
        if (formers.removeIf(f -> f.team().equals(team) || f.other().equals(team))) {
            setDirty();
        }
    }

    /** Puts the records read from the database in place of the local ones, keeping the changes made here lately ({@code added}, {@code spent}). */
    void replaceFormers(List<Former> fresh, List<Former> added, java.util.Set<UUID> spent, long nowMs) {
        formers.clear();
        for (Former f : fresh) {
            if (!spent.contains(f.team()) && !spent.contains(f.other())) {
                formers.add(f);
            }
        }
        for (Former f : added) {
            if (!spent.contains(f.team()) && !spent.contains(f.other()) && formers.stream().noneMatch(x -> x.between(f.team(), f.other()))) {
                formers.add(f);
            }
        }
        formers.removeIf(f -> f.untilMs() <= nowMs);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Alliance a : alliances.values()) {
            list.add(a.save());
        }
        tag.put("Alliances", list);
        ListTag w = new ListTag();
        for (Wait x : waits) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Team", x.team());
            t.putUUID("Other", x.other());
            t.putLong("Until", x.untilMs());
            w.add(t);
        }
        tag.put("Waits", w);
        ListTag fl = new ListTag();
        for (Former f : formers) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Team", f.team());
            t.putUUID("Other", f.other());
            t.putUUID("Alliance", f.allianceId());
            t.putString("Name", f.allianceName());
            t.putLong("Until", f.untilMs());
            fl.add(t);
        }
        tag.put("Formers", fl);
        return tag;
    }

    public static AllianceLedger load(CompoundTag tag) {
        AllianceLedger l = new AllianceLedger();
        for (Tag x : tag.getList("Alliances", Tag.TAG_COMPOUND)) {
            Alliance a = Alliance.load((CompoundTag) x);
            l.alliances.put(a.id, a);
            for (UUID t : a.teams().keySet()) {
                l.byTeam.put(t, a.id);
            }
        }
        for (Tag x : tag.getList("Waits", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            l.waits.add(new Wait(t.getUUID("Team"), t.getUUID("Other"), t.getLong("Until")));
        }
        for (Tag x : tag.getList("Formers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            l.formers.add(new Former(t.getUUID("Team"), t.getUUID("Other"), t.getUUID("Alliance"), t.getString("Name"), t.getLong("Until")));
        }
        return l;
    }
}
