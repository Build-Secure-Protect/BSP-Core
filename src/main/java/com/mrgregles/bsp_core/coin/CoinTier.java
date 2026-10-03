package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/** The five Shatter Coin tiers, cheapest first. Values, press times and energy come from config. */
public enum CoinTier {
    COPPER("copper"), GOLD("gold"), DIAMOND("diamond"), NETHERITE("netherite"), ETHERIUM("etherium");

    public final String key;

    CoinTier(String key) {
        this.key = key;
    }

    /** Worth in copper-coin units; each tier defaults to double the previous. */
    public int value() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.COIN_VALUES, java.util.List.of()), ordinal() + 1, 1 << ordinal());
    }

    /** Real-time milliseconds to press one coin with no upgrades. */
    public long pressMillis() {
        Number hours = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_PRESS_HOURS, java.util.List.of()), ordinal() + 1, 12.0 * (1 << ordinal()));
        return Math.max(1000L, (long) (hours.doubleValue() * 3_600_000d));
    }

    public int energyPerCoin() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_ENERGY_PER_COIN, java.util.List.of()), ordinal() + 1, 0);
    }

    public Item coin() {
        return ModItems.COINS.get(this).get();
    }

    public Item blank() {
        return ModItems.BLANKS.get(this).get();
    }

    @Nullable
    public static CoinTier byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
