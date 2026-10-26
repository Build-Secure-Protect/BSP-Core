package com.mrgregles.bsp_core.shop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.bank.Bank;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Shop command backups, hung under /bsp:
 * <pre>
 * /bsp shop list                                 every shop
 * /bsp shop create &lt;id&gt; &lt;name...&gt;                  a new shop (id: letters, digits, _)
 * /bsp shop delete &lt;id&gt;
 * /bsp shop add &lt;id&gt; &lt;buy&gt; &lt;sell&gt;                  add the held stack as a lot (-1 = that side off), pieces
 * /bsp shop item &lt;id&gt; &lt;index&gt; stock &lt;global&gt; &lt;perPlayer&gt; &lt;restockMinutes&gt;
 * /bsp shop item &lt;id&gt; &lt;index&gt; sell &lt;global&gt; &lt;perPlayer&gt;     sell limits in lots (-1 none)
 * /bsp shop item &lt;id&gt; &lt;index&gt; refill stock|stockplayer|sell|sellplayer &lt;rule&gt;
 *                                                never, 12h, 90m, day 00:00, monday 00:00, rotation, same
 * /bsp shop set &lt;id&gt; remote|buyonly on|off
 * /bsp shop buy &lt;id&gt; &lt;index&gt; &lt;lots&gt; [test]        buy as yourself (test: nothing moves)
 * /bsp shop sell &lt;id&gt; &lt;index&gt; &lt;lots&gt;
 * /bsp shop open &lt;id&gt; [player]                    opens the shop screen for a player (Easy NPC runs this); until the screen exists it lists the shop
 * </pre>
 * Admins change shops; moderators may list and open.
 */
