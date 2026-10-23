package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemPlacer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mrgregles.bsp_core.data.PlayerPersistent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A player cannot take a Shatter Totem offline. On logout every carried totem is placed as a block
 * next to where they stood (or at their last main-dimension position), so it can still be stolen.
 *
 * <p>Forge fires this event before the player's data is saved, so the removal persists. A server
 * switch inside a network looks identical to a logout, unless a teleport mod announced it through
 * {@link com.mrgregles.bsp_core.api.BspTransfer}: then the totem travels through the database instead.
 *
 * <p>A client crash is a logout too, so the placement is remembered on the player: when they log in again on this server and the
 * totem still stands where the logout put it, untouched and not being stolen, it comes straight back into their inventory
 * ({@code restrictions.logoutReturnMinutes}). While they are away it stands in the world like any other, so nothing is hidden.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemLogoutHandler {
    private TotemLogoutHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            placeOnLogout(player);
        }
    }

    /** Takes every carried totem and places it in the world (or lets it travel with an announced transfer). */
    public static void placeOnLogout(ServerPlayer player) {
        List<ItemStack> totems = TotemInventories.removeAll(player);
        totems.addAll(com.mrgregles.bsp_core.zone.ZoneHandler.takeHeld(player)); // left while dead in an Anti Totem zone
        List<ItemStack> transferring = com.mrgregles.bsp_core.api.BspTransfer.takeOnLogout(player.getUUID());
        if (transferring != null) {
            // a server transfer announced by a teleport mod: totems in the database travel with the player; any held here are placed as usual
            totems.addAll(transferring);
            if (totems.isEmpty()) {
                BSPCore.LOGGER.info("{} left for another server; their totem travels with them", player.getGameProfile().getName());
                return;
            }
        }
        if (totems.isEmpty()) {
            return;
        }
        if (com.mrgregles.bsp_core.totem.NoTotemServers.here()) {
            com.mrgregles.bsp_core.totem.NoTotemServers.keepForNextLogin(player, totems); // nothing is placed on a server without totem placement
            return;
        }
        TotemPlacer.Target target = TotemPlacer.safeTargetFor(player);
        for (ItemStack totem : totems) {
            com.mrgregles.bsp_core.totem.TotemIdentity.ensure(totem);
            BlockPos placedAt = TotemPlacer.placeAt(target.level(), target.pos(), totem);
            if (placedAt == null) {
                ShatterTotemItemEntity entity = new ShatterTotemItemEntity(target.level(),
                        target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5, totem);
                target.level().addFreshEntity(entity);
            } else {
                PlayerPersistent.addLogoutPlaced(player, new PlayerPersistent.LogoutPlaced(target.level().dimension(), placedAt,
                        com.mrgregles.bsp_core.totem.TotemIdentity.id(totem), com.mrgregles.bsp_core.totem.TotemIdentity.instance(totem), System.currentTimeMillis()));
            }
        }
        BSPCore.LOGGER.info("{} logged out carrying {} totem(s); placed at {} in {}",
                player.getGameProfile().getName(), totems.size(), target.pos(), target.level().dimension().location());
    }

    /** What became of a totem the logout placed, when its owner came back. */
    /** QUIET: the placement is gone but nobody took it (destroyed, forgotten, reissued, or back in the owner's own hands), so nothing is said. */
    public enum Returned { BACK, BEING_STOLEN, GONE, TOO_LATE, QUIET }

    /** Whether the ledger says the totem of a logout placement is now with someone else (a completed steal); anything else is nobody's doing. */
    private static boolean takenByOther(ServerPlayer player, PlayerPersistent.LogoutPlaced placed) {
        var sighting = com.mrgregles.bsp_core.data.TotemLedger.get(player.server).sighting(placed.id());
        return sighting != null && sighting.instance().equals(placed.instance()) && sighting.holder() != null && !sighting.holder().equals(player.getUUID());
    }

    /** Players who logged in dead: the totems their logout placed come back at the respawn instead. */
    private static final java.util.Set<java.util.UUID> AFTER_RESPAWN = new java.util.HashSet<>();

    /**
     * After the identity scan and the pending-item hand-backs: the totems the logout placed come back if they still stand untouched.
     * Never into a dead player (they logged out on the death screen): a dead entity's inventory does not pass to the respawned one,
     * so the totems stay standing until the respawn. The placements are kept in the data that survives death.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isDeadOrDying()) {
                AFTER_RESPAWN.add(player.getUUID());
            } else {
                returnPlaced(player);
            }
        }
    }

    /** A respawn (not the trip home from the End) after a dead login: the totems the logout placed come back now. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player && AFTER_RESPAWN.remove(player.getUUID())) {
            returnPlaced(player);
        }
    }

    @SubscribeEvent
    public static void onLogoutForget(PlayerEvent.PlayerLoggedOutEvent event) {
        AFTER_RESPAWN.remove(event.getEntity().getUUID()); // the placements stay recorded for their next login
    }

    /**
     * Gives back every totem the logout rule placed for {@code player}, if it still stands where it was put with the same identity,
     * is still theirs and is not being stolen. A totem mid-steal stays, with a warning; a moved, stolen or picked-up one is gone.
     */
    public static List<Returned> returnPlaced(ServerPlayer player) {
        List<Returned> out = new java.util.ArrayList<>();
        int minutes = com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LOGOUT_RETURN_MINUTES, 0);
        for (PlayerPersistent.LogoutPlaced placed : PlayerPersistent.takeLogoutPlaced(player)) {
            net.minecraft.server.level.ServerLevel level = placed.level(player.server);
            Component where = Component.translatable("message.bsp_core.link.where", com.mrgregles.bsp_core.link.Links.worldName(placed.dimension()),
                    placed.pos().getX(), placed.pos().getY(), placed.pos().getZ());
            if (level == null) {
                out.add(Returned.GONE);
                continue;
            }
            if (minutes > 0 && System.currentTimeMillis() - placed.whenMs() > minutes * 60_000L) {
                player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.stands_at", where).withStyle(ChatFormatting.GOLD), false);
                out.add(Returned.TOO_LATE);
                continue;
            }
            level.getChunk(placed.pos()); // the chunk may have unloaded while they were away
            boolean standing = level.getBlockEntity(placed.pos()) instanceof com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity totem0 && totem0.isOwner(player.getUUID());
            ItemStack stack = standing ? com.mrgregles.bsp_core.totem.ShatterTotemBlock.toItemStack(level, placed.pos()) : ItemStack.EMPTY;
            if (!standing || !placed.id().equals(com.mrgregles.bsp_core.totem.TotemIdentity.id(stack)) || !placed.instance().equals(com.mrgregles.bsp_core.totem.TotemIdentity.instance(stack))) {
                // the placement is not there as it was: say so only when the ledger shows someone else has that totem now
                if (takenByOther(player, placed)) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.gone", where).withStyle(ChatFormatting.RED), false);
                    out.add(Returned.GONE);
                } else {
                    BSPCore.LOGGER.debug("{}'s logout placement at {} is gone but nobody took it; nothing said", player.getGameProfile().getName(), placed.pos());
                    out.add(Returned.QUIET);
                }
                continue;
            }
            var totem = (com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity) level.getBlockEntity(placed.pos());
            if (totem.getSteal().isPresent()) {
                player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.being_stolen", where).withStyle(ChatFormatting.RED), false);
                out.add(Returned.BEING_STOLEN);
                continue;
            }
            level.removeBlock(placed.pos(), false); // onRemove tells the ledger and the chunk loading, and fires TotemEvent.Removed
            TotemInventories.giveBack(player, stack);
            player.displayClientMessage(Component.translatable("message.bsp_core.shatter_totem.back", where).withStyle(ChatFormatting.GOLD), false);
            BSPCore.LOGGER.info("{}'s Shatter Totem stood at {} since their logout and came back to them", player.getGameProfile().getName(), placed.pos());
            out.add(Returned.BACK);
        }
        return out;
    }
}
