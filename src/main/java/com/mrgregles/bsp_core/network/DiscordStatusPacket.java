package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.discord.DiscordLinks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's own Discord link, for the line on Herald's card. Never a Discord name or id: there is none to send. */
public record DiscordStatusPacket(DiscordLinks.State state, String code, int secondsLeft) {
    public static void encode(DiscordStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.state);
        buf.writeUtf(msg.code, 8);
        buf.writeVarInt(msg.secondsLeft);
    }

    public static DiscordStatusPacket decode(FriendlyByteBuf buf) {
        return new DiscordStatusPacket(buf.readEnum(DiscordLinks.State.class), buf.readUtf(8), buf.readVarInt());
    }

    public static void handle(DiscordStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.DiscordClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
