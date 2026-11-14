package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.TotemAuras;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Warp Ward, a Mob Chamber power at a Projector: an outsider (as {@link MobPowerEffects#outsider}) may not teleport into or out of its
 * reach by ender pearl or chorus fruit. The pearl is spent and the fruit eaten, but the player stays. BSP-Teleport asks the same question
 * for its commands through {@link com.mrgregles.bsp_core.api.BspWarpWard}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class WarpWard {
    private WarpWard() {}

    /** Why {@code player} may not move from where they stand to {@code to} in {@code toLevel}: held in, kept out, or null if they may. */
    @Nullable
    public static Component why(ServerPlayer player, ServerLevel toLevel, BlockPos to) {
        if (player.isSpectator() || player.isCreative()) {
            return null;
        }
        for (var p : TotemAuras.projectorsCovering(player.serverLevel(), player.blockPosition(), Buff.WARP_WARD)) {
            if (MobPowerEffects.outsider(p, player)) {
                return Component.translatable("message.bsp_core.warp_ward.out");
            }
        }
        for (var p : TotemAuras.projectorsCovering(toLevel, to, Buff.WARP_WARD)) {
            if (MobPowerEffects.outsider(p, player)) {
                return Component.translatable("message.bsp_core.warp_ward.in");
            }
        }
        return null;
    }

    private static boolean refuse(ServerPlayer player, ServerLevel toLevel, BlockPos to) {
        Component why = why(player, toLevel, to);
        if (why != null) {
            player.displayClientMessage(why.copy().withStyle(ChatFormatting.DARK_PURPLE), true);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPearl(EntityTeleportEvent.EnderPearl event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel to = event.getPearlEntity().level() instanceof ServerLevel sl ? sl : player.serverLevel();
        if (refuse(player, to, BlockPos.containing(event.getTarget()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntityLiving() instanceof ServerPlayer player && refuse(player, player.serverLevel(), BlockPos.containing(event.getTarget()))) {
            event.setCanceled(true);
        }
    }
}
