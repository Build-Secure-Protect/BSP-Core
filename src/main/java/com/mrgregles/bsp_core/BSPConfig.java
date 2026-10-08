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

    /** Totem tiers: coins (of the tier being left) and XP levels to raise the totem from tier I, II, III, IV. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> TIER_GATE_COINS, TIER_GATE_XP;
    /** Cost of the first and second level bought within a tier, per branch. XP is multiplied by the tier number. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> CARRIED_LEVEL_COINS, CARRIED_LEVEL_XP, BASE_LEVEL_COINS, BASE_LEVEL_XP,
            RAID_LEVEL_COINS, RAID_LEVEL_XP;
    /** Level the previous upgrade on a path must reach before the next one opens. */
    public static final ForgeConfigSpec.IntValue UNLOCK_LEVEL;
    /** Effect per level (one entry per level; a short list repeats its last value). */
    public static final ForgeConfigSpec.DoubleValue DAMAGE_PER_LEVEL, RESISTANCE_PER_LEVEL, MINING_SPEED_PER_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FORTIFY_RADIUS, HEALING_RADIUS, VITALITY_HEALTH, WARD_RADIUS, ALARM_RADIUS,
            SANCTUARY_RADIUS, DEADLOCK_SECONDS, OVERCLOCK_RADIUS, LOCKPICK_SECONDS, SHROUD_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> FORTIFY_BREAK_SPEED, FORTIFY_EXPLOSION_PROTECTION, HEALING_PER_SECOND,
            SWIFTNESS_BONUS, FEATHERFALL_REDUCTION, OVERCLOCK_BONUS;
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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPASS_UPGRADE_TIERS;

    // ------------------------------------------------------------------ admin

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADMINS, MODERATORS;
    public static final ForgeConfigSpec.IntValue VAULT_LOCKPICK_SECONDS, VAULT_LOCK_LEVEL_SECONDS, VAULT_LOCKPICK_RADIUS, VAULT_ILLYRIUM_SAFE, VAULT_ALARM_COINS,
            VAULT_ILLYRIUM_PER_PERIOD, VAULT_INTRUDER_BREAK_SECONDS, VAULT_INTEREST_UNIT, VAULT_MAX_BLOCKS, STORAGE_NOTICE_SECONDS,
            CENT_SEPARATE_TICKS, CENT_CHARGE_TICKS, CENT_CHARGE_TICKS_FULL, CENT_RF_SEPARATING, CENT_RF_CHARGING, CENT_MAGNETISE_TICKS, CENT_MAGNETISE_TICKS_FULL,
            DECOY_RF, DECOY_MAX, DECOY_REPAIR_INGOTS, DECOY_EFFECT_SECONDS, DECOY_WARP_DISTANCE,
            PROJECTOR_NEED, PROJECTOR_TANK, EXTRACTOR_TANK, PROJECTOR_CHANNELS, INTERFACE_MAX, CABLE_CHECK_SECONDS, CHARGER_TANK, BATTERY_FEED, EMITTER_DRAW, PROJECTOR_USE, VALVE_MAX, INTERFACE_SIDE_MAX, TANK_PER_BLOCK, TANK_MAX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> CABLE_REACH, CABLE_THROUGHPUT, PLASMA_OUTPUT, BATTERY_CAPACITY, BATTERY_POWERS, CELL_CAPACITY, CELL_POWERS;
    public static final ForgeConfigSpec.DoubleValue REPEATER_PRESSURE;
    public static final ForgeConfigSpec.DoubleValue DECOY_BLAST_POWER;
    public static final ForgeConfigSpec.BooleanValue CHUNKS_ENABLED, CHUNKS_OWNER_ONLINE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> BOUNCY_REDUCTION, BOUNCY_BOUNCE, XRAY_ALPHA;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> XRAY_RADIUS, XRAY_SECONDS, XRAY_RECHARGE, CLOAK_RADIUS, RECALL_RADIUS, RECALL_BLOCK_SECONDS;
    public static final ForgeConfigSpec.IntValue CLOAK_DRAW, RECALL_PROMPT_SECONDS, RECALL_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue CHUNKS_MAX_PER_PLAYER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> CHUNKS_PER_LEVEL, CHUNK_RANGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> DECOY_RANGES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> CENT_NUGGETS_MIN, CENT_NUGGETS_MAX, CENT_CHARGE_ODDS;
    public static final ForgeConfigSpec.DoubleValue VAULT_LOCKPICK_SHARE, VAULT_XP_RATE, VAULT_TETRIUM_RATE, VAULT_CAP_DAYS, VAULT_ILLYRIUM_PERIOD_DAYS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> VAULT_LOCK_COINS;

    // ------------------------------------------------------------------ scoring

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> SCORE_TIER_POINTS;
    public static final ForgeConfigSpec.IntValue SCORE_REFRESH_SECONDS;

    // ------------------------------------------------------------------ storage

    public static final ForgeConfigSpec.ConfigValue<String> STORAGE_MODE, STORAGE_SERVER_ID, STORAGE_HOST, STORAGE_DATABASE, STORAGE_USER, STORAGE_PASSWORD,
            STORAGE_TABLE_PREFIX;
    public static final ForgeConfigSpec.IntValue STORAGE_PORT;
    public static final ForgeConfigSpec.BooleanValue STORAGE_SSL;

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

        BUILDER.comment("Shatter Totem upgrades.",
                        "The totem has five tiers, I to V, each with its own coin: Copper, Gold, Diamond, Netherite, Illyrium.",
                        "An upgrade gains two levels per totem tier from the tier it appears at, and each level costs the coin of the tier it is bought at.")
               .push("upgrades");

        BUILDER.comment("Prices. Every price is a number of one specific Shatter Coin plus XP levels.").push("costs");
        TIER_GATE_COINS = BUILDER.comment("Coins to raise the totem from tier I, II, III, IV. They are coins of the tier being left: Copper, Gold, Diamond, Netherite.")
                .defineList("tierGateCoins", List.of(4, 4, 4, 4), BSPConfig::isPositiveInt);
        TIER_GATE_XP = BUILDER.comment("XP levels to raise the totem from tier I, II, III, IV.")
                .defineList("tierGateXpLevels", List.of(15, 25, 35, 50), BSPConfig::isPositiveInt);
        CARRIED_LEVEL_COINS = BUILDER.comment("Carried upgrades: coins for the first and second level bought within a tier.")
                .defineList("carriedLevelCoins", List.of(1, 1), BSPConfig::isPositiveInt);
        CARRIED_LEVEL_XP = BUILDER.comment("Carried upgrades: XP levels for the first and second level within a tier, multiplied by the tier number (I = 1 ... V = 5).")
                .defineList("carriedLevelXp", List.of(4, 6), BSPConfig::isPositiveInt);
        BASE_LEVEL_COINS = BUILDER.comment("Base upgrades: coins for the first and second level within a tier.")
                .defineList("baseLevelCoins", List.of(1, 2), BSPConfig::isPositiveInt);
        BASE_LEVEL_XP = BUILDER.comment("Base upgrades: XP levels, multiplied by the tier number.")
                .defineList("baseLevelXp", List.of(2, 3), BSPConfig::isPositiveInt);
        RAID_LEVEL_COINS = BUILDER.comment("Raid upgrades: coins for the first and second level within a tier.")
                .defineList("raidLevelCoins", List.of(2, 3), BSPConfig::isPositiveInt);
        RAID_LEVEL_XP = BUILDER.comment("Raid upgrades: XP levels, multiplied by the tier number.")
                .defineList("raidLevelXp", List.of(3, 4), BSPConfig::isPositiveInt);
        UNLOCK_LEVEL = BUILDER.comment("Level an upgrade must reach before the next one on its path opens.").defineInRange("unlockLevel", 2, 1, 10);
        BUILDER.pop();

        BUILDER.comment("Carried upgrades (work while the totem is in the offhand).").push("carried");
        DAMAGE_PER_LEVEL = BUILDER.comment("Damage: attack damage added per level (10 levels).").defineInRange("damagePerLevel", 0.5, 0.0, 100.0);
        RESISTANCE_PER_LEVEL = BUILDER.comment("Resistance: share of incoming damage removed per level (8 levels).").defineInRange("resistancePerLevel", 0.03, 0.0, 0.12);
        MINING_SPEED_PER_LEVEL = BUILDER.comment("Mining Speed: mining speed added per level, as a fraction (10 levels).").defineInRange("miningSpeedPerLevel", 0.10, 0.0, 10.0);
        SWIFTNESS_BONUS = BUILDER.comment("Swiftness: movement speed added, as a fraction, per level (8 levels).")
                .defineList("swiftnessSpeedBonus", List.of(0.02, 0.04, 0.06, 0.08, 0.10, 0.12, 0.14, 0.16), BSPConfig::isFraction);
        VITALITY_HEALTH = BUILDER.comment("Vitality: extra health points per level (2 = one heart; 6 levels).")
                .defineList("vitalityHealth", List.of(2, 4, 6, 8, 10, 12), BSPConfig::isPositiveInt);
        FEATHERFALL_REDUCTION = BUILDER.comment("Featherfall: share of fall damage removed per level (6 levels).")
                .defineList("featherfallReduction", List.of(0.16, 0.32, 0.48, 0.64, 0.80, 0.96), BSPConfig::isFraction);
        BUILDER.pop();

        BUILDER.comment("Base upgrades (work only while the totem is placed).").push("base");
        FORTIFY_RADIUS = BUILDER.comment("Fortify: protected radius in blocks per level (10 levels)")
                .defineList("fortifyRadius", List.of(1, 2, 3, 4, 5, 6, 7, 9, 12, 15), BSPConfig::isPositiveInt);
        FORTIFY_BREAK_SPEED = BUILDER.comment("Fortify: non-owner mining speed multiplier per level (lower = harder; never below limits.minIntruderMiningSpeed)")
                .defineList("fortifyBreakSpeedMultiplier", List.of(0.8, 0.7, 0.6, 0.5, 0.45, 0.4, 0.35, 0.3, 0.25, 0.2), BSPConfig::isFraction);
        FORTIFY_EXPLOSION_PROTECTION = BUILDER.comment("Fortify: chance (0-1) that each block in range survives an explosion, per level")
                .defineList("fortifyExplosionProtection", List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0), BSPConfig::isFraction);
        HEALING_RADIUS = BUILDER.comment("Healing Aura: radius in blocks per level (10 levels)")
                .defineList("healingRadius", List.of(3, 4, 5, 6, 7, 8, 9, 10, 12, 15), BSPConfig::isPositiveInt);
        HEALING_PER_SECOND = BUILDER.comment("Healing Aura: health points healed per second per level (2 = one heart)")
                .defineList("healingPerSecond", List.of(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0), o -> o instanceof Double d && d > 0);
        ALARM_RADIUS = BUILDER.comment("Alarm: intruders within this many blocks are outlined and the owner is told (8 levels)")
                .defineList("alarmRadius", List.of(4, 8, 12, 16, 20, 24, 28, 32), BSPConfig::isPositiveInt);
        SANCTUARY_RADIUS = BUILDER.comment("Sanctuary: hostile mobs do not spawn naturally within this many blocks (8 levels)")
                .defineList("sanctuaryRadius", List.of(6, 12, 18, 24, 30, 36, 42, 48), BSPConfig::isPositiveInt);
        WARD_RADIUS = BUILDER.comment("Ward: intruders within this many blocks are weakened (6 levels; Weakness I at levels 1-2, II at 3-4, III at 5-6)")
                .defineList("wardRadius", List.of(3, 6, 9, 12, 15, 18), BSPConfig::isPositiveInt);
        DEADLOCK_SECONDS = BUILDER.comment("Deadlock: seconds added to the time needed to steal this totem (4 levels)")
                .defineList("deadlockSeconds", List.of(30, 60, 90, 120), BSPConfig::isPositiveInt);
        OVERCLOCK_RADIUS = BUILDER.comment("Overclock: BSP-Core machines within this many blocks work faster (4 levels)")
                .defineList("overclockRadius", List.of(8, 10, 12, 16), BSPConfig::isPositiveInt);
        OVERCLOCK_BONUS = BUILDER.comment("Overclock: speed added per level, as a fraction")
                .defineList("overclockSpeedBonus", List.of(0.04, 0.08, 0.12, 0.16), BSPConfig::isFraction);
        BUILDER.pop();

        BUILDER.comment("Raid upgrades (apply when the totem carrying them is in the thief's offhand).").push("raid");
        LOCKPICK_SECONDS = BUILDER.comment("Lockpick: seconds taken off the time you need to steal a totem (8 levels)")
                .defineList("lockpickSeconds", List.of(10, 20, 30, 40, 50, 60, 70, 80), BSPConfig::isPositiveInt);
        SHROUD_SECONDS = BUILDER.comment("Shroud: seconds before the owner is warned that you are stealing (6 levels)")
                .defineList("shroudSeconds", List.of(4, 8, 12, 16, 20, 24), BSPConfig::isPositiveInt);
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
        COMPASS_UPGRADE_COSTS = BUILDER.comment("How many coins each cooldown upgrade level costs. The kind of coin is set in cooldownUpgradeCoinTiers.")
                .defineList("cooldownUpgradeCoins", List.of(4, 4, 4, 4, 2, 4, 8), BSPConfig::isPositiveInt);
        COMPASS_UPGRADE_TIERS = BUILDER.comment("Which Shatter Coin each cooldown upgrade level is paid in: copper, gold, diamond, netherite or illyrium.",
                        "One entry per level, matching cooldownUpgradeCoins. A short list repeats its last entry.")
                .defineList("cooldownUpgradeCoinTiers", List.of("copper", "gold", "diamond", "netherite", "illyrium", "illyrium", "illyrium"),
                        o -> o instanceof String str && java.util.Arrays.stream(com.mrgregles.bsp_core.coin.CoinTier.values()).anyMatch(t -> t.key.equals(str)));
        BUILDER.pop();

        BUILDER.comment("Who may use BSP-Core's admin tools (admin block, Score Screen settings, season controls).").push("admin");
        ADMINS = BUILDER.comment("Player names with full control. If this list is empty, every server operator is an admin.")
                .defineListAllowEmpty(List.of("admins"), List::of, o -> o instanceof String);
        MODERATORS = BUILDER.comment("Player names who may open the admin panel to look, but cannot change or reset anything.")
                .defineListAllowEmpty(List.of("moderators"), List::of, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("The Magnetic Centrifuge. Lists have one entry for each number of stacked layers, 1 to 6.").push("centrifuge");
        CENT_SEPARATE_TICKS = BUILDER.comment("Ticks to separate one Magnatite Ore (20 ticks = 1 second).").defineInRange("separateTicks", 900, 1, 1_000_000);
        CENT_NUGGETS_MIN = BUILDER.comment("Fewest Magnatite Nuggets from one ore, by layers.").defineList("nuggetsMin", List.of(3, 3, 4, 5, 6, 7), BSPConfig::isPositiveInt);
        CENT_NUGGETS_MAX = BUILDER.comment("Most Magnatite Nuggets from one ore, by layers.").defineList("nuggetsMax", List.of(3, 4, 5, 6, 7, 9), BSPConfig::isPositiveInt);
        CENT_CHARGE_TICKS = BUILDER.comment("Ticks for one try at charging a Magnatite Ingot.").defineInRange("chargeTicks", 900, 1, 1_000_000);
        CENT_CHARGE_TICKS_FULL = BUILDER.comment("Ticks for one try with all six layers.").defineInRange("chargeTicksFullStack", 500, 1, 1_000_000);
        CENT_CHARGE_ODDS = BUILDER.comment("A try succeeds one time in this many, by layers.").defineList("chargeOdds", List.of(6, 5, 4, 3, 2, 2), BSPConfig::isPositiveInt);
        CENT_RF_SEPARATING = BUILDER.comment("RF per tick, per layer, while separating ore.").defineInRange("rfPerTickSeparating", 60, 0, 1_000_000);
        CENT_RF_CHARGING = BUILDER.comment("RF per tick, per layer, while charging.").defineInRange("rfPerTickCharging", 240, 0, 1_000_000);
        CENT_MAGNETISE_TICKS = BUILDER.comment("Ticks to magnetise one Resonance Crystal (with a Magnatite Nugget in the upgrade slot).").defineInRange("magnetiseTicks", 600, 1, 1_000_000);
        CENT_MAGNETISE_TICKS_FULL = BUILDER.comment("Ticks to magnetise with all six layers.").defineInRange("magnetiseTicksFullStack", 400, 1, 1_000_000);
        BUILDER.pop();

        BUILDER.comment("Decoy Totems: fake totems that mislead Totem Compasses.").push("decoy");
        DECOY_RANGES = BUILDER.comment("Blocks within which a rival-tracking compass is fooled, with 0, 1, 2 and 3 Range Coils fitted. Also editable in the admin panel.")
                .defineList("ranges", List.of(24, 40, 64, 96), BSPConfig::isPositiveInt);
        DECOY_RF = BUILDER.comment("RF per tick a decoy draws from its Decoy Power Base. Decoys are meant to be costly to run.").defineInRange("rfPerTick", 100, 0, 1_000_000);
        DECOY_MAX = BUILDER.comment("Most decoys a player may have placed at once.").defineInRange("maxPerPlayer", 5, 1, 100);
        DECOY_REPAIR_INGOTS = BUILDER.comment("Magnatite Ingots it costs the owner to repair a broken decoy.").defineInRange("repairIngots", 2, 0, 64);
        DECOY_EFFECT_SECONDS = BUILDER.comment("How long the Hex, Poison and Fatigue traps last.").defineInRange("effectSeconds", 30, 1, 3600);
        DECOY_BLAST_POWER = BUILDER.comment("Strength of the Blast trap (TNT is 4). It never breaks blocks.").defineInRange("blastPower", 3.0, 0.0, 16.0);
        DECOY_WARP_DISTANCE = BUILDER.comment("Furthest the Warp trap throws a thief, in blocks.").defineInRange("warpDistance", 30, 4, 256);
        BUILDER.pop();

        BUILDER.comment("Wave Plasma: the fluid a placed totem gives off, and the extractors, interfaces, cables, repeaters and projectors that carry it.").push("plasma");
        PLASMA_OUTPUT = BUILDER.comment("mB per tick a placed totem gives off with the Output upgrade at level 0 (none), 1, 2 ... Split evenly between the extractors stacked under it.")
                .defineList("outputPerLevel", List.of(100, 200, 350, 500, 750, 1000, 1500, 2000, 3000, 4500, 6000), BSPConfig::isPositiveInt);
        EXTRACTOR_TANK = BUILDER.comment("mB a Plasma Extractor keeps in reserve. On top of it the extractor holds one second of its flow, which the network takes each second.").defineInRange("extractorTank", 4000, 100, 1_000_000);
        INTERFACE_MAX = BUILDER.comment("Most Plasma Interface blocks that may join into one. Any more show a red seam and do nothing, so interfaces cannot be used as cables.").defineInRange("interfaceMax", 12, 1, 64);
        PROJECTOR_NEED = BUILDER.comment("mB per tick that must arrive at a Projector for it to project.").defineInRange("projectorNeed", 100, 1, 100_000);
        PROJECTOR_USE = BUILDER.comment("mB per tick a Projector burns from its base while projecting. Less than projectorNeed, so the base fills while fed and keeps the projector going for a while after a cut.").defineInRange("projectorUse", 20, 1, 100_000);
        VALVE_MAX = BUILDER.comment("Most mB per tick a Plasma Valve can be set to let through: the top of its dial.").defineInRange("valveMax", 1000, 10, 1_000_000);
        PROJECTOR_TANK = BUILDER.comment("mB a Projector Base holds for the projector on it: it fills from the cable and the projector draws from it, so it runs on for a while after a cut.").defineInRange("projectorTank", 5000, 100, 1_000_000);
        PROJECTOR_CHANNELS = BUILDER.comment("How many powers a Projector receives at once. A fitted Channel Expander adds one.").defineInRange("channels", 2, 1, 7);
        REPEATER_PRESSURE = BUILDER.comment("Share of the plasma that passes each Plasma Repeater (0.9 = a tenth is lost at every repeater). Power levels also drop one per repeater when two or more are received.")
                .defineInRange("repeaterPressure", 0.9, 0.1, 1.0);
        CABLE_CHECK_SECONDS = BUILDER.comment("How often, in seconds, an interface follows its cables all the way to the projectors, loading the chunks along the run for a moment to confirm it.",
                        "In between it only looks at chunks that are already loaded and trusts the last full check for the rest. Lower is quicker to notice a cut cable far away; higher is lighter on the server.")
                .defineInRange("cableCheckSeconds", 30, 5, 600);
        CHARGER_TANK = BUILDER.comment("mB a Battery Charger holds while waiting to pour into the item in it.").defineInRange("chargerTank", 4000, 100, 1_000_000);
        BATTERY_FEED = BUILDER.comment("mB per tick a Plasma Battery on an extractor stack gives, split between the stack like a totem's output.").defineInRange("batteryFeed", 100, 1, 100_000);
        EMITTER_DRAW = BUILDER.comment("mB per tick a Wave Emitter draws from its Power Cell while switched on in the offhand.").defineInRange("emitterDraw", 20, 0, 100_000);
        BATTERY_CAPACITY = BUILDER.comment("mB a Plasma Battery holds, tiers I to IV.").defineList("batteryCapacity", List.of(40_000, 200_000, 1_000_000, 5_000_000), BSPConfig::isPositiveInt);
        BATTERY_POWERS = BUILDER.comment("Powers a Plasma Battery may hold, tiers I to IV (0 = plasma only).").defineList("batteryPowers", List.of(0, 2, 3, 4), o -> o instanceof Integer i && i >= 0);
        CELL_CAPACITY = BUILDER.comment("mB a Power Cell holds, tiers I to III.").defineList("cellCapacity", List.of(8_000, 24_000, 60_000), BSPConfig::isPositiveInt);
        CELL_POWERS = BUILDER.comment("Powers a Power Cell may hold, tiers I to III.").defineList("cellPowers", List.of(1, 2, 3), BSPConfig::isPositiveInt);
        CABLE_REACH = BUILDER.comment("The longest run each cable carries at full pressure: Tetrium Core, Magnatite Core, Illyrium Core, Charged Illyrium Core. A repeater starts a fresh run.")
                .defineList("cableReach", List.of(15, 25, 40, 80), BSPConfig::isPositiveInt);
        CABLE_THROUGHPUT = BUILDER.comment("Most mB per tick each cable kind carries: Tetrium Core, Magnatite Core, Illyrium Core, Charged Illyrium Core. A run is held to its weakest cable.")
                .defineList("cableThroughput", List.of(250, 500, 1000, 1000), BSPConfig::isPositiveInt);
        TANK_PER_BLOCK = BUILDER.comment("mB each block of a Plasma Tank's shell holds (casing, glass and ports alike).").defineInRange("tankPerBlock", 2_500_000, 1000, 100_000_000);
        TANK_MAX = BUILDER.comment("Longest side of a Plasma Tank, in blocks (the shortest is 3).").defineInRange("tankMax", 12, 3, 32);
        INTERFACE_SIDE_MAX = BUILDER.comment("Most mB per tick one face of a Plasma Interface sends into the run leaving it. More output needs more faces, so a big totem needs a bigger interface group.")
                .defineInRange("interfaceSideMax", 1000, 10, 1_000_000);
        BUILDER.pop();

        BUILDER.comment("The Coin Vault: a safe for Shatter Coins. Vault blocks of one owner that touch join into one vault of up to 3 x 3 x 3.").push("vault");
        VAULT_LOCKPICK_SECONDS = BUILDER.comment("Seconds a player without access needs to pick the lock of a vault with no lock upgrade.")
                .defineInRange("lockpickSeconds", 120, 5, 3600);
        VAULT_LOCK_LEVEL_SECONDS = BUILDER.comment("Extra seconds each lock level (up to 3) adds to that.").defineInRange("lockpickSecondsPerLockLevel", 60, 0, 3600);
        VAULT_LOCKPICK_RADIUS = BUILDER.comment("The thief must stay within this many blocks of the vault while picking.").defineInRange("lockpickRadius", 4, 1, 32);
        VAULT_LOCKPICK_SHARE = BUILDER.comment("Share of the coins in the vault a successful pick takes. Which coins is random.").defineInRange("lockpickShare", 0.25, 0.0, 1.0);
        VAULT_ILLYRIUM_SAFE = BUILDER.comment("No Illyrium coins are taken while the vault holds this many or fewer.").defineInRange("illyriumSafeCount", 3, 0, 10000);
        VAULT_LOCK_COINS = BUILDER.comment("Coins lock level 1, 2, 3 costs. Level 1 is paid in Gold coins, 2 in Diamond, 3 in Netherite.")
                .defineList("lockCoins", List.of(4, 4, 4), BSPConfig::isPositiveInt);
        VAULT_ALARM_COINS = BUILDER.comment("Gold coins the Alarm costs. It warns everyone with access when someone starts picking the lock.")
                .defineInRange("alarmCoins", 4, 1, 64);
        VAULT_INTRUDER_BREAK_SECONDS = BUILDER.comment("Seconds anyone but the owner needs to mine a vault block, with any tool. They get no coins: those are kept for the owner.",
                        "Keep this short: a vault must never be a way to wall a totem in.")
                .defineInRange("intruderBreakSeconds", 30, 1, 600);
        VAULT_MAX_BLOCKS = BUILDER.comment("Most vault blocks one player may own on this server. 27 is one full 3 x 3 x 3 vault.").defineInRange("maxBlocksPerPlayer", 27, 1, 1000);
        BUILDER.comment("Interest, worked out on everything a player keeps in all their vaults together. It builds up while they are offline too,",
                "stops at the cap, and has to be redeemed at a vault.").push("interest");
        VAULT_INTEREST_UNIT = BUILDER.comment("The amount of stored coin value the two rates below are paid for (Copper 1, Gold 2, Diamond 4, Netherite 8, Illyrium 16 by default).")
                .defineInRange("coinValuePerUnit", 50, 1, 1000000);
        VAULT_XP_RATE = BUILDER.comment("XP levels earned per day for every coinValuePerUnit of coin value stored.")
                .defineInRange("xpLevelsPerDayPerUnit", 1.0, 0.0, 1000.0);
        VAULT_TETRIUM_RATE = BUILDER.comment("Tetrium Ingots earned per day for every coinValuePerUnit of coin value stored.").defineInRange("tetriumPerDayPerUnit", 0.5, 0.0, 1000.0);
        VAULT_CAP_DAYS = BUILDER.comment("Unredeemed XP and Tetrium stop building up at this many days' worth.").defineInRange("capDays", 3.0, 0.1, 365.0);
        VAULT_ILLYRIUM_PER_PERIOD = BUILDER.comment("Illyrium Ingots earned per period while the vaults hold at least one Netherite or Illyrium coin. This is also the most that can wait unredeemed.")
                .defineInRange("illyriumIngotsPerPeriod", 2, 0, 64);
        VAULT_ILLYRIUM_PERIOD_DAYS = BUILDER.comment("Length of that period in days.").defineInRange("illyriumPeriodDays", 3.0, 0.1, 365.0);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("The leaderboard. A player's score is the sum of the tier points of every totem they own.").push("scoring");
        SCORE_TIER_POINTS = BUILDER.comment("Points a totem is worth at tier I, II, III, IV, V.")
                .defineList("tierPoints", List.of(1, 2, 4, 7, 10), BSPConfig::isPositiveInt);
        SCORE_REFRESH_SECONDS = BUILDER.comment("How often the leaderboard is rebuilt and sent to players. On a single server a change also shows within a second.",
                        "On a network this is how often the shared database is read.")
                .defineInRange("refreshSeconds", 30, 5, 3600);
        BUILDER.pop();

        BUILDER.comment("Where BSP-Core keeps its records.",
                        "\"local\": in this world only. Nothing to set up; right for a single server.",
                        "\"mysql\": also in a MySQL or MariaDB database shared by every server of a network, so first-join totems and the",
                        "factory slice limit apply across the whole network. You provide the database and an account; BSP-Core only creates",
                        "its own tables in it. Fill in the details below, run /bsp storage test, then /bsp storage migrate, then set mode to",
                        "\"mysql\" and restart.")
               .push("storage");
        STORAGE_MODE = BUILDER.comment("\"local\" or \"mysql\".").define("mode", "local");
        STORAGE_SERVER_ID = BUILDER.comment("A short name for this server, different on every server of the network (for example \"hub\", \"survival-1\").")
                .define("serverId", "server-1");
        STORAGE_HOST = BUILDER.comment("Database host name or address.").define("host", "localhost");
        STORAGE_PORT = BUILDER.comment("Database port.").defineInRange("port", 3306, 1, 65535);
        STORAGE_DATABASE = BUILDER.comment("Name of the existing database (schema) to use.").define("database", "bsp");
        STORAGE_USER = BUILDER.comment("Database user. It needs CREATE, SELECT, INSERT, UPDATE and DELETE on that database.").define("user", "bsp");
        STORAGE_PASSWORD = BUILDER.comment("Database password. This file is plain text: keep it private.").define("password", "");
        STORAGE_TABLE_PREFIX = BUILDER.comment("Prefix for BSP-Core's table names (letters, digits and underscores).").define("tablePrefix", "bsp_");
        STORAGE_SSL = BUILDER.comment("Connect with SSL/TLS.").define("useSsl", false);
        STORAGE_NOTICE_SECONDS = BUILDER.comment("How often this server collects messages left for its players by other servers, such as \"your totem is being stolen\".",
                        "Lower is quicker to warn but asks the database more often.")
                .defineInRange("noticeSeconds", 3, 1, 60);
        BUILDER.pop();

        BUILDER.comment("Chunk loading: the totem's Anchor and Survey upgrades.").push("chunks");
        CHUNKS_ENABLED = BUILDER.comment("Master switch. When false no chunk is kept loaded by a totem or projector; choices players have made are kept.").define("enabled", true);
        CHUNKS_PER_LEVEL = BUILDER.comment("Chunks a totem keeps loaded at Anchor level 1, 2 and 3. The totem's own chunk is always one of them.",
                        "A player's second and later totems count as level 1 whatever they have bought.")
                .defineList("chunksPerLevel", List.of(1, 3, 6), BSPConfig::isPositiveInt);
        CHUNK_RANGE = BUILDER.comment("How many chunks either side of the totem (or projector) may be picked, with Survey at level 0, 1 and 2. 1 is a 3 x 3 square, 3 is 7 x 7 (the most).")
                .defineList("rangePerLevel", List.of(1, 2, 3), BSPConfig::isPositiveInt);
        CHUNKS_OWNER_ONLINE = BUILDER.comment("When true a totem's chunks are only kept loaded while its owner is online. Also a switch in the admin panel.").define("ownerMustBeOnline", false);
        CHUNKS_MAX_PER_PLAYER = BUILDER.comment("Most chunks one player may keep loaded across all their totems. 0 means no limit beyond the upgrades.").defineInRange("maxPerPlayer", 0, 0, 10_000);
        BUILDER.pop();

        BUILDER.comment("The newer totem powers: Bouncy, X-ray, Cloaking, Recall and Recall Block. Lists have one entry per level.").push("effects");
        BOUNCY_REDUCTION = BUILDER.comment("Share of fall damage Bouncy takes away, by level.").defineList("bouncyFallReduction", List.of(0.25, 0.5, 0.75, 1.0), BSPConfig::isFraction);
        BOUNCY_BOUNCE = BUILDER.comment("Share of the landing speed Bouncy throws you back up with, by level (like slime boots: a higher fall bounces higher). Sneak to land flat.")
                .defineList("bouncyBounce", List.of(0.3, 0.45, 0.6, 0.75), BSPConfig::isFraction);
        XRAY_RADIUS = BUILDER.comment("Blocks around the carrier X-ray shows ores and containers within, by level.").defineList("xrayRadius", List.of(4, 6, 8, 10, 12), BSPConfig::isPositiveInt);
        XRAY_SECONDS = BUILDER.comment("Seconds X-ray lasts, by level.").defineList("xraySeconds", List.of(25, 30, 35, 40, 45), BSPConfig::isPositiveInt);
        XRAY_RECHARGE = BUILDER.comment("Seconds X-ray needs to recharge after a use, by level.").defineList("xrayRecharge", List.of(120, 110, 100, 90, 80), BSPConfig::isPositiveInt);
        XRAY_ALPHA = BUILDER.comment("How strongly X-ray marks show, 0 to 1, by level.").defineList("xrayStrength", List.of(0.35, 0.45, 0.55, 0.65, 0.75), BSPConfig::isFraction);
        CLOAK_RADIUS = BUILDER.comment("Blocks either side of the totem a Cloaking cube reaches, by level.").defineList("cloakRadius", List.of(6, 9, 12, 16), BSPConfig::isPositiveInt);
        CLOAK_DRAW = BUILDER.comment("mB per tick of the totem's Wave Plasma output that Cloaking uses while on.").defineInRange("cloakDraw", 50, 0, 100_000);
        RECALL_RADIUS = BUILDER.comment("Recall lands the owner within this many blocks of the totem, by level. No offer is made when they are already that close.")
                .defineList("recallRadius", List.of(150, 120, 100, 80, 60, 40, 20), BSPConfig::isPositiveInt);
        RECALL_PROMPT_SECONDS = BUILDER.comment("Seconds the Recall offer stays open.").defineInRange("recallPromptSeconds", 20, 3, 300);
        RECALL_COOLDOWN_MINUTES = BUILDER.comment("Minutes Recall rests after a use.").defineInRange("recallCooldownMinutes", 10, 0, 1440);
        RECALL_BLOCK_SECONDS = BUILDER.comment("Seconds a thief's Recall Block delays the owner's Recall offer, by level, on top of Shroud.").defineList("recallBlockSeconds", List.of(10, 20, 30, 45), BSPConfig::isPositiveInt);
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
