package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.projector.TotemGeneratorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: open the screen of the Totem Generator at {@code pos}, picked from the array grid. */
public record GeneratorOpenPacket(BlockPos pos) {
    public static void encode(GeneratorOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static GeneratorOpenPacket decode(FriendlyByteBuf buf) {
        return new GeneratorOpenPacket(buf.readBlockPos());
    }

    public static void handle(GeneratorOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            TotemGeneratorBlock.open(player, msg.pos);
        }
        ctx.get().setPacketHandled(true);
    }
}
