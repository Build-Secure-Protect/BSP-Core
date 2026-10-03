package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Shatter Totem where the rules say it may be:
 * <ul>
 *   <li>Dropping it in a dimension outside {@code restrictions.allowedDimensions} is refused and the
 *       totem goes straight back into the player's inventory.</li>
 *   <li>It may only sit in the player's own inventory. Any totem found in another container's slots
 *       while a menu is open is pulled back out.</li>
 *   <li>The player's last position in an allowed dimension is remembered for death handling.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemRestrictionHandler {
    private static final int CONTAINER_SCAN_INTERVAL = 5;   // ticks
    private static final int POSITION_SAVE_INTERVAL = 20;   // ticks

    private TotemRestrictionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!TotemInventories.isTotem(stack)) {
            return;
        }
        if (BSPConfig.isDimensionAllowed(event.getPlayer().level().dimension().location())) {
            return;
        }
        // Cancelling removes the drop from the world but not from the player, so put it back ourselves.
        event.setCanceled(true);
        TotemInventories.giveBack(event.getPlayer(), stack.copy());
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable("message.bsp_core.shatter_totem.dimension_blocked").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        int tick = player.tickCount;
        if (tick % CONTAINER_SCAN_INTERVAL == 0) {
            ejectFromForeignSlots(player);
        }
        if (tick % POSITION_SAVE_INTERVAL == 0 && BSPConfig.isDimensionAllowed(player.level().dimension().location())) {
            PlayerPersistent.setLastMainPosition(player, player.level().dimension(), player.blockPosition());
        }
    }

    private static void ejectFromForeignSlots(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            return;
        }
        boolean ejected = false;
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!TotemInventories.isTotem(stack)) {
                continue;
            }
            ItemStack totem = stack.copy();
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            TotemInventories.giveBack(player, totem);
            ejected = true;
        }
        if (ejected) {
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("message.bsp_core.shatter_totem.container_blocked").withStyle(ChatFormatting.RED), true);
        }
    }
}
