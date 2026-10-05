package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: show the three by three of a joined Totem Generator array. {@code centre} is the
 * generator under the totem, bit {@code (dz + 1) * 3 + (dx + 1)} of {@code mask} is set where a
 * generator stands, and {@code clicked} is the one the player right-clicked.
 */
public record GeneratorArrayPacket(BlockPos centre, int mask, BlockPos clicked) {
    public static void encode(GeneratorArrayPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.centre);
        buf.writeVarInt(msg.mask);
        buf.writeBlockPos(msg.clicked);
    }

    public static GeneratorArrayPacket decode(FriendlyByteBuf buf) {
        return new GeneratorArrayPacket(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(GeneratorArrayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.GeneratorArrayScreen.open(msg));
        ctx.get().setPacketHandled(true);
    }
}
