package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: what the chunk picker of the totem or projector at {@code pos} shows.
 *
 * @param slots  chunks the totem may keep loaded; {@code used} of them are picked, here or elsewhere
 * @param radius chunks either side of the block that may be picked
 * @param mine   chunks picked at this block; {@code others} are the rest of the same totem's picks
 * @param rf     a projector's stored RF; {@code levels} are the powers reaching it, in generator order
 */
public record ChunkViewPacket(BlockPos pos, boolean projector, int slots, int used, int radius, long[] mine, long[] others, int flags, int rf, int[] levels) {
    public static final int OPEN = 1, EDIT = 2, MAIN = 4, ENABLED = 8, ONLINE_ONLY = 16, SIGNAL = 32, ACTIVE = 64, LOADING = 128;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public static void encode(ChunkViewPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.projector);
        buf.writeVarInt(msg.slots);
        buf.writeVarInt(msg.used);
        buf.writeVarInt(msg.radius);
        buf.writeLongArray(msg.mine);
        buf.writeLongArray(msg.others);
        buf.writeVarInt(msg.flags);
        buf.writeVarInt(msg.rf);
        buf.writeVarIntArray(msg.levels);
    }

    public static ChunkViewPacket decode(FriendlyByteBuf buf) {
        return new ChunkViewPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readLongArray(null, 64), buf.readLongArray(null, 64), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(32));
    }

    public static void handle(ChunkViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ChunkMap.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
