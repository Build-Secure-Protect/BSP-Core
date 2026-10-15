package com.mrgregles.bsp_core.compat.regionguard;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.zone.ZoneLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * BSP Region Guard's say over totems, when it is installed: its regions carry {@code totem-place}
 * and {@code totem-drop} flags, and the Anti Totem checks ask them as well as BSP-Core's own zones,
 * so an admin can keep totems out with a region too. Region Guard is optional and loads after
 * BSP-Core, so it is reached only here, lazily, through its {@code api} package; without it every
 * answer is "allowed". (Region Guard itself also reads BSP-Core's zones for these two rules.)
 */
public final class RegionGuardZones {
    private static final String MOD_ID = "bsp_region_guard";
    private static Boolean present;
    private static boolean warned;

    private RegionGuardZones() {}

    public static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded(MOD_ID);
        }
        return present;
    }

    /** Whether Region Guard allows the totem rule {@code key} ({@link ZoneLedger#TOTEM_PLACE} or {@link ZoneLedger#TOTEM_DROP}) at {@code pos} for {@code player}; true when it is absent or the key is not one it knows. */
    public static boolean allows(ServerLevel level, BlockPos pos, String key, @Nullable Player player) {
        if (!present() || !(key.equals(ZoneLedger.TOTEM_PLACE) || key.equals(ZoneLedger.TOTEM_DROP))) {
            return true;
        }
        try {
            return Api.allows(level, pos, key, player);
        } catch (LinkageError | RuntimeException e) {
            if (!warned) {
                warned = true;
                BSPCore.LOGGER.warn("BSP-Core could not ask Region Guard about totem rules ({}); allowing. Is Region Guard's API older than expected?", e.toString());
            }
            return true;
        }
    }

    /** Touches Region Guard's classes only when it is installed. */
    private static final class Api {
        static boolean allows(ServerLevel level, BlockPos pos, String key, @Nullable Player player) {
            var flag = key.equals(ZoneLedger.TOTEM_PLACE) ? com.mrgregles.bsp_region_guard.flag.Flags.TOTEM_PLACE : com.mrgregles.bsp_region_guard.flag.Flags.TOTEM_DROP;
            return com.mrgregles.bsp_region_guard.api.RegionGuardApi.get().regions().allows(level, pos, flag, player);
        }
    }
}
