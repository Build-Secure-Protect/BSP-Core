package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.plasma.CarriedPowers;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thief's Step: a carrier makes no footstep sounds. Step sounds are played at a position, not by the entity, so the nearest
 * player within a block and a half of the sound is taken to be the one stepping. Runs on both sides, since the client plays its
 * own steps too. The Alarm immunity is in the totem's intruder tick.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ThiefStepHandler {
    private ThiefStepHandler() {}

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        var holder = event.getSound();
        if (holder == null || !holder.isBound()) {
            return;
        }
        String path = holder.get().getLocation().getPath();
        if (!path.endsWith(".step") && !path.endsWith(".fall")) {
            return;
        }
        Vec3 at = event.getPosition();
        for (Player p : event.getLevel().players()) {
            if (p.distanceToSqr(at) <= 2.25 && CarriedPowers.level(p, Buff.THIEF_STEP) > 0) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
