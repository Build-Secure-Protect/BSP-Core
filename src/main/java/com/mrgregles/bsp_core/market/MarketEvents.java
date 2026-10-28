package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The market's clock: every 30 seconds, lots past their end go to their sellers' Collect boxes; on a network, every minute
 * the copy of the open lots is read again (Redis notices make it quicker) and anything held on this server is written.
 * Every second the Market Board's summary goes to every client if it changed, and to each player at login.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class MarketEvents {
    private MarketEvents() {}

    private static int sweepTicks = 30 * 20, refreshTicks = 60 * 20, summaryTicks = 20;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && --sweepTicks <= 0) {
            sweepTicks = 30 * 20;
            Market.expireDue(event.getServer());
        }
        if (event.phase == TickEvent.Phase.END && --refreshTicks <= 0) {
            refreshTicks = 60 * 20;
            NetworkMarket net = com.mrgregles.bsp_core.storage.NetworkStorage.market();
            if (net != null) {
                net.refresh(event.getServer());
                net.sweep();
            }
        }
        if (event.phase == TickEvent.Phase.END && --summaryTicks <= 0) {
            summaryTicks = 20;
            MarketSummary.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
            MarketSummary.sendTo(p);
        }
    }

    @SubscribeEvent
    public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        MarketSummary.forget();
    }
}
