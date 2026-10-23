package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the Land Office as it stands: listings, the player's balance, whether Region Guard is there and claims are
 * allowed here, the outlined ground, a message, and, when Region Guard prices claims in Shatter Coins, the coin wording.
 */
public record LandOfficeDataPacket(List<Plot> plots, long balance, boolean present, boolean allowed, @Nullable int[] selection, String message, boolean ok,
                                   @Nullable CoinTexts coins) {
    /**
     * Region Guard prices in Shatter Coins (BSP Region Guard request RG-C9): the coins the player carries, and per plot (same order
     * as {@code plots}) its price, its cost per block and the quote for the outlined ground, as Region Guard words them ("" where none).
     */
    public record CoinTexts(String carried, List<String> price, List<String> perBlock, List<String> outline) {}

    public LandOfficeDataPacket(List<Plot> plots, long balance, boolean present, boolean allowed, @Nullable int[] selection, String message, boolean ok) {
        this(plots, balance, present, allowed, selection, message, ok, null);
    }

    public static void encode(LandOfficeDataPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.plots.size());
        for (Plot p : m.plots) {
            buf.writeUtf(p.id(), 64);
            buf.writeUtf(p.name(), 64);
            buf.writeInt(p.minX());
            buf.writeInt(p.minZ());
            buf.writeInt(p.maxX());
            buf.writeInt(p.maxZ());
            buf.writeLong(p.price());
            buf.writeLong(p.costPerBlock());
            buf.writeVarInt(p.minSize());
            buf.writeVarInt(p.maxSize());
            buf.writeByte(p.kind());
            buf.writeUtf(p.owner(), 64);
        }
        buf.writeLong(m.balance);
        buf.writeBoolean(m.present);
        buf.writeBoolean(m.allowed);
        buf.writeBoolean(m.selection != null);
        if (m.selection != null) {
            for (int v : m.selection) {
                buf.writeInt(v);
            }
        }
        buf.writeUtf(m.message, 256);
        buf.writeBoolean(m.ok);
        buf.writeBoolean(m.coins != null);
        if (m.coins != null) {
            buf.writeUtf(m.coins.carried(), 128);
            for (int i = 0; i < m.plots.size(); i++) {
                buf.writeUtf(i < m.coins.price().size() ? m.coins.price().get(i) : "", 64);
                buf.writeUtf(i < m.coins.perBlock().size() ? m.coins.perBlock().get(i) : "", 64);
                buf.writeUtf(i < m.coins.outline().size() ? m.coins.outline().get(i) : "", 64);
            }
        }
    }

    public static LandOfficeDataPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        List<Plot> plots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            plots.add(new Plot(buf.readUtf(64), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readUtf(64)));
        }
        long balance = buf.readLong();
        boolean present = buf.readBoolean(), allowed = buf.readBoolean();
        int[] selection = null;
        if (buf.readBoolean()) {
            selection = new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()};
        }
        String message = buf.readUtf(256);
        boolean ok = buf.readBoolean();
        CoinTexts coins = null;
        if (buf.readBoolean()) {
            String carried = buf.readUtf(128);
            List<String> price = new ArrayList<>(n), perBlock = new ArrayList<>(n), outline = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                price.add(buf.readUtf(64));
                perBlock.add(buf.readUtf(64));
                outline.add(buf.readUtf(64));
            }
            coins = new CoinTexts(carried, price, perBlock, outline);
        }
        return new LandOfficeDataPacket(plots, balance, present, allowed, selection, message, ok, coins);
    }

    public static void handle(LandOfficeDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.LandOfficeScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
