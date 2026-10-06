package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/** What is flowing through each cable right now, in mB per tick, as the interfaces last reported it. Server side, in memory. */
public final class PlasmaFlow {
    private record Entry(int perTick, long at) {
    }

    private static final Map<GlobalPos, Entry> FLOW = new HashMap<>();

    private PlasmaFlow() {}

    static void put(ServerLevel level, BlockPos pos, int perTick) {
        FLOW.put(GlobalPos.of(level.dimension(), pos.immutable()), new Entry(perTick, level.getGameTime()));
    }

    /** mB per tick through the cable at {@code pos}, or 0 if nothing has been reported in the last two seconds. */
    public static int at(ServerLevel level, BlockPos pos) {
        Entry e = FLOW.get(GlobalPos.of(level.dimension(), pos));
        return e == null || level.getGameTime() - e.at > 45 ? 0 : e.perTick;
    }
}
