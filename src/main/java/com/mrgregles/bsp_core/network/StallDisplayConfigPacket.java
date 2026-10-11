package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.shop.StallDisplayBlock;
import com.mrgregles.bsp_core.shop.StallDisplayBlockEntity;
import com.mrgregles.bsp_core.shop.StallDisplayMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: one change to a Stall Display from its settings screen. Applied at once so the
 * admin can step out and look; LINK closes the screen and waits for the click on the post or stall.
 */
public record StallDisplayConfigPacket(Action action, int value) {
    public enum Action { INTERVAL, PIN, SCALE, HEIGHT, RESET, LINK, UNLINK }

    public static void encode(StallDisplayConfigPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeVarInt(m.value);
    }

    public static StallDisplayConfigPacket decode(FriendlyByteBuf buf) {
        return new StallDisplayConfigPacket(buf.readEnum(Action.class), buf.readVarInt());
    }

    public static void handle(StallDisplayConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof StallDisplayMenu menu && Admins.isAdmin(player)) {
            ctx.get().enqueueWork(() -> {
                if (!(player.level().getBlockEntity(menu.pos) instanceof StallDisplayBlockEntity display)) {
                    return;
                }
                switch (msg.action) {
                    case INTERVAL -> display.intervalSec = Math.max(0, Math.min(600, msg.value));
                    case PIN -> display.pinned = Math.max(-1, msg.value);
                    case SCALE -> display.scalePct = Math.max(0, Math.min(300, msg.value));
                    case HEIGHT -> display.heightPct = Math.max(0, Math.min(400, msg.value));
                    case RESET -> {
                        display.scalePct = 0;
                        display.heightPct = 0;
                        display.intervalSec = 0;
                    }
                    case LINK -> {
                        player.closeContainer();
                        StallDisplayBlock.startLinking(player, display);
                    }
                    case UNLINK -> display.unlink();
                }
                display.refreshNow();
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
