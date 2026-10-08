package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client, the answer to a {@link CloakRefreshPacket}: whether the totem at {@code origin} still cloaks, and whether this
 * player may see the real cube. The client keeps hiding only while both say so.
 */
public record CloakStatusPacket(BlockPos origin, boolean cloaked, boolean maySee) {
    public static void encode(CloakStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.origin);
        buf.writeBoolean(msg.cloaked);
        buf.writeBoolean(msg.maySee);
    }

    public static CloakStatusPacket decode(FriendlyByteBuf buf) {
        return new CloakStatusPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(CloakStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.CloakClient.status(msg));
        ctx.get().setPacketHandled(true);
    }
}
