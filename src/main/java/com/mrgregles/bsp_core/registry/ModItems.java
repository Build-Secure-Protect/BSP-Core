package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinBlankItem;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockItem;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import com.mrgregles.bsp_core.coin.SpeedGearItem;
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

    /** Crafting material for Etherium coin blanks. How it is obtained is defined later. */
    public static final RegistryObject<Item> ETHERIUM_SHARD =
            ITEMS.register("etherium_shard", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<CoinFactoryBlockItem> COIN_FACTORY =
            ITEMS.register("shatter_coin_factory", () -> new CoinFactoryBlockItem(ModBlocks.COIN_FACTORY.get()));

    private ModItems() {}
}
