package com.mrgregles.bsp_core;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-side settings: what this player sees. Lives in {@code config/bsp_core-client.toml}. */
public final class BSPClientConfig {
    public static final ForgeConfigSpec SPEC;
    /** Whether aura cubes are drawn for this player. Also switched from the totem panel's header and a keybind. */
    public static final ForgeConfigSpec.BooleanValue SHOW_AURAS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("What this player sees").push("auras");
        SHOW_AURAS = b.comment("Draw the aura cubes of totems and projectors. Hiding them changes nothing about what they do.").define("showAuras", true);
        b.pop();
        SPEC = b.build();
    }

    private BSPClientConfig() {}

    public static boolean showAuras() {
        return BSPConfig.getOr(SHOW_AURAS, true);
    }

    /** Flips the setting and writes it to the client config file. */
    public static void toggleAuras() {
        SHOW_AURAS.set(!showAuras());
        SHOW_AURAS.save();
    }
}
