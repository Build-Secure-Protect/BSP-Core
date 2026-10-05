package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: open the settings of the Anti Totem block at {@code pos}. Only sent to admins. */
public record ZoneOpenPacket(BlockPos pos) {
    public static void encode(ZoneOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ZoneOpenPacket decode(FriendlyByteBuf buf) {
        return new ZoneOpenPacket(buf.readBlockPos());
    }

    public static void handle(ZoneOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ZoneScreen.open(msg.pos));
        ctx.get().setPacketHandled(true);
    }
}
