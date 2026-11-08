package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: an action on the admin panel's RADIATION tab. {@code save} false asks for the tab's data; true sends every
 * setting on it, with {@code setProtection} true only when the admin changed the protection (so saving this server's numbers never
 * overwrites a protection another admin set meanwhile). The server checks the sender: moderators may look, only admins may save.
 */
public record RadiationEditPacket(boolean save, boolean setProtection, int mode, String rank, int hours, boolean enabled, int fullLevel, int maxSeen,
                                  int blurPct, int cutPct, int maxLayers, int radius, int seconds) {
    public static RadiationEditPacket request() {
        return new RadiationEditPacket(false, false, 0, "", 0, true, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void encode(RadiationEditPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.save);
        buf.writeBoolean(m.setProtection);
        buf.writeVarInt(m.mode);
        buf.writeUtf(m.rank, 64);
        buf.writeVarInt(m.hours);
        buf.writeBoolean(m.enabled);
        buf.writeVarInt(m.fullLevel);
        buf.writeVarInt(m.maxSeen);
        buf.writeVarInt(m.blurPct);
        buf.writeVarInt(m.cutPct);
        buf.writeVarInt(m.maxLayers);
        buf.writeVarInt(m.radius);
        buf.writeVarInt(m.seconds);
    }

    public static RadiationEditPacket decode(FriendlyByteBuf buf) {
        return new RadiationEditPacket(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RadiationEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) {
                com.mrgregles.bsp_core.totem.RadiationAdmin.act(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
