package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.admin.SeasonRewardsMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: an admin typed the LuckPerms group of a place (0 to 2) on the season prize screen; "" gives none. */
public record SeasonGroupPacket(int place, String group) {
    public static void encode(SeasonGroupPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.place);
        buf.writeUtf(BSPNetwork.cut(msg.group, SeasonData.GROUP_MAX), SeasonData.GROUP_MAX);
    }

    public static SeasonGroupPacket decode(FriendlyByteBuf buf) {
        return new SeasonGroupPacket(buf.readVarInt(), buf.readUtf(SeasonData.GROUP_MAX));
    }

    public static void handle(SeasonGroupPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        // only from the open prize screen of an admin (a moderator's is read-only)
        if (player != null && player.containerMenu instanceof SeasonRewardsMenu menu && menu.editable() && Admins.isAdmin(player)
                && msg.place >= 0 && msg.place < SeasonData.PLACES) {
            String g = msg.group.trim();
            if (!SeasonData.validGroup(g)) {
                player.displayClientMessage(Component.translatable("message.bsp_core.rewards.group_bad").withStyle(ChatFormatting.RED), true);
            } else {
                SeasonData.setPrizeGroup(player.server, msg.place, g, player.getGameProfile().getName());
                player.displayClientMessage(Component.translatable(g.isEmpty() ? "message.bsp_core.rewards.group_cleared" : "message.bsp_core.rewards.group_set", msg.place + 1, g)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
