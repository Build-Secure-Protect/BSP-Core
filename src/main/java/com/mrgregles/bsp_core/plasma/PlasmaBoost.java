package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wave Plasma as a machine accelerator. A {@link PlasmaInjectorBlockEntity} stands against a machine and is fed by cable; the
 * mB/t arriving at it turn into a speed factor through the curve in config ({@code plasma.injectorRates} and
 * {@code plasma.injectorFactors}: straight lines from (0, x1) through each point, flat after the last). BSP-Core machines
 * read their injectors themselves through {@link #rate}; other mods' machines are ticked extra by the injector.
 */
public final class PlasmaBoost {
    private PlasmaBoost() {}

    /** Speed factor for {@code mbPerTick} of plasma arriving: 1.0 with none, up to the last point of the curve. */
    public static double factor(int mbPerTick) {
        List<? extends Integer> rates = BSPConfig.getOr(BSPConfig.INJECTOR_RATES, List.of(300, 500));
        List<? extends Double> factors = BSPConfig.getOr(BSPConfig.INJECTOR_FACTORS, List.of(2.0, 3.0));
        double x0 = 0, y0 = 1.0;
        for (int i = 0; i < Math.min(rates.size(), factors.size()); i++) {
            double x1 = rates.get(i), y1 = factors.get(i);
            if (mbPerTick <= x1) {
                return x1 <= x0 ? y1 : y0 + (y1 - y0) * (mbPerTick - x0) / (x1 - x0);
            }
            x0 = x1;
            y0 = y1;
        }
        return y0;
    }

    /**
     * Overclock's part: the totem power no longer speeds a machine by itself; it scales what the plasma does. {@code scale} is
     * {@link com.mrgregles.bsp_core.totem.TotemAuras#overclock} (1.0 with no Overclock in reach): the speed-up above 1 is multiplied by it.
     */
    public static double apply(double factor, double scale) {
        return 1.0 + (factor - 1.0) * Math.max(0.0, scale);
    }

    /** The most mB/t that still raises the factor: the last point of the curve. */
    public static int topRate() {
        List<? extends Integer> rates = BSPConfig.getOr(BSPConfig.INJECTOR_RATES, List.of(300, 500));
        return rates.isEmpty() ? 500 : rates.get(rates.size() - 1);
    }

    /** The strongest Overclock scale among the fed injectors that stand against {@code machine}'s blocks and point at it; 1.0 if none. */
    public static double scale(ServerLevel level, Iterable<BlockPos> machine) {
        double best = 1.0;
        for (BlockPos p : machine) {
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (level.isLoaded(q) && level.getBlockEntity(q) instanceof PlasmaInjectorBlockEntity injector && injector.target().equals(p)) {
                    best = Math.max(best, injector.overclockScale());
                }
            }
        }
        return best;
    }

    /** mB/t arriving at every injector that stands against one of {@code machine}'s blocks and points at it. Each injector counts once. */
    public static int rate(ServerLevel level, Iterable<BlockPos> machine) {
        Set<BlockPos> seen = new HashSet<>();
        int sum = 0;
        for (BlockPos p : machine) {
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (level.isLoaded(q) && level.getBlockEntity(q) instanceof PlasmaInjectorBlockEntity injector && injector.target().equals(p) && seen.add(q.immutable())) {
                    sum += injector.rate();
                }
            }
        }
        return sum;
    }
}
