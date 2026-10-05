package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: show what looks like a Shatter Totem's panel for the decoy at {@code pos}. Sent to players who do not own it. */
public record DecoyFakeOpenPacket(BlockPos pos, String ownerName) {
    public static void encode(DecoyFakeOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.ownerName, 32);
    }

    public static DecoyFakeOpenPacket decode(FriendlyByteBuf buf) {
        return new DecoyFakeOpenPacket(buf.readBlockPos(), buf.readUtf(32));
    }

    public static void handle(DecoyFakeOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.DecoyFakeScreen.open(msg.pos, msg.ownerName));
        ctx.get().setPacketHandled(true);
    }
}
