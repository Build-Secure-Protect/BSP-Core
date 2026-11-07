package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.credit.CreditSpend;
import com.mrgregles.bsp_core.land.LandOfficeMenu;
import com.mrgregles.bsp_core.shop.ShopMenu;
import com.mrgregles.bsp_core.shop.ShopOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the player clicked the store credit strip on a shop or the Land Office: PAY WITH CREDIT flips, and the screen is sent again. */
public record CreditTogglePacket(int page) {
    public static void encode(CreditTogglePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.page);
    }

    public static CreditTogglePacket decode(FriendlyByteBuf buf) {
        return new CreditTogglePacket(buf.readVarInt());
    }

    public static void handle(CreditTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof ShopMenu menu) {
                CreditSpend.setWants(player.getUUID(), !CreditSpend.wants(player.getUUID()));
                ShopOpener.sync(player, menu, Math.max(0, msg.page), "", null);
            } else if (player.containerMenu instanceof LandOfficeMenu) {
                CreditSpend.setWants(player.getUUID(), !CreditSpend.wants(player.getUUID()));
                LandOfficeMenu.sync(player, "", true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
