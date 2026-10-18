package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: a team invite to show as a card in the prompt HUD, or ({@code clear}) one to
 * take down because it was answered elsewhere, ran out or its team is gone.
 */
public record TeamPromptPacket(int id, boolean clear, UUID inviter, String inviterName, String team, int colour, int members, int maxMembers, String leader, int seconds) {
    public static TeamPromptPacket clear(int id) {
        return new TeamPromptPacket(id, true, new UUID(0L, 0L), "", "", 0, 0, 0, "", 0);
    }

    public static void encode(TeamPromptPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeBoolean(m.clear);
        buf.writeUUID(m.inviter);
        buf.writeUtf(m.inviterName);
        buf.writeUtf(m.team);
        buf.writeByte(m.colour);
        buf.writeVarInt(m.members);
        buf.writeVarInt(m.maxMembers);
        buf.writeUtf(m.leader);
        buf.writeVarInt(m.seconds);
    }

    public static TeamPromptPacket decode(FriendlyByteBuf buf) {
        return new TeamPromptPacket(buf.readVarInt(), buf.readBoolean(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(TeamPromptPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.PromptHud.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
