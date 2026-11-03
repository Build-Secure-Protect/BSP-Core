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
            player.displayClientMessage(Component.translatable(com.mrgregles.bsp_core.totem.NoTotemServers.here() ? "message.bsp_core.no_totem.no_place"
                    : "message.bsp_core.shatter_totem.dimension_blocked").withStyle(ChatFormatting.RED), true);
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
    public static void trackIdentity(ServerPlayer player) {
        TotemLedger ledger = TotemLedger.get(player.server);
        long now = player.server.overworld().getGameTime();
        Inventory inv = player.getInventory();
        java.util.Set<UUID> seen = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            seen = track(player, ledger, inv.getItem(i), now, seen);
        }
        seen = track(player, ledger, player.containerMenu.getCarried(), now, seen);
        // the inventory's own 2x2 crafting grid: a totem there is still in the player's hands (and comes back out on closing)
        var grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            seen = track(player, ledger, grid.getItem(i), now, seen);
        }
    }

    /** Tracks one stack; returns the ids seen so far in this scan (a second live stack of the same id is a clone and is spent). */
    @javax.annotation.Nullable
    private static java.util.Set<UUID> track(ServerPlayer player, TotemLedger ledger, ItemStack stack, long now, @javax.annotation.Nullable java.util.Set<UUID> seen) {
        if (stack.isEmpty() || !stack.is(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get()) || TotemIdentity.isSpent(stack)) {
            return seen;
        }
        UUID id = TotemIdentity.id(stack);
        if (id != null && seen != null && seen.contains(id) && !ledger.stale(id, TotemIdentity.instance(stack))) {
            TotemIdentity.spend(stack); // the same live totem twice (a creative clone): only the first is real
            return seen;
        }
        track(player, ledger, stack, now);
        if (!TotemIdentity.isSpent(stack) && TotemIdentity.id(stack) != null) { // only a stack that stayed live counts (a stale copy is spent above)
            if (seen == null) {
                seen = new java.util.HashSet<>(4);
            }
            seen.add(TotemIdentity.id(stack));
        }
        return seen;
    }

    private static void track(ServerPlayer player, TotemLedger ledger, ItemStack stack, long now) {
        if (stack.isEmpty() || !stack.is(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get()) || TotemIdentity.isSpent(stack)) {
            return;
        }
        TotemIdentity.ensure(stack);
        if (TotemIdentity.oldSeason(stack, player.server)) {
            TotemIdentity.spend(stack); // from an earlier season: the season's end replaced it
            player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.old_season").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (com.mrgregles.bsp_core.totem.TotemOwner.fromStack(stack).isEmpty()) {
            // a totem nobody owns (from the creative menu, a give command, JEI) is its holder's: never "Unclaimed" in a player's hands
            new com.mrgregles.bsp_core.totem.TotemOwner(player.getUUID(), player.getGameProfile().getName()).applyTo(stack);
            BSPCore.LOGGER.info("An unowned Shatter Totem in {}'s inventory is now theirs", player.getGameProfile().getName());
        }
        UUID id = TotemIdentity.id(stack), instance = TotemIdentity.instance(stack), current = ledger.currentInstance(id);
        if (current != null && !current.equals(instance) && !(player.isCreative() && ledger.creativeReturn(id, instance, player.getUUID(), now))) {
            TotemLedger.Sighting real = ledger.sighting(id);
            TotemIdentity.spend(stack); // a copy that went missing and was reissued, a duplicate of one elsewhere, or a destroyed totem: a husk now
            player.displayClientMessage(spentMessage(player, real).withStyle(ChatFormatting.RED), true);
            BSPCore.LOGGER.info("A stale copy of Shatter Totem {} in {}'s inventory is a husk now; {}", id, player.getGameProfile().getName(),
                    real == null || real.where() == TotemLedger.RETIRED ? "the totem was destroyed" : real.holder() == null ? "the real one's holder is unknown"
                            : "the real one is " + com.mrgregles.bsp_core.score.ScoreService.name(player.server, real.holder()) + "'s");
            return;
        }
        ledger.seen(id, instance, player.getUUID(), TotemLedger.HELD, stack.getTag(), now);
    }

    /** What a player is told when a copy in their hands turns into a husk: whose the real totem is, or that the totem was destroyed. */
    private static net.minecraft.network.chat.MutableComponent spentMessage(ServerPlayer player, @javax.annotation.Nullable TotemLedger.Sighting real) {
        if (real == null || real.where() == TotemLedger.RETIRED) {
            return Component.translatable("message.bsp_core.shatter_totem.spent_destroyed");
        }
        if (real.holder() == null) {
            return Component.translatable("message.bsp_core.shatter_totem.spent");
        }
        if (real.holder().equals(player.getUUID())) {
            return Component.translatable("message.bsp_core.shatter_totem.spent_yours");
        }
        return Component.translatable("message.bsp_core.shatter_totem.spent_duplicate", com.mrgregles.bsp_core.score.ScoreService.name(player.server, real.holder()));
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
            lost(holder, ledger, id, now);
        }
    }

    /** What became of a held totem that is nowhere. */
    public enum Lost { REISSUED, DESTROYED }

    /**
     * A held totem has been nowhere for a minute. A survival player gets it back with a new instance (it was stored away or carried
     * off). A creative player is taken at their word: the creative inventory's bin and its item browser delete what is dropped on them,
     * and an admin must be able to get rid of a totem for good, so it is destroyed: retired here and on the network, and dropped from
     * every record that could bring it back, so every other copy of it is a husk. The same player putting the same copy back (a stack
     * held on the creative cursor, which only the client knows of) gets it back live on this server ({@link TotemLedger#creativeReturn}).
     */
    public static Lost lost(ServerPlayer holder, TotemLedger ledger, UUID id, long now) {
        if (holder.isDeadOrDying()) {
            return null; // a dead entity's inventory does not pass to the respawned player: asked again once they are back
        }
        if (com.mrgregles.bsp_core.admin.LoginSettle.isWaiting(holder.getUUID())) {
            return null; // the login waits for BSP-InventorySync's restore, which may bring the totem back: asked again once it is settled
        }
        if (holder.isCreative()) {
            ledger.creativeDestroyed(id, holder.getUUID());
            com.mrgregles.bsp_core.totem.TotemWipe.dropRecords(holder.server, id, holder.getUUID(), holder,
                    holder.getGameProfile().getName() + " in creative mode (nowhere for " + LOST_AFTER_TICKS + " ticks)");
            holder.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.destroyed").withStyle(ChatFormatting.GRAY), true);
            return Lost.DESTROYED;
        }
        reissue(holder, ledger, id, now);
        return Lost.REISSUED;
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
                lost(player, ledger, id, now);
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
