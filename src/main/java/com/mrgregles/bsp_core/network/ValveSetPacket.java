package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: refresh a valve's screen ({@code VIEW}) or apply a limit and the redstone switch ({@code SET}). The server answers with a view. */
public record ValveSetPacket(BlockPos pos, int action, int limit, boolean redstone) {
    public static final int VIEW = 0, SET = 1;

    public static void encode(ValveSetPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.limit);
        buf.writeBoolean(msg.redstone);
    }

    public static ValveSetPacket decode(FriendlyByteBuf buf) {
        return new ValveSetPacket(buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ValveSetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.level().isLoaded(msg.pos) || player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64 * 64) {
                return;
            }
            if (!(player.level().getBlockEntity(msg.pos) instanceof PlasmaValveBlockEntity valve) || !valve.mayUse(player)) {
                return;
            }
            if (msg.action == SET) {
                valve.set(msg.limit, msg.redstone);
            }
            BSPNetwork.sendTo(player, valve.view());
        });
        ctx.get().setPacketHandled(true);
    }
}
