package com.mrgregles.bsp_core.lottery;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * {@code /bsp lottery ...}, operators only:
 * <ul>
 *   <li>(nothing) the pot, how many players are in and why the others are not, the settings and the last winners;</li>
 *   <li>{@code admin} opens the lottery's admin page (also the cog tab on a Trade Post);</li>
 *   <li>{@code draw} draws the lottery now: the countdown in chat on every server, then the winner takes the whole pot;</li>
 *   <li>{@code add <pieces> <reason>} and {@code take <pieces> <reason>} move pieces into or out of the pot, logged with the reason.</li>
 * </ul>
 */
public final class LotteryCommands {
    private LotteryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("lottery").requires(src -> src.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("admin").executes(ctx -> {
                    LotteryAdmin.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("draw").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    LotteryDraw.start(src.getServer(), src.getTextName(), key -> {
                        switch (key) {
                            case "ok" -> src.sendSuccess(() -> Component.literal("The lottery is being drawn."), true);
                            case "busy" -> src.sendFailure(Component.literal("A draw is already running."));
                            case "nobody" -> src.sendFailure(Component.literal("Nobody is in the lottery: no player who is not left out was seen within the days."));
                            case "empty" -> src.sendFailure(Component.literal("The lottery pot is empty."));
                            default -> src.sendFailure(Component.literal("The network could not be reached; nothing was drawn."));
                        }
                    });
                    return 1;
                }))
                .then(Commands.literal("add").then(Commands.argument("pieces", LongArgumentType.longArg(1, Market.MAX_PRICE))
                        .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> move(ctx.getSource(), true,
                                LongArgumentType.getLong(ctx, "pieces"), StringArgumentType.getString(ctx, "reason"))))))
                .then(Commands.literal("take").then(Commands.argument("pieces", LongArgumentType.longArg(1, Market.MAX_PRICE))
                        .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> move(ctx.getSource(), false,
                                LongArgumentType.getLong(ctx, "pieces"), StringArgumentType.getString(ctx, "reason"))))));
    }

    private static int status(CommandSourceStack src) {
        var server = src.getServer();
        long now = System.currentTimeMillis();
        src.sendSuccess(() -> Component.literal("Lottery pot: " + Bank.format(Market.pot(server)) + " pieces." + (LotteryDraw.busy() ? " A draw is running." : "")), false);
        Lottery.standings(server, now, now, (settings, all) -> {
            if (all == null) {
                src.sendFailure(Component.literal("The network could not be reached."));
                return;
            }
            long in = all.stream().filter(Lottery.Entry::in).count();
            long ops = all.stream().filter(e -> e.standing() == Lottery.Standing.OPERATOR).count();
            long ranked = all.stream().filter(e -> e.standing() == Lottery.Standing.RANK).count();
            src.sendSuccess(() -> Component.literal(in + " players are in, seen in the past " + settings.days() + " days; left out: " + ops + " operators, "
                    + ranked + " by rank (" + (settings.leftOut().isEmpty() ? "no ranks" : String.join(", ", settings.leftOut())) + ")."), false);
            Lottery.wins(server, wins -> {
                if (wins == null || wins.isEmpty()) {
                    src.sendSuccess(() -> Component.literal("Nobody has won the lottery yet."), false);
                    return;
                }
                SimpleDateFormat day = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
                src.sendSuccess(() -> Component.literal("Last winners:"), false);
                for (LotteryWin w : wins.subList(0, Math.min(5, wins.size()))) {
                    src.sendSuccess(() -> Component.literal("  " + day.format(new Date(w.ms())) + ": " + w.name() + ", " + Bank.format(w.pieces()) + " pieces of "
                            + w.entrants() + " players (draw " + w.draw() + ")"), false);
                }
            });
        });
        return 1;
    }

    private static int move(CommandSourceStack src, boolean add, long pieces, String reason) {
        var server = src.getServer();
        java.util.function.Consumer<com.mrgregles.bsp_core.bank.BankResult> then = r -> {
            if (r.ok()) {
                src.sendSuccess(() -> Component.literal((add ? "Added " : "Took ") + Bank.format(pieces) + " pieces " + (add ? "to" : "from") + " the lottery pot ("
                        + reason + "); it holds " + Bank.format(r.pieces()) + "."), true);
            } else {
                src.sendFailure(Component.literal(r.key().equals("short") ? "The pot holds less than that."
                        : r.key().equals("drawing") ? "A draw is running; the pot can change again once it has ended." : "The network could not be reached; nothing moved."));
            }
        };
        if (add) {
            Lottery.addToPot(server, pieces, src.getTextName(), reason, then);
        } else {
            Lottery.takeFromPot(server, pieces, src.getTextName(), reason, then);
        }
        return 1;
    }
}
