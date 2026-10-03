package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemItem;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies totem buffs that need events rather than attributes. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemBuffHandler {
    private TotemBuffHandler() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float multiplier = ShatterTotemItem.miningSpeedMultiplier(event.getEntity());
        if (multiplier != 1.0F) {
            event.setNewSpeed(event.getNewSpeed() * multiplier);
        }
    }
}
