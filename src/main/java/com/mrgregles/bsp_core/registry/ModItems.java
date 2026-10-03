package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterCoinItem;
import com.mrgregles.bsp_core.totem.ShatterTotemItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BSPCore.MODID);

    public static final RegistryObject<ShatterTotemItem> SHATTER_TOTEM =
            ITEMS.register("shatter_totem", () -> new ShatterTotemItem(ModBlocks.SHATTER_TOTEM.get()));

    public static final RegistryObject<ShatterCoinItem> SHATTER_COIN =
            ITEMS.register("shatter_coin", ShatterCoinItem::new);

    private ModItems() {}
}
