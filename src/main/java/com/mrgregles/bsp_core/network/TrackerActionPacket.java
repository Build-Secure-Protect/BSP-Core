package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.tracking.TrackerActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: a machine tracking action from the tagger's screen or the Holo Grid. {@code action}: 0 rename ({@code id},
 * {@code text}), 1 untag ({@code id}), 2 buy a slot, 3 buy the Remote Control unlock, 4 save the layout ({@code layout}), 5 operate the
 * machine of tag {@code id} (open its own screen through a Remote Control Transmitter).
 */
public record TrackerActionPacket(int action, String id, String text, CompoundTag layout) {
    public static final int RENAME = 0, UNTAG = 1, BUY_SLOT = 2, UNLOCK_REMOTE = 3, LAYOUT = 4, OPERATE = 5;

    public static void encode(TrackerActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUtf(BSPNetwork.cut(msg.id, 16), 16);
        buf.writeUtf(BSPNetwork.cut(msg.text, 64), 64);
        buf.writeNbt(msg.layout);
    }

    public static TrackerActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        String id = buf.readUtf(16);
        String text = buf.readUtf(64);
        CompoundTag layout = buf.readNbt();
        return new TrackerActionPacket(action, id, text, layout == null ? new CompoundTag() : layout);
    }

    public static void handle(TrackerActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            TrackerActions.handle(player, msg);
        }
        ctx.get().setPacketHandled(true);
    }
}
