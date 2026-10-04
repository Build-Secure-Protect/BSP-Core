package com.mrgregles.bsp_core;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModCreativeTabs;
import com.mrgregles.bsp_core.registry.ModEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.network.BSPNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for BSP Core, the companion mod for the Build Secure Protect modpack.
 *
 * <p>Registries, event handlers and features are added in their own packages and wired up here.
 */
@Mod(BSPCore.MODID)
public class BSPCore {
    /** Mod id. Must match {@code mod_id} in gradle.properties and {@code modId} in mods.toml. */
    public static final String MODID = "bsp_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BSPCore(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        // Registration order matters: items and block entities reference blocks.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        com.mrgregles.bsp_core.registry.ModRecipes.SERIALIZERS.register(modEventBus);
        com.mrgregles.bsp_core.loot.AddItemLootModifier.SERIALIZERS.register(modEventBus);

        // Server config: lives in <world>/serverconfig/bsp_core-server.toml and syncs to clients.
        context.registerConfig(ModConfig.Type.SERVER, BSPConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(BSPNetwork::register);
        LOGGER.info("BSP Core loaded");
    }
}
