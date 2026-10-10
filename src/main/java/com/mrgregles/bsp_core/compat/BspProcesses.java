package com.mrgregles.bsp_core.compat;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What BSP-Core's machines do, built from the live config values the machines use, for the recipe viewers (JEI and EMI) to show.
 * The machines' jobs are code, not recipe files, so this is the one place that describes them; both plugins read it.
 */
public final class BspProcesses {
    private BspProcesses() {}

    /** The machine names, in the order the viewers list them; each is a recipe category. */
    public static final List<String> MACHINES = List.of("tetrium_crucible", "combination_forge", "illyrium_crucible", "illyrium_refinery", "magnetic_centrifuge", "coin_pressing", "hand_crushing");

    /** The item that stands for a machine category (its icon and workstation). */
    public static ItemStack icon(String machine) {
        return switch (machine) {
            case "tetrium_crucible" -> new ItemStack(ModItems.TETRIUM_CRUCIBLE.get());
            case "combination_forge" -> new ItemStack(ModItems.COMBINATION_FORGE.get());
            case "coin_pressing" -> new ItemStack(ModItems.COIN_FACTORY.get());
            case "hand_crushing" -> new ItemStack(Items.IRON_PICKAXE);
            default -> stack(machine);
        };
    }

    /** The lang key of a machine category's title. */
    public static String titleKey(String machine) {
        return switch (machine) {
            case "tetrium_crucible", "combination_forge" -> "block.bsp_core." + machine;
            default -> "jei.bsp_core." + machine;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack stack(String name) {
        return stack(name, 1);
    }

    private static ItemStack stack(String name, int count) {
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, name));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static List<ItemStack> ores(String metal) {
        List<ItemStack> out = new ArrayList<>();
        ModItems.ORE_ITEMS.forEach(o -> {
            if (o.getId().getPath().contains(metal)) {
                out.add(new ItemStack(o.get()));
            }
        });
        return out;
    }

    private static List<ItemStack> one(Item item, int count) {
        return List.of(new ItemStack(item, Math.max(1, count)));
    }

    private static Component seconds(int ticks) {
        return Component.translatable("jei.bsp_core.time", String.format("%.1f", ticks / 20.0));
    }

    private static String hours(double h) {
        return h >= 24 ? String.format("%.1f d", h / 24) : String.format("%.1f h", h);
    }

    private static int cfg(net.minecraftforge.common.ForgeConfigSpec.IntValue value, int fallback) {
        return BSPConfig.getOr(value, fallback);
    }

    /** Every machine's jobs, keyed by the machine name used for the recipe category ids. */
    public static Map<String, List<Process>> all() {
        Map<String, List<Process>> out = new LinkedHashMap<>();
        Component fuel = Component.translatable("jei.bsp_core.fuel"), fuelOrRf = Component.translatable("jei.bsp_core.fuel_or_rf");
        Component lavaNote = Component.translatable("jei.bsp_core.lava_note");

        out.put("tetrium_crucible", List.of(new Process(List.of(ores("tetrium")), List.of(), null, 0,
                List.of(new ItemStack(ModItems.TETRIUM_NUGGET.get(), cfg(BSPConfig.TCRUC_NUGGETS, 3)), new ItemStack(ModItems.TETRIUM_SLAG.get(), cfg(BSPConfig.TCRUC_SLAG, 1))),
                List.of(), List.of(seconds(cfg(BSPConfig.TCRUC_TICKS, 200)), fuel))));

        Component forgeTime = seconds(cfg(BSPConfig.FORGE_TICKS, 600));
        out.put("combination_forge", List.of(
                new Process(List.of(one(ModItems.TETRIUM_NUGGET.get(), 9)), List.of(), null, 0, List.of(new ItemStack(ModItems.TETRIUM_INGOT.get())), List.of(), List.of(forgeTime, fuelOrRf)),
                new Process(List.of(one(ModItems.TETRIUM_INGOT.get(), 1)), List.of(), null, 0, List.of(new ItemStack(ModItems.TETRIUM_PLATE.get())), List.of(), List.of(forgeTime, fuelOrRf)),
                new Process(List.of(one(ModItems.ILLYRIUM_NUGGET.get(), 9)), List.of(one(ModItems.ILLYRIUM_FORGE_UPGRADE.get(), 1)), null, 0,
                        List.of(new ItemStack(ModItems.ILLYRIUM_INGOT.get())), List.of(), List.of(forgeTime, fuelOrRf, Component.translatable("jei.bsp_core.forge_upgrade")))));

        int lava = cfg(BSPConfig.ICRUC_LAVA_PER_JOB, 250);
        List<List<ItemStack>> smeltIn = new ArrayList<>();
        smeltIn.add(ores("illyrium").stream().map(s -> s.copyWithCount(Math.max(1, cfg(BSPConfig.ICRUC_ORE_IN, 1)))).toList());
        if (cfg(BSPConfig.ICRUC_SLAG_IN, 1) > 0) {
            smeltIn.add(one(ModItems.TETRIUM_SLAG.get(), cfg(BSPConfig.ICRUC_SLAG_IN, 1)));
        }
        List<List<ItemStack>> alloyIn = new ArrayList<>();
        alloyIn.add(one(ModItems.PURE_ILLYRIUM_DUST.get(), cfg(BSPConfig.ICRUC_PURE_IN, 1)));
        if (cfg(BSPConfig.ICRUC_TDUST_IN, 1) > 0) {
            alloyIn.add(one(ModItems.TETRIUM_DUST.get(), cfg(BSPConfig.ICRUC_TDUST_IN, 1)));
        }
        out.put("illyrium_crucible", List.of(
                new Process(smeltIn, List.of(), Fluids.LAVA, lava, List.of(new ItemStack(ModItems.DIRTY_ILLYRIUM_INGOT.get())), List.of(),
                        List.of(seconds(cfg(BSPConfig.ICRUC_SMELT_TICKS, 400)), lavaNote)),
                new Process(alloyIn, List.of(), Fluids.LAVA, lava, List.of(new ItemStack(ModItems.ILLYRIUM_NUGGET.get(), cfg(BSPConfig.ICRUC_NUGGETS_OUT, 1))), List.of(),
                        List.of(seconds(cfg(BSPConfig.ICRUC_ALLOY_TICKS, 400)), lavaNote))));

        List<ItemStack> filters = new ArrayList<>();
        ModItems.FILTERS.values().forEach(f -> filters.add(new ItemStack(f.get())));
        out.put("illyrium_refinery", List.of(new Process(List.of(one(ModItems.DIRTY_ILLYRIUM_DUST.get(), 1)), List.of(filters), Fluids.WATER, cfg(BSPConfig.REFINERY_WATER, 500),
                List.of(new ItemStack(ModItems.PURE_ILLYRIUM_DUST.get())), List.of(),
                List.of(seconds(cfg(BSPConfig.REFINERY_TICKS, 2400)), Component.translatable("jei.bsp_core.filter_note")))));

        out.put("magnetic_centrifuge", List.of(
                new Process(List.of(List.of(stack("magnatite_ore"), stack("deepslate_magnatite_ore"))), List.of(), null, 0,
                        List.of(new ItemStack(ModItems.MAGNATITE_NUGGET.get(), 3), new ItemStack(ModItems.CARBON_DUST.get())), List.of(),
                        List.of(seconds(cfg(BSPConfig.CENT_SEPARATE_TICKS, 900)), Component.translatable("jei.bsp_core.centrifuge_stack_note"))),
                new Process(List.of(one(ModItems.MAGNATITE_INGOT.get(), 1)), List.of(List.of(new ItemStack(ModItems.COPPER_TETRIUM_COIL.get()))), null, 0,
                        List.of(new ItemStack(ModItems.CHARGED_MAGNATITE_INGOT.get())), List.of(),
                        List.of(seconds(cfg(BSPConfig.CENT_CHARGE_TICKS, 900)), Component.translatable("jei.bsp_core.centrifuge_charge_note"))),
                new Process(List.of(one(ModItems.RESONANCE_CRYSTAL.get(), 1)), List.of(List.of(new ItemStack(ModItems.MAGNATITE_NUGGET.get()))), null, 0,
                        List.of(new ItemStack(ModItems.CHARGED_RESONANCE_CRYSTAL.get())), List.of(),
                        List.of(seconds(cfg(BSPConfig.CENT_MAGNETISE_TICKS, 600)), Component.translatable("jei.bsp_core.centrifuge_magnetise_note")))));

        List<Process> coins = new ArrayList<>();
        for (CoinTier tier : CoinTier.values()) {
            coins.add(new Process(List.of(one(tier.blank(), 1)), List.of(), null, 0, List.of(new ItemStack(tier.coin())), List.of(),
                    List.of(Component.translatable("jei.bsp_core.press_time", hours(tier.pressMillis() / 3_600_000.0)),
                            Component.translatable("jei.bsp_core.press_rf", String.format("%,d", tier.energyPerCoin())),
                            Component.translatable("jei.bsp_core.press_note"))));
        }
        out.put("coin_pressing", coins);

        List<ItemStack> picks = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE));
        double tetChance = BSPConfig.getOr(BSPConfig.CRUSH_TETRIUM_CHANCE, 1 / 3.0), dirtyChance = BSPConfig.getOr(BSPConfig.CRUSH_DIRTY_CHANCE, 1 / 6.0);
        out.put("hand_crushing", List.of(
                crush(picks, ModItems.TETRIUM_INGOT.get(), ModItems.TETRIUM_DUST.get(), tetChance, ModItems.TETRIUM_NUGGET.get(), cfg(BSPConfig.CRUSH_TETRIUM_NUGGETS, 3)),
                crush(picks, ModItems.DIRTY_ILLYRIUM_INGOT.get(), ModItems.DIRTY_ILLYRIUM_DUST.get(), dirtyChance, ModItems.DIRTY_ILLYRIUM_NUGGET.get(), cfg(BSPConfig.CRUSH_DIRTY_NUGGETS, 3))));

