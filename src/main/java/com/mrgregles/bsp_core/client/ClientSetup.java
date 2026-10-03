package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModEntities;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
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
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHATTER_TOTEM_ITEM.get(), ItemEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHATTER_TOTEM.get(), ShatterTotemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COIN_FACTORY.get(), CoinFactoryRenderer::new);
    }

    /** The press head is drawn by the renderer, so its model must be baked even though no block state uses it. */
    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(CoinFactoryRenderer.HEAD_MODEL);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("steal_timer", StealHudOverlay.INSTANCE);
    }
}
