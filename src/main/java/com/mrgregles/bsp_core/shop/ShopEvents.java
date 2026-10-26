package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shop work tied to the player's own comings and goings. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ShopEvents {
    private ShopEvents() {}

    /**
     * A player leaving with the Sell Here box open: the box is settled before the save (the logout
     * event comes first, then the save, and only then is the box closed). HIGH, so a sync mod saving
     * at LOWEST sees the items back in the inventory.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof SellHereMenu box) {
            box.leaving(player);
        }
    }
}
