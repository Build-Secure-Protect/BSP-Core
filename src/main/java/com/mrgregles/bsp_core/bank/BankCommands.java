package com.mrgregles.bsp_core.bank;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The bank's command backups (the Bank block and the Portable Banker are the real front):
 * <pre>
 * /bank                                   your pieces and the coins you keep in the bank
 * /bank pay &lt;player&gt; &lt;pieces&gt; [confirm]    pay a player, online or not, anywhere on the network
 * /bank log [page]                        your transaction log, newest first
 * /bank paytoggle                         refuse or accept payments from other players
 * /bsp bank give|take|set &lt;player&gt; &lt;pieces&gt;   admin
 * /bsp bank remote &lt;player&gt; on|off           admin: Portable Banker shop network access
 * /bsp bank remote reset                      admin: clear everyone's shop network access (season end)
 * /bsp bank boxlapse &lt;player&gt; &lt;days&gt;        admin, testing: make the player's deposit box rent run out that many days ago
 * </pre>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BankCommands {
    private BankCommands() {}

    private static final int PAGE = 8;
    /** Large payments waiting for "confirm": player -> (recipient, amount, deadline). */
    private static final Map<UUID, Object[]> PENDING = new HashMap<>();

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bank")
                .executes(ctx -> balance(ctx.getSource()))
                .then(Commands.literal("pay")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("pieces", LongArgumentType.longArg(1))
                                        .executes(ctx -> pay(ctx, false))
                                        .then(Commands.literal("confirm").executes(ctx -> pay(ctx, true))))))
                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> log(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("paytoggle").executes(ctx -> payToggle(ctx.getSource()))));
    }

    /** The admin branch, hung under /bsp by {@code BSPCommands}. */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("bank").requires(src -> src.hasPermission(2))
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("pieces", LongArgumentType.longArg(1))
                        .executes(ctx -> adminMove(ctx, 1)))))
                .then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("pieces", LongArgumentType.longArg(1))
                        .executes(ctx -> adminMove(ctx, -1)))))
                .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("pieces", LongArgumentType.longArg(0))
                        .executes(BankCommands::adminSet))))
                .then(Commands.literal("boxlapse").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("days", IntegerArgumentType.integer(0, 3650))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int d = IntegerArgumentType.getInteger(ctx, "days");
                            BankAccount a = Bank.account(ctx.getSource().getServer(), target.getUUID());
                            if (a.boxSlots <= 0) {
                                a.boxSlots = DepositBox.SLOTS_DEFAULT;
                            }
                            a.boxUntilMs = System.currentTimeMillis() - d * DepositBox.DAY_MS + 1000;
                            DepositBox.saved(ctx.getSource().getServer(), target.getUUID());
                            ctx.getSource().sendSuccess(() -> tr("boxlapse", target.getGameProfile().getName(), d), true);
                            return 1;
                        }))))
                .then(Commands.literal("remote").then(Commands.literal("reset").executes(ctx -> {
                            int n = Bank.resetRemoteAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> tr("remote_reset", n), true);
                            return 1;
                        })).then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("on").executes(ctx -> adminRemote(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> adminRemote(ctx, false)))
                        .then(Commands.literal("market")
                                .then(Commands.literal("on").executes(ctx -> adminMarket(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> adminMarket(ctx, false))))));
    }

    private static ServerPlayer player(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? p : null;
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("message.bsp_core.bank." + key, args);
    }

    private static int balance(CommandSourceStack src) {
        ServerPlayer p = player(src);
        if (p == null) {
            src.sendFailure(tr("players_only"));
            return 0;
        }
        BankAccount a = Bank.account(src.getServer(), p.getUUID());
        src.sendSuccess(() -> tr("balance", Bank.format(a.pieces)).withStyle(ChatFormatting.GOLD), false);
        StringBuilder coins = new StringBuilder();
        for (CoinTier t : CoinTier.values()) {
            if (a.coins(t) > 0) {
                coins.append(coins.length() > 0 ? ", " : "").append(a.coins(t)).append(' ').append(Bank.tierName(t));
            }
        }
        src.sendSuccess(() -> coins.length() == 0 ? tr("no_coins") : tr("coins", coins.toString()), false);
        if (!a.acceptPay) {
            src.sendSuccess(() -> tr("refusing"), false);
        }
        Bank.refresh(src.getServer(), p.getUUID());
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = player(src);
        if (p == null) {
            src.sendFailure(tr("players_only"));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "pieces");
        Bank.resolve(src.getServer(), name, hit -> {
            if (hit == null) {
                src.sendFailure(tr("no_player", name));
                return;
            }
            long threshold = BSPConfig.getOr(BSPConfig.BANK_PAY_CONFIRM, 1000);
            if (amount >= threshold && !confirmed) {
                PENDING.put(p.getUUID(), new Object[]{hit.getKey(), amount, System.currentTimeMillis() + 60_000});
                src.sendSuccess(() -> tr("confirm", Bank.format(amount), hit.getValue(), name, amount), false);
                return;
            }
            if (amount >= threshold) {
                Object[] pending = PENDING.remove(p.getUUID());
                if (pending == null || !pending[0].equals(hit.getKey()) || (long) pending[1] != amount || (long) pending[2] < System.currentTimeMillis()) {
                    src.sendFailure(tr("confirm_first"));
                    return;
                }
            }
            Bank.transfer(src.getServer(), p.getUUID(), hit.getKey(), amount, r -> {
                if (r.ok()) {
                    src.sendSuccess(() -> tr("paid", Bank.format(amount), hit.getValue(), Bank.format(r.pieces())).withStyle(ChatFormatting.GOLD), false);
                } else {
                    src.sendFailure(tr(r.key(), hit.getValue()));
                }
            });
        });
        return 1;
    }

    private static int log(CommandSourceStack src, int page) {
        ServerPlayer p = player(src);
        if (p == null) {
            src.sendFailure(tr("players_only"));
            return 0;
        }
        Bank.log(src.getServer(), p.getUUID(), list -> {
            if (list.isEmpty()) {
                src.sendSuccess(() -> tr("log_empty"), false);
                return;
            }
            int pages = (list.size() + PAGE - 1) / PAGE, pg = Math.min(page, pages);
            src.sendSuccess(() -> tr("log_head", pg, pages).withStyle(ChatFormatting.GOLD), false);
            SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.ROOT);
            List<BankTransaction> slice = list.subList((pg - 1) * PAGE, Math.min(list.size(), pg * PAGE));
            for (BankTransaction t : slice) {
                String amount = t.pieces() != 0 ? (t.pieces() > 0 ? "+" : "-") + Bank.format(Math.abs(t.pieces())) : "";
                String coins = t.coinTier() >= 0 && t.coinCount() != 0 ? (t.coinCount() > 0 ? " +" : " ") + t.coinCount() + " " + Bank.tierName(CoinTier.values()[t.coinTier()]) : "";
                String line = f.format(new Date(t.ms())) + "  " + t.type().name().toLowerCase(Locale.ROOT) + "  " + t.with() + (t.detail().isEmpty() ? "" : ", " + t.detail()) + "  " + amount + coins;
                src.sendSuccess(() -> Component.literal(line).withStyle(t.pieces() < 0 ? ChatFormatting.RED : t.pieces() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
            }
        });
        return 1;
    }

    private static int payToggle(CommandSourceStack src) {
        ServerPlayer p = player(src);
        if (p == null) {
            src.sendFailure(tr("players_only"));
            return 0;
        }
        boolean now = !Bank.account(src.getServer(), p.getUUID()).acceptPay;
        Bank.setAcceptPay(src.getServer(), p.getUUID(), now);
        src.sendSuccess(() -> tr(now ? "accepting" : "refusing"), false);
        return 1;
    }

    private static int adminMove(CommandContext<CommandSourceStack> ctx, int sign) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "pieces");
        String admin = src.getTextName();
        java.util.function.Consumer<BankResult> then = r -> {
            if (r.ok()) {
                src.sendSuccess(() -> tr("admin_done", target.getGameProfile().getName(), Bank.format(r.pieces())), true);
            } else {
                src.sendFailure(tr(r.key(), target.getGameProfile().getName()));
            }
        };
        if (sign > 0) {
            Bank.give(src.getServer(), target.getUUID(), amount, BankTransaction.Type.ADMIN, admin, "given", then);
        } else {
            Bank.take(src.getServer(), target.getUUID(), amount, BankTransaction.Type.ADMIN, admin, "taken", then);
        }
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "pieces");
        Bank.set(src.getServer(), target.getUUID(), amount, src.getTextName(), r -> {
            if (r.ok()) {
                src.sendSuccess(() -> tr("admin_done", target.getGameProfile().getName(), Bank.format(r.pieces())), true);
            } else {
                src.sendFailure(tr(r.key(), target.getGameProfile().getName()));
            }
        });
        return 1;
    }

    private static int adminRemote(CommandContext<CommandSourceStack> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Bank.setRemoteShops(src.getServer(), target.getUUID(), on);
        src.sendSuccess(() -> tr(on ? "remote_on" : "remote_off", target.getGameProfile().getName()), true);
        return 1;
    }

    private static int adminMarket(CommandContext<CommandSourceStack> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Bank.setRemoteMarket(src.getServer(), target.getUUID(), on);
        src.sendSuccess(() -> tr(on ? "market_on" : "market_off", target.getGameProfile().getName()), true);
        return 1;
    }
}
