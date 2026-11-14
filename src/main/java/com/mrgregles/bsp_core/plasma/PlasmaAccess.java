package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemAccess;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what with the blocks fed by an interface, gathered from every totem on it: owners and
 * players with Upgrades may change settings; those with Machines may open the machines; those with
 * Alarm or Ward are left alone by the projectors' Alarm and Ward. Teammates count with every flag;
 * allies with the perks the owner's team gives them.
 */
public final class PlasmaAccess {
    public final Set<UUID> editors = new HashSet<>(), users = new HashSet<>(), alarmSafe = new HashSet<>(), wardSafe = new HashSet<>();

    public static PlasmaAccess of(ShatterTotemBlockEntity totem) {
        PlasmaAccess out = new PlasmaAccess();
        out.add(totem);
        return out;
    }

    public void add(ShatterTotemBlockEntity totem) {
        totem.getOwner().ifPresent(o -> {
            editors.add(o.uuid());
            users.add(o.uuid());
            alarmSafe.add(o.uuid());
            wardSafe.add(o.uuid());
        });
        for (TotemAccess a : totem.access()) {
            if (a.has(TotemAccess.UPGRADES)) {
                editors.add(a.id());
            }
            if (a.has(TotemAccess.MACHINES)) {
                users.add(a.id());
            }
            if (a.has(TotemAccess.ALARM)) {
                alarmSafe.add(a.id());
            }
            if (a.has(TotemAccess.WARD)) {
                wardSafe.add(a.id());
            }
        }
        // teammates count as friends with every flag
        if (totem.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && com.mrgregles.bsp_core.team.Teams.enabled()) {
            totem.getOwner().ifPresent(o -> {
                for (UUID m : com.mrgregles.bsp_core.api.BspTeams.teammates(sl.getServer(), o.uuid())) {
                    editors.add(m);
                    users.add(m);
                    alarmSafe.add(m);
                    wardSafe.add(m);
                }
            });
        }
        // allies get what the owner's team gives them (never Upgrades)
        if (totem.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && com.mrgregles.bsp_core.team.Alliances.enabled()) {
            totem.getOwner().ifPresent(o -> {
                for (UUID m : com.mrgregles.bsp_core.team.Alliances.allies(sl.getServer(), o.uuid())) {
                    int perks = com.mrgregles.bsp_core.team.Alliances.perksFor(sl.getServer(), o.uuid(), m);
                    if ((perks & TotemAccess.MACHINES) != 0) {
                        users.add(m);
                    }
                    if ((perks & TotemAccess.ALARM) != 0) {
                        alarmSafe.add(m);
                    }
                    if ((perks & TotemAccess.WARD) != 0) {
                        wardSafe.add(m);
                    }
                }
            });
        }
    }

    /**
     * The access a block a player placed carries (a Tank Extractor): the player with every flag, their teammates with every flag, and
     * their allies with what the player's team gives them (never Upgrades), as for a totem of theirs without its friends list.
     */
    public static PlasmaAccess forPlayer(net.minecraft.server.MinecraftServer server, UUID owner) {
        PlasmaAccess out = new PlasmaAccess();
        out.editors.add(owner);
        out.users.add(owner);
        out.alarmSafe.add(owner);
        out.wardSafe.add(owner);
        if (com.mrgregles.bsp_core.team.Teams.enabled()) {
            for (UUID m : com.mrgregles.bsp_core.api.BspTeams.teammates(server, owner)) {
                out.editors.add(m);
                out.users.add(m);
                out.alarmSafe.add(m);
                out.wardSafe.add(m);
            }
        }
        if (com.mrgregles.bsp_core.team.Alliances.enabled()) {
            for (UUID m : com.mrgregles.bsp_core.team.Alliances.allies(server, owner)) {
                int perks = com.mrgregles.bsp_core.team.Alliances.perksFor(server, owner, m);
                if ((perks & TotemAccess.MACHINES) != 0) {
                    out.users.add(m);
                }
                if ((perks & TotemAccess.ALARM) != 0) {
                    out.alarmSafe.add(m);
                }
                if ((perks & TotemAccess.WARD) != 0) {
                    out.wardSafe.add(m);
                }
            }
        }
        return out;
    }

    /** All four lists, as {@code <key>Editors}, {@code Users}, {@code Alarm} and {@code Ward} (each UUID as two longs). */
    public void save(net.minecraft.nbt.CompoundTag tag, String key) {
        tag.putLongArray(key + "Editors", ids(editors));
        tag.putLongArray(key + "Users", ids(users));
        tag.putLongArray(key + "Alarm", ids(alarmSafe));
        tag.putLongArray(key + "Ward", ids(wardSafe));
    }

    public static PlasmaAccess load(net.minecraft.nbt.CompoundTag tag, String key) {
        PlasmaAccess out = new PlasmaAccess();
        uuids(tag.getLongArray(key + "Editors"), out.editors);
        uuids(tag.getLongArray(key + "Users"), out.users);
        uuids(tag.getLongArray(key + "Alarm"), out.alarmSafe);
        uuids(tag.getLongArray(key + "Ward"), out.wardSafe);
        return out;
    }

    private static long[] ids(Set<UUID> set) {
        long[] out = new long[set.size() * 2];
        int i = 0;
        for (UUID id : set) {
            out[i++] = id.getMostSignificantBits();
            out[i++] = id.getLeastSignificantBits();
        }
        return out;
    }

    private static void uuids(long[] ids, Set<UUID> into) {
        for (int i = 0; i + 1 < ids.length; i += 2) {
            into.add(new UUID(ids[i], ids[i + 1]));
        }
    }

    public void addAll(PlasmaAccess other) {
        editors.addAll(other.editors);
        users.addAll(other.users);
        alarmSafe.addAll(other.alarmSafe);
        wardSafe.addAll(other.wardSafe);
    }

    /** Whether {@code other}'s owners are among this access's owners or the players its Ward spares (the same team, a friend, an ally). */
    public boolean trusts(PlasmaAccess other) {
        for (UUID id : other.editors) {
            if (editors.contains(id) || wardSafe.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public PlasmaAccess copy() {
        PlasmaAccess out = new PlasmaAccess();
        out.addAll(this);
        return out;
    }

    public boolean isEmpty() {
        return editors.isEmpty() && users.isEmpty();
    }
}
