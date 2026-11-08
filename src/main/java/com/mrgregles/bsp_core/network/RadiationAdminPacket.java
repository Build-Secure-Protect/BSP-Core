package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the admin panel's RADIATION tab. The new-player protection (mode, the RankUp group, the hours, RankUp's ladder
 * lowest first, how many owners of totems placed here are protected now) and this server's totem radiation settings, with the
 * answer to the last action.
 */
public record RadiationAdminPacket(int mode, String rank, int hours, List<String> ladder, int protectedNow, boolean enabled, int fullLevel,
                                   int maxSeen, double exponent, int blurPct, int cutPct, int maxLayers, int radius, int seconds, String message, boolean ok) {
    public static final int MAX_RUNGS = 64;

    public static void encode(RadiationAdminPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.mode);
        buf.writeUtf(m.rank, 64);
        buf.writeVarInt(m.hours);
        int n = Math.min(MAX_RUNGS, m.ladder.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(m.ladder.get(i), 64);
        }
        buf.writeVarInt(m.protectedNow);
        buf.writeBoolean(m.enabled);
        buf.writeVarInt(m.fullLevel);
        buf.writeVarInt(m.maxSeen);
        buf.writeDouble(m.exponent);
        buf.writeVarInt(m.blurPct);
        buf.writeVarInt(m.cutPct);
        buf.writeVarInt(m.maxLayers);
        buf.writeVarInt(m.radius);
        buf.writeVarInt(m.seconds);
        buf.writeUtf(m.message, 256);
        buf.writeBoolean(m.ok);
    }

    public static RadiationAdminPacket decode(FriendlyByteBuf buf) {
        int mode = buf.readVarInt();
        String rank = buf.readUtf(64);
        int hours = buf.readVarInt();
        int n = Math.min(MAX_RUNGS, buf.readVarInt());
        List<String> ladder = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ladder.add(buf.readUtf(64));
        }
        return new RadiationAdminPacket(mode, rank, hours, ladder, buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(RadiationAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveRadiation(msg)));
        ctx.get().setPacketHandled(true);
    }
}
