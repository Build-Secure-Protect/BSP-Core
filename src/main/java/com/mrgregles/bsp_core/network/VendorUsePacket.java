package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.VendorPostBlock;
import com.mrgregles.bsp_core.shop.VendorPostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player right-clicked the figure drawn on a Vendor Post (the figure is not
 * an entity, so the client finds the hit itself). The server checks the post is real and in reach,
 * then treats it as a click on the post.
 */
public record VendorUsePacket(BlockPos pos) {
    public static void encode(VendorUsePacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
    }

    public static VendorUsePacket decode(FriendlyByteBuf buf) {
        return new VendorUsePacket(buf.readBlockPos());
    }

    public static void handle(VendorUsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> {
                if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 1.5, msg.pos.getZ() + 0.5) < 64
                        && player.level().getBlockEntity(msg.pos) instanceof VendorPostBlockEntity post) {
                    VendorPostBlock.use(player, post);
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
