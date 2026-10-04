package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server, for the totem in {@code hand}: buy the next level of the upgrade with ordinal
 * {@code buffOrdinal}, or raise the totem's tier when {@code buffOrdinal} is {@link #RAISE_TIER}.
 */
public record UpgradeRequestPacket(InteractionHand hand, int buffOrdinal) {
    public static final int RAISE_TIER = -1;

    public static void encode(UpgradeRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeVarInt(msg.buffOrdinal);
    }

    public static UpgradeRequestPacket decode(FriendlyByteBuf buf) {
        return new UpgradeRequestPacket(buf.readEnum(InteractionHand.class), buf.readVarInt());
    }

    public static void handle(UpgradeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            apply(player, msg.hand, msg.buffOrdinal);
        }
        ctx.get().setPacketHandled(true);
    }

    static void apply(ServerPlayer player, InteractionHand hand, int ordinal) {
        ItemStack totem = player.getItemInHand(hand);
        if (!TotemInventories.isTotem(totem)) {
            return;
        }
        int tier = TotemUpgrades.getTier(totem);
        if (ordinal == RAISE_TIER) {
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(tier);
            Component why = TotemUpgrades.whyNotGate(tier, player);
            if (price == null || why != null) {
                player.displayClientMessage((why == null ? Component.translatable("gui.bsp_core.tree.gate.max") : why).copy().withStyle(ChatFormatting.RED), true);
                return;
            }
            TotemUpgrades.pay(player, price);
            TotemUpgrades.setTier(totem, tier + 1);
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.tier_raised", TotemUpgrades.roman(tier + 1)).withStyle(ChatFormatting.GOLD), true);
            BSPCore.LOGGER.info("{} raised a held totem to tier {}", player.getGameProfile().getName(), tier + 2);
        } else {
            TotemUpgrades.Buff buff = TotemUpgrades.Buff.byOrdinal(ordinal);
            if (buff == null || buff.placedOnly) {
                return; // Base upgrades are bought at the placed totem
            }
            int level = TotemUpgrades.getLevel(totem, buff);
            TotemUpgrades.Price price = TotemUpgrades.price(buff, level);
            Component why = TotemUpgrades.whyNot(buff, b -> TotemUpgrades.getLevel(totem, b), tier, player);
            if (price == null || why != null) {
                player.displayClientMessage((why == null ? Component.translatable("message.bsp_core.upgrade.maxed") : why).copy().withStyle(ChatFormatting.RED), true);
                return;
            }
            TotemUpgrades.pay(player, price);
            TotemUpgrades.setLevel(totem, buff, level + 1);
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.bought",
                    Component.translatable(buff.translationKey()), level + 1).withStyle(ChatFormatting.GOLD), true);
            BSPCore.LOGGER.info("{} upgraded {} to level {}", player.getGameProfile().getName(), buff.key, level + 1);
        }
        // The upgrade menu has no slots, so the open container never re-sends the hand slot. Push it directly.
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, totem.copy()));
    }
}
