package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side index of loaded placed totems, for fast "is this block inside a Fortify aura" checks. */
public final class TotemAuras {
    private static final Map<ResourceKey<Level>, Map<BlockPos, ShatterTotemBlockEntity>> LOADED = new ConcurrentHashMap<>();

    private TotemAuras() {}

    public static void register(ServerLevel level, ShatterTotemBlockEntity totem) {
        LOADED.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(totem.getBlockPos().immutable(), totem);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Map<BlockPos, ShatterTotemBlockEntity> map = LOADED.get(level.dimension());
        if (map != null) {
            map.remove(pos);
        }
    }

    public static void clear() {
        LOADED.clear();
    }

    /**
     * Highest Fortify level whose radius covers {@code pos}, ignoring totems owned by {@code actor}
     * (owners may work on their own base). 0 if none.
     */
    public static int fortifyLevelAt(ServerLevel level, BlockPos pos, @Nullable UUID actor) {
        Map<BlockPos, ShatterTotemBlockEntity> map = LOADED.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (ShatterTotemBlockEntity totem : map.values()) {
            if (totem.isRemoved()) {
                continue;
            }
            int lvl = totem.getUpgradeLevel(TotemUpgrades.Buff.FORTIFY);
            if (lvl <= best) {
                continue;
            }
            if (actor != null && totem.isOwner(actor)) {
                continue;
            }
            int r = TotemUpgrades.Buff.FORTIFY.radius(lvl);
            if (pos.distSqr(totem.getBlockPos()) <= (double) r * r) {
                best = lvl;
            }
        }
        return best;
    }

    public static double breakSpeedMultiplier(int fortifyLevel) {
        return BSPConfig.levelValue(BSPConfig.FORTIFY_BREAK_SPEED.get(), fortifyLevel, 1.0);
    }

    public static double explosionProtection(int fortifyLevel) {
        return BSPConfig.levelValue(BSPConfig.FORTIFY_EXPLOSION_PROTECTION.get(), fortifyLevel, 0.0);
    }
}
