package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinBlankItem;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockItem;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import com.mrgregles.bsp_core.material.FilterItem;
import com.mrgregles.bsp_core.totem.ShatterTotemItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BSPCore.MODID);

    public static final RegistryObject<ShatterTotemItem> SHATTER_TOTEM =
            ITEMS.register("shatter_totem", () -> new ShatterTotemItem(ModBlocks.SHATTER_TOTEM.get()));

    /** bsp_core:<tier>_shatter_coin */
    public static final Map<CoinTier, RegistryObject<ShatterCoinItem>> COINS = new EnumMap<>(CoinTier.class);
    /** bsp_core:<tier>_coin_blank */
    public static final Map<CoinTier, RegistryObject<CoinBlankItem>> BLANKS = new EnumMap<>(CoinTier.class);

    static {
        for (CoinTier tier : CoinTier.values()) {
            COINS.put(tier, ITEMS.register(tier.key + "_shatter_coin", () -> new ShatterCoinItem(tier)));
            BLANKS.put(tier, ITEMS.register(tier.key + "_coin_blank", () -> new CoinBlankItem(tier)));
        }
    }

    // --- Tetrium and Illyrium chain
    public static final RegistryObject<Item> TETRIUM_SLAG = simple("tetrium_slag");
    public static final RegistryObject<Item> TETRIUM_NUGGET = simple("tetrium_nugget");
    public static final RegistryObject<Item> TETRIUM_INGOT = simple("tetrium_ingot");
    public static final RegistryObject<Item> TETRIUM_DUST = simple("tetrium_dust");
    public static final RegistryObject<Item> DIRTY_ILLYRIUM_INGOT = simple("dirty_illyrium_ingot");
    public static final RegistryObject<Item> DIRTY_ILLYRIUM_DUST = simple("dirty_illyrium_dust");
    /** What a failed hand-crush of a Dirty Illyrium Ingot leaves; nine recombine into the ingot. */
    public static final RegistryObject<Item> DIRTY_ILLYRIUM_NUGGET = simple("dirty_illyrium_nugget");
    public static final RegistryObject<Item> PURE_ILLYRIUM_DUST = simple("pure_illyrium_dust");
    public static final RegistryObject<Item> ILLYRIUM_NUGGET = simple("illyrium_nugget");
    public static final RegistryObject<Item> ILLYRIUM_INGOT = simple("illyrium_ingot");
    // --- Magnatite chain: ore, Magnetic Centrifuge, nuggets (and Carbon Dust), ingot, charged ingot
    public static final RegistryObject<Item> MAGNATITE_NUGGET = simple("magnatite_nugget");
    public static final RegistryObject<Item> MAGNATITE_INGOT = simple("magnatite_ingot");
    /** A Magnatite Ingot magnetised in a Magnetic Centrifuge fitted with a Copper Tetrium Coil. */
    public static final RegistryObject<Item> CHARGED_MAGNATITE_INGOT = ITEMS.register("charged_magnatite_ingot", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)) {
        @Override
        public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
            return true;
        }
    });
    /** What the centrifuge spins out of the ore besides metal. Goes into rotors and coil insulation. */
    public static final RegistryObject<Item> CARBON_DUST = simple("carbon_dust");
    /** Fitted to a Magnetic Centrifuge, lets it charge Magnatite Ingots. */
    public static final RegistryObject<Item> COPPER_TETRIUM_COIL = ITEMS.register("copper_tetrium_coil", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ILLYRIUM_FORGE_UPGRADE = ITEMS.register("illyrium_forge_upgrade", () -> new Item(new Item.Properties().stacksTo(1)));
    /** Results of crushing by pickaxe; they resolve into dust or nuggets on reaching an inventory. */
    public static final RegistryObject<Item> CRUSHED_TETRIUM = ITEMS.register("crushed_tetrium",
            () -> new com.mrgregles.bsp_core.material.CrushedIngotItem(true, TETRIUM_DUST, TETRIUM_NUGGET));
    public static final RegistryObject<Item> CRUSHED_DIRTY_ILLYRIUM = ITEMS.register("crushed_dirty_illyrium",
            () -> new com.mrgregles.bsp_core.material.CrushedIngotItem(false, DIRTY_ILLYRIUM_DUST, DIRTY_ILLYRIUM_NUGGET));
    /** The base, tier-less blank made from Tetrium. Tier blanks are built on top of it. */
    public static final RegistryObject<Item> SHATTER_BLANK = simple("shatter_blank");

    /** bsp_core:<tier>_filter */
    public static final Map<FilterItem.Tier, RegistryObject<FilterItem>> FILTERS = new EnumMap<>(FilterItem.Tier.class);
    /** Block items for the ores, in creative-tab order. */
    public static final List<RegistryObject<Item>> ORE_ITEMS = new ArrayList<>();

    static {
        for (FilterItem.Tier tier : FilterItem.Tier.values()) {
            FILTERS.put(tier, ITEMS.register(tier.name().toLowerCase() + "_filter", () -> new FilterItem(tier)));
        }
        for (RegistryObject<net.minecraft.world.level.block.Block> ore : List.of(ModBlocks.TETRIUM_ORE, ModBlocks.DEEPSLATE_TETRIUM_ORE,
                ModBlocks.ILLYRIUM_ORE, ModBlocks.DEEPSLATE_ILLYRIUM_ORE, ModBlocks.END_STONE_ILLYRIUM_ORE,
                ModBlocks.MAGNATITE_ORE, ModBlocks.DEEPSLATE_MAGNATITE_ORE)) {
            ORE_ITEMS.add(ITEMS.register(ore.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(ore.get(), new Item.Properties())));
        }
        // what the ores drop when mined without Silk Touch; the machines take them like the ore blocks
        for (String raw : List.of("raw_tetrium", "raw_illyrium", "raw_magnatite")) {
            ORE_ITEMS.add(ITEMS.register(raw, () -> new Item(new Item.Properties())));
        }
    }

    public static final RegistryObject<Item> TETRIUM_CRUCIBLE = ITEMS.register("tetrium_crucible",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.TETRIUM_CRUCIBLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> COMBINATION_FORGE = ITEMS.register("combination_forge",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.COMBINATION_FORGE.get(), new Item.Properties()));

    /** bsp_core:rf_upgrade_mk1..3 (Flux Coil, Power Cell, Induction Core), index 0 = Mk I */
    public static final List<RegistryObject<com.mrgregles.bsp_core.material.RfUpgradeItem>> RF_UPGRADES = new ArrayList<>();

    static {
        for (int mark = 1; mark <= 3; mark++) {
            final int m = mark;
            RF_UPGRADES.add(ITEMS.register("rf_upgrade_mk" + m, () -> new com.mrgregles.bsp_core.material.RfUpgradeItem(m)));
        }
    }

    public static final RegistryObject<com.mrgregles.bsp_core.compass.TotemCompassItem> TOTEM_COMPASS =
            ITEMS.register("totem_compass", com.mrgregles.bsp_core.compass.TotemCompassItem::new);

    /** Controllers and structural parts of the two Illyrium multiblocks. */
    public static final List<RegistryObject<Item>> MULTIBLOCK_ITEMS = new ArrayList<>();

    static {
        for (var block : List.of(ModBlocks.ILLYRIUM_CRUCIBLE, ModBlocks.ILLYRIUM_REFINERY, ModBlocks.ILLYRIUM_CASING, ModBlocks.ILLYRIUM_GLASS,
                ModBlocks.ILLYRIUM_CORE, ModBlocks.LAVA_PYLON, ModBlocks.REFINERY_PUMP, ModBlocks.ITEM_HATCH,
                ModBlocks.MAGNETIC_CENTRIFUGE, ModBlocks.CENTRIFUGE_CASING, ModBlocks.CENTRIFUGE_ROTOR, ModBlocks.CENTRIFUGE_POWER_PORT)) {
            MULTIBLOCK_ITEMS.add(ITEMS.register(block.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties())));
        }
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    /** Machine components: crafted parts that the machine blocks are built from. Order = creative tab order. */
    public static final Map<String, RegistryObject<Item>> COMPONENTS = new java.util.LinkedHashMap<>();

    static {
        for (String name : List.of("slag_brick", "tetrium_plate", "machine_chassis", "tetrium_coil", "drive_motor", "circuit_substrate", "basic_control_circuit",
                "crucible_control_circuit", "refinery_control_circuit", "mint_control_circuit", "thermal_lining", "conveyor_belt", "press_die",
                "resonance_crystal", "illyrium_processor")) {
            COMPONENTS.put(name, simple(name));
        }
    }

    /** Pressed from a Tetrium Ingot in the Combination Forge. */
    public static final RegistryObject<Item> TETRIUM_PLATE = COMPONENTS.get("tetrium_plate");
    public static final RegistryObject<Item> RESONANCE_CRYSTAL = COMPONENTS.get("resonance_crystal");

    public static final RegistryObject<Item> ADMIN_RACK = ITEMS.register("admin_rack",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.ADMIN_RACK.get(), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> ANTI_TOTEM = ITEMS.register("anti_totem",
            () -> new com.mrgregles.bsp_core.zone.AntiTotemBlockItem(ModBlocks.ANTI_TOTEM.get()));

    public static final RegistryObject<Item> DECOY_TOTEM = ITEMS.register("decoy_totem", () -> new com.mrgregles.bsp_core.decoy.DecoyTotemBlockItem(ModBlocks.DECOY_TOTEM.get()));
    public static final RegistryObject<Item> DECOY_POWER_BASE = ITEMS.register("decoy_power_base",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.DECOY_POWER_BASE.get(), new Item.Properties()));
    /** The hard-to-make heart of a Decoy Totem: Charged Magnatite around an Illyrium Processor. */
    public static final RegistryObject<Item> MAGNET_CORE = ITEMS.register("magnet_core", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    /** The parts that go into a Decoy Totem's sockets, in tree order. */
    public static final Map<com.mrgregles.bsp_core.decoy.DecoyUpgradeItem.Kind, RegistryObject<Item>> DECOY_UPGRADES = new EnumMap<>(com.mrgregles.bsp_core.decoy.DecoyUpgradeItem.Kind.class);

    static {
        for (var kind : com.mrgregles.bsp_core.decoy.DecoyUpgradeItem.Kind.values()) {
            DECOY_UPGRADES.put(kind, ITEMS.register(kind.id, () -> new com.mrgregles.bsp_core.decoy.DecoyUpgradeItem(kind)));
        }
    }

    /** The plasma chain: extractor, interface, repeater, projector, the four cables and the Channel Expander, in creative-tab order. */
    public static final List<RegistryObject<Item>> PROJECTOR_ITEMS = new ArrayList<>();
    public static final RegistryObject<Item> CHANNEL_EXPANDER;

    static {
        List<RegistryObject<net.minecraft.world.level.block.Block>> blocks = new ArrayList<>(List.of(ModBlocks.PLASMA_EXTRACTOR, ModBlocks.PLASMA_INTERFACE, ModBlocks.PLASMA_REPEATER, ModBlocks.PLASMA_VALVE, ModBlocks.PROJECTOR_BASE, ModBlocks.TOTEM_PROJECTOR));
        blocks.addAll(ModBlocks.TOTEM_CABLES.values());
        for (var block : blocks) {
            PROJECTOR_ITEMS.add(ITEMS.register(block.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties())));
        }
        CHANNEL_EXPANDER = ITEMS.register("channel_expander", com.mrgregles.bsp_core.plasma.ChannelExpanderItem::new);
        PROJECTOR_ITEMS.add(CHANNEL_EXPANDER);
        PROJECTOR_ITEMS.add(ITEMS.register("battery_charger", () -> new net.minecraft.world.item.BlockItem(ModBlocks.BATTERY_CHARGER.get(), new Item.Properties())));
        for (var battery : ModBlocks.PLASMA_BATTERIES) {
            PROJECTOR_ITEMS.add(ITEMS.register(battery.getId().getPath(), () -> new com.mrgregles.bsp_core.plasma.PlasmaBatteryBlock.Item(battery.get())));
        }
        for (int tier = 0; tier < 3; tier++) {
            int t = tier;
            PROJECTOR_ITEMS.add(ITEMS.register("power_cell_" + (tier + 1), () -> new com.mrgregles.bsp_core.plasma.PowerCellItem(t)));
        }
        PROJECTOR_ITEMS.add(ITEMS.register("wave_emitter", com.mrgregles.bsp_core.plasma.WaveEmitterItem::new));
        PROJECTOR_ITEMS.add(ITEMS.register("wrench", com.mrgregles.bsp_core.plasma.WrenchItem::new));
    }

    public static final RegistryObject<Item> CHARGED_RESONANCE_CRYSTAL = ITEMS.register("charged_resonance_crystal", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> COIN_VAULT = ITEMS.register("coin_vault",
            () -> new com.mrgregles.bsp_core.vault.CoinVaultBlockItem(ModBlocks.COIN_VAULT.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCORE_SCREEN = ITEMS.register("score_screen",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.SCORE_SCREEN.get(), new Item.Properties()));

    /** The blocks a factory slice is built from, and the Motivator that goes on top. */
    public static final List<RegistryObject<Item>> FACTORY_ITEMS = new ArrayList<>();

    static {
        for (var block : List.of(ModBlocks.FACTORY_FRAME, ModBlocks.FACTORY_PRESS, ModBlocks.FACTORY_BLANK_HATCH, ModBlocks.FACTORY_POWER_PORT, ModBlocks.FACTORY_MOTIVATOR)) {
            FACTORY_ITEMS.add(ITEMS.register(block.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<CoinFactoryBlockItem> COIN_FACTORY =
            ITEMS.register("shatter_coin_factory", () -> new CoinFactoryBlockItem(ModBlocks.COIN_FACTORY.get()));

    private ModItems() {}
}
