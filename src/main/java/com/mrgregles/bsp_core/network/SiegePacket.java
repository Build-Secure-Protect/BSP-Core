package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.Siege;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the Siege key was pressed. */
public record SiegePacket() {
    public static void encode(SiegePacket msg, FriendlyByteBuf buf) {
    }

    public static SiegePacket decode(FriendlyByteBuf buf) {
        return new SiegePacket();
    }

    public static void handle(SiegePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            Siege.toggle(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
