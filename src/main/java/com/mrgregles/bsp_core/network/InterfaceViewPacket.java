package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: what the interface group at {@code pos} is made of and what runs through it, for its
 * screen: every block with its kind and the mB/t passing it.
 *
 * @param supply  mB/t the group's extractors give together
 * @param cloak   mB/t the totems' Cloaking took before that
 * @param members blocks in the group; {@code max} is the most allowed
 */
public record InterfaceViewPacket(BlockPos pos, int supply, int cloak, int members, int max, List<Entry> blocks) {
    public static final int INTERFACE = 0, REFUSED = 1, EXTRACTOR = 2, CABLE = 3, REPEATER = 4, RECEIVER = 5, VALVE = 6;

    /** One block: where, what, and the flow through it (an extractor's output, a cable's load, a receiver's delivery), in mB/t. For a valve, {@code repeaters} is 1 when shut. */
    public record Entry(BlockPos pos, int kind, int flow, int repeaters) {
    }

    public static void encode(InterfaceViewPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.supply);
        buf.writeVarInt(msg.cloak);
        buf.writeVarInt(msg.members);
        buf.writeVarInt(msg.max);
        buf.writeVarInt(msg.blocks.size());
        for (Entry e : msg.blocks) {
            buf.writeBlockPos(e.pos);
            buf.writeByte(e.kind);
            buf.writeVarInt(e.flow);
            buf.writeByte(e.repeaters);
        }
    }

    public static InterfaceViewPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int supply = buf.readVarInt(), cloak = buf.readVarInt(), members = buf.readVarInt(), max = buf.readVarInt(), n = Math.min(buf.readVarInt(), 2048);
        List<Entry> blocks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            blocks.add(new Entry(buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readByte()));
        }
        return new InterfaceViewPacket(pos, supply, cloak, members, max, blocks);
    }

    public static void handle(InterfaceViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.InterfaceScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
