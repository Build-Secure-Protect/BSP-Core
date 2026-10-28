package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.network.BSPNetwork;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What the lottery's admin page shows: the pot, the settings, every rank that can be left out, and the players seen this
 * season with where each stands (at most {@link #MAX_ROWS}, the most recently seen first; the counts cover them all).
 * {@code message} is a lang key ("" none) with its arguments; {@code open} asks the client to open the page by itself when
 * it is not already showing (the {@code /bsp lottery admin} command).
 */
public record LotteryAdminView(long pot, int days, List<String> leftOut, List<String> ranks, List<Row> rows, int in, int out, int seen,
                               boolean busy, long now, String message, List<String> args, boolean open) {
    public static final int MAX_ROWS = 300;
    public static final LotteryAdminView EMPTY = new LotteryAdminView(0, 7, List.of(), List.of(), List.of(), 0, 0, 0, false, 0, "", List.of(), false);

    /** One player: {@code standing} is a {@link Lottery.Standing} ordinal, {@code rank} the rank leaving them out (else ""). */
    public record Row(String name, long seenMs, String ranks, int standing, String rank) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(pot);
        buf.writeVarInt(days);
        strings(buf, leftOut);
        strings(buf, ranks);
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUtf(BSPNetwork.cut(r.name, 16), 16);
            buf.writeLong(r.seenMs);
            buf.writeUtf(BSPNetwork.cut(r.ranks, 64), 64);
            buf.writeVarInt(r.standing);
            buf.writeUtf(BSPNetwork.cut(r.rank, 32), 32);
        }
        buf.writeVarInt(in);
        buf.writeVarInt(out);
        buf.writeVarInt(seen);
        buf.writeBoolean(busy);
        buf.writeLong(now);
        buf.writeUtf(BSPNetwork.cut(message, 128), 128);
        strings(buf, args);
        buf.writeBoolean(open);
    }

    public static LotteryAdminView decode(FriendlyByteBuf buf) {
        long pot = buf.readLong();
        int days = buf.readVarInt();
        List<String> leftOut = strings(buf), ranks = strings(buf);
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUtf(16), buf.readLong(), buf.readUtf(64), buf.readVarInt(), buf.readUtf(32)));
        }
        int in = buf.readVarInt(), out = buf.readVarInt(), seen = buf.readVarInt();
        boolean busy = buf.readBoolean();
        long now = buf.readLong();
        String message = buf.readUtf(128);
        List<String> args = strings(buf);
        return new LotteryAdminView(pot, days, leftOut, ranks, rows, in, out, seen, busy, now, message, args, buf.readBoolean());
    }

    /** Up to 64 strings of up to 64 characters (rank names, message arguments). */
    private static void strings(FriendlyByteBuf buf, List<String> list) {
        int n = Math.min(64, list.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(BSPNetwork.cut(list.get(i), 64), 64);
        }
    }

    private static List<String> strings(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readUtf(64));
        }
        return out;
    }
}
