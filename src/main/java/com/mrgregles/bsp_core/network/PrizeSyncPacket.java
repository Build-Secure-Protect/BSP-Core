package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.client.ClientScores;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the season number and its prize rows (first, second, third place), for Score Screens set to show them. */
public record PrizeSyncPacket(int season, List<ItemStack> prizes) {
    public static void encode(PrizeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.season);
        buf.writeVarInt(msg.prizes.size());
        msg.prizes.forEach(buf::writeItem);
    }

    public static PrizeSyncPacket decode(FriendlyByteBuf buf) {
        int season = buf.readVarInt(), n = Math.min(64, buf.readVarInt());
        List<ItemStack> prizes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            prizes.add(buf.readItem());
        }
        return new PrizeSyncPacket(season, prizes);
    }

    public static void handle(PrizeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientScores.acceptPrizes(msg.season, msg.prizes));
        ctx.get().setPacketHandled(true);
    }
}
