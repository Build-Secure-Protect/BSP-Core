package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Opens a player's bank account on their first join anywhere on the network (with the starting
 * pieces, once) and tells them what arrived while they were away: payments, market sales and a lottery win.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BankEvents {
    private BankEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var server = player.server;
        var id = player.getUUID();
        long seen = Bank.account(server, id).seenMs;
        Bank.ensure(server, id, player.getGameProfile().getName(), granted -> {
            if (granted) {
                player.displayClientMessage(Component.translatable("message.bsp_core.bank.welcome", Bank.format(Bank.pieces(server, id))).withStyle(ChatFormatting.GOLD), false);
            }
            Bank.pushState(server, id);
            Bank.log(server, id, log -> {
                long received = 0, sold = 0, won = 0;
                int n = 0, sales = 0;
                for (BankTransaction t : log) {
                    if (t.ms() > seen && t.type() == BankTransaction.Type.RECEIVED) {
                        received += t.pieces();
                        n++;
                    } else if (t.ms() > seen && t.type() == BankTransaction.Type.MARKET && t.pieces() > 0) {
                        sold += t.pieces();
                        sales++;
                    } else if (t.ms() > seen && t.type() == BankTransaction.Type.LOTTERY && t.pieces() > 0) {
                        won += t.pieces();
                    }
                }
                if (n > 0 && !player.hasDisconnected()) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.bank.while_away", n, Bank.format(received)).withStyle(ChatFormatting.GOLD), false);
                }
                if (sales > 0 && !player.hasDisconnected()) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.market.while_away", sales, Bank.format(sold)).withStyle(ChatFormatting.GOLD), false);
                }
                if (won > 0 && !player.hasDisconnected()) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.lottery.while_away", Bank.format(won)).withStyle(ChatFormatting.GOLD), false);
                }
                Bank.touch(server, id);
            });
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Bank.touch(player.server, player.getUUID());
        }
    }
}
