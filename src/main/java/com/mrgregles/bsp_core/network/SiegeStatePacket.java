package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: ticks of Siege left, and ticks until it may be used again, for the ring by the crosshair. */
public record SiegeStatePacket(int activeTicks, int readyInTicks) {
    public static void encode(SiegeStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.activeTicks);
        buf.writeVarInt(msg.readyInTicks);
    }

    public static SiegeStatePacket decode(FriendlyByteBuf buf) {
        return new SiegeStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SiegeStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.SiegeClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
