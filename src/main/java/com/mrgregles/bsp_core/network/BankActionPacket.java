package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankLedger;
import com.mrgregles.bsp_core.bank.BankMenu;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → server: something done on the banking screen. Every outcome is answered with a
 * {@link BankDataPacket} carrying the fresh account and a message key.
 */
public record BankActionPacket(Action action, int tier, int count, String text, long amount, boolean flag) {
    public enum Action { DEPOSIT, WITHDRAW, PAY, TOGGLE_PAY, SEARCH, REFRESH, UNLOCK_NETWORK, OPEN_NETWORK }

    public static void encode(BankActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.tier);
        buf.writeVarInt(msg.count);
        buf.writeUtf(msg.text, 32);
        buf.writeLong(msg.amount);
        buf.writeBoolean(msg.flag);
    }

    public static BankActionPacket decode(FriendlyByteBuf buf) {
        return new BankActionPacket(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readLong(), buf.readBoolean());
    }

    public static void handle(BankActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof BankMenu menu) {
            ctx.get().enqueueWork(() -> run(player, menu, msg));
        }
        ctx.get().setPacketHandled(true);
    }

    private static void run(ServerPlayer player, BankMenu menu, BankActionPacket msg) {
        var server = player.server;
        var id = player.getUUID();
        switch (msg.action) {
            case DEPOSIT -> {
                if (menu.portable()) {
                    BankMenu.sync(player, "at_bank_only", List.of(), null);
                    return;
                }
                CoinTier tier = CoinTier.byOrdinal(msg.tier);
                if (tier == null) {
                    return;
                }
                int count = msg.count <= 0 ? player.getInventory().countItem(tier.coin()) : msg.count;
                Bank.depositCoins(server, player, tier, count, msg.flag, r -> {
                    if (r.ok() && menu.atm != null) {
                        player.level().playSound(null, menu.atm, SoundEvents.NOTE_BLOCK_BIT.get(), SoundSource.BLOCKS, 0.5f, 1.9f);
                    }
                    BankMenu.sync(player, r.ok() ? (msg.flag ? "converted" : "deposited") : r.key(), List.of(), new String[]{String.valueOf(count), Bank.tierName(tier), Bank.format(Bank.piecesPerCoin(tier) * count)});
                });
            }
            case WITHDRAW -> {
                if (menu.portable()) {
                    BankMenu.sync(player, "at_bank_only", List.of(), null);
                    return;
                }
                CoinTier tier = CoinTier.byOrdinal(msg.tier);
                if (tier == null) {
                    return;
                }
                int count = msg.count <= 0 ? Bank.account(server, id).coins(tier) : msg.count;
                Bank.withdrawCoins(server, player, tier, count, r -> {
                    if (r.ok() && menu.atm != null) {
                        player.level().playSound(null, menu.atm, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.6f);
                    }
                    BankMenu.sync(player, r.ok() ? "withdrawn" : r.key(), List.of(), new String[]{String.valueOf(count), Bank.tierName(tier)});
                });
            }
            case PAY -> {
                String name = msg.text.trim();
                long amount = msg.amount;
                if (name.isEmpty() || amount <= 0) {
                    BankMenu.sync(player, "amount", List.of(), null);
                    return;
                }
                Bank.resolve(server, name, hit -> {
                    if (hit == null) {
                        BankMenu.sync(player, "no_player", List.of(), new String[]{name});
                        return;
                    }
                    if (amount >= BSPConfig.getOr(BSPConfig.BANK_PAY_CONFIRM, 1000) && !msg.flag) {
                        BankMenu.sync(player, "confirm_screen", List.of(), new String[]{Bank.format(amount), hit.getValue()});
                        return;
                    }
                    Bank.transfer(server, id, hit.getKey(), amount, r -> BankMenu.sync(player, r.ok() ? "paid" : r.key(), List.of(),
                            new String[]{Bank.format(amount), hit.getValue(), r.ok() ? Bank.format(r.pieces()) : ""}));
                });
            }
            case TOGGLE_PAY -> {
                boolean now = !Bank.account(server, id).acceptPay;
                Bank.setAcceptPay(server, id, now);
                BankMenu.sync(player, now ? "accepting" : "refusing", List.of(), null);
            }
            case SEARCH -> {
                String q = msg.text.trim().toLowerCase(java.util.Locale.ROOT);
                List<String> found = new ArrayList<>();
                if (!q.isEmpty()) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        String n = p.getGameProfile().getName();
                        if (n.toLowerCase(java.util.Locale.ROOT).startsWith(q) && !p.getUUID().equals(id)) {
                            found.add(n);
                        }
                    }
                    for (var e : BankLedger.get(server).all().entrySet()) {
                        String n = e.getValue().name;
                        if (found.size() < 6 && !n.isEmpty() && n.toLowerCase(java.util.Locale.ROOT).startsWith(q) && !found.contains(n) && !e.getKey().equals(id)) {
                            found.add(n);
                        }
                    }
                }
                BankMenu.sync(player, "", found, null);
            }
            case UNLOCK_NETWORK -> Bank.unlockRemote(server, player, r -> BankMenu.sync(player, r.ok() ? "network_unlocked" : r.key(), List.of(),
                    new String[]{String.valueOf(BSPConfig.getOr(BSPConfig.BANK_REMOTE_UNLOCK_VALUE, 16))}));
            case OPEN_NETWORK -> {
                if (menu.portable() && Bank.account(server, id).remoteShops) {
                    com.mrgregles.bsp_core.shop.ShopListMenu.open(player);
                } else {
                    BankMenu.sync(player, "network_locked", List.of(), null);
                }
            }
            case REFRESH -> {
                Bank.refresh(server, id);
                BankMenu.sync(player, "", List.of(), null);
            }
        }
    }
}
