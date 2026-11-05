package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a steal the steal-back rule refused ({@code totem.StealBacks}). The client fills the screen with "NAUGHTY
 * NAUGHTY" for 5 seconds ({@code client.NaughtyHud}). It carries nothing, so it says nothing about why.
 */
public record NaughtyPacket() {
    public static void encode(NaughtyPacket msg, FriendlyByteBuf buf) {
    }

    public static NaughtyPacket decode(FriendlyByteBuf buf) {
        return new NaughtyPacket();
    }

    public static void handle(NaughtyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.mrgregles.bsp_core.client.NaughtyHud::show);
        ctx.get().setPacketHandled(true);
    }
}
