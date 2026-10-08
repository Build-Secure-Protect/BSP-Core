package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: whether this operator has chosen to be treated like an outsider by Cloaking. */
public record CloakBlindPacket(boolean blind) {
    public static void encode(CloakBlindPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.blind);
    }

    public static CloakBlindPacket decode(FriendlyByteBuf buf) {
        return new CloakBlindPacket(buf.readBoolean());
    }

    public static void handle(CloakBlindPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.CloakClient.setBlind(msg.blind));
        ctx.get().setPacketHandled(true);
    }
}
