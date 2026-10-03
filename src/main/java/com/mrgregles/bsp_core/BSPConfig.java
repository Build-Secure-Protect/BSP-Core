package com.mrgregles.bsp_core;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side configuration. Values are read through the {@link ForgeConfigSpec} accessors so they
 * always reflect the loaded config for the current server/world.
 */
public final class BSPConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.push("first_join");
    }

    /**
     * Whether this server hands out a Shatter Totem on a player's first ever join. The BSP server
     * network only enables this on the Spawn Hub; every other backend server must leave it false.
     */
    public static final ForgeConfigSpec.BooleanValue GRANT_TOTEM_ON_FIRST_JOIN = BUILDER
            .comment("Give new players a Shatter Totem on their first join.",
                     "Enable ONLY on the Spawn Hub server of the network.")
            .define("grantTotemOnFirstJoin", false);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private BSPConfig() {}
}
