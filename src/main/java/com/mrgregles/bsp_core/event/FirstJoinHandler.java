package com.mrgregles.bsp_core.event;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hands every player exactly one Shatter Totem the first time they join a server that has
 * {@code first_join.grantTotemOnFirstJoin} enabled (the Spawn Hub).
 *
 * <p>Two records guard against double grants: the player's persisted NBT (travels with the player
 * file) and the {@link TotemLedger} (travels with the world). Either one being set blocks a grant.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class FirstJoinHandler {
    private FirstJoinHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!BSPConfig.GRANT_TOTEM_ON_FIRST_JOIN.get()) {
            return;
        }
        TotemLedger ledger = TotemLedger.get(player.server);
        if (ledger.hasBeenGranted(player.getUUID()) || PlayerPersistent.isTotemGranted(player)) {
            return;
        }
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            grant(player, ledger);
            giveGuideBook(player);
            return;
        }
        // On a network the database decides: only the first server to record the player hands out a totem.
        java.util.UUID id = player.getUUID();
        com.mrgregles.bsp_core.storage.NetworkStorage.claimGrant(id, result -> {
            if (result == com.mrgregles.bsp_core.storage.NetworkStorage.CLAIM_FAILED) {
                return; // database unreachable: nothing recorded, so the next join tries again
            }
            if (result == com.mrgregles.bsp_core.storage.NetworkStorage.CLAIM_GRANTED_ELSEWHERE) {
                ledger.markGranted(id); // remember locally, so this server does not ask again
                PlayerPersistent.setTotemGranted(player, true);
                return;
            }
            if (player.hasDisconnected()) {
                com.mrgregles.bsp_core.storage.NetworkStorage.clearGrant(id); // left before it could be given: undo the record
                return;
            }
            grant(player, ledger);
            giveGuideBook(player);
        });
    }

    /** Hands over the BSP Field Guide, if Patchouli is installed. */
    private static void giveGuideBook(ServerPlayer player) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("patchouli", "guide_book"));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        ItemStack book = new ItemStack(item);
        book.getOrCreateTag().putString("patchouli:book", BSPCore.MODID + ":guide");
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    /** Creates an owned totem for {@code player} and records the grant. Also used by admin commands. */
    public static void grant(ServerPlayer player, TotemLedger ledger) {
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        new TotemOwner(player.getUUID(), player.getGameProfile().getName()).applyTo(totem);

        if (!player.getInventory().add(totem)) {
            // Inventory full: drop at the player's feet. The item form cannot despawn or be destroyed.
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), totem);
            drop.setNoPickUpDelay();
            player.level().addFreshEntity(drop);
        }

        ledger.markGranted(player.getUUID());
        PlayerPersistent.setTotemGranted(player, true);

        player.displayClientMessage(
                Component.translatable("message.bsp_core.shatter_totem.granted").withStyle(ChatFormatting.GOLD), false);
        BSPCore.LOGGER.info("Granted a Shatter Totem to {} ({})", player.getGameProfile().getName(), player.getUUID());
    }
}
