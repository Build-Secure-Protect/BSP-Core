package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes every dimension this server has into the server config's {@code restrictions.activeDimensions} at each start, so an
 * admin can copy the exact names into {@code restrictions.allowedDimensions}. A name in {@code allowedDimensions} that this
 * server does not have (a typo, or a mod taken out) is logged as a warning.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ActiveDimensions {
    private ActiveDimensions() {}

    /** Every dimension this server has, by registry name, in alphabetical order. */
    public static List<String> names(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        server.levelKeys().forEach(key -> out.add(key.location().toString()));
        out.sort(null);
        return out;
    }

    /** The names in {@code allowed} that are not in {@code active}, in their order. */
    public static List<String> missing(List<? extends String> allowed, List<String> active) {
        List<String> out = new ArrayList<>();
        for (String name : allowed) {
            if (!active.contains(name.trim())) {
                out.add(name);
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        write(event.getServer());
    }

    /** Writes the list into the config file when it differs from what is there; returns the list. */
    public static List<String> write(MinecraftServer server) {
        List<String> active = names(server);
        try {
            if (!active.equals(BSPConfig.ACTIVE_DIMENSIONS.get())) {
                BSPConfig.ACTIVE_DIMENSIONS.set(active);
                BSPConfig.ACTIVE_DIMENSIONS.save();
            }
            for (String name : missing(BSPConfig.ALLOWED_DIMENSIONS.get(), active)) {
                BSPCore.LOGGER.warn("restrictions.allowedDimensions lists \"{}\", which this server does not have; see restrictions.activeDimensions for the names it has", name);
            }
        } catch (RuntimeException e) { // the config not loaded, or the file not writable (a read-only mount): never stop the start for this
            BSPCore.LOGGER.warn("BSP-Core could not list the active dimensions in its config: {}", e.toString());
        }
        return active;
    }
}
