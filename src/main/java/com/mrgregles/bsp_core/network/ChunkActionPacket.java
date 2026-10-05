package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.chunk.ChunkLoading;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the chunk picker of the totem or projector at {@code pos} wants fresh data, or a chunk in it was clicked. */
public record ChunkActionPacket(BlockPos pos, int action, int chunkX, int chunkZ) {
    public static final int VIEW = 0, TOGGLE = 1;

    public static void encode(ChunkActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.chunkX);
        buf.writeVarInt(msg.chunkZ);
    }

    public static ChunkActionPacket decode(FriendlyByteBuf buf) {
        return new ChunkActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ChunkActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) <= 64 && player.level().isLoaded(msg.pos)) {
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof ShatterTotemBlockEntity || be instanceof TotemProjectorBlockEntity) {
                if (msg.action == TOGGLE) {
                    ChunkLoading.toggle(player, msg.pos, msg.chunkX, msg.chunkZ);
                } else {
                    ChunkLoading.sendView(player, msg.pos, false);
                }
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
