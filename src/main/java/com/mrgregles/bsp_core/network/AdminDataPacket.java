package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: everything the admin panel shows. {@code open} opens the panel; otherwise it
 * only refreshes a panel that is already showing. {@code canEdit} is false for moderators. {@code here} is the
 * server's id; {@code network} says the rows cover the whole network (totems on other servers name their server).
 */
public record AdminDataPacket(boolean open, boolean canEdit, String here, boolean network, int season, int day, String lastWinner, int lastWinnerPoints, int holderHours, int holderMinutesLeft, int[] decoyRanges, boolean chunksOnlineOnly, List<Row> rows) {
    /** One totem: its tier (0 = I), whether it is carried, and where it (or its carrier) is: on which server, in which dimension. */
    public record Totem(int tier, boolean carried, String server, String dimension, BlockPos pos) {
    }

    /** One player. {@code totemCount} may be larger than the list, which is cut short for very large holdings. */
    public record Row(UUID id, String name, boolean online, int points, int totemCount, List<Totem> totems, int[] coins, int vaultValue, int vaultBlocks) {
    }

    public static void encode(AdminDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeBoolean(msg.canEdit);
        buf.writeUtf(BSPNetwork.cut(msg.here, 64), 64);
        buf.writeBoolean(msg.network);
        buf.writeVarInt(msg.season);
        buf.writeVarInt(msg.day);
        buf.writeUtf(msg.lastWinner, 32);
        buf.writeVarInt(msg.lastWinnerPoints);
        buf.writeVarInt(msg.holderHours);
        buf.writeVarInt(msg.holderMinutesLeft + 1);
        buf.writeVarIntArray(msg.decoyRanges);
        buf.writeBoolean(msg.chunksOnlineOnly);
        buf.writeVarInt(msg.rows.size());
        for (Row r : msg.rows) {
            buf.writeUUID(r.id);
            buf.writeUtf(r.name, 32);
            buf.writeBoolean(r.online);
            buf.writeVarInt(r.points);
            buf.writeVarInt(r.totemCount);
            buf.writeVarInt(r.totems.size());
            for (Totem t : r.totems) {
                buf.writeVarInt(t.tier);
                buf.writeBoolean(t.carried);
                buf.writeUtf(BSPNetwork.cut(t.server, 64), 64);
                buf.writeUtf(BSPNetwork.cut(t.dimension, 128), 128);
                buf.writeBlockPos(t.pos);
            }
            buf.writeVarIntArray(r.coins);
            buf.writeVarInt(r.vaultValue);
            buf.writeVarInt(r.vaultBlocks);
        }
    }

    public static AdminDataPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean(), canEdit = buf.readBoolean();
        String here = buf.readUtf(64);
        boolean network = buf.readBoolean();
        int season = buf.readVarInt(), day = buf.readVarInt();
        String winner = buf.readUtf(32);
        int winnerPoints = buf.readVarInt(), holderHours = buf.readVarInt(), holderLeft = buf.readVarInt() - 1;
        int[] decoyRanges = buf.readVarIntArray(8);
        boolean chunksOnlineOnly = buf.readBoolean();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(32);
            boolean online = buf.readBoolean();
            int points = buf.readVarInt(), count = buf.readVarInt(), tn = buf.readVarInt();
            List<Totem> totems = new ArrayList<>(tn);
            for (int k = 0; k < tn; k++) {
                totems.add(new Totem(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readUtf(128), buf.readBlockPos()));
            }
            rows.add(new Row(id, name, online, points, count, totems, buf.readVarIntArray(16), buf.readVarInt(), buf.readVarInt()));
        }
        return new AdminDataPacket(open, canEdit, here, network, season, day, winner, winnerPoints, holderHours, holderLeft, decoyRanges, chunksOnlineOnly, rows);
    }

    public static void handle(AdminDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
