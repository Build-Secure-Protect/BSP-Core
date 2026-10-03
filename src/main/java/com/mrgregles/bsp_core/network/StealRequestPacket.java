package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the player pressed Steal on the totem at {@code pos}. */
public record StealRequestPacket(BlockPos pos) {
    public static void encode(StealRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static StealRequestPacket decode(FriendlyByteBuf buf) {
        return new StealRequestPacket(buf.readBlockPos());
    }

    public static void handle(StealRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64) {
            return; // too far to be a legitimate click
        }
        if (player.level().getBlockEntity(msg.pos) instanceof ShatterTotemBlockEntity totem) {
            totem.tryStartSteal(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
