package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.CloakSnapshot;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: asks about the cloak at {@code totem} (radius {@code radius} as the client last knew it). The server answers with
 * a {@link CloakStatusPacket}, and, when this player may see the real cube (no totem or no cloak there any more, the player inside
 * it, or allowed through), sends the cube's chunks again, because the client's swap threw away its block entities. Sent every two
 * seconds while a cube is hidden and once when the blocks are put back; an outsider gets the answer but never the chunks.
 */
public record CloakRefreshPacket(BlockPos totem, int radius) {
    public static void encode(CloakRefreshPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.totem);
        buf.writeVarInt(msg.radius);
    }

    public static CloakRefreshPacket decode(FriendlyByteBuf buf) {
        return new CloakRefreshPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(CloakRefreshPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ServerLevel sl = player.serverLevel();
            boolean cloaked = false, maySee = true;
            int r = Math.max(0, Math.min(64, msg.radius));
            if (sl.isLoaded(msg.totem) && sl.getBlockEntity(msg.totem) instanceof ShatterTotemBlockEntity totem) {
                CloakSnapshot snap = totem.cloak();
                cloaked = snap != null;
                if (snap != null) {
                    r = snap.radius;
                    maySee = snap.contains(player.blockPosition()) || totem.seesThroughCloak(player);
                }
            }
            BSPNetwork.sendTo(player, new CloakStatusPacket(msg.totem, cloaked, maySee));
            if (maySee) {
                int cx0 = SectionPos.blockToSectionCoord(msg.totem.getX() - r), cx1 = SectionPos.blockToSectionCoord(msg.totem.getX() + r);
                int cz0 = SectionPos.blockToSectionCoord(msg.totem.getZ() - r), cz1 = SectionPos.blockToSectionCoord(msg.totem.getZ() + r);
                for (int cx = cx0; cx <= cx1; cx++) {
                    for (int cz = cz0; cz <= cz1; cz++) {
                        LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                        if (chunk != null) {
                            player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, sl.getLightEngine(), null, null));
                        }
                    }
                }
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
