package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.MarketSummaryPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every client knows of the market, for the Market Board on Score Screens: the newest open lots (at most {@link #CAP}),
 * each its first stack as a picture ({@link ItemPicture}), how many stacks, the price, when it was listed and by whom.
 * Checked once a second from {@link MarketEvents} and sent to everyone when it changed (a listing, a sale, a cancel, an
 * expiry, another server's change read from the network), and to each player at login.
 */
public final class MarketSummary {
    public static final int CAP = 60;

    public record Entry(ItemStack first, int stacks, long price, long listedMs, String seller) {}

    /** The ids of the lots last sent, newest first; null until the first send. */
    @Nullable
    private static String sent;

    private MarketSummary() {}

    /** The open lots still running, newest first, at most {@link #CAP}. */
    static List<MarketLot> newest(MinecraftServer server) {
        long now = Market.now();
        List<MarketLot> out = new ArrayList<>(Market.open(server).stream().filter(l -> !l.endedBy(now)).toList());
        out.sort(Comparator.comparingLong((MarketLot l) -> l.listedMs).reversed().thenComparing(l -> l.id));
        return out.size() > CAP ? out.subList(0, CAP) : out;
    }

    public static List<Entry> build(MinecraftServer server) {
        List<Entry> out = new ArrayList<>();
        for (MarketLot l : newest(server)) {
            List<ItemStack> pictures = l.pictures();
            out.add(new Entry(pictures.isEmpty() ? ItemStack.EMPTY : pictures.get(0), pictures.size(), l.price, l.listedMs, l.sellerName));
        }
        return out;
    }

    private static String key(List<MarketLot> lots) {
        StringBuilder b = new StringBuilder();
        for (MarketLot l : lots) {
            b.append(l.id).append(',');
        }
        return b.toString();
    }

    /** Sends the summary to every player when the newest lots changed since the last send; true if it sent. Cheap when nothing changed. */
    public static boolean tick(MinecraftServer server) {
        String now = key(newest(server));
        if (now.equals(sent)) {
            return false;
        }
        sent = now;
        MarketSummaryPacket packet = new MarketSummaryPacket(Market.now(), build(server));
        if (BSPNetwork.sizeOf(packet, MarketSummaryPacket::encode) >= BSPNetwork.MAX_PAYLOAD) {
            BSPCore.LOGGER.error("BSP-Core market: the Market Board summary is too big to send; not sent");
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BSPNetwork.sendTo(p, packet);
        }
        return true;
    }

    public static void sendTo(ServerPlayer player) {
        BSPNetwork.sendChecked(player, new MarketSummaryPacket(Market.now(), build(player.server)), MarketSummaryPacket::encode, "the Market Board summary");
    }

    /** The server stopped: the next one starts by sending. */
    public static void forget() {
        sent = null;
    }
}
