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
 * Server to client: a Plasma Accelerator's state for its screen: whether the ring is formed, what comes in and goes out (mB/t), the RF
 * pool, the links it holds and may hold, the powers the plasma carries, and every link with its world, position, state and rate.
 */
public record LinkViewPacket(BlockPos pos, String world, boolean formed, int in, int out, int energy, int energyCap, int maxLinks, int expanders, List<Power> powers, List<Link> links) {
    public record Power(int ordinal, int level) {}

    public record Link(String world, BlockPos pos, int state, int rate) {}

    public static void encode(LinkViewPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.world);
        buf.writeBoolean(msg.formed);
        buf.writeVarInt(msg.in);
        buf.writeVarInt(msg.out);
        buf.writeVarInt(msg.energy);
        buf.writeVarInt(msg.energyCap);
        buf.writeVarInt(msg.maxLinks);
        buf.writeVarInt(msg.expanders);
        buf.writeVarInt(msg.powers.size());
        for (Power p : msg.powers) {
            buf.writeByte(p.ordinal);
            buf.writeByte(p.level);
        }
        buf.writeVarInt(msg.links.size());
        for (Link l : msg.links) {
            buf.writeUtf(l.world);
            buf.writeBlockPos(l.pos);
            buf.writeByte(l.state);
            buf.writeVarInt(l.rate);
        }
    }

    public static LinkViewPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String world = buf.readUtf();
        boolean formed = buf.readBoolean();
        int in = buf.readVarInt(), out = buf.readVarInt(), energy = buf.readVarInt(), cap = buf.readVarInt(), max = buf.readVarInt(), expanders = buf.readVarInt();
        int n = buf.readVarInt();
        List<Power> powers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            powers.add(new Power(buf.readByte(), buf.readByte()));
        }
        n = buf.readVarInt();
        List<Link> links = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            links.add(new Link(buf.readUtf(), buf.readBlockPos(), buf.readByte(), buf.readVarInt()));
        }
        return new LinkViewPacket(pos, world, formed, in, out, energy, cap, max, expanders, powers, links);
    }

    public static void handle(LinkViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AcceleratorScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
