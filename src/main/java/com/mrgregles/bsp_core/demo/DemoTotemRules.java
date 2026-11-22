package com.mrgregles.bsp_core.demo;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Only admins break a Demo Totem, in survival or creative (a creative break skips the block's mining progress), and only admins put
 * one down by any means: a tool that places blocks without the item's own check (a Construction Wand, say) fires the place event.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class DemoTotemRules {
    private DemoTotemRules() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof DemoTotemBlock && !DemoTotems.isAdmin(event.getPlayer())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.bsp_core.demo.mine_admins").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Covers the multi-block place event too (it is a kind of this one). */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof DemoTotemBlock
                && !(event.getEntity() instanceof net.minecraft.world.entity.player.Player p && DemoTotems.isAdmin(p))) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof net.minecraftforge.common.util.FakePlayer)) {
                player.displayClientMessage(Component.translatable("message.bsp_core.demo.place_admins").withStyle(ChatFormatting.RED), true);
            }
        }
    }
}
