package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.vault.CoinVaultBlockEntity;
import com.mrgregles.bsp_core.vault.CoinVaultMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: add a player, by name, to the access list of the Coin Vault the sender has open. They must be online. */
public record VaultAccessPacket(String name) {
    public static void encode(VaultAccessPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, 16);
    }

    public static VaultAccessPacket decode(FriendlyByteBuf buf) {
        return new VaultAccessPacket(buf.readUtf(16));
    }

    public static void handle(VaultAccessPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof CoinVaultMenu menu && !menu.locked() && menu.vault() != null && menu.vault().mayManage(player)) {
            CoinVaultBlockEntity vault = menu.vault();
            ServerPlayer target = player.server.getPlayerList().getPlayerByName(msg.name.trim());
            String key = target == null ? "message.bsp_core.vault.access_offline"
                    : vault.addAccess(target.getUUID(), target.getGameProfile().getName()) ? null
                    : vault.accessList().size() >= CoinVaultBlockEntity.MAX_ACCESS ? "message.bsp_core.vault.access_full" : "message.bsp_core.vault.access_already";
            if (key != null) {
                player.displayClientMessage(Component.translatable(key, msg.name.trim()).withStyle(ChatFormatting.RED), true);
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
