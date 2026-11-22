package com.mrgregles.bsp_core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the admin panel's CREDIT tab (Store Credit task 10): every kind (its saved form) with how many tickets of it
 * were given, the shops (for the places list), the plot names sold on the network (or kept in a kind) with how many plots share
 * each and the servers they are on, and the answer to the last action. {@code answer} marks the reply to this admin's SAVE or DELETE
 * (the form takes the saved copy only from that), not a REQUEST's or a VOID's.
 */
public record CreditAdminPacket(List<CompoundTag> kinds, List<Integer> given, List<Shop> shops, List<PlotName> plots, String message, boolean ok, boolean answer) {
    public record Shop(String id, String name, String parent) {}

    /** A plot name with how many plots share it and, on a network, the servers they are on (empty otherwise, or for a name nothing sells). */
    public record PlotName(String name, int count, List<String> servers) {}

    public static final int MAX_KINDS = 200, MAX_SHOPS = 500, MAX_PLOTS = 500, MAX_SERVERS = 16;

    public static void encode(CreditAdminPacket m, FriendlyByteBuf buf) {
        int k = Math.min(MAX_KINDS, m.kinds.size());
        buf.writeVarInt(k);
        for (int i = 0; i < k; i++) {
            buf.writeNbt(m.kinds.get(i));
            buf.writeVarInt(i < m.given.size() ? m.given.get(i) : 0);
        }
        int s = Math.min(MAX_SHOPS, m.shops.size());
        buf.writeVarInt(s);
        for (int i = 0; i < s; i++) {
            buf.writeUtf(cut(m.shops.get(i).id(), 64), 64);
            buf.writeUtf(cut(m.shops.get(i).name(), 64), 64);
            buf.writeUtf(cut(m.shops.get(i).parent(), 64), 64);
        }
        int p = Math.min(MAX_PLOTS, m.plots.size());
        buf.writeVarInt(p);
        for (int i = 0; i < p; i++) {
            buf.writeUtf(cut(m.plots.get(i).name(), 64), 64);
            buf.writeVarInt(m.plots.get(i).count());
            List<String> servers = m.plots.get(i).servers();
            // more servers than fit: the last place says how many more ("+3")
            int n = Math.min(MAX_SERVERS, servers.size()), shown = servers.size() > MAX_SERVERS ? MAX_SERVERS - 1 : n;
            buf.writeVarInt(n);
            for (int j = 0; j < shown; j++) {
                buf.writeUtf(cut(servers.get(j), 32), 32);
            }
            if (shown < n) {
                buf.writeUtf("+" + (servers.size() - shown), 32);
            }
        }
        buf.writeUtf(cut(m.message, 256), 256);
        buf.writeBoolean(m.ok);
        buf.writeBoolean(m.answer);
    }

    /** Long text is cut rather than failing the whole packet (which would disconnect the admin). */
    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static CreditAdminPacket decode(FriendlyByteBuf buf) {
        int k = Math.min(MAX_KINDS, buf.readVarInt());
        List<CompoundTag> kinds = new ArrayList<>(k);
        List<Integer> given = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            CompoundTag t = buf.readNbt();
            kinds.add(t == null ? new CompoundTag() : t);
            given.add(buf.readVarInt());
        }
        int s = Math.min(MAX_SHOPS, buf.readVarInt());
        List<Shop> shops = new ArrayList<>(s);
        for (int i = 0; i < s; i++) {
            shops.add(new Shop(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64)));
        }
        int p = Math.min(MAX_PLOTS, buf.readVarInt());
        List<PlotName> plots = new ArrayList<>(p);
        for (int i = 0; i < p; i++) {
            String name = buf.readUtf(64);
            int count = buf.readVarInt();
            int n = Math.min(MAX_SERVERS, buf.readVarInt());
            List<String> servers = new ArrayList<>(n);
            for (int j = 0; j < n; j++) {
                servers.add(buf.readUtf(32));
            }
            plots.add(new PlotName(name, count, List.copyOf(servers)));
        }
        String message = buf.readUtf(256);
        boolean ok = buf.readBoolean();
        return new CreditAdminPacket(kinds, given, shops, plots, message, ok, buf.readBoolean());
    }

    public static void handle(CreditAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveCredit(msg)));
        ctx.get().setPacketHandled(true);
    }
}
