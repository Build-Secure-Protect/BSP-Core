package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the admin panel's VAULT tab: the vault prize table (each prize's stack and weight), how many saved prizes this
 * server cannot show (their mod is not installed here; they are kept), and the answer to the last action.
 */
public record VaultPrizeAdminPacket(List<ItemStack> stacks, List<Integer> weights, int unknown, String message, boolean ok) {
    /** The most prizes a table holds ({@link com.mrgregles.bsp_core.vault.VaultPrizes#MAX_PRIZES}). */
    public static final int MAX = com.mrgregles.bsp_core.vault.VaultPrizes.MAX_PRIZES;

    public static void encode(VaultPrizeAdminPacket m, FriendlyByteBuf buf) {
        int n = Math.min(MAX, Math.min(m.stacks.size(), m.weights.size()));
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeItem(m.stacks.get(i));
            buf.writeVarInt(m.weights.get(i));
        }
        buf.writeVarInt(m.unknown);
        buf.writeUtf(BSPNetwork.cut(m.message, 256), 256);
        buf.writeBoolean(m.ok);
    }

    public static VaultPrizeAdminPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(MAX, buf.readVarInt());
        List<ItemStack> stacks = new ArrayList<>(n);
        List<Integer> weights = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            stacks.add(buf.readItem());
            weights.add(buf.readVarInt());
        }
        return new VaultPrizeAdminPacket(stacks, weights, buf.readVarInt(), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(VaultPrizeAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveVaultPrizes(msg)));
        ctx.get().setPacketHandled(true);
    }
}
