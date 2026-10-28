package com.mrgregles.bsp_core.market;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_core.bank.Bank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * {@code /bsp market ...}, operators only:
 * <ul>
 *   <li>{@code list [player]} the open lots (or one player's), newest first, with their ids;</li>
 *   <li>{@code pot} the pieces in the lottery pot;</li>
 *   <li>{@code cancel <id>} takes a lot off the market, its items to the seller's Collect box;</li>
 *   <li>{@code sell <price>}, {@code buy <id>}, {@code collect} the market for the operator themself, until the Trade Post
 *       screen exists: lists the stack in the main hand, buys a lot, empties their own Collect box;</li>
 *   <li>{@code expire} runs the sweep of lots past their end now (it also runs every 30 seconds);</li>
 *   <li>{@code box <player>} what waits in a player's Collect box.</li>
 * </ul>
 */
public final class MarketCommands {
    private MarketCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("market").requires(src -> src.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), null))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> list(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                .then(Commands.literal("pot").executes(ctx -> {
                    long pot = Market.pot(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("The lottery pot holds " + Bank.format(pot) + " pieces."), false);
                    return 1;
                }))
                .then(Commands.literal("cancel").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    String id = StringArgumentType.getString(ctx, "id");
                    var by = src.getEntity() == null ? new java.util.UUID(0, 0) : src.getEntity().getUUID();
                    Market.cancel(src.getServer(), by, id, true, r -> {
                        if (r.ok()) {
                            src.sendSuccess(() -> Component.literal("Lot " + id + " (" + r.lot().describe() + ") taken off; its items wait in " + r.lot().sellerName + "'s Collect box."), true);
                        } else {
                            src.sendFailure(Component.literal("No open lot " + id + "."));
                        }
                    });
                    return 1;
                })))
                .then(Commands.literal("sell").then(Commands.argument("price", LongArgumentType.longArg(1, Market.MAX_PRICE)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack held = p.getMainHandItem().copy();
                    long price = LongArgumentType.getLong(ctx, "price");
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Market.list(ctx.getSource().getServer(), p.getUUID(), p.getGameProfile().getName(), List.of(held), price, r -> {
                        if (r.ok()) {
                            ctx.getSource().sendSuccess(() -> Component.literal("Listed lot " + r.lot().id + " (" + r.lot().describe() + ") for " + Bank.format(price)
                                    + " pieces; tax paid " + Bank.format(Market.tax(price)) + "."), false);
                        } else {
                            Market.intoInventory(p, r.items()).forEach(left -> p.drop(left, false));
                            ctx.getSource().sendFailure(Component.translatable("message.bsp_core.market." + r.key(), Market.maxStacks()));
                        }
                    });
                    return 1;
                })))
                .then(Commands.literal("buy").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(ctx, "id");
                    Market.buy(ctx.getSource().getServer(), p.getUUID(), p.getGameProfile().getName(), id, r -> {
                        if (r.ok()) {
                            ctx.getSource().sendSuccess(() -> Component.literal("Bought lot " + id + " (" + r.lot().describe() + ") for " + Bank.format(r.lot().price)
                                    + " + " + Bank.format(Market.tax(r.lot().price)) + " tax."), false);
                        } else {
                            ctx.getSource().sendFailure(Component.translatable("message.bsp_core.market." + r.key()));
                        }
                    });
                    return 1;
                })))
                .then(Commands.literal("collect").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Market.collect(ctx.getSource().getServer(), p, r -> ctx.getSource().sendSuccess(() -> Component.literal(r.count() + " stack(s) collected"
                            + (r.items().isEmpty() ? "." : "; " + r.items().size() + " still wait in the box (inventory full).")), false));
                    return 1;
                }))
                .then(Commands.literal("expire").executes(ctx -> {
                    int n = Market.expireDue(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(n + " lot(s) ran out and went to their sellers' Collect boxes."), true);
                    return n;
                }))
                .then(Commands.literal("box").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(ctx -> {
                    for (GameProfile p : GameProfileArgument.getGameProfiles(ctx, "player")) {
                        List<ItemStack> box = Market.collectBox(ctx.getSource().getServer(), p.getId());
                        StringBuilder sb = new StringBuilder(p.getName() + "'s Collect box: " + (box.isEmpty() ? "empty" : box.size() + " stack(s)"));
                        box.forEach(s -> sb.append("\n  ").append(s.getCount()).append(" ").append(s.getHoverName().getString()));
                        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    }
                    return 1;
                })));
    }

    private static int list(CommandSourceStack src, Collection<GameProfile> who) {
        List<MarketLot> lots = Market.open(src.getServer());
        if (who != null) {
            var ids = who.stream().map(GameProfile::getId).toList();
            lots = lots.stream().filter(l -> ids.contains(l.seller)).toList();
        }
        long now = Market.now();
        StringBuilder sb = new StringBuilder(lots.size() + " open lot(s).");
        for (MarketLot l : lots) {
            long hours = Math.max(0, (l.endsMs - now) / 3_600_000L);
            sb.append("\n").append(l.id).append("  ").append(l.sellerName).append("  ").append(l.describe()).append("  ")
                    .append(Bank.format(l.price)).append(" pieces  ends in ").append(hours / 24).append(" d ").append(hours % 24).append(" h");
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return lots.size();
    }
}
