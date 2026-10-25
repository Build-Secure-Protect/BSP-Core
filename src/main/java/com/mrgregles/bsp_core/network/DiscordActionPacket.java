package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.discord.DiscordLinks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: Herald's card asks for the player's Discord link, a new code, or an unlink. */
public record DiscordActionPacket(DiscordLinks.Action action) {
    public static void encode(DiscordActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static DiscordActionPacket decode(FriendlyByteBuf buf) {
        return new DiscordActionPacket(buf.readEnum(DiscordLinks.Action.class));
    }

    public static void handle(DiscordActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            DiscordLinks.act(player, msg.action, false);
        }
        ctx.get().setPacketHandled(true);
    }
}
