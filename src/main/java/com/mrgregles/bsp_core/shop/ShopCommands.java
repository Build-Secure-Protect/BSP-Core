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
 * /bsp shop import &lt;file&gt; [test]                  shops from config/bsp_core/shops/&lt;file&gt;.json (format 1), all or nothing; test only checks
 * /bsp shop report [days] [shop]                 pieces out and in per shop and card, top sellers, a CSV (7 days by default)
 * /bsp shop export &lt;id|all&gt; &lt;file&gt; [overwrite]      the same format back out (a shop with its sub-sections); never over a file without overwrite
 * /bsp shop set &lt;id&gt; remote|buyonly|norepeat|broadcast on|off
 * /bsp shop set &lt;id&gt; requires &lt;spec&gt;                 what the whole shop needs (see below)
 * /bsp shop item &lt;id&gt; &lt;index&gt; requires &lt;spec&gt;        none, or permission &lt;node&gt; playtime &lt;hours&gt; show|hide reason &lt;text&gt;
 * /bsp shop set &lt;id&gt; rotate &lt;day 06:00|monday 00:00|none&gt;   a rotating shop's fixed change time
 * /bsp shop item &lt;id&gt; &lt;index&gt; weight &lt;0-100&gt;              how likely a rotating shop draws the card
 * /bsp shop item &lt;id&gt; &lt;index&gt; match exact|item            selling: the exact item, or the same item whatever it carries
 * /bsp shop item &lt;id&gt; &lt;index&gt; note &lt;line|line|line&gt;      a note at the foot of the card's tooltip (none clears it)
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
                        .then(Commands.literal("requires").then(Commands.argument("spec", StringArgumentType.greedyString()).executes(ctx -> requires(ctx, true))))
                        .then(Commands.literal("note").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ShopCommands::note)))
                        .then(Commands.literal("match").then(Commands.literal("exact").executes(ctx -> match(ctx, false))).then(Commands.literal("item").executes(ctx -> match(ctx, true))))
                        .then(Commands.literal("weight").then(Commands.argument("weight", IntegerArgumentType.integer(0, 100)).executes(ShopCommands::weight)))
                        .then(Commands.literal("refill").then(Commands.argument("count", StringArgumentType.word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(REFILLS, b))
                                .then(Commands.argument("rule", StringArgumentType.greedyString()).executes(ShopCommands::refill)))))))
                .then(Commands.literal("import").requires(ShopCommands::admin).then(Commands.argument("file", StringArgumentType.word())
                        .executes(ctx -> importFile(ctx, false)).then(Commands.literal("test").executes(ctx -> importFile(ctx, true)))))
                .then(Commands.literal("report").requires(ShopCommands::admin).executes(ctx -> report(ctx, 7, null))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1)).executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "days"), null))
                                .then(Commands.argument("shop", StringArgumentType.word()).executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "days"), StringArgumentType.getString(ctx, "shop"))))))
                .then(Commands.literal("export").requires(ShopCommands::admin).then(Commands.argument("which", StringArgumentType.word()).then(Commands.argument("file", StringArgumentType.word())
                        .executes(ctx -> exportFile(ctx, false)).then(Commands.literal("overwrite").executes(ctx -> exportFile(ctx, true))))))
                .then(Commands.literal("set").requires(ShopCommands::admin).then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.literal("remote").then(Commands.literal("on").executes(ctx -> flag(ctx, "remote", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "remote", false))))
                        .then(Commands.literal("buyonly").then(Commands.literal("on").executes(ctx -> flag(ctx, "buyonly", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "buyonly", false))))
                        .then(Commands.literal("norepeat").then(Commands.literal("on").executes(ctx -> flag(ctx, "norepeat", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "norepeat", false))))
                        .then(Commands.literal("broadcast").then(Commands.literal("on").executes(ctx -> flag(ctx, "broadcast", true))).then(Commands.literal("off").executes(ctx -> flag(ctx, "broadcast", false))))
                        .then(Commands.literal("requires").then(Commands.argument("spec", StringArgumentType.greedyString()).executes(ctx -> requires(ctx, false))))
                        .then(Commands.literal("rotate").then(Commands.argument("rule", StringArgumentType.greedyString()).executes(ShopCommands::rotate)))))
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

    /** A rotating shop's fixed change time ("monday 00:00", "day 06:00") or none for its interval; the draw on show stays until the next change. */
    private static int rotate(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "rule");
        Refill at = text.trim().equalsIgnoreCase("none") ? Refill.NEVER : Refill.parse(text);
        if (at == null || !at.fixedTime() && at.kind() != Refill.Kind.NEVER) {
            ctx.getSource().sendFailure(tr("bad_rotate"));
            return 0;
        }
        s.rotateAt = at;
        s.rotateSince = Shops.now();
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("rotate_set", at.fixedTime() ? at.text() : "none"), true);
        return 1;
    }

    /** The shops' takings from the bank log: per shop, the busiest cards, the top sellers, a CSV of every item. */
    private static int report(CommandContext<CommandSourceStack> ctx, int asked, @javax.annotation.Nullable String shopId) {
        var src = ctx.getSource();
        Shop shop = shopId == null ? null : Shops.ledger(src.getServer()).shop(shopId.toLowerCase(Locale.ROOT));
        if (shopId != null && shop == null) {
            src.sendFailure(tr("no_shop", shopId));
            return 0;
        }
        int days = ShopReport.days(asked);
        ShopReport.rows(src.getServer(), Shops.now() - days * 86_400_000L, rows -> {
            if (rows == null) {
                src.sendFailure(tr("report_unreachable"));
                return;
            }
            ShopReport.Report r = ShopReport.build(src.getServer(), rows, days, asked > days, shop);
            src.sendSystemMessage(tr("report_head", days, shop == null ? "all shops" : shop.name, r.rows()).withStyle(ChatFormatting.GOLD));
            if (r.capped()) {
                src.sendSystemMessage(tr("report_capped", days).withStyle(ChatFormatting.GRAY));
            }
            if (r.rows() == 0) {
                src.sendSystemMessage(tr("report_empty").withStyle(ChatFormatting.GRAY));
                return;
            }
            r.shops().stream().limit(10).forEach(l -> src.sendSystemMessage(Component.literal("  " + l.shopName + ": out " + ShopReport.n(l.out) + ", in " + ShopReport.n(l.in) + ", net " + ShopReport.n(l.net())
                    + ", items " + l.itemsSold + " sold / " + l.itemsBought + " bought" + coinText(l))));
            src.sendSystemMessage(tr("report_cards").withStyle(ChatFormatting.YELLOW));
            r.items().stream().limit(5).forEach(l -> src.sendSystemMessage(Component.literal("  " + l.shopName + ", " + l.item + ": out " + ShopReport.n(l.out) + ", in " + ShopReport.n(l.in))));
            if (!r.sellers().isEmpty()) {
                src.sendSystemMessage(tr("report_sellers").withStyle(ChatFormatting.YELLOW));
                int[] place = {0};
                r.sellers().forEach(e -> src.sendSystemMessage(Component.literal("  " + (++place[0]) + ". " + e.getKey() + ": " + ShopReport.n(e.getValue()))));
            }
            src.sendSystemMessage(tr("report_total", ShopReport.n(r.total().out), ShopReport.n(r.total().in), ShopReport.n(r.total().net())).withStyle(ChatFormatting.GOLD));
            if (!r.file().isEmpty()) {
                src.sendSystemMessage(tr("report_file", r.file()).withStyle(ChatFormatting.GRAY));
            }
        });
        return 1;
    }

    private static String coinText(ShopReport.Line l) {
        String in = ShopReport.coins(l.coinsIn), out = ShopReport.coins(l.coinsOut);
        return (in.isEmpty() ? "" : ", coins in " + in) + (out.isEmpty() ? "" : ", coins out " + out);
    }

    /** Imports shops from config/bsp_core/shops/<file>.json, or with test only checks it; the short report here, the full one beside the file. */
    private static int importFile(CommandContext<CommandSourceStack> ctx, boolean test) {
        String name = StringArgumentType.getString(ctx, "file");
        ShopImport.Result r = ShopImport.run(ctx.getSource().getServer(), name, test);
        var src = ctx.getSource();
        if (!r.read() && r.reportFile().isEmpty()) {
            r.errors().forEach(e -> src.sendFailure(Component.literal(e)));
            return 0;
        }
        int created = r.shops().size() - r.replaced().size();
        if (!r.ok()) {
            src.sendFailure(tr("import_refused", name, r.errors().size(), r.warnings().size()));
        } else {
            src.sendSuccess(() -> tr(test ? "import_test_ok" : "import_done", name, created, r.replaced().size(), r.cards(), r.warnings().size()).withStyle(ChatFormatting.GOLD), true);
        }
        List<String> problems = new java.util.ArrayList<>(r.errors());
        problems.addAll(r.warnings());
        problems.stream().limit(10).forEach(p -> src.sendSystemMessage(Component.literal("  " + p).withStyle(r.errors().contains(p) ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        if (problems.size() > 10) {
            src.sendSystemMessage(tr("import_more", problems.size() - 10).withStyle(ChatFormatting.GRAY));
        }
        if (!r.reportFile().isEmpty()) {
            src.sendSystemMessage(tr("import_report", r.reportFile()).withStyle(ChatFormatting.GRAY));
        }
        return r.ok() ? 1 : 0;
    }

    /** Writes a shop (with its sub-sections) or all of them to config/bsp_core/shops/<file>.json; an existing file only with overwrite. */
    private static int exportFile(CommandContext<CommandSourceStack> ctx, boolean overwrite) {
        ShopExport.Result r = ShopExport.run(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "which"), StringArgumentType.getString(ctx, "file"), overwrite);
        if (r.ok()) {
            ctx.getSource().sendSuccess(() -> tr("export_done", r.shops(), r.cards(), r.file()).withStyle(ChatFormatting.GOLD), true);
            return 1;
        }
        ctx.getSource().sendFailure(tr("export_" + r.key(), r.file()));
        return 0;
    }

    /** What a card (or the whole shop) needs: "none", or permission <node>, playtime <hours>, show|hide, reason <text>. */
    private static int requires(CommandContext<CommandSourceStack> ctx, boolean card) {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        ShopItem item = card ? s.item(IntegerArgumentType.getInteger(ctx, "index")) : null;
        if (card && item == null) {
            ctx.getSource().sendFailure(tr("no_item"));
            return 0;
        }
        Requirement r = Requirement.parse(StringArgumentType.getString(ctx, "spec"));
        if (r == null) {
            ctx.getSource().sendFailure(tr("bad_requires"));
            return 0;
        }
        if (card) {
            item.requires = r;
        } else {
            s.requires = r;
        }
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("requires_set", r.text()), true);
        return 1;
    }

    /** A note at the foot of the card's tooltip: up to three lines with | between them; "none" clears it. */
    private static int note(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        ShopItem item = s == null ? null : s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            if (s != null) {
                ctx.getSource().sendFailure(tr("no_item"));
            }
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        item.setNote(text.strip().equalsIgnoreCase("none") ? "" : text);
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr(item.note.isEmpty() ? "note_cleared" : "note_set", item.note.size()), true);
        return 1;
    }

    /** Selling: the card takes its exact item, or the same item whatever data it carries (still not damaged or holding items). */
    private static int match(CommandContext<CommandSourceStack> ctx, boolean byItem) {
        Shop s = shop(ctx);
        ShopItem item = s == null ? null : s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            if (s != null) {
                ctx.getSource().sendFailure(tr("no_item"));
            }
            return 0;
        }
        item.matchItem = byItem;
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("match_set", byItem ? "item" : "exact"), true);
        return 1;
    }

    private static int weight(CommandContext<CommandSourceStack> ctx) {
        Shop s = shop(ctx);
        ShopItem item = s == null ? null : s.item(IntegerArgumentType.getInteger(ctx, "index"));
        if (item == null) {
            if (s != null) {
                ctx.getSource().sendFailure(tr("no_item"));
            }
            return 0;
        }
        item.weight = IntegerArgumentType.getInteger(ctx, "weight");
        Shops.save(ctx.getSource().getServer(), s);
        ctx.getSource().sendSuccess(() -> tr("weight_set", item.weight), true);
        return 1;
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, String which, boolean on) {
        Shop s = shop(ctx);
        if (s == null) {
            return 0;
        }
        switch (which) {
            case "remote" -> s.remote = on;
            case "norepeat" -> s.noRepeat = on;
            case "broadcast" -> s.broadcast = on;
            default -> s.buyOnly = on;
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
