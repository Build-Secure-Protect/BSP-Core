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

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("BSP Core server configuration").push("first_join");
        GRANT_TOTEM_ON_FIRST_JOIN = BUILDER
                .comment("Give new players a Shatter Totem on their first join.",
                         "Enable ONLY on the Spawn Hub server of the network.")
                .define("grantTotemOnFirstJoin", false);
        BUILDER.pop();

        BUILDER.comment("Shatter Totem buff upgrade costs, in experience levels.",
                        "One entry per buff level; the number of entries is the maximum level.")
               .push("upgrades");
        List<Integer> defaults = List.of(5, 10, 20, 35, 55);
        DAMAGE_XP_COSTS = BUILDER.comment("Damage buff").defineList("damageXpLevelCosts", defaults, BSPConfig::isPositiveInt);
        RESISTANCE_XP_COSTS = BUILDER.comment("Resistance buff").defineList("resistanceXpLevelCosts", defaults, BSPConfig::isPositiveInt);
        MINING_SPEED_XP_COSTS = BUILDER.comment("Mining speed buff").defineList("miningSpeedXpLevelCosts", defaults, BSPConfig::isPositiveInt);

        List<Integer> coinDefaults = List.of(5, 10, 20, 40, 80);
        FORTIFY_COIN_COSTS = BUILDER.comment("Fortify (placed only): Shatter Coin cost per level")
                .defineList("fortifyCoinCosts", coinDefaults, BSPConfig::isPositiveInt);
        FORTIFY_RADIUS = BUILDER.comment("Fortify: protected radius in blocks per level")
                .defineList("fortifyRadius", List.of(1, 3, 5, 7, 15), BSPConfig::isPositiveInt);
        FORTIFY_BREAK_SPEED = BUILDER.comment("Fortify: non-owner mining speed multiplier per level (lower = harder)")
                .defineList("fortifyBreakSpeedMultiplier", List.of(0.6, 0.45, 0.3, 0.2, 0.1), BSPConfig::isFraction);
        FORTIFY_EXPLOSION_PROTECTION = BUILDER.comment("Fortify: chance (0-1) that each block in range survives an explosion, per level")
                .defineList("fortifyExplosionProtection", List.of(0.3, 0.5, 0.7, 0.85, 1.0), BSPConfig::isFraction);
        HEALING_COIN_COSTS = BUILDER.comment("Healing Aura (placed only): Shatter Coin cost per level")
                .defineList("healingCoinCosts", coinDefaults, BSPConfig::isPositiveInt);
        HEALING_RADIUS = BUILDER.comment("Healing Aura: radius in blocks per level")
                .defineList("healingRadius", List.of(3, 5, 7, 10, 15), BSPConfig::isPositiveInt);
        HEALING_PER_SECOND = BUILDER.comment("Healing Aura: health points healed per second per level (2 = one heart)")
                .defineList("healingPerSecond", List.of(0.5, 1.0, 1.5, 2.0, 3.0), o -> o instanceof Double d && d > 0);
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

        SPEC = BUILDER.build();
    }

    public static boolean isDimensionAllowed(net.minecraft.resources.ResourceLocation dimension) {
        return ALLOWED_DIMENSIONS.get().contains(dimension.toString());
    }

    private static boolean isFraction(Object o) {
        return o instanceof Double d && d >= 0 && d <= 1;
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
