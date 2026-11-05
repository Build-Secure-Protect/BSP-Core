package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the NEXT key on Tripwire's card, for the totem at {@code pos}. The totem decides who may and what comes next. */
public record TripwireRangePacket(BlockPos pos) {
    public static void encode(TripwireRangePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static TripwireRangePacket decode(FriendlyByteBuf buf) {
        return new TripwireRangePacket(buf.readBlockPos());
    }

    public static void handle(TripwireRangePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null
                && player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) <= 64
                && player.level().getBlockEntity(msg.pos) instanceof ShatterTotemBlockEntity totem) {
            totem.stepTripwireRange(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
