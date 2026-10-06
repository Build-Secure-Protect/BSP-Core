package com.mrgregles.bsp_core.totem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One person on a totem's access list and what they may do: {@link #UPGRADES} (buy upgrades, change
 * chunks and projector settings), {@link #ALARM} (does not set it off), {@link #WARD} (not weakened
 * by it), {@link #MACHINES} (may open the totem's plasma machines).
 */
public record TotemAccess(UUID id, String name, int flags) {
    public static final int UPGRADES = 1, ALARM = 2, WARD = 4, MACHINES = 8, ALL = 15, MAX = 8;
    public static final String TAG = "Access";

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public TotemAccess with(int flag, boolean on) {
        return new TotemAccess(id, name, on ? flags | flag : flags & ~flag);
    }

    public static ListTag save(List<TotemAccess> list) {
        ListTag out = new ListTag();
        for (TotemAccess a : list) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", a.id);
            t.putString("Name", a.name);
            t.putInt("Flags", a.flags);
            out.add(t);
        }
        return out;
    }

    public static List<TotemAccess> load(CompoundTag tag) {
        List<TotemAccess> out = new ArrayList<>();
        for (Tag raw : tag.getList(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (t.hasUUID("Id") && out.size() < MAX) {
                out.add(new TotemAccess(t.getUUID("Id"), t.getString("Name"), t.getInt("Flags")));
            }
        }
        return out;
    }
}
