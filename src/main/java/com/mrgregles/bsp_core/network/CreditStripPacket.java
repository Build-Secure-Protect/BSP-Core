package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the store credit the player can spend on the screen they have open (Store Credit task 8), sent with every
 * shop page and every Land Office update. One {@link Summary} per place: {@code "shop"} on a shop; {@code "ground"} (open ground
 * and enlarging) and {@code "plot:<name in lower case>"} at the Land Office. {@code wants} is the PAY WITH CREDIT switch.
 */
public record CreditStripPacket(boolean wants, List<Summary> places) {
    /** What a place's credit is: the total, and the ticket that pays first (its kind's name and look, and its time). */
    public record Summary(String key, long total, String name, String colour, String mark, long endsMs, long durationMs) {}

    public static final int MAX = 256;

    public static void encode(CreditStripPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.wants);
        buf.writeVarInt(Math.min(MAX, m.places.size()));
        for (int i = 0; i < Math.min(MAX, m.places.size()); i++) {
            Summary s = m.places.get(i);
            buf.writeUtf(CreditAdminPacket.cut(s.key(), 80), 80);
            buf.writeVarLong(Math.max(0, s.total()));
            buf.writeUtf(CreditAdminPacket.cut(s.name(), 64), 64);
            buf.writeUtf(s.colour(), 16);
            buf.writeUtf(s.mark(), 1);
            buf.writeLong(s.endsMs());
            buf.writeVarLong(s.durationMs());
        }
    }

    public static CreditStripPacket decode(FriendlyByteBuf buf) {
        boolean wants = buf.readBoolean();
        int n = Math.min(MAX, buf.readVarInt());
        List<Summary> places = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            places.add(new Summary(buf.readUtf(80), buf.readVarLong(), buf.readUtf(64), buf.readUtf(16), buf.readUtf(1), buf.readLong(), buf.readVarLong()));
        }
        return new CreditStripPacket(wants, places);
    }

    public static void handle(CreditStripPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientCredit.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
