package com.mrgregles.bsp_core;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server-side configuration, written to {@code <world>/serverconfig/bsp_core-server.toml}.
 * Values are read through the {@link ForgeConfigSpec} accessors so they always reflect the loaded
 * config for the current server.
 */
public final class BSPConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ------------------------------------------------------------------ first_join

    /**
     * Whether this server hands out a Shatter Totem on a player's first ever join. The BSP server
     * network only enables this on the Spawn Hub; every other backend server must leave it false.
     */
    public static final ForgeConfigSpec.BooleanValue GRANT_TOTEM_ON_FIRST_JOIN;

    // ------------------------------------------------------------------ upgrades

    /** XP level cost of each buff level, index 0 = level 1. List length = max level. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> DAMAGE_XP_COSTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> RESISTANCE_XP_COSTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> MINING_SPEED_XP_COSTS;

    /** Placed-only upgrades, paid in Shatter Coins. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FORTIFY_COIN_COSTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> HEALING_COIN_COSTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FORTIFY_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> FORTIFY_BREAK_SPEED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> FORTIFY_EXPLOSION_PROTECTION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> HEALING_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HEALING_PER_SECOND;

    /** Costs of the newer upgrades: XP levels for carried ones, coin value for base and raid ones. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> SWIFTNESS_XP_COSTS, VITALITY_XP_COSTS, FEATHERFALL_XP_COSTS, NIGHT_SIGHT_XP_COSTS,
            WARD_COIN_COSTS, ALARM_COIN_COSTS, SANCTUARY_COIN_COSTS, DEADLOCK_COIN_COSTS, OVERCLOCK_COIN_COSTS, LOCKPICK_COIN_COSTS, SHROUD_COIN_COSTS;
    /** Their effect per level. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> VITALITY_HEALTH, WARD_RADIUS, ALARM_RADIUS, SANCTUARY_RADIUS, DEADLOCK_SECONDS,
            OVERCLOCK_RADIUS, LOCKPICK_SECONDS, SHROUD_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SWIFTNESS_BONUS, FEATHERFALL_REDUCTION, OVERCLOCK_BONUS;
    /** Guarantees that a totem can always be reached and stolen. */
    public static final ForgeConfigSpec.DoubleValue MIN_INTRUDER_MINING_SPEED;
    public static final ForgeConfigSpec.IntValue MIN_STEAL_SECONDS, MAX_STEAL_SECONDS;

    // ------------------------------------------------------------------ restrictions

    /** Dimensions in which a Shatter Totem may be placed, dropped or auto-placed. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;

    /** Seconds a dropped totem lies on the ground before it places itself as a block. */
    public static final ForgeConfigSpec.IntValue GROUND_SECONDS_BEFORE_PLACE;

    // ------------------------------------------------------------------ steal

    public static final ForgeConfigSpec.IntValue STEAL_SECONDS;
    public static final ForgeConfigSpec.IntValue STEAL_RADIUS;
    public static final ForgeConfigSpec.IntValue STEAL_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue UNCLAIMED_STEAL_SECONDS;
    public static final ForgeConfigSpec.IntValue STEAL_INVINCIBILITY_SECONDS;
    public static final ForgeConfigSpec.IntValue STEAL_WARNING_SECONDS;

    // ------------------------------------------------------------------ coins + factory

    /** Value of each coin tier (Copper, Gold, Diamond, Netherite, Illyrium) in copper-coin units. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> COIN_VALUES;
    /** Real-time hours to press one coin of each tier with no upgrades. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Number>> FACTORY_PRESS_HOURS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FACTORY_ENERGY_PER_COIN;
    public static final ForgeConfigSpec.IntValue FACTORY_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue FACTORY_MAX_RECEIVE;
    /** Fraction of press time removed by each Speed Gear (Mk I, II, III); fitted gears add together. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Number>> FACTORY_MOTIVATOR_REDUCTIONS;
    public static final ForgeConfigSpec.DoubleValue FACTORY_MAX_REDUCTION;
    public static final ForgeConfigSpec.IntValue FACTORY_MAX_PER_PLAYER;

    // ------------------------------------------------------------------ machines

    public static final ForgeConfigSpec.IntValue TCRUC_NUGGETS, TCRUC_SLAG, TCRUC_TICKS, FORGE_TICKS;
    /** Speed gain and lava saving of RF Upgrade Mk I, II, III while powered. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Number>> RF_BONUS;
    public static final ForgeConfigSpec.IntValue RF_PER_TICK, RF_CAPACITY;
    public static final ForgeConfigSpec.DoubleValue FORGE_RF_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue ICRUC_ORE_IN, ICRUC_SLAG_IN, ICRUC_SMELT_TICKS, ICRUC_PURE_IN, ICRUC_TDUST_IN, ICRUC_NUGGETS_OUT, ICRUC_ALLOY_TICKS,
            ICRUC_LAVA_PER_JOB, ICRUC_LAVA_PER_MAGMA, ICRUC_TANK, REFINERY_WATER, REFINERY_TICKS, REFINERY_TANK;

    /** Refinements a filter lasts: Iron, Diamond, Netherite, Illyrium. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FILTER_USES;

    // ------------------------------------------------------------------ hand crushing

    public static final ForgeConfigSpec.DoubleValue CRUSH_TETRIUM_CHANCE, CRUSH_DIRTY_CHANCE;
    public static final ForgeConfigSpec.IntValue CRUSH_TETRIUM_NUGGETS, CRUSH_DIRTY_NUGGETS;

    // ------------------------------------------------------------------ totem compass

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> COMPASS_SECONDS_PER_COIN;
    public static final ForgeConfigSpec.IntValue COMPASS_COOLDOWN, COMPASS_MIN_COOLDOWN, COMPASS_COOLDOWN_STEP;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> COMPASS_UPGRADE_COSTS;

    // ------------------------------------------------------------------ visuals

    public static final ForgeConfigSpec.IntValue AURA_SPHERE_VIEW_DISTANCE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("BSP Core server configuration").push("first_join");
        GRANT_TOTEM_ON_FIRST_JOIN = BUILDER
                .comment("Give new players a Shatter Totem (and the guide book, if Patchouli is installed) on their first join.",
                         "On by default so a single server or single-player world works out of the box.",
                         "On a network, leave it on for the Spawn Hub only and turn it off on every other server.")
                .define("grantTotemOnFirstJoin", true);
        BUILDER.pop();

        BUILDER.comment("Shatter Totem buff upgrade costs, in experience levels.",
                        "One entry per buff level; the number of entries is the maximum level.")
               .push("upgrades");
        List<Integer> defaults = List.of(5, 10, 20, 35, 55);
        DAMAGE_XP_COSTS = BUILDER.comment("Damage buff").defineList("damageXpLevelCosts", defaults, BSPConfig::isPositiveInt);
        RESISTANCE_XP_COSTS = BUILDER.comment("Resistance buff").defineList("resistanceXpLevelCosts", defaults, BSPConfig::isPositiveInt);
        MINING_SPEED_XP_COSTS = BUILDER.comment("Mining speed buff").defineList("miningSpeedXpLevelCosts", defaults, BSPConfig::isPositiveInt);

        List<Integer> coinDefaults = List.of(5, 10, 20, 40, 80);
        FORTIFY_COIN_COSTS = BUILDER.comment("Fortify (placed only): cost per level in coin value (see coins.values)")
                .defineList("fortifyCoinCosts", coinDefaults, BSPConfig::isPositiveInt);
        FORTIFY_RADIUS = BUILDER.comment("Fortify: protected radius in blocks per level")
                .defineList("fortifyRadius", List.of(1, 3, 5, 7, 15), BSPConfig::isPositiveInt);
        FORTIFY_BREAK_SPEED = BUILDER.comment("Fortify: non-owner mining speed multiplier per level (lower = harder)")
                .defineList("fortifyBreakSpeedMultiplier", List.of(0.6, 0.45, 0.3, 0.2, 0.1), BSPConfig::isFraction);
        FORTIFY_EXPLOSION_PROTECTION = BUILDER.comment("Fortify: chance (0-1) that each block in range survives an explosion, per level")
                .defineList("fortifyExplosionProtection", List.of(0.3, 0.5, 0.7, 0.85, 1.0), BSPConfig::isFraction);
        HEALING_COIN_COSTS = BUILDER.comment("Healing Aura (placed only): cost per level in coin value")
                .defineList("healingCoinCosts", coinDefaults, BSPConfig::isPositiveInt);
        HEALING_RADIUS = BUILDER.comment("Healing Aura: radius in blocks per level")
                .defineList("healingRadius", List.of(3, 5, 7, 10, 15), BSPConfig::isPositiveInt);
        HEALING_PER_SECOND = BUILDER.comment("Healing Aura: health points healed per second per level (2 = one heart)")
                .defineList("healingPerSecond", List.of(0.5, 1.0, 1.5, 2.0, 3.0), o -> o instanceof Double d && d > 0);

        BUILDER.comment("Carried upgrades (work while the totem is in the offhand), paid in XP levels.").push("carried");
        SWIFTNESS_XP_COSTS = BUILDER.defineList("swiftnessXpLevelCosts", List.of(8, 16, 24), BSPConfig::isPositiveInt);
        SWIFTNESS_BONUS = BUILDER.comment("Swiftness: movement speed added per level, as a fraction (0.05 = 5%)")
                .defineList("swiftnessSpeedBonus", List.of(0.05, 0.10, 0.15), BSPConfig::isFraction);
        VITALITY_XP_COSTS = BUILDER.defineList("vitalityXpLevelCosts", List.of(10, 20, 30, 40), BSPConfig::isPositiveInt);
        VITALITY_HEALTH = BUILDER.comment("Vitality: extra health points per level (2 = one heart)")
                .defineList("vitalityHealth", List.of(2, 4, 6, 8), BSPConfig::isPositiveInt);
        FEATHERFALL_XP_COSTS = BUILDER.defineList("featherfallXpLevelCosts", List.of(6, 12, 18, 24), BSPConfig::isPositiveInt);
        FEATHERFALL_REDUCTION = BUILDER.comment("Featherfall: share of fall damage removed per level")
                .defineList("featherfallReduction", List.of(0.25, 0.5, 0.75, 1.0), BSPConfig::isFraction);
        NIGHT_SIGHT_XP_COSTS = BUILDER.defineList("nightSightXpLevelCosts", List.of(12), BSPConfig::isPositiveInt);
        BUILDER.pop();

        BUILDER.comment("Base upgrades (work only while the totem is placed), paid in coin value.").push("base");
        WARD_COIN_COSTS = BUILDER.defineList("wardCoinCosts", List.of(16, 32, 64), BSPConfig::isPositiveInt);
        WARD_RADIUS = BUILDER.comment("Ward: intruders within this many blocks are weakened (Weakness I, II, III by level)")
                .defineList("wardRadius", List.of(6, 10, 14), BSPConfig::isPositiveInt);
        ALARM_COIN_COSTS = BUILDER.defineList("alarmCoinCosts", List.of(8, 16, 32), BSPConfig::isPositiveInt);
        ALARM_RADIUS = BUILDER.comment("Alarm: intruders within this many blocks are outlined and the owner is told")
                .defineList("alarmRadius", List.of(8, 16, 24), BSPConfig::isPositiveInt);
        SANCTUARY_COIN_COSTS = BUILDER.defineList("sanctuaryCoinCosts", List.of(8, 16, 32), BSPConfig::isPositiveInt);
        SANCTUARY_RADIUS = BUILDER.comment("Sanctuary: hostile mobs do not spawn naturally within this many blocks")
                .defineList("sanctuaryRadius", List.of(8, 16, 32), BSPConfig::isPositiveInt);
        DEADLOCK_COIN_COSTS = BUILDER.defineList("deadlockCoinCosts", List.of(16, 48, 128), BSPConfig::isPositiveInt);
        DEADLOCK_SECONDS = BUILDER.comment("Deadlock: seconds added to the time needed to steal this totem")
                .defineList("deadlockSeconds", List.of(30, 60, 120), BSPConfig::isPositiveInt);
        OVERCLOCK_COIN_COSTS = BUILDER.defineList("overclockCoinCosts", List.of(32, 64, 128), BSPConfig::isPositiveInt);
        OVERCLOCK_RADIUS = BUILDER.comment("Overclock: BSP-Core machines within this many blocks work faster")
                .defineList("overclockRadius", List.of(8, 12, 16), BSPConfig::isPositiveInt);
        OVERCLOCK_BONUS = BUILDER.comment("Overclock: speed added per level, as a fraction")
                .defineList("overclockSpeedBonus", List.of(0.05, 0.10, 0.15), BSPConfig::isFraction);
        BUILDER.pop();

        BUILDER.comment("Raid upgrades (apply when the totem carrying them is in the thief's offhand), paid in coin value.").push("raid");
        LOCKPICK_COIN_COSTS = BUILDER.defineList("lockpickCoinCosts", List.of(16, 48, 128), BSPConfig::isPositiveInt);
        LOCKPICK_SECONDS = BUILDER.comment("Lockpick: seconds taken off the time you need to steal a totem")
                .defineList("lockpickSeconds", List.of(15, 30, 60), BSPConfig::isPositiveInt);
        SHROUD_COIN_COSTS = BUILDER.defineList("shroudCoinCosts", List.of(16, 32, 64), BSPConfig::isPositiveInt);
        SHROUD_SECONDS = BUILDER.comment("Shroud: seconds before the owner is warned that you are stealing")
                .defineList("shroudSeconds", List.of(5, 10, 20), BSPConfig::isPositiveInt);
        BUILDER.pop();

        BUILDER.comment("Limits that keep every totem stealable, whatever upgrades it has.").push("limits");
        MIN_INTRUDER_MINING_SPEED = BUILDER.comment("Lowest mining speed multiplier an intruder can be reduced to near a totem. Never 0: blocks must stay breakable.")
                .defineInRange("minIntruderMiningSpeed", 0.2, 0.05, 1.0);
        MIN_STEAL_SECONDS = BUILDER.comment("A steal can never be made shorter than this.").defineInRange("minStealSeconds", 60, 1, 86400);
        MAX_STEAL_SECONDS = BUILDER.comment("A steal can never be made longer than this.").defineInRange("maxStealSeconds", 600, 1, 86400);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("Where and how the Shatter Totem may exist in the world").push("restrictions");
        ALLOWED_DIMENSIONS = BUILDER
                .comment("Dimensions where the totem can be placed or dropped (registry names).")
                .defineListAllowEmpty("allowedDimensions",
                        List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                        o -> o instanceof String);
        GROUND_SECONDS_BEFORE_PLACE = BUILDER
                .comment("Seconds a dropped totem waits on the ground before placing itself as a block.")
                .defineInRange("groundSecondsBeforePlace", 30, 1, 3600);
        BUILDER.pop();

        BUILDER.comment("Stealing a placed Shatter Totem").push("steal");
        STEAL_SECONDS = BUILDER.comment("Seconds a thief must stay near the totem to steal it.")
                .defineInRange("stealSeconds", 300, 1, 86400);
        STEAL_RADIUS = BUILDER.comment("Radius in blocks the thief must stay within.")
                .defineInRange("radiusBlocks", 5, 1, 64);
        STEAL_GRACE_SECONDS = BUILDER.comment("Seconds the thief may be outside the radius before the attempt fails.")
                .defineInRange("graceSeconds", 15, 0, 3600);
        UNCLAIMED_STEAL_SECONDS = BUILDER.comment("Seconds needed to claim a placed totem that has no owner.")
                .defineInRange("unclaimedStealSeconds", 60, 1, 86400);
        STEAL_INVINCIBILITY_SECONDS = BUILDER.comment("Seconds of invincibility granted to the thief when a steal completes.")
                .defineInRange("invincibilitySeconds", 15, 0, 600);
        STEAL_WARNING_SECONDS = BUILDER.comment("When this many seconds remain, the steal timer and bar pulse red.")
                .defineInRange("warningSeconds", 30, 0, 86400);
        BUILDER.pop();

        BUILDER.comment("Shatter Coins").push("coins");
        COIN_VALUES = BUILDER.comment("Value of Copper, Gold, Diamond, Netherite, Illyrium coins in copper units. Totem upgrade costs are in these units.")
                .defineList("values", List.of(1, 2, 4, 8, 16), BSPConfig::isPositiveInt);
        BUILDER.pop();

        BUILDER.comment("Shatter Coin Factory").push("factory");
        FACTORY_PRESS_HOURS = BUILDER.comment("Real-time hours to press one Copper, Gold, Diamond, Netherite, Illyrium coin without upgrades.",
                        "Time keeps running while the chunk is unloaded or the server is off.")
                .defineList("pressHours", List.of(12.0, 24.0, 48.0, 96.0, 168.0), o -> o instanceof Number n && n.doubleValue() > 0);
        FACTORY_ENERGY_PER_COIN = BUILDER.comment("Forge Energy (RF) used per coin, taken when a press starts.")
                .defineList("energyPerCoin", List.of(50_000, 100_000, 200_000, 400_000, 800_000), o -> o instanceof Integer i && i >= 0);
        FACTORY_ENERGY_CAPACITY = BUILDER.comment("Energy each slice adds to the machine's shared buffer.")
                .defineInRange("energyCapacity", 1_000_000, 1, Integer.MAX_VALUE);
        FACTORY_MAX_RECEIVE = BUILDER.comment("Maximum energy accepted per tick through one Power Port. Feeding more ports of a joined machine charges it faster.")
                .defineInRange("maxReceivePerTick", 10_000, 1, Integer.MAX_VALUE);
        FACTORY_MOTIVATOR_REDUCTIONS = BUILDER.comment("Fraction of a slice's press time removed by one, two and three Motivators on top of it.")
                .defineList("motivatorTimeReduction", List.of(0.15, 0.30, 0.50), o -> o instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() < 1);
        FACTORY_MAX_REDUCTION = BUILDER.comment("Upper limit on the combined reduction, so time can never reach zero.")
                .defineInRange("maxTotalReduction", 0.75, 0.0, 0.99);
        FACTORY_MAX_PER_PLAYER = BUILDER.comment("How many factory slices (controllers) one player may own on this server. A joined machine holds at most 10.")
                .defineInRange("maxPerPlayer", 10, 0, 10_000);
        BUILDER.pop();

        BUILDER.comment("RF upgrades for the processing machines").push("rf_upgrade");
        RF_BONUS = BUILDER.comment("Speed gain of RF Upgrade Mk I, Mk II, Mk III while powered. The same share of lava is saved.")
                .defineList("bonus", List.of(0.05, 0.10, 0.30), o -> o instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() < 1);
        RF_PER_TICK = BUILDER.comment("RF used per working tick by a machine with an RF upgrade.").defineInRange("rfPerTick", 40, 0, 1_000_000);
        RF_CAPACITY = BUILDER.comment("RF buffer of a machine with an RF upgrade.").defineInRange("capacity", 100_000, 1000, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Tetrium Crucible").push("tetrium_crucible");
        TCRUC_NUGGETS = BUILDER.comment("Tetrium Nuggets from one ore.").defineInRange("nuggetsPerOre", 2, 0, 64);
        TCRUC_SLAG = BUILDER.comment("Tetrium Slag from one ore.").defineInRange("slagPerOre", 1, 0, 64);
        TCRUC_TICKS = BUILDER.comment("Ticks to process one ore (20 ticks = 1 second).").defineInRange("ticksPerOre", 400, 1, 1_000_000);
        BUILDER.pop();

        BUILDER.comment("Combination Forge").push("combination_forge");
        FORGE_TICKS = BUILDER.comment("Ticks to forge nine nuggets into one ingot when burning coal or other furnace fuel.").defineInRange("ticksPerIngot", 600, 1, 1_000_000);
        FORGE_RF_MULTIPLIER = BUILDER.comment("How many times faster the forge runs on RF than on fuel (2 = coal is 100% slower).").defineInRange("rfSpeedMultiplier", 2.0, 1.0, 100.0);
        BUILDER.pop();

        BUILDER.comment("Illyrium Crucible (multiblock). Runs on lava only.").push("illyrium_crucible");
        ICRUC_ORE_IN = BUILDER.comment("Smelting: Illyrium Ore per Dirty Illyrium Ingot.").defineInRange("smeltOre", 1, 1, 64);
        ICRUC_SLAG_IN = BUILDER.comment("Smelting: Tetrium Slag per Dirty Illyrium Ingot.").defineInRange("smeltSlag", 2, 0, 64);
        ICRUC_SMELT_TICKS = BUILDER.comment("Smelting: ticks per Dirty Illyrium Ingot.").defineInRange("smeltTicks", 1200, 1, 1_000_000);
        ICRUC_PURE_IN = BUILDER.comment("Alloying: Pure Illyrium Dust per job.").defineInRange("alloyPureDust", 1, 1, 64);
        ICRUC_TDUST_IN = BUILDER.comment("Alloying: Tetrium Dust per job.").defineInRange("alloyTetriumDust", 1, 0, 64);
        ICRUC_NUGGETS_OUT = BUILDER.comment("Alloying: Illyrium Nuggets per job.").defineInRange("alloyNuggets", 1, 1, 64);
        ICRUC_ALLOY_TICKS = BUILDER.comment("Alloying: ticks per job.").defineInRange("alloyTicks", 1800, 1, 1_000_000);
        ICRUC_LAVA_PER_JOB = BUILDER.comment("Lava used per job, in millibuckets.").defineInRange("lavaPerJob", 250, 0, 100_000);
        ICRUC_LAVA_PER_MAGMA = BUILDER.comment("Lava a Magma Block is worth, in millibuckets.").defineInRange("lavaPerMagmaBlock", 250, 1, 100_000);
        ICRUC_TANK = BUILDER.comment("Lava tank size in millibuckets.").defineInRange("lavaTank", 8000, 1000, 1_000_000);
        BUILDER.pop();

        BUILDER.comment("Illyrium Refinery").push("refinery");
        REFINERY_WATER = BUILDER.comment("Water per Pure Illyrium Dust, in millibuckets.").defineInRange("waterPerDust", 500, 0, 100_000);
        REFINERY_TICKS = BUILDER.comment("Ticks per Pure Illyrium Dust.").defineInRange("ticksPerDust", 2400, 1, 1_000_000);
        REFINERY_TANK = BUILDER.comment("Water tank size in millibuckets.").defineInRange("waterTank", 8000, 1000, 1_000_000);
        FILTER_USES = BUILDER.comment("Pure Illyrium Dust a filter can refine before it is used up: Iron, Diamond, Netherite, Illyrium.")
                .defineList("filterUses", List.of(1, 20, 100, 1000), BSPConfig::isPositiveInt);
        BUILDER.pop();

        BUILDER.comment("Crushing an ingot with a pickaxe on a crafting table, for packs without a crusher").push("hand_crushing");
        CRUSH_TETRIUM_CHANCE = BUILDER.comment("Chance a Tetrium Ingot becomes Tetrium Dust.").defineInRange("tetriumDustChance", 1.0 / 3.0, 0.0, 1.0);
        CRUSH_TETRIUM_NUGGETS = BUILDER.comment("Tetrium Nuggets returned when it does not.").defineInRange("tetriumNuggetsOnFail", 3, 0, 64);
        CRUSH_DIRTY_CHANCE = BUILDER.comment("Chance a Dirty Illyrium Ingot becomes Dirty Illyrium Dust.").defineInRange("dirtyIllyriumDustChance", 1.0 / 6.0, 0.0, 1.0);
        CRUSH_DIRTY_NUGGETS = BUILDER.comment("Dirty Illyrium Nuggets returned when it does not. Nine craft back into a Dirty Illyrium Ingot.")
                .defineInRange("dirtyIllyriumNuggetsOnFail", 3, 0, 64);
        BUILDER.pop();

        BUILDER.comment("Totem Compass").push("compass");
        COMPASS_SECONDS_PER_COIN = BUILDER.comment("Seconds of rival-totem tracking a Copper, Gold, Diamond, Netherite, Illyrium coin adds.")
                .defineList("secondsPerCoin", List.of(1, 5, 10, 30, 60), BSPConfig::isPositiveInt);
        COMPASS_COOLDOWN = BUILDER.comment("Cooldown in seconds after tracking ends.").defineInRange("cooldownSeconds", 600, 0, 86400);
        COMPASS_MIN_COOLDOWN = BUILDER.comment("Shortest cooldown reachable with upgrades.").defineInRange("minCooldownSeconds", 180, 0, 86400);
        COMPASS_COOLDOWN_STEP = BUILDER.comment("Seconds removed from the cooldown per upgrade level.").defineInRange("cooldownStepSeconds", 60, 1, 86400);
        COMPASS_UPGRADE_COSTS = BUILDER.comment("Coin value cost of each cooldown upgrade level.")
                .defineList("cooldownUpgradeCosts", List.of(8, 16, 32, 64, 128, 256, 512), BSPConfig::isPositiveInt);
        BUILDER.pop();

        BUILDER.comment("How the totem is drawn").push("visuals");
        AURA_SPHERE_VIEW_DISTANCE = BUILDER.comment("Aura radius spheres are only drawn when the viewer is within this many blocks of the totem.")
                .defineInRange("auraSphereViewDistance", 32, 1, 256);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean isDimensionAllowed(net.minecraft.resources.ResourceLocation dimension) {
        return ALLOWED_DIMENSIONS.get().contains(dimension.toString());
    }

    private static boolean isFraction(Object o) {
        return o instanceof Double d && d >= 0 && d <= 1;
    }

    /** Reads a config value, or the fallback if the config is not loaded yet (e.g. a tooltip on the title screen). */
    public static <T> T getOr(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    /** Value of a per-level list for a 1-based level, or the fallback when the list is shorter. */
    public static <T> T levelValue(List<? extends T> list, int level, T fallback) {
        if (level <= 0 || list.isEmpty()) {
            return fallback;
        }
        return list.get(Math.min(level, list.size()) - 1);
    }

    private static boolean isPositiveInt(Object o) {
        return o instanceof Integer i && i > 0;
    }

    private BSPConfig() {}
}
