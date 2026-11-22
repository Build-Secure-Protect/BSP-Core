package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.score.VipList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the server's VIPs, sorted by name, for the Score Screen's VIPs board. Sent at login and whenever the list changes. */
public record VipListPacket(List<VipList.Vip> vips) {
    /** At most this many VIPs are sent (a name is at most 16 characters, so this stays far under a packet's limit). */
    public static final int MAX = 1000;
    private static final int NAME_MAX = 32;

    public static VipListPacket of(List<VipList.Vip> vips) {
        return new VipListPacket(List.copyOf(vips.subList(0, Math.min(MAX, vips.size()))));
    }

    public static void encode(VipListPacket msg, FriendlyByteBuf buf) {
        int n = Math.min(MAX, msg.vips.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            VipList.Vip v = msg.vips.get(i);
            buf.writeUUID(v.id());
            buf.writeUtf(BSPNetwork.cut(v.name(), NAME_MAX), NAME_MAX);
        }
    }

    public static VipListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) {
            throw new IllegalArgumentException("VIP list of " + n);
        }
        List<VipList.Vip> vips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            vips.add(new VipList.Vip(buf.readUUID(), buf.readUtf(NAME_MAX)));
        }
        return new VipListPacket(vips);
    }

    public static void handle(VipListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientVips.accept(msg.vips));
        ctx.get().setPacketHandled(true);
    }
}
