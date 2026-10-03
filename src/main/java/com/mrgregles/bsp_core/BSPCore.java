package com.mrgregles.bsp_core;

import com.mojang.logging.LogUtils;
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

        // Server config: lives in <world>/serverconfig/bsp_core-server.toml and syncs to clients.
        context.registerConfig(ModConfig.Type.SERVER, BSPConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("BSP Core loaded");
    }
}
