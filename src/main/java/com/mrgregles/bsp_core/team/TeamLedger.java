package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This server's teams, saved with the overworld: every team and which team each player is in. The
 * record on a single server; with network storage it becomes the local copy of the shared tables.
 */
public class TeamLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_teams";

    private final Map<UUID, Team> teams = new HashMap<>();
    private final Map<UUID, UUID> byPlayer = new HashMap<>();

    public static TeamLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TeamLedger::load, TeamLedger::new, DATA_NAME);
    }

    @Nullable
    public Team team(UUID id) {
        return teams.get(id);
    }

    /** The team the player is in, or null. */
    @Nullable
    public Team teamOf(UUID player) {
        UUID id = byPlayer.get(player);
        return id == null ? null : teams.get(id);
    }

    /** A team by name, case-insensitively; null when none has it. */
    @Nullable
    public Team byName(String name) {
        for (Team t : teams.values()) {
            if (t.name.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    public Collection<Team> all() {
        return teams.values();
    }

    void add(Team team) {
        teams.put(team.id, team);
        for (UUID p : team.members().keySet()) {
            byPlayer.put(p, team.id);
        }
        setDirty();
    }

    void joined(Team team, UUID player) {
        byPlayer.put(player, team.id);
        setDirty();
    }

    void left(UUID player) {
        byPlayer.remove(player);
        setDirty();
    }

    void remove(Team team) {
        teams.remove(team.id);
        byPlayer.values().removeIf(team.id::equals);
        setDirty();
    }

    /** Puts a team read from the database in place of the local copy (same id), fixing who is in it. */
    void replace(Team team) {
        Team old = teams.put(team.id, team);
        if (old != null) {
            byPlayer.values().removeIf(team.id::equals);
        }
        for (UUID p : team.members().keySet()) {
            byPlayer.put(p, team.id);
        }
        setDirty();
    }

    /** Marks a team changed in place (name, colour, leader, board). */
    void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Team t : teams.values()) {
            list.add(t.save());
        }
        tag.put("Teams", list);
        return tag;
    }

    public static TeamLedger load(CompoundTag tag) {
        TeamLedger l = new TeamLedger();
        for (Tag x : tag.getList("Teams", Tag.TAG_COMPOUND)) {
            Team t = Team.load((CompoundTag) x);
            l.teams.put(t.id, t);
            for (UUID p : t.members().keySet()) {
                l.byPlayer.put(p, t.id);
            }
        }
        return l;
    }
}
