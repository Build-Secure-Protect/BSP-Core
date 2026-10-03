package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.client.ClientStealState;
import com.mrgregles.bsp_core.totem.StealState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server → client: progress of a steal attempt, shown on the HUD of the thief and of the owner.
 *
 * @param pos       the totem
 * @param ownerName current owner's name
 * @param state     the attempt, or null when it has ended
 * @param role      0 = you are the thief, 1 = you are the owner
 * @param outcome   0 = in progress, 1 = cancelled/failed, 2 = succeeded
 */
public record StealStatusPacket(BlockPos pos, String ownerName, @Nullable StealState state, byte role, byte outcome) {
    public static final byte ROLE_THIEF = 0;
    public static final byte ROLE_OWNER = 1;
    public static final byte OUTCOME_ACTIVE = 0;
    public static final byte OUTCOME_FAILED = 1;
    public static final byte OUTCOME_SUCCESS = 2;

    public static void encode(StealStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.ownerName);
        buf.writeBoolean(msg.state != null);
        if (msg.state != null) {
            msg.state.write(buf);
        }
        buf.writeByte(msg.role);
        buf.writeByte(msg.outcome);
    }

    public static StealStatusPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String owner = buf.readUtf();
        StealState state = buf.readBoolean() ? StealState.read(buf) : null;
        return new StealStatusPacket(pos, owner, state, buf.readByte(), buf.readByte());
    }

    public static void handle(StealStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStealState.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
