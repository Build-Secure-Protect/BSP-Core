package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryMenu;
import com.mrgregles.bsp_core.machine.MachineMenu;
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
            () -> IForgeMenuType.create((id, inv, buf) -> new CoinFactoryMenu(id, inv, buf.readBlockPos(), buf.readVarInt())));

    public static final RegistryObject<MenuType<MachineMenu>> MACHINE = MENUS.register("machine",
            () -> IForgeMenuType.create((id, inv, buf) -> new MachineMenu(id, inv, buf.readBlockPos())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.compass.TotemCompassMenu>> TOTEM_COMPASS = MENUS.register("totem_compass",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.compass.TotemCompassMenu(id, inv, buf.readEnum(InteractionHand.class))));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.vault.CoinVaultMenu>> COIN_VAULT = MENUS.register("coin_vault",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.vault.CoinVaultMenu(id, inv, buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.admin.SeasonRewardsMenu>> SEASON_REWARDS = MENUS.register("season_rewards",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.admin.SeasonRewardsMenu(id, inv, buf.readBoolean())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.decoy.DecoyMenu>> DECOY = MENUS.register("decoy_totem",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.decoy.DecoyMenu(id, inv, buf.readBlockPos())));


    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.plasma.WaveEmitterMenu>> WAVE_EMITTER = MENUS.register("wave_emitter",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.plasma.WaveEmitterMenu(id, inv, buf.readEnum(InteractionHand.class))));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.plasma.BatteryChargerMenu>> BATTERY_CHARGER = MENUS.register("battery_charger",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.plasma.BatteryChargerMenu(id, inv, buf.readBlockPos())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.bank.BankMenu>> BANK = MENUS.register("bank",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.bank.BankMenu(id, inv, buf.readBoolean() ? buf.readBlockPos() : null)));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.bank.DepositBoxMenu>> DEPOSIT_BOX = MENUS.register("deposit_box",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.bank.DepositBoxMenu(id, inv, buf.readBlockPos(), buf.readBoolean(), buf.readVarInt())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.ShopMenu>> SHOP = MENUS.register("shop",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.shop.ShopMenu(id, inv, buf.readUtf(32), buf.readBoolean() ? buf.readBlockPos() : null, buf.readBoolean(),
                    com.mrgregles.bsp_core.shop.OpenHours.load(java.util.Objects.requireNonNullElse(buf.readNbt(), new net.minecraft.nbt.CompoundTag())), buf.readBoolean())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.ShopConfigMenu>> SHOP_CONFIG = MENUS.register("shop_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.shop.ShopConfigMenu(id, inv, buf.readBoolean())));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.SellHereMenu>> SELL_HERE = MENUS.register("sell_here",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.shop.SellHereMenu(id, inv, buf.readUtf(32), buf.readBoolean() ? buf.readBlockPos() : null, buf.readBoolean(),
                    com.mrgregles.bsp_core.shop.OpenHours.load(java.util.Objects.requireNonNullElse(buf.readNbt(), new net.minecraft.nbt.CompoundTag())))));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.VendorMenu>> VENDOR = MENUS.register("vendor",
            () -> IForgeMenuType.create((id, inv, buf) -> {
                var pos = buf.readBlockPos();
                boolean editable = buf.readBoolean();
                String shop = buf.readUtf(32);
                var hours = com.mrgregles.bsp_core.shop.OpenHours.load(java.util.Objects.requireNonNullElse(buf.readNbt(), new net.minecraft.nbt.CompoundTag()));
                String mob = buf.readUtf(64), nbt = buf.readUtf(255);
                int skin = buf.readInt();
                int n = buf.readVarInt();
                java.util.List<String[]> shops = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) {
                    shops.add(new String[]{buf.readUtf(32), buf.readUtf(64)});
                }
                return new com.mrgregles.bsp_core.shop.VendorMenu(id, inv, pos, editable, shop, hours, mob, nbt, skin, shops);
            }));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.land.LandOfficeMenu>> LAND_OFFICE = MENUS.register("land_office",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.mrgregles.bsp_core.land.LandOfficeMenu(id, inv)));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.StallDisplayMenu>> STALL_DISPLAY = MENUS.register("stall_display",
            () -> IForgeMenuType.create(com.mrgregles.bsp_core.shop.StallDisplayMenu::read));

    public static final RegistryObject<MenuType<com.mrgregles.bsp_core.shop.ShopListMenu>> SHOP_LIST = MENUS.register("shop_list",
            () -> IForgeMenuType.create((id, inv, buf) -> {
                int n = buf.readVarInt();
                java.util.List<com.mrgregles.bsp_core.shop.ShopListMenu.Entry> list = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) {
                    list.add(new com.mrgregles.bsp_core.shop.ShopListMenu.Entry(buf.readUtf(32), buf.readUtf(64), buf.readEnum(com.mrgregles.bsp_core.shop.Shop.Kind.class), buf.readVarInt(), buf.readBoolean()));
                }
                return new com.mrgregles.bsp_core.shop.ShopListMenu(id, inv, list);
            }));

    private ModMenus() {}
}
