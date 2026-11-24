package com.mrgregles.bsp_core.client.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of the player's machine tracking: the view the server sends ({@code TrackerSyncPacket}) and the latest reading of each
 * watched machine ({@code TrackerSnapshotPacket}), with the time it arrived here. Read by the tagger's screen, the Holo Grid, the popout,
 * the pinned box and the green outline. Client thread only.
 */
public final class ClientTracking {
    private static CompoundTag view = new CompoundTag();
    private static final Map<String, CompoundTag> READINGS = new HashMap<>();
    /** Bumped on every change, so screens and the popout redraw only when something changed. */
    private static int version;

    private ClientTracking() {}

    public static void accept(CompoundTag v) {
        view = v;
        Set<String> ids = new HashSet<>();
        ListTag tags = v.getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            ids.add(tags.getCompound(i).getString("id"));
        }
        READINGS.keySet().retainAll(ids);
        version++;
        GridModel.refresh();
        WatchSet.update();
    }

    public static void reading(String id, CompoundTag reading) {
        CompoundTag was = READINGS.get(id);
        long now = System.currentTimeMillis();
        if (reading.getByte("st") != 0 && was != null && was.contains("block")) {
            // asleep, gone or offline: keep the last reading to show, marked with the new status and when it was last live (this clock)
            CompoundTag kept = was.copy();
            kept.putByte("st", reading.getByte("st"));
            kept.putLong("liveMs", was.contains("liveMs") ? was.getLong("liveMs") : was.getLong("arrived"));
            kept.remove("op");
            READINGS.put(id, kept);
        } else {
            CompoundTag r = reading.copy();
            r.putLong("arrived", now);
            READINGS.put(id, r);
        }
        version++;
    }

    public static CompoundTag view() {
        return view;
    }

    public static CompoundTag reading(String id) {
        CompoundTag r = READINGS.get(id);
        return r == null ? new CompoundTag() : r;
    }

    public static int version() {
        return version;
    }

    /** Forgets everything (on leaving a server). */
    public static void clear() {
        view = new CompoundTag();
        READINGS.clear();
        version++;
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLeave(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
            GridModel.clear();
        }
    }
}
