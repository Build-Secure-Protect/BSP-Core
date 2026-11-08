package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.FactoryCap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The team surcharge on totem upgrades: {@code team.upgradeSurchargePercent} for each member of the totem owner's team after
 * the first (a team of 4 pays 18% more at 6), counted by current members across the network. It is paid in Shatter Pieces from
 * the buyer's bank: the price's copper value x {@code bank.piecesPerCopper} x the percent. Tier gates and upgrades on placed and
 * carried totems pay it; compass upgrades do not.
 */
public final class TeamSurcharge {
    private TeamSurcharge() {}

    /** The surcharge percent for a team of {@code size}: nothing alone. */
    public static int percentFor(int size) {
        return Math.max(0, size - 1) * Math.max(0, BSPConfig.getOr(BSPConfig.TEAM_UPGRADE_SURCHARGE, 6));
    }

    /** The percent on a totem owned by {@code owner} (null: unowned, the buyer's team counts). */
    public static int percent(MinecraftServer server, @Nullable UUID owner, UUID buyer) {
        return percentFor(FactoryCap.teamSize(server, owner != null ? owner : buyer));
    }

    /** Pieces one copper coin's value is worth, for the surcharge (the bank's conversion rate). */
    public static long perCopper() {
        return Math.max(0, BSPConfig.getOr(BSPConfig.BANK_PIECES_PER_COPPER, 1000));
    }

    /** The surcharge on {@code price} in Pieces, rounded to the nearest piece. */
    public static long pieces(TotemUpgrades.Price price, int percent, long perCopper) {
        if (price == null || percent <= 0) {
            return 0;
        }
        return Math.round((double) price.coins() * price.coin().value() * perCopper * percent / 100.0);
    }

    /**
     * Takes the surcharge from the buyer's bank, then runs {@code buy} (on the server thread). Creative players and solo owners pay
     * nothing and {@code buy} runs at once. When the bank answers later (a network), {@code stillValid} is asked again first; if the
     * purchase no longer holds, the pieces go back. Refusals are told to the player.
     */
    public static void charge(ServerPlayer player, TotemUpgrades.Price price, int percent, String what, BooleanSupplier stillValid, Runnable buy) {
        long pieces = player.isCreative() ? 0 : pieces(price, percent, perCopper());
        if (pieces <= 0) {
            buy.run();
            return;
        }
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        // with local storage the balance here is the record; on a network the database decides (a cached balance may be behind)
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.enabled() && Bank.pieces(server, id) < pieces) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.surcharge_short", Bank.format(pieces), Bank.format(Bank.pieces(server, id))).withStyle(ChatFormatting.RED), true);
            return;
        }
        String detail = "+" + percent + "% team surcharge: " + what;
        Bank.take(server, id, pieces, BankTransaction.Type.FEE, "Totem upgrade", detail, r -> {
            if (!r.ok()) {
                player.displayClientMessage(("short".equals(r.key())
                        ? Component.translatable("message.bsp_core.upgrade.surcharge_short", Bank.format(pieces), Bank.format(Bank.pieces(server, id)))
                        : Component.translatable("message.bsp_core.upgrade.surcharge_failed")).withStyle(ChatFormatting.RED), true);
                return;
            }
            if (player.hasDisconnected() || !stillValid.getAsBoolean()) {
                Bank.give(server, id, pieces, BankTransaction.Type.FEE, "Totem upgrade", "team surcharge returned: " + what, back -> {
                    if (!back.ok()) {
                        com.mrgregles.bsp_core.BSPCore.LOGGER.error("BSP-Core: could not return {} Pieces of team surcharge to {} ({}): {}. Give them back by hand.",
                                pieces, player.getGameProfile().getName(), id, back.key());
                        player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.surcharge_lost", Bank.format(pieces)).withStyle(ChatFormatting.RED), false);
                    }
                });
                player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.surcharge_returned").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            buy.run();
        });
    }
}
