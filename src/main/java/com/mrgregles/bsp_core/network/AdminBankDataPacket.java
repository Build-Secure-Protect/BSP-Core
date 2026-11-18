package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.api.BspMarket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: one player's Bank &amp; Market page for the admin panel: their pieces and the bank's coins, their bank log (newest
 * first, at most {@link com.mrgregles.bsp_core.admin.AdminBank#MAX_ROWS} rows), their market lots and requests, whether the
 * viewer may act ({@code canEdit}), whether the bank was reached, and the answer to the last ask ({@code result}: "" for none,
 * "ok", or why not).
 */
public record AdminBankDataPacket(UUID player, String name, boolean online, boolean canEdit, boolean reached, long pieces, int[] coins,
                                  List<BspBank.Row> rows, List<BspMarket.Lot> lots, List<BspMarket.Order> orders, String result) {
    private static final int MAX_ROWS = 600, MAX_ENTRIES = 64, MAX_STACKS = 64;

    public static void encode(AdminBankDataPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeUtf(BSPNetwork.cut(msg.name, 64), 64);
        buf.writeBoolean(msg.online);
        buf.writeBoolean(msg.canEdit);
        buf.writeBoolean(msg.reached);
        buf.writeLong(msg.pieces);
        buf.writeVarIntArray(msg.coins);
        int rows = Math.min(msg.rows.size(), MAX_ROWS);
        buf.writeVarInt(rows);
        for (int i = 0; i < rows; i++) {
            BspBank.Row r = msg.rows.get(i);
            buf.writeLong(r.id());
            buf.writeLong(r.ms());
            buf.writeUtf(BSPNetwork.cut(r.type(), 16), 16);
            buf.writeUtf(BSPNetwork.cut(r.with(), 64), 64);
            buf.writeUtf(BSPNetwork.cut(r.detail(), 255), 255);
            buf.writeLong(r.pieces());
            buf.writeVarInt(r.coinTier() + 1);
            buf.writeInt(r.coinCount());
            buf.writeUtf(BSPNetwork.cut(r.serverId(), 64), 64);
            buf.writeLong(r.refundedPieces());
            buf.writeVarInt(r.refundedCoins());
            buf.writeLong(r.refundOf());
        }
        int lots = Math.min(msg.lots.size(), MAX_ENTRIES);
        buf.writeVarInt(lots);
        for (int i = 0; i < lots; i++) {
            BspMarket.Lot l = msg.lots.get(i);
            buf.writeUtf(BSPNetwork.cut(l.id(), 32), 32);
            buf.writeLong(l.price());
            buf.writeLong(l.listedMs());
            buf.writeLong(l.endsMs());
            buf.writeUtf(BSPNetwork.cut(l.serverId(), 64), 64);
            int n = Math.min(l.items().size(), MAX_STACKS);
            buf.writeVarInt(n);
            for (int k = 0; k < n; k++) {
                buf.writeItem(l.items().get(k));
            }
        }
        int orders = Math.min(msg.orders.size(), MAX_ENTRIES);
        buf.writeVarInt(orders);
        for (int i = 0; i < orders; i++) {
            BspMarket.Order o = msg.orders.get(i);
            buf.writeUtf(BSPNetwork.cut(o.id(), 32), 32);
            buf.writeItem(o.item());
            buf.writeVarInt(o.wanted());
            buf.writeVarInt(o.filled());
            buf.writeLong(o.each());
            buf.writeLong(o.placedMs());
            buf.writeLong(o.endsMs());
            buf.writeUtf(BSPNetwork.cut(o.serverId(), 64), 64);
        }
        buf.writeUtf(BSPNetwork.cut(msg.result, 32), 32);
    }

    public static AdminBankDataPacket decode(FriendlyByteBuf buf) {
        UUID player = buf.readUUID();
        String name = buf.readUtf(64);
        boolean online = buf.readBoolean(), canEdit = buf.readBoolean(), reached = buf.readBoolean();
        long pieces = buf.readLong();
        int[] coins = buf.readVarIntArray(16);
        int rn = Math.min(buf.readVarInt(), MAX_ROWS);
        List<BspBank.Row> rows = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) {
            rows.add(new BspBank.Row(buf.readLong(), buf.readLong(), buf.readUtf(16), buf.readUtf(64), buf.readUtf(255), buf.readLong(), buf.readVarInt() - 1, buf.readInt(),
                    buf.readUtf(64), buf.readLong(), buf.readVarInt(), buf.readLong()));
        }
        int ln = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<BspMarket.Lot> lots = new ArrayList<>(ln);
        for (int i = 0; i < ln; i++) {
            String id = buf.readUtf(32);
            long price = buf.readLong(), listed = buf.readLong(), ends = buf.readLong();
            String server = buf.readUtf(64);
            int n = Math.min(buf.readVarInt(), MAX_STACKS);
            List<ItemStack> items = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                items.add(buf.readItem());
            }
            lots.add(new BspMarket.Lot(id, player, name, price, listed, ends, server, items));
        }
        int on = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<BspMarket.Order> orders = new ArrayList<>(on);
        for (int i = 0; i < on; i++) {
            orders.add(new BspMarket.Order(buf.readUtf(32), player, name, buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readLong(), buf.readUtf(64)));
        }
        return new AdminBankDataPacket(player, name, online, canEdit, reached, pieces, coins, rows, lots, orders, buf.readUtf(32));
    }

    public static void handle(AdminBankDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.AdminBankClient.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
