package com.mrgregles.bsp_core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: everything the admin panel's KITS tab shows: every kit as its saved tag, the
 * server ids known to the network, the rank names an admin may pick (in ladder order when RankUp
 * gives them), the players online here (for GIVE TO), and a note from the last action. {@code open}
 * turns the panel to the KITS tab ({@code /bsp admin kits}).
 */
public record KitAdminPacket(boolean open, boolean canEdit, List<CompoundTag> kits, List<String> servers, List<String> ranks, boolean ladder,
                             List<Online> online, String note) {
    public record Online(UUID id, String name) {}

    public static void encode(KitAdminPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeBoolean(msg.canEdit);
        buf.writeVarInt(msg.kits.size());
        for (CompoundTag t : msg.kits) {
            buf.writeNbt(t);
        }
        buf.writeCollection(msg.servers, (b, s) -> b.writeUtf(s, 64));
        buf.writeCollection(msg.ranks, (b, s) -> b.writeUtf(s, 64));
        buf.writeBoolean(msg.ladder);
        buf.writeVarInt(msg.online.size());
        for (Online o : msg.online) {
            buf.writeUUID(o.id);
            buf.writeUtf(o.name, 32);
        }
        buf.writeUtf(msg.note, 256);
    }

    public static KitAdminPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean(), canEdit = buf.readBoolean();
        int n = buf.readVarInt();
        List<CompoundTag> kits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag t = buf.readNbt();
            if (t != null) {
                kits.add(t);
            }
        }
        List<String> servers = buf.readList(b -> b.readUtf(64));
        List<String> ranks = buf.readList(b -> b.readUtf(64));
        boolean ladder = buf.readBoolean();
        int on = buf.readVarInt();
        List<Online> online = new ArrayList<>(on);
        for (int i = 0; i < on; i++) {
            online.add(new Online(buf.readUUID(), buf.readUtf(32)));
        }
        return new KitAdminPacket(open, canEdit, kits, servers, ranks, ladder, online, buf.readUtf(256));
    }

    public static void handle(KitAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveKits(msg));
        ctx.get().setPacketHandled(true);
    }
}
