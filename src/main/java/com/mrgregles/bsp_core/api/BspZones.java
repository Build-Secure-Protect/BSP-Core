package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.zone.ZoneLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Anti Totem zones: whether a rule blocks something at a position. Rule keys are the constants here. */
public final class BspZones {
    private BspZones() {}

    /** No totem may be placed here. */
    public static final String TOTEM_PLACE = ZoneLedger.TOTEM_PLACE;
    /** No totem may be dropped here. */
    public static final String TOTEM_DROP = ZoneLedger.TOTEM_DROP;
    /** For a teleport mod to enforce: no teleporting into the zone, none out of it, no setting a home in it. */
    public static final String TELEPORT_IN = ZoneLedger.TELEPORT_IN, TELEPORT_OUT = ZoneLedger.TELEPORT_OUT, SET_HOME = ZoneLedger.SET_HOME;

    /**
     * Whether this server allows no Shatter Totem placement anywhere ({@code restrictions.noTotemServers} lists its
     * {@code storage.serverId}). Totems may still be held there; placing and dropping are refused in every world, so
     * {@link BspPlayers#isDimensionAllowed} answers false for every dimension. For a region mod to show the rule.
     */
    public static boolean isNoTotemServer() {
        return com.mrgregles.bsp_core.BSPConfig.isNoTotemServer();
    }

    /** Whether the server with this {@code storage.serverId} allows no Shatter Totem placement anywhere. */
    public static boolean isNoTotemServer(String serverId) {
        return com.mrgregles.bsp_core.BSPConfig.isNoTotemServer(serverId);
    }

    /** Every server listed in {@code restrictions.noTotemServers} (the same list is kept on every server). */
    public static java.util.List<String> noTotemServers() {
        return java.util.List.copyOf(com.mrgregles.bsp_core.BSPConfig.NO_TOTEM_SERVERS.get());
    }

    /** True when an Anti Totem zone covering {@code pos} has the rule {@code key} switched on. Unknown keys are never blocked. */
    public static boolean isBlocked(ServerLevel level, BlockPos pos, String key) {
        return ZoneLedger.isBlocked(level, pos, key);
    }
}
