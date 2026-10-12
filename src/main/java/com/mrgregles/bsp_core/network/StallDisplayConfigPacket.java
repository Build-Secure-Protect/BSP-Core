package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.shop.StallDisplayBlockEntity;
import com.mrgregles.bsp_core.shop.StallDisplayMenu;
import com.mrgregles.bsp_core.shop.StallSkin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: one change to a Stall Display from its screen. Applied at once so the admin can
 * step out and look; SHOP (an index into the menu's shop list) and UNLINK reopen the screen with
 * the new shop's items.
 */
public record StallDisplayConfigPacket(Action action, int value) {
    public enum Action { INTERVAL, PIN, SCALE, HEIGHT, RESET, SHOP, UNLINK, SKIN }

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
                    case SKIN -> display.setSkin(StallSkin.values()[Math.floorMod(msg.value, StallSkin.values().length)]);
                    case SHOP -> {
                        if (msg.value >= 0 && msg.value < menu.shops.size()) {
                            display.linkShop(menu.shops.get(msg.value)[0]);
                        }
                        player.closeContainer();
                        StallDisplayMenu.open(player, display);
                        return;
                    }
                    case UNLINK -> {
                        display.unlink();
                        player.closeContainer();
                        StallDisplayMenu.open(player, display);
                        return;
                    }
                }
                display.refreshNow();
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
