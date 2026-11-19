package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The bounties' clock: every 30 seconds, bounties past their end close and give their reward and bonus back, and kills that could not
 * be claimed are tried again; on a network, every minute the copy of the bounties is read again (Redis notices make it quicker).
 * And the staff check at login.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BountyEvents {
    private BountyEvents() {}

    private static int sweepTicks = 30 * 20, refreshTicks = 60 * 20, summaryTicks = 20;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && --sweepTicks <= 0) {
            sweepTicks = 30 * 20;
            Bounties.retryKills(event.getServer()); // kills held while the network was away first, so one made before the end is not lost to the sweep
            Bounties.expireDue(event.getServer());
        }
        if (event.phase == TickEvent.Phase.END && --summaryTicks <= 0) {
            summaryTicks = 20;
            BountySummary.tick(event.getServer());
        }
        if (event.phase == TickEvent.Phase.END && --refreshTicks <= 0) {
            refreshTicks = 60 * 20;
            NetworkBounties net = com.mrgregles.bsp_core.storage.NetworkStorage.bounties();
            if (net != null) {
                net.refresh(event.getServer());
                net.sweep();
            }
        }
    }

    /** A player who is staff now has every bounty on them ended, everything back to the placers. */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
            Bounties.endIfStaff(p.server, p.getUUID(), p.getGameProfile().getName());
            BountySummary.sendTo(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        BountyScreens.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        BountySummary.forget();
    }
}
