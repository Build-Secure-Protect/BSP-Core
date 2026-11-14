package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client, once a second while the viewer stands in a Projector's Wireframe reach: the entity ids of the outsiders the viewer
 * sees there. The client keeps a list for a couple of seconds after the last packet from that projector.
 */
public record WireframePacket(BlockPos projector, int[] ids) {
    public static void encode(WireframePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.projector);
        buf.writeVarIntArray(msg.ids);
    }

    public static WireframePacket decode(FriendlyByteBuf buf) {
        return new WireframePacket(buf.readBlockPos(), buf.readVarIntArray(256));
    }

    public static void handle(WireframePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.WireframeClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