        return out;
    }

    /** The information pages: lang key and the items it is shown on. */
    public static final List<Map.Entry<String, List<String>>> INFO = List.of(
            Map.entry("jei.bsp_core.info.illyrium_crucible", List.of("illyrium_crucible", "lava_pylon")),
            Map.entry("jei.bsp_core.info.illyrium_refinery", List.of("illyrium_refinery", "refinery_pump", "illyrium_glass")),
            Map.entry("jei.bsp_core.info.shared_parts", List.of("illyrium_casing", "illyrium_core", "item_hatch")),
            Map.entry("jei.bsp_core.info.factory", List.of("shatter_coin_factory", "factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port")),
            Map.entry("jei.bsp_core.info.motivator", List.of("factory_motivator")),
            Map.entry("jei.bsp_core.info.centrifuge", List.of("magnetic_centrifuge", "centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port")),
            Map.entry("jei.bsp_core.info.copper_coil", List.of("copper_tetrium_coil")),
            Map.entry("jei.bsp_core.info.magnatite_ore", List.of("magnatite_ore", "deepslate_magnatite_ore")),
            Map.entry("jei.bsp_core.info.vault", List.of("coin_vault")),
            Map.entry("jei.bsp_core.info.extractor", List.of("plasma_extractor")),
            Map.entry("jei.bsp_core.info.interface", List.of("plasma_interface")),
            Map.entry("jei.bsp_core.info.injector", List.of("plasma_injector")),
            Map.entry("jei.bsp_core.info.repeater", List.of("plasma_repeater")),
            Map.entry("jei.bsp_core.info.valve", List.of("plasma_valve")),
            Map.entry("jei.bsp_core.info.tank", List.of("tank_casing", "tank_glass", "tank_port")),
            Map.entry("jei.bsp_core.info.tetrium_glass", List.of("tetrium_glass")),
            Map.entry("jei.bsp_core.info.expander", List.of("channel_expander")),
            Map.entry("jei.bsp_core.info.charger", List.of("battery_charger")),
            Map.entry("jei.bsp_core.info.battery", List.of("plasma_battery_1", "plasma_battery_2", "plasma_battery_3", "plasma_battery_4")),
            Map.entry("jei.bsp_core.info.cell", List.of("power_cell_1", "power_cell_2", "power_cell_3")),
            Map.entry("jei.bsp_core.info.charged_crystal", List.of("charged_resonance_crystal")),
            Map.entry("jei.bsp_core.info.emitter", List.of("wave_emitter")),
            Map.entry("jei.bsp_core.info.wrench", List.of("wrench")),
            Map.entry("jei.bsp_core.info.projector", List.of("totem_projector")),
            Map.entry("jei.bsp_core.info.projector_base", List.of("projector_base")),
            Map.entry("jei.bsp_core.info.cable", List.of("tetrium_core_cable", "magnatite_core_cable", "illyrium_core_cable", "charged_illyrium_core_cable")),
            Map.entry("jei.bsp_core.info.decoy", List.of("decoy_totem", "magnet_core")),
            Map.entry("jei.bsp_core.info.decoy_base", List.of("decoy_power_base")),
            Map.entry("jei.bsp_core.info.score_screen", List.of("score_screen")),
            Map.entry("jei.bsp_core.info.admin_rack", List.of("admin_rack")),
            Map.entry("jei.bsp_core.info.anti_totem", List.of("anti_totem")),
            Map.entry("jei.bsp_core.info.totem", List.of("shatter_totem")),
            Map.entry("jei.bsp_core.info.plate", List.of("tetrium_plate")));


    private static Process crush(List<ItemStack> picks, Item ingot, Item dust, double chance, Item nugget, int nuggets) {
        int pct = (int) Math.round(chance * 100);
        return new Process(List.of(picks, one(ingot, 1)), List.of(), null, 0, List.of(new ItemStack(dust), new ItemStack(nugget, Math.max(1, nuggets))),
                List.of(Component.translatable("jei.bsp_core.chance", pct), Component.translatable("jei.bsp_core.chance", 100 - pct)),
                List.of(Component.translatable("jei.bsp_core.crush_1"), Component.translatable("jei.bsp_core.crush_2", pct)));
    }


}
