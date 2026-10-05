package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the player pressed Steal on what turned out to be the Decoy Totem at {@code pos}. */
public record DecoyStealPacket(BlockPos pos) {
    public static void encode(DecoyStealPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static DecoyStealPacket decode(FriendlyByteBuf buf) {
        return new DecoyStealPacket(buf.readBlockPos());
    }

    public static void handle(DecoyStealPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.level().getBlockEntity(msg.pos) instanceof DecoyTotemBlockEntity decoy) {
            decoy.stealAttempt(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
