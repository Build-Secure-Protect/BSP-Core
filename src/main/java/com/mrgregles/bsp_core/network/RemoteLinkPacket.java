package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a machine screen just opened from the Holo Grid, and whether it is locked to the machine's own items (no Inventory
 * Link on the transmitter), so the client covers the player's inventory in it.
 */
public record RemoteLinkPacket(int containerId, boolean locked) {
    public static void encode(RemoteLinkPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.locked);
    }

    public static RemoteLinkPacket decode(FriendlyByteBuf buf) {
        return new RemoteLinkPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(RemoteLinkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.tracking.LinkCover.opened(msg.containerId, msg.locked));
        ctx.get().setPacketHandled(true);
    }
}
