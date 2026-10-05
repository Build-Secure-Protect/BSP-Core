package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces Anti Totem zones on players who are not admins:
 * <ul>
 *   <li>a blocked BSP block, or a Shatter Totem where placing is blocked, cannot be placed;</li>
 *   <li>a Shatter Totem cannot be dropped where dropping is blocked;</li>
 *   <li>a player who dies in a zone that blocks totems keeps their totems: they are held back
 *       from the death and returned when the player respawns.</li>
 * </ul>
 * A totem that has to be put down inside a zone (its carrier logged out, or it lay on the ground)
 * is sent to the nearest spot outside by {@code TotemPlacer}. Carrying a totem through a zone is allowed.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ZoneHandler {
    private static final String HELD = BSPCore.MODID + ":ZoneHeldTotems";

    private ZoneHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level) || Admins.isAdmin(player)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (id == null || !BSPCore.MODID.equals(id.getNamespace())) {
            return;
        }
        String key = id.getPath().equals("shatter_totem") ? ZoneLedger.TOTEM_PLACE : id.getPath();
        if (ZoneLedger.isBlocked(level, event.getPos(), key)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.bsp_core.zone.no_place", event.getPlacedBlock().getBlock().getName()).withStyle(ChatFormatting.RED), true);
            player.inventoryMenu.sendAllDataToRemote(); // the client already took the item out of the hand
        }
    }

    /** Runs before the dimension check in TotemRestrictionHandler would let the drop through. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (event.isCanceled() || !TotemInventories.isTotem(stack) || !(event.getPlayer() instanceof ServerPlayer player) || Admins.isAdmin(player)) {
            return;
        }
        if (ZoneLedger.isBlocked(player.serverLevel(), player.blockPosition(), ZoneLedger.TOTEM_DROP)) {
            event.setCanceled(true);
            TotemInventories.giveBack(player, stack.copy());
            player.displayClientMessage(Component.translatable("message.bsp_core.zone.no_drop").withStyle(ChatFormatting.RED), true);
        }
    }

    /** True if the player stands where a totem may not be put down or dropped. */
    public static boolean inTotemZone(ServerPlayer player) {
        ZoneLedger zones = ZoneLedger.get(player.server);
        return zones.blocked(player.serverLevel(), player.blockPosition(), ZoneLedger.TOTEM_PLACE)
                || zones.blocked(player.serverLevel(), player.blockPosition(), ZoneLedger.TOTEM_DROP);
    }

    /**
     * Dying in a totem zone: called by TotemDeathHandler instead of placing or dropping the totems.
     * They are taken out of the inventory before drops are worked out and kept in the player's own
     * data, which survives death.
     */
    public static void holdTotems(ServerPlayer player) {
        List<ItemStack> totems = TotemInventories.removeAll(player);
        if (totems.isEmpty()) {
            return;
        }
        CompoundTag data = PlayerPersistent.get(player);
        ListTag held = data.getList(HELD, Tag.TAG_COMPOUND);
        totems.forEach(t -> held.add(t.save(new CompoundTag())));
        data.put(HELD, held);
        player.displayClientMessage(Component.translatable("message.bsp_core.zone.kept_on_death").withStyle(ChatFormatting.GOLD), false);
    }

    /** Takes back, and clears, the totems held for a player since they died in a zone. */
    public static List<ItemStack> takeHeld(Player player) {
        CompoundTag data = PlayerPersistent.get(player);
        List<ItemStack> out = new ArrayList<>();
        for (Tag raw : data.getList(HELD, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) raw);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        data.remove(HELD);
        return out;
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        for (ItemStack totem : takeHeld(event.getEntity())) {
            TotemInventories.giveBack(event.getEntity(), totem);
        }
    }
}
