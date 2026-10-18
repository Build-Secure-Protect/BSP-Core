package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client to server: what the player did on the Team screen (or pressed the Team key). The server answers with a {@link TeamViewPacket}. */
public record TeamActionPacket(Action action, String text, UUID target, int value) {
    public enum Action { VIEW, REFRESH, CREATE, INVITE, ACCEPT, DECLINE, LEAVE, KICK, TRANSFER, RENAME, RECOLOUR, DISBAND, PIN, POST, MARK_READ, CHAT_TOGGLE, TELEPORT }

    private static final UUID NONE = new UUID(0L, 0L);

    public static TeamActionPacket of(Action action) {
        return new TeamActionPacket(action, "", NONE, 0);
    }

    public static TeamActionPacket text(Action action, String text, int value) {
        return new TeamActionPacket(action, text, NONE, value);
    }

    public static TeamActionPacket target(Action action, UUID target) {
        return new TeamActionPacket(action, "", target, 0);
    }

    public static void encode(TeamActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.text, 512);
        buf.writeUUID(m.target);
        buf.writeVarInt(m.value);
    }

    public static TeamActionPacket decode(FriendlyByteBuf buf) {
        return new TeamActionPacket(buf.readEnum(Action.class), buf.readUtf(512), buf.readUUID(), buf.readVarInt());
    }

    public static void handle(TeamActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            com.mrgregles.bsp_core.team.TeamActions.apply(player, msg);
        }
        ctx.get().setPacketHandled(true);
    }
}
