package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.OrderSummaryPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every client knows of the buy orders, for the Wanted board on Score Screens: the open orders still wanting something
 * (at most {@link #CAP}), best pay first, each its plain item, how many it still wants of how many, the price each and the
 * buyer. Checked once a second from {@link MarketEvents} and sent to everyone when it changed (a placing, a fill, a cancel,
 * an expiry, another server's change read from the network), and to each player at login.
 */
public final class OrderSummary {
    public static final int CAP = 60;

    public record Entry(ItemStack item, int toGo, int wanted, long each, String buyer) {}

    /** The orders last sent, as ids and fills; null until the first send. */
    @Nullable
    private static String sent;

    private OrderSummary() {}

    static List<BuyOrder> shown(MinecraftServer server) {
        long now = Market.now();
        List<BuyOrder> out = new ArrayList<>(BuyOrders.open(server).stream().filter(o -> !o.endedBy(now) && o.toGo() > 0).toList());
        out.sort(Comparator.comparingLong((BuyOrder o) -> o.each).reversed().thenComparing(Comparator.comparingLong((BuyOrder o) -> o.placedMs).reversed()).thenComparing(o -> o.id));
        return out.size() > CAP ? out.subList(0, CAP) : out;
    }

    public static List<Entry> build(MinecraftServer server) {
        List<Entry> out = new ArrayList<>();
        for (BuyOrder o : shown(server)) {
            out.add(new Entry(o.item(), o.toGo(), o.wanted, o.each, o.buyerName));
        }
        return out;
    }

    private static String key(List<BuyOrder> orders) {
        StringBuilder b = new StringBuilder();
        for (BuyOrder o : orders) {
            b.append(o.id).append(':').append(o.filled).append(',');
        }
        return b.toString();
    }

    /** Sends the summary to every player when the orders shown changed since the last send; true if it sent. Cheap when nothing changed. */
    public static boolean tick(MinecraftServer server) {
        String now = key(shown(server));
        if (now.equals(sent)) {
            return false;
        }
        sent = now;
        OrderSummaryPacket packet = new OrderSummaryPacket(build(server));
        if (BSPNetwork.sizeOf(packet, OrderSummaryPacket::encode) >= BSPNetwork.MAX_PAYLOAD) {
            BSPCore.LOGGER.error("BSP-Core orders: the Wanted board summary is too big to send; not sent");
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BSPNetwork.sendTo(p, packet);
        }
        return true;
    }

    public static void sendTo(ServerPlayer player) {
        BSPNetwork.sendChecked(player, new OrderSummaryPacket(build(player.server)), OrderSummaryPacket::encode, "the Wanted board summary");
    }

    /** The server stopped: the next one starts by sending. */
    public static void forget() {
        sent = null;
    }
}
