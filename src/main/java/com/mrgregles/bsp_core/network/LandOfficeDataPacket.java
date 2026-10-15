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

/** Server → client: the Land Office as it stands: listings, the player's balance, whether Region Guard is there and claims are allowed here, the outlined ground, and a message. */
public record LandOfficeDataPacket(List<Plot> plots, long balance, boolean present, boolean allowed, @Nullable int[] selection, String message, boolean ok) {
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
        return new LandOfficeDataPacket(plots, balance, present, allowed, selection, buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(LandOfficeDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.LandOfficeScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
