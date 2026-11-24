package com.mrgregles.bsp_core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's own machine tracking (tags, slots, prices, the Remote Control unlock, the Holo Grid layout). */
public record TrackerSyncPacket(CompoundTag view) {
    public static void encode(TrackerSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.view);
    }

    public static TrackerSyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return new TrackerSyncPacket(t == null ? new CompoundTag() : t);
    }

    public static void handle(TrackerSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.tracking.ClientTracking.accept(msg.view));
        ctx.get().setPacketHandled(true);
    }
}
