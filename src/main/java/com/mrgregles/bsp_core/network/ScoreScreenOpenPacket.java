package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: open the settings of the Score Screen whose bottom-left panel is at {@code origin}. Only sent to admins. */
public record ScoreScreenOpenPacket(BlockPos origin) {
    public static void encode(ScoreScreenOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.origin);
    }

    public static ScoreScreenOpenPacket decode(FriendlyByteBuf buf) {
        return new ScoreScreenOpenPacket(buf.readBlockPos());
    }

    public static void handle(ScoreScreenOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ScoreScreenConfigScreen.open(msg.origin));
        ctx.get().setPacketHandled(true);
    }
}
