package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.BankMenu;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the player's account and log for the open banking screen, with an outcome message and name search results. */
public record BankDataPacket(BankAccount account, List<BankTransaction> log, String messageKey, String[] messageArgs, List<String> found) {
    private static final int MAX_LOG = 200;

    public static void encode(BankDataPacket msg, FriendlyByteBuf buf) {
        BankAccount a = msg.account;
        buf.writeLong(a.pieces);
        for (int c : a.coins) {
            buf.writeVarInt(c);
        }
        buf.writeBoolean(a.acceptPay);
        buf.writeBoolean(a.remoteShops);
        buf.writeBoolean(a.remoteMarket);
        buf.writeUtf(a.name, 16);
        buf.writeVarInt(a.boxSlots);
        buf.writeLong(a.boxUntilMs);
        int n = Math.min(MAX_LOG, msg.log.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            BankTransaction t = msg.log.get(i);
            buf.writeLong(t.ms());
            buf.writeEnum(t.type());
            buf.writeUtf(t.with(), 64);
            buf.writeUtf(t.detail(), 255);
            buf.writeLong(t.pieces());
            buf.writeVarInt(t.coinTier());
            buf.writeVarInt(t.coinCount());
            buf.writeUtf(t.server(), 64);
        }
        buf.writeUtf(msg.messageKey, 64);
        buf.writeVarInt(msg.messageArgs.length);
        for (String s : msg.messageArgs) {
            buf.writeUtf(s, 64);
        }
        buf.writeVarInt(msg.found.size());
        for (String s : msg.found) {
            buf.writeUtf(s, 16);
        }
    }

    public static BankDataPacket decode(FriendlyByteBuf buf) {
        BankAccount a = new BankAccount();
        a.pieces = buf.readLong();
        for (int i = 0; i < a.coins.length; i++) {
            a.coins[i] = buf.readVarInt();
        }
        a.acceptPay = buf.readBoolean();
        a.remoteShops = buf.readBoolean();
        a.remoteMarket = buf.readBoolean();
        a.name = buf.readUtf(16);
        a.boxSlots = buf.readVarInt();
        a.boxUntilMs = buf.readLong();
        int n = buf.readVarInt();
        List<BankTransaction> log = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            log.add(new BankTransaction(buf.readLong(), buf.readEnum(BankTransaction.Type.class), buf.readUtf(64), buf.readUtf(255), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));
        }
        String key = buf.readUtf(64);
        String[] args = new String[buf.readVarInt()];
        for (int i = 0; i < args.length; i++) {
            args[i] = buf.readUtf(64);
        }
        int f = buf.readVarInt();
        List<String> found = new ArrayList<>(f);
        for (int i = 0; i < f; i++) {
            found.add(buf.readUtf(16));
        }
        return new BankDataPacket(a, log, key, args, found);
    }

    public static void handle(BankDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.BankScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
