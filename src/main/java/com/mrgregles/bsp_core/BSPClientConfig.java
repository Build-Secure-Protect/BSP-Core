package com.mrgregles.bsp_core;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-side settings: what this player sees. Lives in {@code config/bsp_core-client.toml}. */
public final class BSPClientConfig {
    public static final ForgeConfigSpec SPEC;
    /** Whether aura cubes are drawn for this player. Also switched from the totem panel's header and a keybind. */
    public static final ForgeConfigSpec.BooleanValue SHOW_AURAS;
    /** Whether the TEAM button is drawn above the inventory. */
    public static final ForgeConfigSpec.BooleanValue TEAM_BUTTON, KITS_BUTTON;
    /** How big the shop screens are drawn, in percent, where the window has room. */
    public static final ForgeConfigSpec.IntValue SHOP_SCREEN_PERCENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("What this player sees").push("auras");
        SHOW_AURAS = b.comment("Draw the aura cubes of totems and projectors. Hiding them changes nothing about what they do.").define("showAuras", true);
        b.pop();
        b.comment("Teams").push("team");
        TEAM_BUTTON = b.comment("Draw the TEAM button above the inventory, which opens the Team screen. Turn it off if another mod's button sits there; the key binding and the totem screens still open it.").define("teamButton", true);
        b.pop();
        b.comment("Kits").push("kit");
        KITS_BUTTON = b.comment("Draw the KITS button above the inventory, which opens the kit browser. The key binding and /kits still open it.").define("kitsButton", true);
        b.pop();
        b.comment("Shops").push("shop");
        SHOP_SCREEN_PERCENT = b.comment("How big the shop screens are drawn (the shop, Sell here, the Shopping Network, the Workbench and the vendor, stall and display settings), in percent of their own size.",
                "They are never drawn bigger than the window has room for, and shrink to fit a small window whatever this says.").defineInRange("screenPercent", 130, 100, 200);
        b.pop();
        SPEC = b.build();
    }

    private BSPClientConfig() {}

    public static boolean showAuras() {
        return BSPConfig.getOr(SHOW_AURAS, true);
    }

    public static boolean teamButton() {
        return BSPConfig.getOr(TEAM_BUTTON, true);
    }

    public static boolean kitsButton() {
        return BSPConfig.getOr(KITS_BUTTON, true);
    }

    /** The shop screens' size as a factor (1.3 for 130 percent). */
    public static float shopScreenScale() {
        return BSPConfig.getOr(SHOP_SCREEN_PERCENT, 130) / 100f;
    }

    /** Flips the setting and writes it to the client config file. */
    public static void toggleAuras() {
        SHOW_AURAS.set(!showAuras());
        SHOW_AURAS.save();
    }
}
