package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.world.entity.player.Inventory;
import java.util.UUID;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.data.TotemLedger;
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
        trackIdentity(player);
        if (tick % CONTAINER_SCAN_INTERVAL == 0) {
            ejectFromForeignSlots(player);
        }
        if (tick % POSITION_SAVE_INTERVAL == 0 && BSPConfig.isDimensionAllowed(player.level().dimension().location())) {
            PlayerPersistent.setLastMainPosition(player, player.level().dimension(), player.blockPosition());
        }
    }

    /** Ticks a held totem may go unseen before it counts as stored away or carried off. */
    private static final int LOST_AFTER_TICKS = 60;

    /**
     * Every tick: every totem in the player's inventory (and on their cursor) gets its id if it has none, is spent if it is a stale
     * copy, and is otherwise recorded as seen in this player's hands.
     */
    private static void trackIdentity(ServerPlayer player) {
        TotemLedger ledger = TotemLedger.get(player.server);
        long now = player.server.overworld().getGameTime();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            track(player, ledger, inv.getItem(i), now);
        }
        track(player, ledger, player.containerMenu.getCarried(), now);
    }

    private static void track(ServerPlayer player, TotemLedger ledger, ItemStack stack, long now) {
        if (stack.isEmpty() || !stack.is(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get()) || TotemIdentity.isSpent(stack)) {
            return;
        }
        TotemIdentity.ensure(stack);
        UUID id = TotemIdentity.id(stack), instance = TotemIdentity.instance(stack), current = ledger.currentInstance(id);
        if (current != null && !current.equals(instance)) {
            TotemIdentity.spend(stack); // a copy that went missing and was reissued: a husk now
            player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.spent").withStyle(ChatFormatting.RED), true);
            return;
        }
        ledger.seen(id, instance, player.getUUID(), TotemLedger.HELD, stack.getTag(), now);
    }

    /** Once a second: a totem last seen in someone's hands that has been nowhere for {@link #LOST_AFTER_TICKS} comes back to them. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        TotemLedger ledger = TotemLedger.get(event.getServer());
        long now = event.getServer().overworld().getGameTime();
        for (UUID id : ledger.lostHeld(now, LOST_AFTER_TICKS)) {
            TotemLedger.Sighting lost = ledger.sighting(id);
            ServerPlayer holder = lost == null || lost.holder() == null ? null : event.getServer().getPlayerList().getPlayer(lost.holder());
            if (holder == null) {
                continue; // offline: the check runs again when they are back and the totem is still nowhere
            }
            reissue(holder, ledger, id, now);
        }
    }

    /** A player logging in whose held totem has been nowhere since they left gets it back. */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TotemLedger ledger = TotemLedger.get(player.server);
        long now = player.server.overworld().getGameTime();
        trackIdentity(player); // what they carry counts as seen now
        for (UUID id : ledger.lostHeld(now, LOST_AFTER_TICKS)) {
            TotemLedger.Sighting lost = ledger.sighting(id);
            if (lost != null && player.getUUID().equals(lost.holder())) {
                reissue(player, ledger, id, now);
            }
        }
    }

    private static void reissue(ServerPlayer holder, TotemLedger ledger, UUID id, long now) {
        TotemLedger.Sighting fresh = ledger.reissue(id, now);
        ItemStack totem = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        if (fresh.copy() != null) {
            totem.setTag(fresh.copy().copy());
        }
        totem.getOrCreateTag().putUUID(TotemIdentity.TAG_ID, id);
        TotemIdentity.setInstance(totem, fresh.instance());
        TotemInventories.giveBack(holder, totem);
        holder.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.returned").withStyle(ChatFormatting.GOLD), false);
        BSPCore.LOGGER.info("{}'s Shatter Totem {} was nowhere for {} ticks; reissued to them, the old copy is spent", holder.getGameProfile().getName(), id, LOST_AFTER_TICKS);
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
