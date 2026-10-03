package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryMenu;
import com.mrgregles.bsp_core.menu.ShatterTotemMenu;
import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BSPCore.MODID);

    public static final RegistryObject<MenuType<ShatterTotemMenu>> SHATTER_TOTEM = MENUS.register("shatter_totem",
            () -> IForgeMenuType.create((id, inv, buf) -> new ShatterTotemMenu(id, inv, buf.readBlockPos())));

    public static final RegistryObject<MenuType<TotemUpgradeMenu>> TOTEM_UPGRADES = MENUS.register("totem_upgrades",
            () -> IForgeMenuType.create((id, inv, buf) -> new TotemUpgradeMenu(id, inv, buf.readEnum(InteractionHand.class))));

    public static final RegistryObject<MenuType<CoinFactoryMenu>> COIN_FACTORY = MENUS.register("shatter_coin_factory",
            () -> IForgeMenuType.create((id, inv, buf) -> new CoinFactoryMenu(id, inv, buf.readBlockPos())));

    private ModMenus() {}
}