public final class ShopCommands {
    private ShopCommands() {}

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("message.bsp_core.shop." + key, args);
    }

    private static boolean admin(CommandSourceStack src) {
        return src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && Admins.isAdmin(p);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("shop")
                .requires(src -> true)
                .executes(ctx -> configurator(ctx, ""))
                .then(Commands.literal("edit").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> configurator(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("create").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Shop s = Shops.create(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "name"), Shop.Kind.SHOP);
                            if (s == null) {
                                ctx.getSource().sendFailure(tr("bad_id"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> tr("created", s.id, s.name), true);
                            return 1;
                        }))))
                .then(Commands.literal("delete").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (Shops.ledger(ctx.getSource().getServer()).shop(id) == null) {
                        ctx.getSource().sendFailure(tr("no_shop", id));
                        return 0;
                    }
                    Shops.delete(ctx.getSource().getServer(), id);
                    ctx.getSource().sendSuccess(() -> tr("deleted", id), true);
                    return 1;
                })))
                .then(Commands.literal("add").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("buy", LongArgumentType.longArg(-1)).then(Commands.argument("sell", LongArgumentType.longArg(-1))
                        .executes(ShopCommands::add)))))
                .then(Commands.literal("item").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .then(Commands.literal("stock").then(Commands.argument("global", IntegerArgumentType.integer(-1)).then(Commands.argument("perPlayer", IntegerArgumentType.integer(-1)).then(Commands.argument("restockMinutes", IntegerArgumentType.integer(0))
                                .executes(ShopCommands::stock)))))
                        .then(Commands.literal("sell").then(Commands.argument("global", IntegerArgumentType.integer(-1)).then(Commands.argument("perPlayer", IntegerArgumentType.integer(-1))
                                .executes(ShopCommands::sellLimits))))
                        .then(Commands.literal("refill").then(Commands.argument("count", StringArgumentType.word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(REFILLS, b))
                                .then(Commands.argument("rule", StringArgumentType.greedyString()).executes(ShopCommands::refill)))))))
                .then(Commands.literal("set").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.literal("remote").then(Commands.literal("on").executes(ctx -> flag(ctx, "remote", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "remote", false))))
                        .then(Commands.literal("buyonly").then(Commands.literal("on").executes(ctx -> flag(ctx, "buyonly", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "buyonly", false))))))
                .then(Commands.literal("buy").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("index", IntegerArgumentType.integer(0)).then(Commands.argument("lots", IntegerArgumentType.integer(1))
                        .executes(ctx -> trade(ctx, true, false)).then(Commands.literal("test").executes(ctx -> trade(ctx, true, true)))))))
                .then(Commands.literal("sell").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("index", IntegerArgumentType.integer(0)).then(Commands.argument("lots", IntegerArgumentType.integer(1))
                        .executes(ctx -> trade(ctx, false, false))))))
                .then(Commands.literal("open").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> open(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player()).requires(src -> src.hasPermission(2)).executes(ctx -> open(ctx, EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int configurator(CommandContext<CommandSourceStack> ctx, String id) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p) || !(Admins.isAdmin(p) || Admins.isModerator(p))) {
            ctx.getSource().sendFailure(tr("admins_only"));
            return 0;
        }
        com.mrgregles.bsp_core.network.ShopActionPacket.ShopConfigOpener.open(p, id.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static Shop shop(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        Shop s = Shops.ledger(ctx.getSource().getServer()).shop(id);
        if (s == null) {
            ctx.getSource().sendFailure(tr("no_shop", id));
        }
        return s;
    }

    private static int list(CommandSourceStack src) {
        var all = Shops.ledger(src.getServer()).all();
        if (all.isEmpty()) {
            src.sendSuccess(() -> tr("none"), false);
            return 1;
        }
        for (Shop s : all.values()) {
            src.sendSuccess(() -> Component.literal(s.id + "  " + s.name + "  " + s.kind.name().toLowerCase(Locale.ROOT) + "  " + s.all().size() + " items" + (s.remote ? "  NET" : "") + (s.buyOnly ? "  buy-only" : "")), false);
        }
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        if (s == null || !(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
            return 0;
        }
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(tr("hold_item"));
            return 0;
        }
        if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(held)) {
            ctx.getSource().sendFailure(tr("placeholder"));
            return 0;
        }
        ShopItem item = new ShopItem();
        item.stack = held.copy();
        item.buy = LongArgumentType.getLong(ctx, "buy");
        item.sell = LongArgumentType.getLong(ctx, "sell");
        int index = s.add(item);
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("added", held.getCount() + " " + held.getHoverName().getString(), s.id, index), true);
        return 1;
    }

    private static int stock(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        ShopItem item = s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            ctx.getSource().sendFailure(tr("no_item"));
            return 0;
        }
        item.stockGlobal = IntegerArgumentType.getInteger(ctx, "global");
        item.stockPlayer = IntegerArgumentType.getInteger(ctx, "perPlayer");
        item.stockRefill = Refill.after(IntegerArgumentType.getInteger(ctx, "restockMinutes"));
        Shops.resetCounts(ctx.getSource().getServer(), s, IntegerArgumentType.getInteger(ctx, "index"), IntegerArgumentType.getInteger(ctx, "index"));
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("stock_set"), true);
        return 1;
    }

    /** The four counts a refill rule can be set for. */
    private static final List<String> REFILLS = List.of("stock", "stockplayer", "sell", "sellplayer");

    private static int sellLimits(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        ShopItem item = s == null ? null : s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            if (s != null) {
                ctx.getSource().sendFailure(tr("no_item"));
            }
            return 0;
        }
        item.sellGlobal = IntegerArgumentType.getInteger(ctx, "global");
        item.sellPlayer = IntegerArgumentType.getInteger(ctx, "perPlayer");
        Shops.resetCounts(ctx.getSource().getServer(), s, IntegerArgumentType.getInteger(ctx, "index"), IntegerArgumentType.getInteger(ctx, "index"));
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("sell_limits_set"), true);
        return 1;
    }

    private static int refill(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        ShopItem item = s == null ? null : s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            if (s != null) {
                ctx.getSource().sendFailure(tr("no_item"));
            }
            return 0;
        }
        String count = StringArgumentType.getString(ctx, "count").toLowerCase(java.util.Locale.ROOT);
        Refill rule = Refill.parse(StringArgumentType.getString(ctx, "rule"));
        boolean perPlayer = count.endsWith("player");
        if (!REFILLS.contains(count) || rule == null || !perPlayer && rule.kind() == Refill.Kind.SAME) {
            ctx.getSource().sendFailure(tr("bad_refill"));
            return 0;
        }
        if (rule.kind() == Refill.Kind.ROTATION && s.kind != Shop.Kind.ROTATING) {
            ctx.getSource().sendFailure(tr("rotation_only"));
            return 0;
        }
        switch (count) {
            case "stock" -> item.stockRefill = rule;
            case "stockplayer" -> item.stockPlayerRefill = rule;
            case "sell" -> item.sellRefill = rule;
            default -> item.sellPlayerRefill = rule;
        }
        Shops.resetCounts(ctx.getSource().getServer(), s, IntegerArgumentType.getInteger(ctx, "index"), IntegerArgumentType.getInteger(ctx, "index"));
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("refill_set", count, rule.text()), true);
        return 1;
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, String which, boolean on) {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        if (which.equals("remote")) {
            s.remote = on;
        } else {
            s.buyOnly = on;
        }
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("flag_set", which, on ? "on" : "off"), true);
        return 1;
    }

    private static int trade(CommandContext<CommandSourceStack> ctx, boolean buy, boolean test) {
        Shop s = shop(ctx);
        if (s == null || !(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
            return 0;
        }
        int index = IntegerArgumentType.getInteger(ctx, "index"), lots = IntegerArgumentType.getInteger(ctx, "lots");
        java.util.function.Consumer<Shops.Outcome> then = o -> {
            if (o.ok()) {
                ctx.getSource().sendSuccess(() -> tr(o.key(), o.lots(), Bank.format(o.amount()), Bank.format(o.pieces())).withStyle(ChatFormatting.GOLD), false);
            } else {
                ctx.getSource().sendFailure(tr(o.key()));
            }
        };
        if (buy) {
            Shops.buy(ctx.getSource().getServer(), p, s, index, lots, test, then);
        } else {
            Shops.sell(ctx.getSource().getServer(), p, s, index, lots, false, then);
        }
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        ServerPlayer p = target != null ? target : ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        if (p == null) {
            ctx.getSource().sendFailure(tr("players_only"));
            return 0;
        }
        ShopOpener.open(p, s, null);
        return 1;
    }
}
