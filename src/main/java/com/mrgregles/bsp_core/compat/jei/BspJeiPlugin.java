package com.mrgregles.bsp_core.compat.jei;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI support. Shows what each BSP-Core machine does (their jobs are code, so the pages are built
 * here from the live config values), crushing by hand, coin pressing, and an information page for
 * every multiblock block. Only loaded when JEI is installed.
 */
@JeiPlugin
public class BspJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(BSPCore.MODID, "jei");
    public static final RecipeType<Process> TETRIUM_CRUCIBLE = RecipeType.create(BSPCore.MODID, "tetrium_crucible", Process.class);
    public static final RecipeType<Process> COMBINATION_FORGE = RecipeType.create(BSPCore.MODID, "combination_forge", Process.class);
    public static final RecipeType<Process> ILLYRIUM_CRUCIBLE = RecipeType.create(BSPCore.MODID, "illyrium_crucible", Process.class);
    public static final RecipeType<Process> MAGNETIC_CENTRIFUGE = RecipeType.create(BSPCore.MODID, "magnetic_centrifuge", Process.class);
    public static final RecipeType<Process> ILLYRIUM_REFINERY = RecipeType.create(BSPCore.MODID, "illyrium_refinery", Process.class);
    public static final RecipeType<Process> COIN_PRESSING = RecipeType.create(BSPCore.MODID, "coin_pressing", Process.class);
    public static final RecipeType<Process> HAND_CRUSHING = RecipeType.create(BSPCore.MODID, "hand_crushing", Process.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /** Once JEI is running, holding Shift over a multiblock block in JEI opens its Assembly Guide. */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        JeiGuideHover.attach(runtime);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        var gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(
                new ProcessCategory(gui, TETRIUM_CRUCIBLE, "block.bsp_core.tetrium_crucible", new ItemStack(ModItems.TETRIUM_CRUCIBLE.get())),
                new ProcessCategory(gui, COMBINATION_FORGE, "block.bsp_core.combination_forge", new ItemStack(ModItems.COMBINATION_FORGE.get())),
                new ProcessCategory(gui, ILLYRIUM_CRUCIBLE, "jei.bsp_core.illyrium_crucible", stack("illyrium_crucible")),
                new ProcessCategory(gui, ILLYRIUM_REFINERY, "jei.bsp_core.illyrium_refinery", stack("illyrium_refinery")),
                new ProcessCategory(gui, MAGNETIC_CENTRIFUGE, "jei.bsp_core.magnetic_centrifuge", stack("magnetic_centrifuge")),
                new ProcessCategory(gui, COIN_PRESSING, "jei.bsp_core.coin_pressing", new ItemStack(ModItems.COIN_FACTORY.get())),
                new ProcessCategory(gui, HAND_CRUSHING, "jei.bsp_core.hand_crushing", new ItemStack(Items.IRON_PICKAXE)));
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

    // ------------------------------------------------------------------ recipes

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        Component fuel = Component.translatable("jei.bsp_core.fuel"), fuelOrRf = Component.translatable("jei.bsp_core.fuel_or_rf");
        Component lavaNote = Component.translatable("jei.bsp_core.lava_note");

        reg.addRecipes(TETRIUM_CRUCIBLE, List.of(new Process(List.of(ores("tetrium")), List.of(), null, 0,
                List.of(new ItemStack(ModItems.TETRIUM_NUGGET.get(), cfg(BSPConfig.TCRUC_NUGGETS, 3)), new ItemStack(ModItems.TETRIUM_SLAG.get(), cfg(BSPConfig.TCRUC_SLAG, 1))),
                List.of(), List.of(seconds(cfg(BSPConfig.TCRUC_TICKS, 200)), fuel))));

        Component forgeTime = seconds(cfg(BSPConfig.FORGE_TICKS, 600));
        reg.addRecipes(COMBINATION_FORGE, List.of(
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
        reg.addRecipes(ILLYRIUM_CRUCIBLE, List.of(
                new Process(smeltIn, List.of(), Fluids.LAVA, lava, List.of(new ItemStack(ModItems.DIRTY_ILLYRIUM_INGOT.get())), List.of(),
                        List.of(seconds(cfg(BSPConfig.ICRUC_SMELT_TICKS, 400)), lavaNote)),
                new Process(alloyIn, List.of(), Fluids.LAVA, lava, List.of(new ItemStack(ModItems.ILLYRIUM_NUGGET.get(), cfg(BSPConfig.ICRUC_NUGGETS_OUT, 1))), List.of(),
                        List.of(seconds(cfg(BSPConfig.ICRUC_ALLOY_TICKS, 400)), lavaNote))));

        List<ItemStack> filters = new ArrayList<>();
        ModItems.FILTERS.values().forEach(f -> filters.add(new ItemStack(f.get())));
        reg.addRecipes(ILLYRIUM_REFINERY, List.of(new Process(List.of(one(ModItems.DIRTY_ILLYRIUM_DUST.get(), 1)), List.of(filters), Fluids.WATER, cfg(BSPConfig.REFINERY_WATER, 500),
                List.of(new ItemStack(ModItems.PURE_ILLYRIUM_DUST.get())), List.of(),
                List.of(seconds(cfg(BSPConfig.REFINERY_TICKS, 2400)), Component.translatable("jei.bsp_core.filter_note")))));

        reg.addRecipes(MAGNETIC_CENTRIFUGE, List.of(
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
        reg.addRecipes(COIN_PRESSING, coins);

        List<ItemStack> picks = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE));
        double tetChance = BSPConfig.getOr(BSPConfig.CRUSH_TETRIUM_CHANCE, 1 / 3.0), dirtyChance = BSPConfig.getOr(BSPConfig.CRUSH_DIRTY_CHANCE, 1 / 6.0);
        reg.addRecipes(HAND_CRUSHING, List.of(
                crush(picks, ModItems.TETRIUM_INGOT.get(), ModItems.TETRIUM_DUST.get(), tetChance, ModItems.TETRIUM_NUGGET.get(), cfg(BSPConfig.CRUSH_TETRIUM_NUGGETS, 3)),
                crush(picks, ModItems.DIRTY_ILLYRIUM_INGOT.get(), ModItems.DIRTY_ILLYRIUM_DUST.get(), dirtyChance, ModItems.DIRTY_ILLYRIUM_NUGGET.get(), cfg(BSPConfig.CRUSH_DIRTY_NUGGETS, 3))));

        // information pages: how each multiblock goes together, and the items with no recipe
        info(reg, "jei.bsp_core.info.illyrium_crucible", "illyrium_crucible", "lava_pylon");
        info(reg, "jei.bsp_core.info.illyrium_refinery", "illyrium_refinery", "refinery_pump", "illyrium_glass");
        info(reg, "jei.bsp_core.info.shared_parts", "illyrium_casing", "illyrium_core", "item_hatch");
        info(reg, "jei.bsp_core.info.factory", "shatter_coin_factory", "factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port");
        info(reg, "jei.bsp_core.info.motivator", "factory_motivator");
        info(reg, "jei.bsp_core.info.centrifuge", "magnetic_centrifuge", "centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port");
        info(reg, "jei.bsp_core.info.copper_coil", "copper_tetrium_coil");
        info(reg, "jei.bsp_core.info.magnatite_ore", "magnatite_ore", "deepslate_magnatite_ore");
        info(reg, "jei.bsp_core.info.vault", "coin_vault");
        info(reg, "jei.bsp_core.info.extractor", "plasma_extractor");
        info(reg, "jei.bsp_core.info.interface", "plasma_interface");
        info(reg, "jei.bsp_core.info.repeater", "plasma_repeater");
        info(reg, "jei.bsp_core.info.expander", "channel_expander");
        info(reg, "jei.bsp_core.info.charger", "battery_charger");
        info(reg, "jei.bsp_core.info.battery", "plasma_battery_1", "plasma_battery_2", "plasma_battery_3", "plasma_battery_4");
        info(reg, "jei.bsp_core.info.cell", "power_cell_1", "power_cell_2", "power_cell_3");
        info(reg, "jei.bsp_core.info.charged_crystal", "charged_resonance_crystal");
        info(reg, "jei.bsp_core.info.emitter", "wave_emitter");
        info(reg, "jei.bsp_core.info.wrench", "wrench");
        info(reg, "jei.bsp_core.info.projector", "totem_projector");
        info(reg, "jei.bsp_core.info.cable", "tetrium_core_cable", "magnatite_core_cable", "illyrium_core_cable", "charged_illyrium_core_cable");
        info(reg, "jei.bsp_core.info.decoy", "decoy_totem", "magnet_core");
        info(reg, "jei.bsp_core.info.decoy_base", "decoy_power_base");
        info(reg, "jei.bsp_core.info.score_screen", "score_screen");
        info(reg, "jei.bsp_core.info.admin_rack", "admin_rack");
        info(reg, "jei.bsp_core.info.anti_totem", "anti_totem");
        info(reg, "jei.bsp_core.info.totem", "shatter_totem");
        info(reg, "jei.bsp_core.info.plate", "tetrium_plate");
    }

    private static Process crush(List<ItemStack> picks, Item ingot, Item dust, double chance, Item nugget, int nuggets) {
        int pct = (int) Math.round(chance * 100);
        return new Process(List.of(picks, one(ingot, 1)), List.of(), null, 0, List.of(new ItemStack(dust), new ItemStack(nugget, Math.max(1, nuggets))),
                List.of(Component.translatable("jei.bsp_core.chance", pct), Component.translatable("jei.bsp_core.chance", 100 - pct)),
                List.of(Component.translatable("jei.bsp_core.crush_1"), Component.translatable("jei.bsp_core.crush_2", pct)));
    }

    private static void info(IRecipeRegistration reg, String key, String... items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String name : items) {
            ItemStack s = stack(name);
            if (!s.isEmpty()) {
                stacks.add(s);
            }
        }
        if (!stacks.isEmpty()) {
            reg.addIngredientInfo(stacks, VanillaTypes.ITEM_STACK, Component.translatable(key));
        }
    }

    /** Clicking the progress dial on a machine screen shows that machine's jobs. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration reg) {
        reg.addGuiContainerHandler(com.mrgregles.bsp_core.client.MachineScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
            @Override
            public java.util.Collection<mezz.jei.api.gui.handlers.IGuiClickableArea> getGuiClickableAreas(com.mrgregles.bsp_core.client.MachineScreen screen, double mouseX, double mouseY) {
                var machine = screen.getMenu().getMachine();
                RecipeType<Process> type = machine instanceof com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity ? TETRIUM_CRUCIBLE
                        : machine instanceof com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity ? COMBINATION_FORGE
                        : machine instanceof com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity ? ILLYRIUM_CRUCIBLE
                        : machine instanceof com.mrgregles.bsp_core.machine.IllyriumRefineryBlockEntity ? ILLYRIUM_REFINERY
                        : machine instanceof com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity ? MAGNETIC_CENTRIFUGE : null;
                return type == null ? List.of() : List.of(mezz.jei.api.gui.handlers.IGuiClickableArea.createBasic(90, 24, 31, 31, type));
            }
        });
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(ModItems.TETRIUM_CRUCIBLE.get()), TETRIUM_CRUCIBLE);
        reg.addRecipeCatalyst(new ItemStack(ModItems.COMBINATION_FORGE.get()), COMBINATION_FORGE);
        reg.addRecipeCatalyst(stack("illyrium_crucible"), ILLYRIUM_CRUCIBLE);
        reg.addRecipeCatalyst(stack("illyrium_refinery"), ILLYRIUM_REFINERY);
        reg.addRecipeCatalyst(stack("magnetic_centrifuge"), MAGNETIC_CENTRIFUGE);
        reg.addRecipeCatalyst(new ItemStack(ModItems.COIN_FACTORY.get()), COIN_PRESSING);
        reg.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE), HAND_CRUSHING);
    }
}
