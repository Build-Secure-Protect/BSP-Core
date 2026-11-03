package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: a card for the prompt HUD, or ({@code clear}) one to take down because it was answered elsewhere, ran out or
 * its team is gone. {@code kind} says which card:
 * <ul>
 * <li>{@link #TEAM_INVITE}: {@code inviterName} invites the player to {@code team} ({@code members} of {@code maxMembers}, led by {@code leader});</li>
 * <li>{@link #ALLY_INVITE}: {@code inviterName} of {@code team} asks the player's team into the alliance {@code leader}
 * ({@code members} of {@code maxMembers} teams);</li>
 * <li>{@link #ALLY_BROKEN}: {@code inviterName} of {@code team} started stealing {@code leader}'s totem, breaking the alliance {@code extra}.
 * Nothing to answer.</li>
 * </ul>
 */
public record TeamPromptPacket(int id, boolean clear, int kind, UUID inviter, String inviterName, String team, int colour, int members, int maxMembers, String leader,
                               String extra, int seconds) {
    public static final int TEAM_INVITE = 0, ALLY_INVITE = 1, ALLY_BROKEN = 2;

    public static TeamPromptPacket clear(int id) {
        return new TeamPromptPacket(id, true, TEAM_INVITE, new UUID(0L, 0L), "", "", 0, 0, 0, "", "", 0);
    }

    /** Whether the accept and decline keys answer this card. */
    public boolean answerable() {
        return kind != ALLY_BROKEN;
    }

    public static void encode(TeamPromptPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeBoolean(m.clear);
        buf.writeByte(m.kind);
        buf.writeUUID(m.inviter);
        buf.writeUtf(m.inviterName);
        buf.writeUtf(m.team);
        buf.writeByte(m.colour);
        buf.writeVarInt(m.members);
        buf.writeVarInt(m.maxMembers);
        buf.writeUtf(m.leader);
        buf.writeUtf(m.extra);
        buf.writeVarInt(m.seconds);
    }

    public static TeamPromptPacket decode(FriendlyByteBuf buf) {
        return new TeamPromptPacket(buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(TeamPromptPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.PromptHud.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
