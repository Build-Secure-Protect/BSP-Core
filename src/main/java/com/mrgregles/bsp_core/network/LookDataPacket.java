package com.mrgregles.bsp_core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server → client: the LOOKS tab's data for the player: their record ({@link com.mrgregles.bsp_core.looks.LookOwner}: dressing,
 * hats, hat choice, saved looks), the look on the totem at {@code pos} when it is theirs (even while hidden), and how long until
 * they may put on or save a look again.
 */
public record LookDataPacket(BlockPos pos, CompoundTag record, @Nullable CompoundTag look, long waitMs) {
    public static void encode(LookDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeNbt(msg.record);
        buf.writeNbt(msg.look);
        buf.writeVarLong(msg.waitMs);
    }

    public static LookDataPacket decode(FriendlyByteBuf buf) {
        return new LookDataPacket(buf.readBlockPos(), buf.readNbt(), buf.readNbt(), buf.readVarLong());
    }

    public static void handle(LookDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientLooks.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
