package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: buy the next level of {@code buff} for the totem in {@code hand}. */
public record UpgradeRequestPacket(InteractionHand hand, int buffOrdinal) {
    public static void encode(UpgradeRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeVarInt(msg.buffOrdinal);
    }

    public static UpgradeRequestPacket decode(FriendlyByteBuf buf) {
        return new UpgradeRequestPacket(buf.readEnum(InteractionHand.class), buf.readVarInt());
    }

    public static void handle(UpgradeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        TotemUpgrades.Buff buff = TotemUpgrades.Buff.byOrdinal(msg.buffOrdinal);
        if (player != null && buff != null) {
            apply(player, msg.hand, buff);
        }
        ctx.get().setPacketHandled(true);
    }

    static void apply(ServerPlayer player, InteractionHand hand, TotemUpgrades.Buff buff) {
        ItemStack totem = player.getItemInHand(hand);
        if (!TotemInventories.isTotem(totem) || buff.placedOnly) {
            return;
        }
        int level = TotemUpgrades.getLevel(totem, buff);
        int cost = buff.costToUpgrade(level);
        if (cost < 0) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.maxed").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (buff.currency == TotemUpgrades.Currency.COINS) {
            if (!player.isCreative() && !com.mrgregles.bsp_core.coin.CoinWallet.pay(player, cost)) {
                player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_enough_coins", cost).withStyle(ChatFormatting.RED), true);
                return;
            }
        } else {
            if (!player.isCreative() && player.experienceLevel < cost) {
                player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_enough_xp", cost).withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!player.isCreative()) {
                player.giveExperienceLevels(-cost);
            }
        }
        TotemUpgrades.setLevel(totem, buff, level + 1);
        // The upgrade menu has no slots, so the open container never re-sends the hand slot. Push it directly.
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, totem.copy()));
        player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.bought",
                Component.translatable(buff.translationKey()), level + 1).withStyle(ChatFormatting.GOLD), true);
        BSPCore.LOGGER.info("{} upgraded {} to level {} for {} XP levels", player.getGameProfile().getName(), buff.key, level + 1, cost);
    }
}
