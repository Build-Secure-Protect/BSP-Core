package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.network.BSPNetwork;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Trade Post's LOTTERY page shows one player: the pot, the names of everyone in (for the drum, at most
 * {@link #MAX_NAMES}), how many are in, where the player stands ({@code standing} a {@link Lottery.Standing} ordinal, -1 not
 * known yet; {@code rank} the rank leaving them out), the days, the last winners, and the draw as it is here
 * ({@code phase} a {@link LotteryDraw.Phase} ordinal, {@code phaseMs} how long ago it began, the winner and pieces once
 * drawn, {@code countdownMs} the whole countdown's length).
 */
public record LotteryPageView(long pot, List<String> names, int in, int standing, String rank, int days, List<Win> wins,
                              int phase, long phaseMs, long countdownMs, String winner, long winPieces, long now) {
    public static final int MAX_NAMES = 200, WINS = 5;
    public static final LotteryPageView EMPTY = new LotteryPageView(0, List.of(), 0, -1, "", 7, List.of(), 0, 0, 6000, "", 0, 0);

    public record Win(String name, long pieces, long ms) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(pot);
        buf.writeVarInt(Math.min(MAX_NAMES, names.size()));
        names.stream().limit(MAX_NAMES).forEach(n -> buf.writeUtf(BSPNetwork.cut(n, 16), 16));
        buf.writeVarInt(in);
        buf.writeVarInt(standing + 1);
        buf.writeUtf(BSPNetwork.cut(rank, 32), 32);
        buf.writeVarInt(days);
        buf.writeVarInt(Math.min(WINS, wins.size()));
        wins.stream().limit(WINS).forEach(w -> {
            buf.writeUtf(BSPNetwork.cut(w.name, 16), 16);
            buf.writeLong(w.pieces);
            buf.writeLong(w.ms);
        });
        buf.writeVarInt(phase);
        buf.writeLong(phaseMs);
        buf.writeLong(countdownMs);
        buf.writeUtf(BSPNetwork.cut(winner, 16), 16);
        buf.writeLong(winPieces);
        buf.writeLong(now);
    }

    public static LotteryPageView decode(FriendlyByteBuf buf) {
        long pot = buf.readLong();
        int n = Math.min(MAX_NAMES, buf.readVarInt());
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            names.add(buf.readUtf(16));
        }
        int in = buf.readVarInt(), standing = buf.readVarInt() - 1;
        String rank = buf.readUtf(32);
        int days = buf.readVarInt();
        n = Math.min(WINS, buf.readVarInt());
        List<Win> wins = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            wins.add(new Win(buf.readUtf(16), buf.readLong(), buf.readLong()));
        }
        return new LotteryPageView(pot, names, in, standing, rank, days, wins, buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readUtf(16), buf.readLong(), buf.readLong());
    }
}
