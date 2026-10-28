package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ends what a player leaves behind: logging out or dying cancels their trade and their requests; the server's tick runs the countdowns. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TradeEvents {
    private TradeEvents() {}

    private static int viewTicks, stateTicks;

    /** Before the sync mods save the inventory at LOWEST, so the items given back are in the saved inventory. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TradePostMenu.emptyLotPlaces(event.getEntity());
        Trades.leave(event.getEntity(), "left");
    }

    /** Before the death drops: the lot places go back into the inventory, so they drop (or stay, with keepInventory) with it. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide) {
            TradePostMenu.emptyLotPlaces(p);
            Trades.leave(p, "died");
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Trades.tick();
            if (++stateTicks >= 4) {
                stateTicks = 0;
                for (var p : event.getServer().getPlayerList().getPlayers()) {
                    if (p.containerMenu instanceof TradeMenu) {
                        TradeMenu.sync(p, "", java.util.List.of());
                    }
                }
            }
            if (++viewTicks >= 20) {
                viewTicks = 0;
                for (var p : event.getServer().getPlayerList().getPlayers()) {
                    if (p.containerMenu instanceof TradePostMenu m && m.locked(p.server, p.getUUID())) {
                        com.mrgregles.bsp_core.network.TradePostActionPacket.closeLocked(p);
                    } else if (p.containerMenu instanceof TradePostMenu) {
                        TradePostMenu.sync(p, "", java.util.List.of());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Trades.stopAll();
    }
}
