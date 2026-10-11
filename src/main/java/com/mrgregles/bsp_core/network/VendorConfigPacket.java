package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.shop.OpenHours;
import com.mrgregles.bsp_core.shop.VendorMenu;
import com.mrgregles.bsp_core.shop.VendorPostBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: an admin's vendor settings (shop, hours, mob or stall skin); the post or stall saves them and the screen closes. */
public record VendorConfigPacket(String shopId, OpenHours hours, String mob, String mobNbt, int skin) {
    public static void encode(VendorConfigPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.shopId, 32);
        buf.writeNbt(m.hours.save());
        buf.writeUtf(m.mob, 64);
        buf.writeUtf(m.mobNbt, 255);
        buf.writeInt(m.skin);
    }

    public static VendorConfigPacket decode(FriendlyByteBuf buf) {
        return new VendorConfigPacket(buf.readUtf(32), OpenHours.load(java.util.Objects.requireNonNullElse(buf.readNbt(), new net.minecraft.nbt.CompoundTag())), buf.readUtf(64), buf.readUtf(255), buf.readInt());
    }

    public static void handle(VendorConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof VendorMenu menu && Admins.isAdmin(player)) {
            ctx.get().enqueueWork(() -> {
                if (player.level().getBlockEntity(menu.pos) instanceof com.mrgregles.bsp_core.shop.MarketStallBlockEntity stall) {
                    stall.assign(msg.shopId, msg.hours, "", "");
                    if (msg.skin >= 0 && msg.skin < com.mrgregles.bsp_core.shop.StallSkin.values().length) {
                        stall.setSkin(com.mrgregles.bsp_core.shop.StallSkin.values()[msg.skin]);
                    }
                } else if (player.level().getBlockEntity(menu.pos) instanceof VendorPostBlockEntity post) {
                    post.set(msg.shopId, msg.hours, msg.mob, msg.mobNbt);
                } else if (player.level().getBlockEntity(menu.pos) instanceof com.mrgregles.bsp_core.shop.Assignable a) {
                    a.assign(msg.shopId, msg.hours, msg.mob, msg.mobNbt);
                }
                player.closeContainer();
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
