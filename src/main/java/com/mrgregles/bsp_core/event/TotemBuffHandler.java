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

    /** Featherfall: the totem (or Wave Emitter) in the offhand takes a share of fall damage away. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            int lvl = com.mrgregles.bsp_core.plasma.CarriedPowers.level(player, com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.FEATHERFALL);
            int bouncy = com.mrgregles.bsp_core.plasma.CarriedPowers.level(player, com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.BOUNCY);
            double cut = lvl > 0 ? com.mrgregles.bsp_core.BSPConfig.levelValue(com.mrgregles.bsp_core.BSPConfig.FEATHERFALL_REDUCTION.get(), lvl, 0.0) : 0.0;
            if (bouncy > 0) { // Bouncy and Featherfall add up, never below no damage at all
                cut += com.mrgregles.bsp_core.BSPConfig.levelValue(com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.BOUNCY_REDUCTION, java.util.List.<Double>of()), bouncy, 0.0);
            }
            if (cut > 0) {
                event.setDamageMultiplier((float) (event.getDamageMultiplier() * Math.max(0.0, 1.0 - cut)));
            }
            // Bouncy: a landing from three blocks or more throws you back up, higher the further you fell, and onward if you were moving; sneaking lands flat
            if (bouncy > 0 && event.getDistance() >= 3 && !player.isShiftKeyDown() && !player.level().isClientSide) {
                double share = com.mrgregles.bsp_core.BSPConfig.levelValue(com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.BOUNCY_BOUNCE, java.util.List.<Double>of()), bouncy, 0.0);
                double landing = Math.sqrt(2 * 0.08 * event.getDistance()), up = Math.min(1.4, landing * share);
                net.minecraft.world.phys.Vec3 dm = player.getDeltaMovement();
                player.setDeltaMovement(dm.x * 1.3, up, dm.z * 1.3);
                player.hurtMarked = true;
                player.fallDistance = 0;
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            }
        }
    }

    /** Resistance: the totem (or Wave Emitter) in the offhand takes a share of every hit away. A percentage, so it can never make the holder immune. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            int lvl = com.mrgregles.bsp_core.plasma.CarriedPowers.level(player, com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.RESISTANCE);
            if (lvl > 0) {
                double cut = Math.min(0.9, com.mrgregles.bsp_core.BSPConfig.RESISTANCE_PER_LEVEL.get() * lvl);
                event.setAmount((float) (event.getAmount() * (1.0 - cut)));
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
