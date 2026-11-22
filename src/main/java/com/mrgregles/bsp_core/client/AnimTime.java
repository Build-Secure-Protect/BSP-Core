package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Time for spinning and bobbing things, in ticks with the fraction of the frame, that stays smooth on an old world.
 *
 * <p>{@code level.getGameTime() + partial} is a float: past 16,777,216 ticks (about ten days of world time) a float cannot hold the
 * fraction any more, so an animation moves only once a tick (later once every two), and it also jumps each time the server sets the
 * client's clock. This counts the client's own ticks instead and wraps about every 14 hours ({@link #WRAP}), so the number stays small
 * enough for a float to keep a sixteenth of a tick. Spins of 0.6, 1.5 or 2 degrees a tick make whole turns by then and do not jump at
 * the wrap; animations timed in radians or seconds make one small jump there, once in 14 hours.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class AnimTime {
    private AnimTime() {}

    /** 14.4 hours of ticks, under 2^20 (so a float keeps 1/16 of a tick), and a whole number of turns at 0.6, 1.5 and 2 degrees a tick. */
    public static final int WRAP = 1_036_800;
    private static int ticks;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            ticks = (ticks + 1) % WRAP;
        }
    }

    /** Now, for a renderer given the partial tick (a block entity renderer's {@code partial}). */
    public static float ticks(float partialTick) {
        return ticks + partialTick;
    }

    /**
     * Now, for a screen: a screen's {@code render} is given the time since the last frame, not the partial tick, so this takes the
     * partial tick from Minecraft itself (the one that stands still while the game is paused, as this counter does).
     */
    public static float ticks() {
        return ticks + Minecraft.getInstance().getPartialTick();
    }

    /** Ticks since {@code from} (a value of {@link #ticks()}), across the wrap. */
    public static float since(float from) {
        float d = ticks() - from;
        return d < 0 ? d + WRAP : d;
    }
}
