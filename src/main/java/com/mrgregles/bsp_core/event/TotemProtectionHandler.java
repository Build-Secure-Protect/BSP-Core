package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stops anyone but the owner (or a server operator) from mining a placed Shatter Totem. */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemProtectionHandler {
    private TotemProtectionHandler() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && event.getState().is(net.minecraftforge.common.Tags.Blocks.ORES)
                && net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()) != null
                && com.mrgregles.bsp_core.BSPCore.MODID.equals(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()).getNamespace())) {
            // a BSP ore mined within a Harvest totem's reach grows back there later
            for (ShatterTotemBlockEntity harvester : com.mrgregles.bsp_core.totem.TotemAuras.totemsCovering(sl, event.getPos(), com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.HARVEST)) {
                harvester.harvestRecord(event.getPos(), event.getState());
            }
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof ShatterTotemBlockEntity totem)) {
            return;
        }
        Player player = event.getPlayer();
        if (canTake(player, totem)) {
            return;
        }
        event.setCanceled(true);
        if (!player.level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable("message.bsp_core.shatter_totem.not_owner").withStyle(ChatFormatting.RED), true);
        }
    }

    /** True if {@code player} is the totem's owner or an operator. Unclaimed totems must be stolen (claimed) first. */
    public static boolean canTake(Player player, ShatterTotemBlockEntity totem) {
        TotemOwner owner = totem.getOwner().orElse(null);
        return (owner != null && owner.uuid().equals(player.getUUID())) || player.hasPermissions(2);
    }
}
