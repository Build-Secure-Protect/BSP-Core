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
 * Server to client: a Plasma Tank for its screen: the master corner, size, level, each port's setting and flow, and the cables near the ports.
 * A zero size means the block is not part of a formed tank. Of {@code stored}, {@code plain} carries no power and {@code totem} is the totem
 * pool; the rest is the mob pools, each a {@link Power} with a Mob Chamber power's ordinal.
 */
public record TankViewPacket(BlockPos pos, int w, int h, int d, long stored, long capacity, long plain, long totem, List<Port> ports, List<Cable> cables, List<Power> powers,
                             @javax.annotation.Nullable Chamber chamber) {
    /**
     * A Mob Chamber's CHAMBER tab: the ALL / HOSTILE / PASSIVE setting ({@code mode}), the start mark in thousandths of the tank, the biome's
     * key, mobs alive here and the most at once, seconds to the next spawn, whether the server's chamber mobs are at their limit, the tallest
     * mob that fits, what can spawn here (most likely first), the 27 loot slots, and the drop being fed: its id, its level, the plasma one
     * item tags at levels I to III, and the plasma a projector uses in a minute.
     */
    public record Chamber(int mode, int markPermille, String biome, int alive, int maxAlive, int nextSeconds, boolean serverFull, int maxHeight,
                          List<Spawn> spawns, List<net.minecraft.world.item.ItemStack> loot, String feedItem, int feedLevel, long[] feedMb, long minuteMb,
                          int spawnTotal) {
    }

    /** One mob that can spawn: its entity id, its chance in thousandths, its main drop's item id ("" for none) and that drop's power's Buff ordinal (-1 for none). */
    public record Spawn(String entity, int permille, String drop, int power) {
    }

    public record Port(BlockPos pos, int mode, int in, int out) {
    }

    /** A power the stored plasma carries: the Buff ordinal, its level, how much plasma carries it (for a mob power, its pool), and whether it is being fed now. */
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
        buf.writeLong(msg.plain);
        buf.writeLong(msg.totem);
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
        buf.writeBoolean(msg.chamber != null);
        if (msg.chamber != null) {
            Chamber c = msg.chamber;
            buf.writeVarInt(c.mode);
            buf.writeVarInt(c.markPermille);
            buf.writeUtf(c.biome, 256);
            buf.writeVarInt(c.alive);
            buf.writeVarInt(c.maxAlive);
            buf.writeVarInt(c.nextSeconds);
            buf.writeBoolean(c.serverFull);
            buf.writeVarInt(c.maxHeight);
            buf.writeVarInt(c.spawns.size());
            for (Spawn s : c.spawns) {
                buf.writeUtf(s.entity, 256);
                buf.writeVarInt(s.permille);
                buf.writeUtf(s.drop, 256);
                buf.writeVarInt(s.power + 1);
            }
            buf.writeVarInt(c.loot.size());
            for (var stack : c.loot) {
                buf.writeItem(stack);
            }
            buf.writeUtf(c.feedItem, 256);
            buf.writeVarInt(c.feedLevel);
            buf.writeLongArray(c.feedMb);
            buf.writeLong(c.minuteMb);
            buf.writeVarInt(c.spawnTotal);
        }
    }

    public static TankViewPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int w = buf.readByte(), h = buf.readByte(), d = buf.readByte();
        long stored = buf.readLong(), capacity = buf.readLong(), plain = buf.readLong(), totem = buf.readLong();
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
        Chamber chamber = null;
        if (buf.readBoolean()) {
            int mode = buf.readVarInt(), mark = buf.readVarInt();
            String biome = buf.readUtf(256);
            int alive = buf.readVarInt(), maxAlive = buf.readVarInt(), next = buf.readVarInt();
            boolean full = buf.readBoolean();
            int maxHeight = buf.readVarInt();
            int sn = Math.min(buf.readVarInt(), 64);
            List<Spawn> spawns = new ArrayList<>(sn);
            for (int i = 0; i < sn; i++) {
                spawns.add(new Spawn(buf.readUtf(256), buf.readVarInt(), buf.readUtf(256), buf.readVarInt() - 1));
            }
            int ln = Math.min(buf.readVarInt(), 64);
            List<net.minecraft.world.item.ItemStack> loot = new ArrayList<>(ln);
            for (int i = 0; i < ln; i++) {
                loot.add(buf.readItem());
            }
            String feed = buf.readUtf(256);
            int feedLevel = buf.readVarInt();
            long[] feedMb = buf.readLongArray(null, 8);
            chamber = new Chamber(mode, mark, biome, alive, maxAlive, next, full, maxHeight, spawns, loot, feed, feedLevel, feedMb, buf.readLong(), buf.readVarInt());
        }
        return new TankViewPacket(pos, w, h, d, stored, capacity, plain, totem, ports, cables, powers, chamber);
    }

    public static void handle(TankViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.TankScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
