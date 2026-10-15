package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims;
import com.mrgregles.bsp_core.land.LandOfficeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: what the player asked the Land Office for. Purchases of outlined ground and
 * enlargements use the ground the server already holds for this player (from the wand), never a
 * ground sent by the client. Every action answers with a fresh {@link LandOfficeDataPacket}.
 */
public record LandOfficeActionPacket(Action action, String id) {
    public enum Action { QUOTE_PLOT, BUY_PLOT, QUOTE_OPEN, BUY_OPEN, ENLARGE, ABANDON, REFRESH }

    public static void encode(LandOfficeActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.id, 64);
    }

    public static LandOfficeActionPacket decode(FriendlyByteBuf buf) {
        return new LandOfficeActionPacket(buf.readEnum(Action.class), buf.readUtf(64));
    }

    public static void handle(LandOfficeActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof LandOfficeMenu menu) {
            ctx.get().enqueueWork(() -> {
                int[] ground = menu.selection;
                switch (msg.action) {
                    case REFRESH -> LandOfficeMenu.sync(player, "", true);
                    case QUOTE_PLOT -> {
                        var r = RegionGuardClaims.quotePlot(player, msg.id);
                        LandOfficeMenu.sync(player, r.message(), r.ok());
                    }
                    case QUOTE_OPEN -> {
                        if (ground == null) {
                            LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.land.no_selection").getString(), false);
                            return;
                        }
                        var r = RegionGuardClaims.quoteOpen(player, msg.id, ground);
                        LandOfficeMenu.sync(player, r.message(), r.ok());
                    }
                    case BUY_PLOT -> RegionGuardClaims.purchasePlot(player, msg.id, r -> LandOfficeMenu.sync(player, r.message(), r.ok()));
                    case BUY_OPEN -> {
                        if (ground == null) {
                            LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.land.no_selection").getString(), false);
                            return;
                        }
                        RegionGuardClaims.purchaseOpen(player, msg.id, ground, r -> {
                            if (r.ok() && player.containerMenu instanceof LandOfficeMenu m) {
                                m.selection = null; // the outline became a claim
                            }
                            LandOfficeMenu.sync(player, r.message(), r.ok());
                        });
                    }
                    case ENLARGE -> {
                        if (ground == null) {
                            LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.land.no_selection").getString(), false);
                            return;
                        }
                        RegionGuardClaims.enlarge(player, msg.id, ground, r -> {
                            if (r.ok() && player.containerMenu instanceof LandOfficeMenu m) {
                                m.selection = null;
                            }
                            LandOfficeMenu.sync(player, r.message(), r.ok());
                        });
                    }
                    case ABANDON -> {
                        var r = RegionGuardClaims.abandon(player, msg.id);
                        LandOfficeMenu.sync(player, r.message(), r.ok());
                    }
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
