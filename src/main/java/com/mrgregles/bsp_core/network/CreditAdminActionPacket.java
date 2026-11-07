package com.mrgregles.bsp_core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: an action on the CREDIT tab. {@code REQUEST} asks for the tab's data; {@code SAVE} saves {@code kind} (a
 * kind's saved form); {@code DELETE} and {@code TICKETS} name a kind in {@code id}; {@code VOID} a ticket's serial. The server
 * checks every action against the sender being a BSP-Core admin or an operator.
 */
public record CreditAdminActionPacket(Action action, String id, CompoundTag kind) {
    public enum Action { REQUEST, SAVE, DELETE, TICKETS, VOID }

    public static void encode(CreditAdminActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.id, 64);
        buf.writeNbt(m.kind);
    }

    public static CreditAdminActionPacket decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        String id = buf.readUtf(64);
        CompoundTag t = buf.readNbt();
        return new CreditAdminActionPacket(a, id, t == null ? new CompoundTag() : t);
    }

    public static void handle(CreditAdminActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) {
                com.mrgregles.bsp_core.credit.CreditAdmin.act(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
