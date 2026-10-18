package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: whether the player's chat goes to their team right now, for the badge above the chat. */
public record TeamChatPacket(boolean on, int colour, String team) {
    public static void encode(TeamChatPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.on);
        buf.writeByte(m.colour);
        buf.writeUtf(m.team);
    }

    public static TeamChatPacket decode(FriendlyByteBuf buf) {
        return new TeamChatPacket(buf.readBoolean(), buf.readByte(), buf.readUtf());
    }

    public static void handle(TeamChatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.TeamChatBadge.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
