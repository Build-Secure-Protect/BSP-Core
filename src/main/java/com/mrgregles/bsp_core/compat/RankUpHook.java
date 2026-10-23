package com.mrgregles.bsp_core.compat;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.MinecraftServer;
import com.mrgregles.bsp_core.api.BspTeams;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * BSP-RankUp's ladder, read by reflection when that mod is loaded: {@code RankUpApi.ladder(server)} gives
 * the rungs lowest first, each a record with {@code group()} (the LuckPerms group name). Nothing here
 * is a compile-time dependency; without RankUp the ladder is empty and the kit screens fall back to
 * LuckPerms' group names in no order.
 *
 * <p>At server start BSP-Core also registers its teams as RankUp's team provider ({@code RankUpApi.setTeamProvider}),
 * so kills between teammates count for nothing towards ranks (RankUp's rule once a teams mod is registered).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class RankUpHook {
    private RankUpHook() {}

    public static final String MOD = "bsp_rankup";
    private static final String API = "com.mrgregles.bsp_rankup.api.RankUpApi";
    private static final String TEAM_PROVIDER = "com.mrgregles.bsp_rankup.api.TeamProvider";
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

    /** Whether two players are teammates on the running server (false with teams switched off, or no server). */
    public static boolean sameTeam(UUID a, UUID b) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && a != null && b != null && Teams.enabled() && BspTeams.sameTeam(server, a, b);
    }

    /**
     * An implementation of {@code iface}, a one-method interface taking two UUIDs and answering a boolean (RankUp's
     * {@code TeamProvider}), that answers with {@link #sameTeam}.
     */
    public static Object teamProvider(Class<?> iface) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "BSP-Core teams";
            default -> args != null && args.length == 2 && args[0] instanceof UUID a && args[1] instanceof UUID b && sameTeam(a, b);
        });
    }

    private static void setTeamProvider(boolean on) {
        if (!isPresent()) {
            return;
        }
        try {
            Class<?> iface = Class.forName(TEAM_PROVIDER);
            Class.forName(API).getMethod("setTeamProvider", iface).invoke(null, on ? teamProvider(iface) : null);
            if (on) {
                BSPCore.LOGGER.info("BSP-Core's teams registered with BSP-RankUp: kills between teammates count for nothing");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.warn("BSP-RankUp is loaded but BSP-Core's teams could not be registered with it; teammate kills may count: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        setTeamProvider(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        setTeamProvider(false);
    }
}
