package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: a Plasma Valve's settings and flow, opening or refreshing its screen. All rates in mB/t. */
public record ValveViewPacket(BlockPos pos, int limit, int max, boolean redstone, boolean powered, int in, int out) {
    public static void encode(ValveViewPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.limit);
        buf.writeVarInt(msg.max);
        buf.writeBoolean(msg.redstone);
        buf.writeBoolean(msg.powered);
        buf.writeVarInt(msg.in);
        buf.writeVarInt(msg.out);
    }

    public static ValveViewPacket decode(FriendlyByteBuf buf) {
        return new ValveViewPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ValveViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.PlasmaValveScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
