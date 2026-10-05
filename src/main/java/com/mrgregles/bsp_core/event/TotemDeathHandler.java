package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemPlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * What happens to a player's Shatter Totems when they die.
 *
 * <p>Runs at highest priority on {@link LivingDeathEvent}, which fires before drops are computed, so
 * grave mods and keep-inventory never see the totem.
 * <ul>
 *   <li>Killed by another player: every totem is placed as a block next to the body at once.</li>
 *   <li>Any other death: every totem becomes a ground item that places itself after the timer.</li>
 *   <li>If the death happened outside an allowed dimension, the totem goes to the player's last
 *       position in an allowed dimension instead (world spawn as a last resort).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemDeathHandler {
    private TotemDeathHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (com.mrgregles.bsp_core.zone.ZoneHandler.inTotemZone(player)) {
            com.mrgregles.bsp_core.zone.ZoneHandler.holdTotems(player); // in an Anti Totem zone the player keeps them through the death
            return;
        }
        List<ItemStack> totems = TotemInventories.removeAll(player);
        if (totems.isEmpty()) {
            return;
        }

        boolean killedByPlayer = event.getSource().getEntity() instanceof ServerPlayer killer && killer != player;

        TotemPlacer.Target target = TotemPlacer.safeTargetFor(player);
        ServerLevel level = target.level();
        BlockPos pos = target.pos();

        for (ItemStack totem : totems) {
            if (killedByPlayer) {
                if (!TotemPlacer.place(level, pos, totem)) {
                    dropAsItem(level, pos, totem);
                }
            } else {
                dropAsItem(level, pos, totem);
            }
        }

        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        player.displayClientMessage(Component.translatable(
                killedByPlayer ? "message.bsp_core.shatter_totem.placed_on_death" : "message.bsp_core.shatter_totem.dropped_on_death",
                where, level.dimension().location().toString()).withStyle(ChatFormatting.GOLD), false);
        BSPCore.LOGGER.info("{} died ({}); {} totem(s) sent to {} in {}",
                player.getGameProfile().getName(), killedByPlayer ? "PvP" : "natural", totems.size(), pos, level.dimension().location());
    }

    private static void dropAsItem(ServerLevel level, BlockPos pos, ItemStack totem) {
        ShatterTotemItemEntity entity = new ShatterTotemItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, totem);
        entity.setPickUpDelay(40);
        level.addFreshEntity(entity);
    }
}
