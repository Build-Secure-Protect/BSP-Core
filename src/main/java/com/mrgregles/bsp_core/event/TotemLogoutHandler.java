package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemPlacer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * A player cannot take a Shatter Totem offline. On logout every carried totem is placed as a block
 * next to where they stood (or at their last main-dimension position), so it can still be stolen.
 *
 * <p>Forge fires this event before the player's data is saved, so the removal persists. A server
 * switch inside a network looks identical to a logout; the cross-server transfer prompt is a
 * Phase 4 feature that needs the shared database.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemLogoutHandler {
    private TotemLogoutHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<ItemStack> totems = TotemInventories.removeAll(player);
        totems.addAll(com.mrgregles.bsp_core.zone.ZoneHandler.takeHeld(player)); // left while dead in an Anti Totem zone
        if (totems.isEmpty()) {
            return;
        }
        TotemPlacer.Target target = TotemPlacer.safeTargetFor(player);
        for (ItemStack totem : totems) {
            if (!TotemPlacer.place(target.level(), target.pos(), totem)) {
                ShatterTotemItemEntity entity = new ShatterTotemItemEntity(target.level(),
                        target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5, totem);
                target.level().addFreshEntity(entity);
            }
        }
        BSPCore.LOGGER.info("{} logged out carrying {} totem(s); placed at {} in {}",
                player.getGameProfile().getName(), totems.size(), target.pos(), target.level().dimension().location());
    }
}
