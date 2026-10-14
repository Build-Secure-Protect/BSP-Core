package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client: a Plasma Tank for its screen: the master corner, size, level, each port's setting and flow, and the cables near the ports. A zero size means the block is not part of a formed tank. */
public record TankViewPacket(BlockPos pos, int w, int h, int d, long stored, long capacity, List<Port> ports, List<Cable> cables, List<Power> powers) {
    public record Port(BlockPos pos, int mode, int in, int out) {
    }

    /** A totem power the stored plasma carries: the Buff ordinal, its level, how much plasma still carries it, and whether a totem is feeding it now. */
    public record Power(int ordinal, int level, long mB, boolean live) {
    }

    public record Cable(BlockPos pos, int flow) {
    }

    public static void encode(TankViewPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeByte(msg.w);
        buf.writeByte(msg.h);
        buf.writeByte(msg.d);
        buf.writeLong(msg.stored);
        buf.writeLong(msg.capacity);
        buf.writeVarInt(msg.ports.size());
        for (Port p : msg.ports) {
            buf.writeBlockPos(p.pos);
            buf.writeByte(p.mode);
            buf.writeVarInt(p.in);
            buf.writeVarInt(p.out);
        }
        buf.writeVarInt(msg.cables.size());
        for (Cable c : msg.cables) {
            buf.writeBlockPos(c.pos);
            buf.writeVarInt(c.flow);
        }
        buf.writeVarInt(msg.powers.size());
        for (Power p : msg.powers) {
            buf.writeVarInt(p.ordinal);
            buf.writeVarInt(p.level);
            buf.writeLong(p.mB);
            buf.writeBoolean(p.live);
        }
    }

    public static TankViewPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int w = buf.readByte(), h = buf.readByte(), d = buf.readByte();
        long stored = buf.readLong(), capacity = buf.readLong();
        int n = Math.min(buf.readVarInt(), 1024);
        List<Port> ports = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ports.add(new Port(buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readVarInt()));
        }
        int m = Math.min(buf.readVarInt(), 4096);
        List<Cable> cables = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            cables.add(new Cable(buf.readBlockPos(), buf.readVarInt()));
        }
        int k = Math.min(buf.readVarInt(), 64);
        List<Power> powers = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            powers.add(new Power(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readBoolean()));
        }
        return new TankViewPacket(pos, w, h, d, stored, capacity, ports, cables, powers);
    }

    public static void handle(TankViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.TankScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
