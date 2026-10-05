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
        PROJECTORS.clear();
    }

    /** Loaded Totem Projectors: while one is projecting, it answers the same questions a placed totem does. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity>> PROJECTORS = new ConcurrentHashMap<>();

    public static void register(ServerLevel level, com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity projector) {
        PROJECTORS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(projector.getBlockPos().immutable(), projector);
    }

    public static void unregisterProjector(ServerLevel level, BlockPos pos) {
        var map = PROJECTORS.get(level.dimension());
        if (map != null) {
            map.remove(pos);
        }
    }

    private static java.util.Collection<com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity> projectors(ServerLevel level) {
        var map = PROJECTORS.get(level.dimension());
        return map == null ? java.util.List.of() : map.values();
    }

    /**
     * Highest Fortify level whose radius covers {@code pos}, ignoring totems owned by {@code actor}
     * (owners may work on their own base). 0 if none.
     */
    public static int fortifyLevelAt(ServerLevel level, BlockPos pos, @Nullable UUID actor) {
        Map<BlockPos, ShatterTotemBlockEntity> map = LOADED.getOrDefault(level.dimension(), Map.of());
        int best = 0;
        for (var projector : projectors(level)) {
            int lvl = projector.isRemoved() ? 0 : projector.level(TotemUpgrades.Buff.FORTIFY);
            if (lvl > best && !(actor != null && projector.isOwner(actor))) {
                int r = TotemUpgrades.Buff.FORTIFY.radius(lvl);
                if (pos.distSqr(projector.getBlockPos()) <= (double) r * r) {
                    best = lvl;
                }
            }
        }
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

    /** Never below {@code limits.minIntruderMiningSpeed}: blocks around a totem must always stay breakable. */
    public static double breakSpeedMultiplier(int fortifyLevel) {
        return Math.max(BSPConfig.MIN_INTRUDER_MINING_SPEED.get(), BSPConfig.levelValue(BSPConfig.FORTIFY_BREAK_SPEED.get(), fortifyLevel, 1.0));
    }

    /** Highest level of a ranged base upgrade (Sanctuary, Overclock, ...) whose reach covers {@code pos}; 0 if none. */
    public static int levelInReach(ServerLevel level, BlockPos pos, TotemUpgrades.Buff buff) {
        Map<BlockPos, ShatterTotemBlockEntity> map = LOADED.getOrDefault(level.dimension(), Map.of());
        int best = 0;
        for (var projector : projectors(level)) {
            int lvl = projector.isRemoved() ? 0 : projector.level(buff);
            if (lvl > best) {
                int r = buff.reach(lvl);
                if (pos.distSqr(projector.getBlockPos()) <= (double) r * r) {
                    best = lvl;
                }
            }
        }
        for (ShatterTotemBlockEntity totem : map.values()) {
            int lvl = totem.isRemoved() ? 0 : totem.getUpgradeLevel(buff);
            if (lvl > best) {
                int r = buff.reach(lvl);
                if (pos.distSqr(totem.getBlockPos()) <= (double) r * r) {
                    best = lvl;
                }
            }
        }
        return best;
    }

    /** Speed multiplier for a BSP-Core machine at {@code pos} from any Overclock aura covering it; 1.0 if none. */
    public static double overclock(ServerLevel level, BlockPos pos) {
        int lvl = levelInReach(level, pos, TotemUpgrades.Buff.OVERCLOCK);
        return lvl <= 0 ? 1.0 : 1.0 + BSPConfig.levelValue(BSPConfig.OVERCLOCK_BONUS.get(), lvl, 0.0);
    }

    public static double explosionProtection(int fortifyLevel) {
        return BSPConfig.levelValue(BSPConfig.FORTIFY_EXPLOSION_PROTECTION.get(), fortifyLevel, 0.0);
    }
}
