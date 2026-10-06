package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemAccess;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what with the blocks fed by an interface, gathered from every totem on it: owners and
 * players with Upgrades may change settings; those with Machines may open the machines; those with
 * Alarm or Ward are left alone by the projectors' Alarm and Ward.
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
    }

    public void addAll(PlasmaAccess other) {
        editors.addAll(other.editors);
        users.addAll(other.users);
        alarmSafe.addAll(other.alarmSafe);
        wardSafe.addAll(other.wardSafe);
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
