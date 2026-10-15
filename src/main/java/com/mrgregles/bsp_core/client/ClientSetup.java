package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModEntities;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registrations. Never referenced from common code. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.SHATTER_TOTEM.get(), ShatterTotemScreen::new);
            MenuScreens.register(ModMenus.TOTEM_UPGRADES.get(), TotemUpgradeScreen::new);
            MenuScreens.register(ModMenus.COIN_FACTORY.get(), CoinFactoryScreen::new);
            MenuScreens.register(ModMenus.MACHINE.get(), MachineScreen::new);
            MenuScreens.register(ModMenus.TOTEM_COMPASS.get(), TotemCompassScreen::new);
            MenuScreens.register(ModMenus.COIN_VAULT.get(), CoinVaultScreen::new);
            MenuScreens.register(ModMenus.DECOY.get(), DecoyScreen::new);
            MenuScreens.register(ModMenus.SEASON_REWARDS.get(), SeasonRewardsScreen::new);
            MenuScreens.register(ModMenus.BATTERY_CHARGER.get(), BatteryChargerScreen::new);
            MenuScreens.register(ModMenus.WAVE_EMITTER.get(), WaveEmitterScreen::new);
            MenuScreens.register(ModMenus.BANK.get(), BankScreen::new);
            MenuScreens.register(ModMenus.DEPOSIT_BOX.get(), DepositBoxScreen::new);
            MenuScreens.register(ModMenus.SHOP.get(), ShopScreen::new);
            MenuScreens.register(ModMenus.SELL_HERE.get(), SellHereScreen::new);
            MenuScreens.register(ModMenus.SHOP_CONFIG.get(), ShopConfigScreen::new);
            MenuScreens.register(ModMenus.VENDOR.get(), VendorScreen::new);
            MenuScreens.register(ModMenus.STALL_DISPLAY.get(), StallDisplayScreen::new);
            MenuScreens.register(ModMenus.LAND_OFFICE.get(), LandOfficeScreen::new);
            MenuScreens.register(ModMenus.SHOP_LIST.get(), ShopListScreen::new);
            // needle angle, exactly as the vanilla compass does it, aimed at the position the server wrote into the stack
            net.minecraft.client.renderer.item.ItemProperties.register(com.mrgregles.bsp_core.registry.ModItems.TOTEM_COMPASS.get(),
                    new net.minecraft.resources.ResourceLocation("angle"),
                    new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) -> com.mrgregles.bsp_core.compass.TotemCompassItem.target(stack)));
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHATTER_TOTEM_ITEM.get(), ShatterTotemItemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHATTER_TOTEM.get(), ShatterTotemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COIN_FACTORY.get(), CoinFactoryRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SCORE_SCREEN.get(), ScoreScreenRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COIN_VAULT.get(), CoinVaultRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECOY_TOTEM.get(), DecoyTotemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TOTEM_PROJECTOR.get(), TotemProjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_CABLE.get(), PlasmaCableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_REPEATER.get(), PlasmaRepeaterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_VALVE.get(), PlasmaValveRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TANK_PART.get(), TankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TANK_PORT.get(), TankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY_CHARGER.get(), BatteryChargerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_INJECTOR.get(), PlasmaInjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_INTERFACE.get(), PlasmaInterfaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PROJECTOR_BASE.get(), ProjectorBaseRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ANTI_TOTEM.get(), AntiTotemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BANK_ATM.get(), BankAtmRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SAFETY_DEPOSIT_BOX.get(), SafetyDepositBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VENDOR_POST.get(), VendorPostRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STALL_DISPLAY.get(), StallDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MARKET_STALL.get(), MarketStallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAND_OFFICE.get(), LandOfficeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TETRIUM_CRUCIBLE.get(), TetriumCrucibleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COMBINATION_FORGE.get(), CombinationForgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ILLYRIUM_CRUCIBLE.get(), IllyriumCrucibleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ILLYRIUM_REFINERY.get(), IllyriumRefineryRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGNETIC_CENTRIFUGE.get(), MagneticCentrifugeRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("steal_timer", StealHudOverlay.INSTANCE);
        event.registerAboveAll("recall_offer", RecallHud.INSTANCE);
        event.registerAboveAll("xray_ring", XrayClient.INSTANCE);
        event.registerAboveAll("siege_ring", SiegeClient.INSTANCE);
        event.registerAboveAll("sentinel_strip", SentinelHud.INSTANCE);
        event.registerAboveAll("wrench_ends", WrenchHud.INSTANCE);
    }
}
