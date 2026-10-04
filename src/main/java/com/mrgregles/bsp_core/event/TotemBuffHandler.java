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

    /** Featherfall: the totem in the offhand takes a share of fall damage away. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            net.minecraft.world.item.ItemStack offhand = player.getOffhandItem();
            int lvl = com.mrgregles.bsp_core.totem.TotemInventories.isTotem(offhand)
                    ? com.mrgregles.bsp_core.totem.TotemUpgrades.getLevel(offhand, com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.FEATHERFALL) : 0;
            if (lvl > 0) {
                double cut = com.mrgregles.bsp_core.BSPConfig.levelValue(com.mrgregles.bsp_core.BSPConfig.FEATHERFALL_REDUCTION.get(), lvl, 0.0);
                event.setDamageMultiplier((float) (event.getDamageMultiplier() * Math.max(0.0, 1.0 - cut)));
            }
        }
    }

    /** Sanctuary: hostile mobs do not spawn naturally near a placed totem that has it. */
    @SubscribeEvent
    public static void onMobSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.NATURAL && event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && com.mrgregles.bsp_core.totem.TotemAuras.levelInReach(level, event.getEntity().blockPosition(), com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.SANCTUARY) > 0) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float multiplier = ShatterTotemItem.miningSpeedMultiplier(event.getEntity());
        if (multiplier != 1.0F) {
            event.setNewSpeed(event.getNewSpeed() * multiplier);
        }
    }
}
