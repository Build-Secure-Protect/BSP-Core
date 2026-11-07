package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the tickets of one store credit kind, for the CREDIT tab's ticket list (newest first, at most 200). */
public record CreditTicketsPacket(String kind, int total, List<Row> rows) {
    /** One ticket: its serial, holder ("" while unbound), credit left, first purchase and end (0 when none), voided. */
    public record Row(String serial, String holder, long left, long firstUseMs, long endsMs, boolean voided) {}

    public static final int MAX = 200;

    public static void encode(CreditTicketsPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(CreditAdminPacket.cut(m.kind, 32), 32);
        buf.writeVarInt(m.total);
        int n = Math.min(MAX, m.rows.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Row r = m.rows.get(i);
            buf.writeUtf(r.serial(), 16);
            buf.writeUtf(r.holder(), 16);
            buf.writeVarLong(Math.max(0, r.left()));
            buf.writeLong(r.firstUseMs());
            buf.writeLong(r.endsMs());
            buf.writeBoolean(r.voided());
        }
    }

    public static CreditTicketsPacket decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        int total = buf.readVarInt();
        int n = Math.min(MAX, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUtf(16), buf.readUtf(16), buf.readVarLong(), buf.readLong(), buf.readLong(), buf.readBoolean()));
        }
        return new CreditTicketsPacket(kind, total, rows);
    }

    public static void handle(CreditTicketsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveCreditTickets(msg)));
        ctx.get().setPacketHandled(true);
    }
}
