package com.mrgregles.bsp_core.compat;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BSP-RankUp's ladder, read by reflection when that mod is loaded: {@code RankUpApi.ladder(server)} gives
 * the rungs lowest first, each a record with {@code group()} (the LuckPerms group name). Nothing here
 * is a compile-time dependency; without RankUp the ladder is empty and the kit screens fall back to
 * LuckPerms' group names in no order.
 */
public final class RankUpHook {
    private RankUpHook() {}

    public static final String MOD = "bsp_rankup";
    private static final String API = "com.mrgregles.bsp_rankup.api.RankUpApi";
    private static boolean warned;

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD);
    }

    /** The ladder's group names, lowest first, lower-cased; empty without RankUp or before an admin set a ladder up. */
    public static List<String> ladder(MinecraftServer server) {
        if (!isPresent()) {
            return List.of();
        }
        try {
            Class<?> api = Class.forName(API);
            Method ladder = api.getMethod("ladder", MinecraftServer.class);
            List<?> rungs = (List<?>) ladder.invoke(null, server);
            List<String> out = new ArrayList<>();
            for (Object rung : rungs) {
                Method group = rung.getClass().getMethod("group");
                out.add(String.valueOf(group.invoke(rung)).toLowerCase(Locale.ROOT));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                BSPCore.LOGGER.warn("BSP-RankUp is loaded but its ladder could not be read; kits show LuckPerms' groups instead: {}", e.toString());
            }
            return List.of();
        }
    }
}
