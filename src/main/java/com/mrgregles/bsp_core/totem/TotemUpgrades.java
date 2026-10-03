package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/** Upgrade levels stored on a Shatter Totem (item NBT / block entity compound {@code Upgrades}). */
public final class TotemUpgrades {
    public static final String TAG_UPGRADES = "Upgrades";

    public enum Currency { XP, COINS }

    public enum Buff {
        // hand-held buffs, bought with XP from the upgrade tree; active while the totem is in the offhand
        DAMAGE("damage", Currency.XP, false, () -> BSPConfig.DAMAGE_XP_COSTS),
        RESISTANCE("resistance", Currency.XP, false, () -> BSPConfig.RESISTANCE_XP_COSTS),
        MINING_SPEED("mining_speed", Currency.XP, false, () -> BSPConfig.MINING_SPEED_XP_COSTS),
        // placed-only auras, bought with Shatter Coins from the placed totem's panel
        FORTIFY("fortify", Currency.COINS, true, () -> BSPConfig.FORTIFY_COIN_COSTS),
        HEALING("healing", Currency.COINS, true, () -> BSPConfig.HEALING_COIN_COSTS);

        public final String key;
        public final Currency currency;
        public final boolean placedOnly;
        private final Supplier<ForgeConfigSpec.ConfigValue<List<? extends Integer>>> costs;

        Buff(String key, Currency currency, boolean placedOnly, Supplier<ForgeConfigSpec.ConfigValue<List<? extends Integer>>> costs) {
            this.key = key;
            this.currency = currency;
            this.placedOnly = placedOnly;
            this.costs = costs;
        }

        public String translationKey() {
            return "buff.bsp_core." + key;
        }

        public int maxLevel() {
            return costs.get().get().size();
        }

        /** Cost to go from {@code currentLevel} to the next, in this buff's currency, or -1 if maxed. */
        public int costToUpgrade(int currentLevel) {
            List<? extends Integer> list = costs.get().get();
            return currentLevel >= list.size() ? -1 : list.get(currentLevel);
        }

        /** Effect radius in blocks for aura buffs at {@code level}; 0 for non-aura buffs or level 0. */
        public int radius(int level) {
            if (level <= 0) {
                return 0;
            }
            return switch (this) {
                case FORTIFY -> BSPConfig.levelValue(BSPConfig.FORTIFY_RADIUS.get(), level, 0);
                case HEALING -> BSPConfig.levelValue(BSPConfig.HEALING_RADIUS.get(), level, 0);
                default -> 0;
            };
        }

        public static Buff byOrdinal(int ordinal) {
            Buff[] values = values();
            return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
        }
    }

    private TotemUpgrades() {}

    public static int getLevel(ItemStack stack, Buff buff) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getCompound(TAG_UPGRADES).getInt(buff.key);
    }

    public static void setLevel(ItemStack stack, Buff buff, int level) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag upgrades = root.getCompound(TAG_UPGRADES);
        upgrades.putInt(buff.key, level);
        root.put(TAG_UPGRADES, upgrades);
    }

    public static CompoundTag copyFrom(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.getCompound(TAG_UPGRADES).copy();
    }

    public static void applyTo(ItemStack stack, CompoundTag upgrades) {
        if (!upgrades.isEmpty()) {
            stack.getOrCreateTag().put(TAG_UPGRADES, upgrades.copy());
        }
    }
}
