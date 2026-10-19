package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.compat.RankUpHook;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * Wires the kits' rank rule to LuckPerms and the rank names to RankUp's ladder: at server start
 * {@link Kits#rankSource} becomes the bridge's group lookup and {@link Kits#rankNamesSource} the
 * ladder when RankUp is there (ordered lowest first), else LuckPerms' loaded groups (unordered).
 * Without LuckPerms a ranked kit stays locked and the log says so once.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class KitRanks {
    private KitRanks() {}

    private static boolean warnedAbsent;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        install();
    }

    /** Puts the bridge and the ladder in place (also from the tests). */
    public static void install() {
        Kits.rankSource = player -> {
            if (!LuckPermsBridge.isPresent()) {
                if (!warnedAbsent) {
                    warnedAbsent = true;
                    BSPCore.LOGGER.warn("A kit asks for a LuckPerms rank but LuckPerms is not reachable; ranked kits stay locked until it is");
                }
                return java.util.Set.of();
            }
            return LuckPermsBridge.groupsOf(player);
        };
        Kits.rankNamesSource = KitRanks::names;
    }

    /** RankUp's ladder when it has one, else LuckPerms' groups. */
    public static Kits.RankNames names() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            List<String> ladder = RankUpHook.ladder(server);
            if (!ladder.isEmpty()) {
                return new Kits.RankNames(ladder, true);
            }
        }
        return new Kits.RankNames(LuckPermsBridge.groupNames(), false);
    }
}
