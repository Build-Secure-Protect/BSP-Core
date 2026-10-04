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
                output.accept(ModItems.TOTEM_COMPASS.get());
                output.accept(ModItems.COIN_FACTORY.get());
                ModItems.BLANKS.values().forEach(i -> output.accept(i.get()));
                ModItems.COINS.values().forEach(i -> output.accept(i.get()));
                ModItems.FACTORY_ITEMS.forEach(i -> output.accept(i.get()));
                output.accept(ModItems.COIN_VAULT.get());
                output.accept(ModItems.SCORE_SCREEN.get());
                output.accept(ModItems.ADMIN_RACK.get());
                output.accept(ModItems.TETRIUM_CRUCIBLE.get());
                output.accept(ModItems.COMBINATION_FORGE.get());
                ModItems.MULTIBLOCK_ITEMS.forEach(i -> output.accept(i.get()));
                ModItems.RF_UPGRADES.forEach(i -> output.accept(i.get()));
                ModItems.ORE_ITEMS.forEach(i -> output.accept(i.get()));
                for (var item : java.util.List.of(ModItems.TETRIUM_SLAG, ModItems.TETRIUM_NUGGET, ModItems.TETRIUM_INGOT, ModItems.TETRIUM_DUST,
                        ModItems.DIRTY_ILLYRIUM_INGOT, ModItems.DIRTY_ILLYRIUM_NUGGET, ModItems.DIRTY_ILLYRIUM_DUST, ModItems.PURE_ILLYRIUM_DUST, ModItems.ILLYRIUM_NUGGET,
                        ModItems.ILLYRIUM_INGOT, ModItems.SHATTER_BLANK, ModItems.ILLYRIUM_FORGE_UPGRADE)) {
                    output.accept(item.get());
                }
                ModItems.FILTERS.values().forEach(i -> output.accept(i.get()));
                ModItems.COMPONENTS.values().forEach(i -> output.accept(i.get()));
            })
            .build());

    private ModCreativeTabs() {}
}
