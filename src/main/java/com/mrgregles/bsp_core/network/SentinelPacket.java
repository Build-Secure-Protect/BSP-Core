package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: Sentinel's view of one totem: who is inside its Alarm cube (name from level 2, distance to the totem from
 * level 3, position from level 4), and the steal in progress if any. An empty list clears the strip for that totem.
 */
public record SentinelPacket(String dimension, BlockPos totem, int level, String thief, int stealSeconds, List<Intruder> intruders) {
    public record Intruder(String name, int distance, double x, double y, double z) {
    }

    public static void encode(SentinelPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dimension, 128);
        buf.writeBlockPos(msg.totem);
        buf.writeVarInt(msg.level);
        buf.writeUtf(msg.thief, 64);
        buf.writeVarInt(msg.stealSeconds);
        buf.writeVarInt(msg.intruders.size());
        for (Intruder i : msg.intruders) {
            buf.writeUtf(i.name, 64);
            buf.writeVarInt(i.distance + 1);
            buf.writeDouble(i.x);
            buf.writeDouble(i.y);
            buf.writeDouble(i.z);
        }
    }

    public static SentinelPacket decode(FriendlyByteBuf buf) {
        String dim = buf.readUtf(128);
        BlockPos totem = buf.readBlockPos();
        int level = buf.readVarInt();
        String thief = buf.readUtf(64);
        int seconds = buf.readVarInt();
        int n = buf.readVarInt();
        List<Intruder> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Intruder(buf.readUtf(64), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new SentinelPacket(dim, totem, level, thief, seconds, list);
    }

    public static void handle(SentinelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.SentinelHud.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
