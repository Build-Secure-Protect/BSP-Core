package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BSPCore.MODID);

    public static final RegistryObject<CreativeModeTab> BSP = TABS.register("bsp", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + BSPCore.MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(ModItems.SHATTER_TOTEM.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.SHATTER_TOTEM.get());
                output.accept(ModItems.SHATTER_COIN.get());
            })
            .build());

    private ModCreativeTabs() {}
}
