package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.DepositBox;
import com.mrgregles.bsp_core.bank.DepositBoxMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: rent days or buy slots on the deposit box screen; {@code tier} is -1 for pieces or a coin tier. */
public record DepositBoxActionPacket(boolean slots, int days, int tier) {
    public static void encode(DepositBoxActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.slots);
        buf.writeVarInt(msg.days);
        buf.writeVarInt(msg.tier + 1);
    }

    public static DepositBoxActionPacket decode(FriendlyByteBuf buf) {
        return new DepositBoxActionPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt() - 1);
    }

    public static void handle(DepositBoxActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof DepositBoxMenu menu) {
            ctx.get().enqueueWork(() -> {
                var server = player.server;
                boolean wasOpen = menu.open;
                if (msg.slots) {
                    DepositBox.addSlots(server, player, msg.tier, r -> DepositBoxMenu.sync(player, r.ok() ? "slots_added" : r.key(), new String[]{String.valueOf(r.ok() ? r.pieces() : 0), Bank.format(DepositBox.slotStepPieces())}));
                } else {
                    long cost = DepositBox.rentCost(Bank.account(server, player.getUUID()), System.currentTimeMillis(), msg.days);
                    DepositBox.rent(server, player, msg.days, msg.tier, r -> {
                        if (r.ok()) {
                            player.level().playSound(null, menu.pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.6f);
                            if (!wasOpen) {
                                DepositBox.open(player, menu.pos); // the box is open now: show the slots
                                return;
                            }
                        }
                        String[] args = {String.valueOf(msg.days), Bank.format(cost)};
                        String okKey = "rented";
                        if (r.ok() && msg.tier >= 0 && msg.tier < com.mrgregles.bsp_core.coin.CoinTier.values().length) {
                            var tier = com.mrgregles.bsp_core.coin.CoinTier.values()[msg.tier];
                            int n = DepositBox.coinsFor(cost, tier);
                            okKey = "rented_coins";
                            args = new String[]{String.valueOf(msg.days), String.valueOf(n), Bank.tierName(tier), Bank.format(n * Bank.piecesPerCoin(tier) - cost)};
                        }
                        DepositBoxMenu.sync(player, r.ok() ? okKey : r.key(), args);
                    });
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
