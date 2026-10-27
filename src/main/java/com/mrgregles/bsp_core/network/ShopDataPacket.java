package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: one page of a shop as the player may see it now, with their balance and an outcome message. */
public record ShopDataPacket(String name, String path, Shop.Kind kind, boolean buyOnly, boolean open, boolean admin, int offerPercent, long offerEnd, long rotatesIn,
                             int page, int pages, int columns, int rows, long balance, String opensAt, int untilOpen, List<ShopMenu.Card> cards, String messageKey, String[] messageArgs, boolean searchable) {
    public static void encode(ShopDataPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.name, 64);
        buf.writeUtf(m.path, 128);
        buf.writeEnum(m.kind);
        buf.writeBoolean(m.buyOnly);
        buf.writeBoolean(m.open);
        buf.writeBoolean(m.admin);
        buf.writeVarInt(m.offerPercent);
        buf.writeLong(m.offerEnd);
        buf.writeLong(m.rotatesIn);
        buf.writeVarInt(m.page);
        buf.writeVarInt(m.pages);
        buf.writeVarInt(m.columns);
        buf.writeVarInt(m.rows);
        buf.writeLong(m.balance);
        buf.writeUtf(m.opensAt, 32);
        buf.writeVarInt(m.untilOpen);
        buf.writeVarInt(m.cards.size());
        for (ShopMenu.Card c : m.cards) {
            buf.writeVarInt(c.index());
            buf.writeItem(c.stack());
            buf.writeLong(c.buy());
            buf.writeLong(c.sell());
            buf.writeVarInt(c.currency() + 1);
            c.stock().write(buf);
            c.selling().write(buf);
            buf.writeBoolean(c.instant());
            buf.writeUtf(c.section(), 32);
            buf.writeUtf(c.sectionName(), 64);
            buf.writeBoolean(c.anyData());
            buf.writeVarInt(c.note().size());
            c.note().forEach(l -> buf.writeUtf(l, 128));
            buf.writeBoolean(c.lock() != null);
            if (c.lock() != null) {
                buf.writeBoolean(c.lock().permission());
                buf.writeUtf(c.lock().node(), 128);
                buf.writeVarInt(c.lock().hours());
                buf.writeVarLong(c.lock().playedMinutes());
                buf.writeUtf(c.lock().reason(), 64);
            }
        }
        buf.writeBoolean(m.searchable);
        buf.writeUtf(m.messageKey, 64);
        buf.writeVarInt(m.messageArgs.length);
        for (String s : m.messageArgs) {
            buf.writeUtf(s, 64);
        }
    }

    public static ShopDataPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(64), path = buf.readUtf(128);
        Shop.Kind kind = buf.readEnum(Shop.Kind.class);
        boolean buyOnly = buf.readBoolean(), open = buf.readBoolean(), admin = buf.readBoolean();
        int offer = buf.readVarInt();
        long offerEnd = buf.readLong(), rotatesIn = buf.readLong();
        int page = buf.readVarInt(), pages = buf.readVarInt(), columns = buf.readVarInt(), rows = buf.readVarInt();
        long balance = buf.readLong();
        String opensAt = buf.readUtf(32);
        int until = buf.readVarInt();
        int n = buf.readVarInt();
        List<ShopMenu.Card> cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cards.add(new ShopMenu.Card(buf.readVarInt(), buf.readItem(), buf.readLong(), buf.readLong(), buf.readVarInt() - 1, ShopMenu.Limit.read(buf), ShopMenu.Limit.read(buf), buf.readBoolean(), buf.readUtf(32), buf.readUtf(64), buf.readBoolean(), readNote(buf),
                    buf.readBoolean() ? new com.mrgregles.bsp_core.shop.Requirement.Lock(buf.readBoolean(), buf.readUtf(128), buf.readVarInt(), buf.readVarLong(), buf.readUtf(64), false) : null));
        }
        boolean searchable = buf.readBoolean();
        String key = buf.readUtf(64);
        String[] args = new String[buf.readVarInt()];
        for (int i = 0; i < args.length; i++) {
            args[i] = buf.readUtf(64);
        }
        return new ShopDataPacket(name, path, kind, buyOnly, open, admin, offer, offerEnd, rotatesIn, page, pages, columns, rows, balance, opensAt, until, cards, key, args, searchable);
    }

    private static List<String> readNote(FriendlyByteBuf buf) {
        int n = Math.min(com.mrgregles.bsp_core.shop.ShopItem.NOTE_LINES, buf.readVarInt());
        List<String> note = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            note.add(buf.readUtf(128));
        }
        return note;
    }

    public static void handle(ShopDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ShopScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
