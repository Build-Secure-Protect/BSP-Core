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
                String refused = LandOfficeMenu.refusal(player.serverLevel(), menu, msg.action, msg.id);
                if (refused != null) {
                    LandOfficeMenu.sync(player, refused, false);
                    return;
                }
                switch (msg.action) {
                    case REFRESH -> LandOfficeMenu.sync(player, "", true);
                    case QUOTE_PLOT -> {
                        var mark = com.mrgregles.bsp_core.credit.CreditLand.begin(player, LandOfficeMenu.creditPlace(player, menu, msg.id), false);
                        var r = RegionGuardClaims.quotePlot(player, msg.id);
                        com.mrgregles.bsp_core.credit.CreditLand.end(player, mark, false, null);
                        LandOfficeMenu.sync(player, r.message(), r.ok());
                    }
                    case QUOTE_OPEN -> {
                        if (ground == null) {
                            LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.land.no_selection").getString(), false);
                            return;
                        }
                        var mark = com.mrgregles.bsp_core.credit.CreditLand.begin(player, com.mrgregles.bsp_core.credit.CreditPlace.ground(), false);
                        var r = RegionGuardClaims.quoteOpen(player, msg.id, ground);
                        com.mrgregles.bsp_core.credit.CreditLand.end(player, mark, false, null);
                        LandOfficeMenu.sync(player, r.message(), r.ok());
                    }
                    case BUY_PLOT -> {
                        if (busy(player)) {
                            return;
                        }
                        var mark = com.mrgregles.bsp_core.credit.CreditLand.begin(player, LandOfficeMenu.creditPlace(player, menu, msg.id), true);
                        RegionGuardClaims.purchasePlot(player, msg.id, r -> {
                            com.mrgregles.bsp_core.credit.CreditLand.end(player, mark, r.ok(), r.claimId());
                            LandOfficeMenu.sync(player, r.message(), r.ok());
                        });
                    }
                    case BUY_OPEN -> {
                        if (ground == null) {
                            LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.land.no_selection").getString(), false);
                            return;
                        }
                        if (busy(player)) {
                            return;
                        }
                        var mark = com.mrgregles.bsp_core.credit.CreditLand.begin(player, com.mrgregles.bsp_core.credit.CreditPlace.ground(), true);
                        RegionGuardClaims.purchaseOpen(player, msg.id, ground, r -> {
                            com.mrgregles.bsp_core.credit.CreditLand.end(player, mark, r.ok(), r.claimId());
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
                        if (busy(player)) {
                            return;
                        }
                        var mark = com.mrgregles.bsp_core.credit.CreditLand.begin(player, com.mrgregles.bsp_core.credit.CreditPlace.ground(), true);
                        RegionGuardClaims.enlarge(player, msg.id, ground, r -> {
                            com.mrgregles.bsp_core.credit.CreditLand.end(player, mark, r.ok(), r.claimId() == null ? msg.id : r.claimId());
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

    /** A purchase of the player's is still going through at the office: a second one is refused with a line saying so. */
    private static boolean busy(net.minecraft.server.level.ServerPlayer player) {
        if (!com.mrgregles.bsp_core.credit.CreditLand.busy(player)) {
            return false;
        }
        LandOfficeMenu.sync(player, net.minecraft.network.chat.Component.translatable("message.bsp_core.credit.office_busy").getString(), false);
        return true;
    }
}
