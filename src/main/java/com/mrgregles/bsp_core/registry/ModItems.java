package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinBlankItem;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockItem;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import com.mrgregles.bsp_core.coin.SpeedGearItem;
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
    /** bsp_core:speed_gear_mk1..3, index 0 = Mk I */
    public static final List<RegistryObject<SpeedGearItem>> SPEED_GEARS = new ArrayList<>();

    static {
        for (CoinTier tier : CoinTier.values()) {
            COINS.put(tier, ITEMS.register(tier.key + "_shatter_coin", () -> new ShatterCoinItem(tier)));
            BLANKS.put(tier, ITEMS.register(tier.key + "_coin_blank", () -> new CoinBlankItem(tier)));
        }
        for (int mark = 1; mark <= 3; mark++) {
            final int m = mark;
            SPEED_GEARS.add(ITEMS.register("speed_gear_mk" + m, () -> new SpeedGearItem(m)));
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
                ModBlocks.ILLYRIUM_ORE, ModBlocks.DEEPSLATE_ILLYRIUM_ORE, ModBlocks.END_STONE_ILLYRIUM_ORE)) {
            ORE_ITEMS.add(ITEMS.register(ore.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(ore.get(), new Item.Properties())));
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
                ModBlocks.ILLYRIUM_CORE, ModBlocks.LAVA_PYLON, ModBlocks.REFINERY_PUMP, ModBlocks.ITEM_HATCH)) {
            MULTIBLOCK_ITEMS.add(ITEMS.register(block.getId().getPath(), () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties())));
        }
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    public static final RegistryObject<CoinFactoryBlockItem> COIN_FACTORY =
            ITEMS.register("shatter_coin_factory", () -> new CoinFactoryBlockItem(ModBlocks.COIN_FACTORY.get()));

    private ModItems() {}
}
