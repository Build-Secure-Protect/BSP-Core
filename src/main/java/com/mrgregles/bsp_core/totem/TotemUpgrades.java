package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The Shatter Totem's upgrade system, stored on the totem (item NBT / block entity compound {@code Upgrades}).
 *
 * <p><b>Tiers.</b> A totem has a tier, I to V (stored 0 to 4). Each tier has its own Shatter Coin:
 * Copper, Gold, Diamond, Netherite, Illyrium. Raising the totem a tier costs coins of the tier being
 * left plus XP levels.
 * <p><b>Paths.</b> Each upgrade appears at a tier and most have a parent on the same path: it opens
 * once the totem has reached that tier and the parent has reached {@code upgrades.unlockLevel}.
 * <p><b>Levels.</b> An upgrade gains {@link #LEVELS_PER_TIER} levels per totem tier from the tier it
 * appears at. Every level costs the coin of the tier it is bought at, plus XP; the amounts come
 * from the config by branch.
 */
public final class TotemUpgrades {
    public static final String TAG_UPGRADES = "Upgrades", TAG_TIER = "tier";
    public static final int MAX_TIER = 4, LEVELS_PER_TIER = 2;
    /** Marks upgrade data written by the tier system. Data without it comes from the older flat system and is reset. */
    private static final String TAG_VERSION = "v";
    private static final int VERSION = 2;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    public enum Branch { CARRIED, BASE, RAID }

    /** What the next level or tier costs: {@code coins} of one specific coin, and XP levels. */
    public record Price(CoinTier coin, int coins, int xp, int tier) {
    }

    public enum Buff {
        // order is fixed: ordinals are sent over the network and index the orb colours
        DAMAGE("damage", Branch.CARRIED, 0, null, 1, false),
        RESISTANCE("resistance", Branch.CARRIED, 1, "damage", 1, false),
        MINING_SPEED("mining_speed", Branch.CARRIED, 0, null, 0, false),
        FORTIFY("fortify", Branch.BASE, 0, null, 2, false),
        HEALING("healing", Branch.BASE, 0, null, 3, false),
        SWIFTNESS("swiftness", Branch.CARRIED, 1, "mining_speed", 0, false),
        VITALITY("vitality", Branch.CARRIED, 2, "resistance", 1, false),
        FEATHERFALL("featherfall", Branch.CARRIED, 2, "swiftness", 0, false),
        NIGHT_SIGHT("night_sight", Branch.CARRIED, 3, "featherfall", 0, true),
        WARD("ward", Branch.BASE, 2, "alarm", 2, false),
        ALARM("alarm", Branch.BASE, 1, "fortify", 2, false),
        SANCTUARY("sanctuary", Branch.BASE, 1, "healing", 3, false),
        DEADLOCK("deadlock", Branch.BASE, 3, "ward", 2, false),
        OVERCLOCK("overclock", Branch.BASE, 3, "sanctuary", 3, false),
        LOCKPICK("lockpick", Branch.RAID, 1, null, 4, false),
        SHROUD("shroud", Branch.RAID, 2, "lockpick", 4, false),
        /** Chunk loading: 1, 3 then 6 chunks. Its rules are in {@link com.mrgregles.bsp_core.chunk.ChunkLoading}. */
        ANCHOR("anchor", Branch.BASE, 1, null, 5, false),
        /** Widens the square of chunks Anchor may pick from. */
        SURVEY("survey", Branch.BASE, 2, "anchor", 5, false);

        public final String key;
        private final Branch branch;
        /** Totem tier (0 = I) at which this upgrade appears. */
        public final int tier;
        @Nullable
        private final String parentKey;
        /** Which of the five paths it sits on: 0 Dig, 1 Fight, 2 Walls, 3 Home, 4 Raid, 5 Anchor. */
        public final int path;
        /** A single-level upgrade (Night Sight). */
        public final boolean single;
        /** True for Base upgrades: they work, and are bought, only while the totem is placed. */
        public final boolean placedOnly;

        Buff(String key, Branch branch, int tier, @Nullable String parentKey, int path, boolean single) {
            this.key = key;
            this.branch = branch;
            this.tier = tier;
            this.parentKey = parentKey;
            this.path = path;
            this.single = single;
            this.placedOnly = branch == Branch.BASE;
        }

        public String translationKey() {
            return "buff.bsp_core." + key;
        }

        public Branch branch() {
            return branch;
        }

        /** The upgrade before this one on its path, or null if it starts the path. */
        @Nullable
        public Buff parent() {
            if (parentKey != null) {
                for (Buff b : values()) {
                    if (b.key.equals(parentKey)) {
                        return b;
                    }
                }
            }
            return null;
        }

        public int maxLevel() {
            // the two chunk upgrades are short: three sizes of allowance, two of range
            return this == ANCHOR ? 3 : this == SURVEY ? 2 : single ? 1 : LEVELS_PER_TIER * (MAX_TIER + 1 - tier);
        }

        /** Highest level a totem of {@code totemTier} may hold. */
        public int cap(int totemTier) {
            return totemTier < tier ? 0 : Math.min(maxLevel(), single ? 1 : LEVELS_PER_TIER * (totemTier - tier + 1));
        }

        /** Effect radius in blocks for the two aura upgrades that draw a sphere; 0 otherwise or at level 0. */
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

        /** How far the other ranged base upgrades reach at {@code level}, in blocks; they draw no sphere. */
        public int reach(int level) {
            if (level <= 0) {
                return 0;
            }
            return switch (this) {
                case WARD -> BSPConfig.levelValue(BSPConfig.WARD_RADIUS.get(), level, 0);
                case ALARM -> BSPConfig.levelValue(BSPConfig.ALARM_RADIUS.get(), level, 0);
                case SANCTUARY -> BSPConfig.levelValue(BSPConfig.SANCTUARY_RADIUS.get(), level, 0);
                case OVERCLOCK -> BSPConfig.levelValue(BSPConfig.OVERCLOCK_RADIUS.get(), level, 0);
                default -> 0;
            };
        }

        @Nullable
        public static Buff byOrdinal(int ordinal) {
            Buff[] values = values();
            return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
        }
    }

    private TotemUpgrades() {}

    public static String roman(int tier) {
        return ROMAN[Math.max(0, Math.min(MAX_TIER, tier))];
    }

    // ------------------------------------------------------------------ prices and rules

    private static int pick(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<? extends Integer>> list, int index, int fallback) {
        return BSPConfig.levelValue(BSPConfig.getOr(list, List.<Integer>of()), index + 1, fallback);
    }

    /** Price of taking {@code buff} from {@code level} to the next, or null if it is already maxed. */
    @Nullable
    public static Price price(Buff buff, int level) {
        if (level >= buff.maxLevel()) {
            return null;
        }
        int tier = buff.single ? buff.tier : buff.tier + level / LEVELS_PER_TIER, step = buff.single ? 0 : level % LEVELS_PER_TIER;
        var coins = switch (buff.branch()) {
            case CARRIED -> BSPConfig.CARRIED_LEVEL_COINS;
            case BASE -> BSPConfig.BASE_LEVEL_COINS;
            case RAID -> BSPConfig.RAID_LEVEL_COINS;
        };
        var xp = switch (buff.branch()) {
            case CARRIED -> BSPConfig.CARRIED_LEVEL_XP;
            case BASE -> BSPConfig.BASE_LEVEL_XP;
            case RAID -> BSPConfig.RAID_LEVEL_XP;
        };
        return new Price(CoinTier.values()[tier], pick(coins, step, 1), pick(xp, step, 1) * (tier + 1), tier);
    }

    /** Price of raising a totem from {@code tier} to the next, or null at the top tier. */
    @Nullable
    public static Price gatePrice(int tier) {
        if (tier >= MAX_TIER) {
            return null;
        }
        return new Price(CoinTier.values()[tier], pick(BSPConfig.TIER_GATE_COINS, tier, 4), pick(BSPConfig.TIER_GATE_XP, tier, 15), tier + 1);
    }

    /** Whether the upgrade is open on the tree: the totem is at its tier and its parent is far enough along. */
    public static boolean unlocked(Buff buff, ToIntFunction<Buff> levels, int totemTier) {
        Buff parent = buff.parent();
        return totemTier >= buff.tier && (parent == null || levels.applyAsInt(parent) >= BSPConfig.getOr(BSPConfig.UNLOCK_LEVEL, 2));
    }

    @Nullable
    private static Component cannotPay(Price price, Player player) {
        if (player.isCreative()) {
            return null;
        }
        if (player.getInventory().countItem(price.coin().coin()) < price.coins()) {
            return Component.translatable("gui.bsp_core.tree.why.coins", price.coins(), new ItemStack(price.coin().coin()).getHoverName());
        }
        return player.experienceLevel < price.xp() ? Component.translatable("gui.bsp_core.tree.why.xp", price.xp()) : null;
    }

    /** Why {@code player} cannot buy the next level of {@code buff} right now, or null if they can (or it is maxed). */
    @Nullable
    public static Component whyNot(Buff buff, ToIntFunction<Buff> levels, int totemTier, Player player) {
        if (totemTier < buff.tier) {
            return Component.translatable("gui.bsp_core.tree.why.tier", roman(buff.tier));
        }
        Buff parent = buff.parent();
        int need = BSPConfig.getOr(BSPConfig.UNLOCK_LEVEL, 2);
        if (parent != null && levels.applyAsInt(parent) < need) {
            return Component.translatable("gui.bsp_core.tree.why.parent", Component.translatable(parent.translationKey()), need);
        }
        Price price = price(buff, levels.applyAsInt(buff));
        if (price == null) {
            return null;
        }
        if (price.tier() > totemTier) {
            return Component.translatable("gui.bsp_core.tree.why.cap", roman(totemTier), roman(price.tier()));
        }
        return cannotPay(price, player);
    }

    /** Why {@code player} cannot raise the totem from {@code tier}, or null if they can (or it is at the top). */
    @Nullable
    public static Component whyNotGate(int tier, Player player) {
        Price price = gatePrice(tier);
        return price == null ? null : cannotPay(price, player);
    }

    /** Takes the coins and XP. Call only after {@link #whyNot} or {@link #whyNotGate} returned null. */
    public static void pay(ServerPlayer player, Price price) {
        if (player.isCreative()) {
            return;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(price.coin().coin()), price.coins(), player.inventoryMenu.getCraftSlots());
        player.giveExperienceLevels(-price.xp());
        // The totem screens have no slots, and while one is open the client ignores ordinary inventory updates
        // (except the hotbar), so the coin counts on screen went stale. Send the whole inventory again.
        player.inventoryMenu.sendAllDataToRemote();
    }

    // ------------------------------------------------------------------ on the item

    /**
     * Upgrade data as the tier system understands it: unchanged if it is empty or current, otherwise a
     * fresh, empty set. Levels bought under the old flat system do not fit tiers and paths, so those
     * totems start again at Tier I.
     */
    public static CompoundTag current(CompoundTag upgrades) {
        if (upgrades.isEmpty() || upgrades.getInt(TAG_VERSION) == VERSION) {
            return upgrades;
        }
        return stamp(new CompoundTag());
    }

    /** Marks upgrade data as written by the tier system. */
    public static CompoundTag stamp(CompoundTag upgrades) {
        upgrades.putInt(TAG_VERSION, VERSION);
        return upgrades;
    }

    /** Resets a held totem's upgrades if they come from the old flat system. Returns true if it changed. */
    public static boolean migrate(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_UPGRADES)) {
            return false;
        }
        CompoundTag old = tag.getCompound(TAG_UPGRADES), now = current(old);
        if (now == old) {
            return false;
        }
        tag.put(TAG_UPGRADES, now);
        return true;
    }

    public static int getLevel(ItemStack stack, Buff buff) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getCompound(TAG_UPGRADES).getInt(buff.key);
    }

    public static void setLevel(ItemStack stack, Buff buff, int level) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag upgrades = root.getCompound(TAG_UPGRADES);
        upgrades.putInt(buff.key, level);
        root.put(TAG_UPGRADES, stamp(upgrades));
    }

    public static int getTier(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(MAX_TIER, tag.getCompound(TAG_UPGRADES).getInt(TAG_TIER)));
    }

    public static void setTier(ItemStack stack, int tier) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag upgrades = root.getCompound(TAG_UPGRADES);
        upgrades.putInt(TAG_TIER, Math.max(0, Math.min(MAX_TIER, tier)));
        root.put(TAG_UPGRADES, stamp(upgrades));
    }

    public static CompoundTag copyFrom(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : current(tag.getCompound(TAG_UPGRADES).copy());
    }

    public static void applyTo(ItemStack stack, CompoundTag upgrades) {
        if (!upgrades.isEmpty()) {
            stack.getOrCreateTag().put(TAG_UPGRADES, upgrades.copy());
        }
    }
}
