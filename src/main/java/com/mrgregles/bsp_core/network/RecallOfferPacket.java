package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: offer a Recall to the totem at {@code pos} ({@code seconds} 0 clears the offer). {@code distance} is -1 from another dimension. */
public record RecallOfferPacket(BlockPos pos, String dimension, int seconds, int distance, int radius) {
    public static RecallOfferPacket clear() {
        return new RecallOfferPacket(BlockPos.ZERO, "", 0, 0, 0);
    }

    public static void encode(RecallOfferPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.dimension, 128);
        buf.writeVarInt(msg.seconds);
        buf.writeVarInt(msg.distance + 1);
        buf.writeVarInt(msg.radius);
    }

    public static RecallOfferPacket decode(FriendlyByteBuf buf) {
        return new RecallOfferPacket(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt());
    }

    public static void handle(RecallOfferPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.RecallHud.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
