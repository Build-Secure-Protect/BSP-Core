package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.TotemAuras;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Fortify aura: blocks near a fortified totem are slower to mine and resist explosions. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemFortifyHandler {
    private TotemFortifyHandler() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || event.getPosition().isEmpty()) {
            return;
        }
        int lvl = TotemAuras.fortifyLevelAt(level, event.getPosition().get(), player.getUUID());
        if (lvl > 0) {
            event.setNewSpeed((float) (event.getNewSpeed() * TotemAuras.breakSpeedMultiplier(lvl)));
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Explosions are resisted no matter who set them off, the totem's owner included.
        event.getAffectedBlocks().removeIf(pos -> {
            int lvl = TotemAuras.fortifyLevelAt(level, pos, null);
            return lvl > 0 && level.random.nextDouble() < TotemAuras.explosionProtection(lvl);
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TotemAuras.clear();
    }
}
