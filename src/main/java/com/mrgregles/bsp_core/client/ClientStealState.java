package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.StealStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side record of steal attempts the local player is part of, for the HUD. */
public final class ClientStealState {
    /** Drop an entry if no update arrives for this long (server sends every second). */
    private static final long STALE_TICKS = 60;
    /** How long a finished attempt stays on screen. */
    private static final long RESULT_TICKS = 100;

    public record Entry(StealStatusPacket packet, long receivedTick) {}

    private static final Map<BlockPos, Entry> ENTRIES = new LinkedHashMap<>();

    private ClientStealState() {}

    public static void accept(StealStatusPacket packet) {
        long now = tick();
        ENTRIES.put(packet.pos(), new Entry(packet, now));
    }

    /** Live entries, with stale and old finished ones pruned. */
    public static Iterable<Entry> entries() {
        long now = tick();
        ENTRIES.values().removeIf(e -> {
            long age = now - e.receivedTick();
            return e.packet().outcome() == StealStatusPacket.OUTCOME_ACTIVE ? age > STALE_TICKS : age > RESULT_TICKS;
        });
        return ENTRIES.values();
    }

    private static long tick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }
}
