package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.RecallService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the owner accepted or declined the Recall offer. */
public record RecallAnswerPacket(boolean accept) {
    public static void encode(RecallAnswerPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.accept);
    }

    public static RecallAnswerPacket decode(FriendlyByteBuf buf) {
        return new RecallAnswerPacket(buf.readBoolean());
    }

    public static void handle(RecallAnswerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            RecallService.answer(player, msg.accept);
        }
        ctx.get().setPacketHandled(true);
    }
}
