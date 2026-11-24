package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerWatchPacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells the server which machines this player is looking at: the boxes of the Holo Grid's page while it is open, the popout's page while
 * the popout is open, and the pinned box while it shows. Sent only when that set changes; empty when nothing shows a machine.
 */
public final class WatchSet {
    private static List<String> sent = List.of();

    private WatchSet() {}

    public static void update() {
        if (Minecraft.getInstance().getConnection() == null) {
            sent = List.of();
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        boolean grid = Minecraft.getInstance().screen instanceof HoloGridScreen;
        if (grid || Popout.isOpen()) {
            for (GridModel.Box b : GridModel.page().boxes) {
                add(ids, b);
            }
            if (GridModel.page().boxes.stream().anyMatch(b -> b.src.equals("all"))) {
                ClientTracking.view().getList("tags", 10).forEach(t -> ids.add(((net.minecraft.nbt.CompoundTag) t).getString("id")));
            }
        }
        GridModel.Box pin = GridModel.pinned();
        if (pin != null && BSPClientConfig.pinShown()) {
            add(ids, pin);
        }
        List<String> now = new ArrayList<>(ids);
        if (now.size() > 32) {
            now = now.subList(0, 32);
        }
        if (!now.equals(sent)) {
            sent = List.copyOf(now);
            BSPNetwork.CHANNEL.sendToServer(new TrackerWatchPacket(sent));
        }
    }

    private static void add(Set<String> ids, GridModel.Box b) {
        String id = b.tagId();
        if (id != null) {
            ids.add(id);
        }
    }

    public static void reset() {
        sent = List.of();
    }

    private static boolean later;

    /** Updates at the next client tick, when Minecraft's screen is the new one. */
    public static void updateLater() {
        later = true;
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Tick {
        private Tick() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onWatchSetTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && later) {
                later = false;
                update();
            }
        }
    }
}
