package com.mrgregles.bsp_core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: one tagged machine's latest reading (or only its status: asleep, gone, no access, offline, waiting). */
public record TrackerSnapshotPacket(String id, CompoundTag reading) {
    public static void encode(TrackerSnapshotPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(BSPNetwork.cut(msg.id, 16), 16);
        buf.writeNbt(msg.reading);
    }

    public static TrackerSnapshotPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(16);
        CompoundTag t = buf.readNbt();
        return new TrackerSnapshotPacket(id, t == null ? new CompoundTag() : t);
    }

    public static void handle(TrackerSnapshotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.tracking.ClientTracking.reading(msg.id, msg.reading));
        ctx.get().setPacketHandled(true);
    }
}
