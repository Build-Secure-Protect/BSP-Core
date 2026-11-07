package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → server: an action on the admin panel's VAULT tab. {@code REQUEST} asks for the vault prize table; {@code SAVE} sends
 * the whole edited table (each prize's item stack, with its count, enchantments and name, and its weight). The server checks the
 * sender: moderators may look, only admins may save.
 */
public record VaultPrizeEditPacket(Action action, List<ItemStack> stacks, List<Integer> weights) {
    public enum Action { REQUEST, SAVE }

    public static void encode(VaultPrizeEditPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        int n = Math.min(VaultPrizeAdminPacket.MAX, Math.min(m.stacks.size(), m.weights.size()));
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeItem(m.stacks.get(i));
            buf.writeVarInt(m.weights.get(i));
        }
    }

    public static VaultPrizeEditPacket decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        int n = Math.min(VaultPrizeAdminPacket.MAX, buf.readVarInt());
        List<ItemStack> stacks = new ArrayList<>(n);
        List<Integer> weights = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            stacks.add(buf.readItem());
            weights.add(buf.readVarInt());
        }
        return new VaultPrizeEditPacket(a, stacks, weights);
    }

    public static void handle(VaultPrizeEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player != null) {
                com.mrgregles.bsp_core.vault.VaultPrizeAdmin.act(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
