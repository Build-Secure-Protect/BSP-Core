package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's own deposit box state and Shopping Network flag, for the block renderer. */
public record BankStatePacket(int boxSlots, long boxUntilMs, boolean remoteShops) {
    public static void encode(BankStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.boxSlots);
        buf.writeLong(msg.boxUntilMs);
        buf.writeBoolean(msg.remoteShops);
    }

    public static BankStatePacket decode(FriendlyByteBuf buf) {
        return new BankStatePacket(buf.readVarInt(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(BankStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientBank.set(msg.boxSlots, msg.boxUntilMs, msg.remoteShops));
        ctx.get().setPacketHandled(true);
    }
}
