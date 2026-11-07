package com.mrgregles.bsp_core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.credit.CreditTicket;
import com.mrgregles.bsp_core.credit.StoreCredit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * {@code /bsp credit ...}: store credit from the console or chat, for BSP-Core admins and operators. Kinds are made and edited in
 * the admin panel's CREDIT tab (Store Credit task 10); until then, and for scripts, {@code kind} makes and edits them here.
 * {@code give} is the command a donation plugin runs; plain {@code /give <player> bsp_core:store_credit{Kind:"<id>"}} works too.
 */
public final class CreditCommands {
    private CreditCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("credit")
                .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && Admins.isAdmin(p))
                .then(Commands.literal("kinds").executes(ctx -> kinds(ctx.getSource())))
                .then(Commands.literal("give").then(Commands.argument("player", GameProfileArgument.gameProfile()).then(kindArg()
                        .executes(ctx -> give(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, StoreCredit.GIVE_MAX)).executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("ticket").then(Commands.argument("serial", StringArgumentType.word()).executes(CreditCommands::ticket)))
                .then(Commands.literal("void").then(Commands.argument("serial", StringArgumentType.word()).executes(CreditCommands::voidTicket)))
                .then(Commands.literal("kind").then(kindArg()
                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))
                        .then(Commands.literal("create").then(Commands.argument("value", LongArgumentType.longArg(1, CreditKind.VALUE_MAX))
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(CreditCommands::create))))
                        .then(Commands.literal("delete").executes(CreditCommands::delete))
                        .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.name = StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("value").then(Commands.argument("value", LongArgumentType.longArg(1, CreditKind.VALUE_MAX))
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.value = LongArgumentType.getLong(c, "value"))))))
                        .then(Commands.literal("time").then(Commands.argument("days", IntegerArgumentType.integer(0, 3650)).then(Commands.argument("hours", IntegerArgumentType.integer(0, 23))
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.durationMs = (IntegerArgumentType.getInteger(c, "days") * 24L + IntegerArgumentType.getInteger(c, "hours")) * 3_600_000L))))))
                        .then(Commands.literal("bind").then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.bind = BoolArgumentType.getBool(c, "on"))))))
                        .then(Commands.literal("colour").then(Commands.argument("colour", StringArgumentType.word()).suggests((c, b) -> {
                                    for (DyeColor d : DyeColor.values()) {
                                        b.suggest(d.getName());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.colour = StringArgumentType.getString(c, "colour"))))))
                        .then(Commands.literal("mark").then(Commands.argument("mark", StringArgumentType.word()).suggests((c, b) -> b.suggest("C").suggest("D").suggest("none").buildFuture())
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.mark = String.valueOf(CreditKind.mark(StringArgumentType.getString(c, "mark"))))))))
                        .then(Commands.literal("anyplot").then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.anyPlot = BoolArgumentType.getBool(c, "on"))))))
                        .then(Commands.literal("teleports").then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> edit(ctx, (k, c) -> set(() -> k.teleports = BoolArgumentType.getBool(c, "on"))))))
                        .then(listEdit("shop", k -> k.shops))
                        .then(listEdit("plot", k -> k.plots))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> kindArg() {
        return Commands.argument("kind", StringArgumentType.word()).suggests((ctx, b) -> {
            for (CreditKind k : StoreCredit.kinds(ctx.getSource().getServer())) {
                b.suggest(k.id);
            }
            return b.buildFuture();
        });
    }

    /** {@code shop add|remove <id>} and {@code plot add|remove <name>}. */
    private static LiteralArgumentBuilder<CommandSourceStack> listEdit(String word, java.util.function.Function<CreditKind, List<String>> list) {
        return Commands.literal(word)
                .then(Commands.literal("add").then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> edit(ctx, (k, c) -> set(() -> list.apply(k).add(StringArgumentType.getString(c, "place")))))))
                .then(Commands.literal("remove").then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> edit(ctx, (k, c) -> {
                            String p = StringArgumentType.getString(c, "place");
                            return list.apply(k).removeIf(s -> s.equalsIgnoreCase(p)) ? null : "\"" + p + "\" is not in the list.";
                        }))));
    }

    private static String set(Runnable r) {
        r.run();
        return null;
    }

    private static int kinds(CommandSourceStack src) {
        Collection<CreditKind> all = StoreCredit.kinds(src.getServer());
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No store credit kinds yet. Make one with /bsp credit kind <id> create <value> <name>."), false);
            return 0;
        }
        for (CreditKind k : all) {
            src.sendSuccess(() -> Component.literal(describe(k)), false);
        }
        return all.size();
    }

    private static String describe(CreditKind k) {
        return k.id + ": " + k.name + ", " + Bank.format(k.value) + " pieces, " + time(k.durationMs) + (k.bind ? ", binds when given" : "")
                + ", " + k.colour + (k.mark.isEmpty() ? "" : " " + k.mark) + ", spends at " + places(k);
    }

    static String time(long ms) {
        if (ms <= 0) {
            return "no time limit";
        }
        long h = ms / 3_600_000L;
        return (h / 24 > 0 ? h / 24 + " d " : "") + (h % 24 > 0 || h < 24 ? h % 24 + " h " : "") + "from the first purchase";
    }

    static String places(CreditKind k) {
        StringBuilder b = new StringBuilder();
        k.shops.forEach(s -> b.append(b.length() > 0 ? ", " : "").append("shop ").append(s));
        if (k.anyPlot) {
            b.append(b.length() > 0 ? ", " : "").append("any Land Office plot");
        }
        k.plots.forEach(p -> b.append(b.length() > 0 ? ", " : "").append("plots named ").append(p));
        if (k.teleports) {
            b.append(b.length() > 0 ? ", " : "").append("teleports");
        }
        return b.length() == 0 ? "nowhere yet" : b.toString();
    }

    private static int info(CommandSourceStack src, String id) {
        CreditKind k = StoreCredit.kind(src.getServer(), id);
        if (k == null) {
            src.sendFailure(Component.literal("No store credit kind \"" + id + "\"."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(describe(k)), false);
        src.sendSuccess(() -> Component.literal("Give it with /give <player> bsp_core:store_credit{Kind:\"" + k.id + "\"} or /bsp credit give <player> " + k.id), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "kind");
        if (StoreCredit.kind(src.getServer(), id) != null) {
            src.sendFailure(Component.literal("There is already a kind \"" + id + "\"; edit it or delete it first."));
            return 0;
        }
        CreditKind k = new CreditKind(id);
        k.value = LongArgumentType.getLong(ctx, "value");
        k.name = StringArgumentType.getString(ctx, "name");
        String problem = StoreCredit.saveKind(src.getServer(), k);
        if (problem != null) {
            src.sendFailure(Component.literal(problem));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Made store credit kind " + id + ". It spends nowhere until you add shops, plots or teleports."), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "kind");
        if (StoreCredit.storageDown()) {
            ctx.getSource().sendFailure(Component.literal(StoreCredit.DOWN));
            return 0;
        }
        if (!StoreCredit.deleteKind(ctx.getSource().getServer(), id)) {
            ctx.getSource().sendFailure(Component.literal("No store credit kind \"" + id + "\"."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Deleted store credit kind " + id + ". Its tickets now show \"Withdrawn\" and spend nothing."), true);
        return 1;
    }

    /** Edits a copy of a kind and saves it: {@code change} returns a sentence when the change cannot be made. */
    private static int edit(CommandContext<CommandSourceStack> ctx, BiFunction<CreditKind, CommandContext<CommandSourceStack>, String> change) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "kind");
        CreditKind k = StoreCredit.kind(src.getServer(), id);
        if (k == null) {
            src.sendFailure(Component.literal("No store credit kind \"" + id + "\"."));
            return 0;
        }
        CreditKind draft = k.copy();
        String why = change.apply(draft, ctx);
        if (why == null) {
            why = StoreCredit.saveKind(src.getServer(), draft);
        }
        if (why != null) {
            String w = why;
            src.sendFailure(Component.literal(w));
            return 0;
        }
        CreditKind saved = StoreCredit.kind(src.getServer(), id);
        src.sendSuccess(() -> Component.literal(describe(saved)), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String kind = StringArgumentType.getString(ctx, "kind");
        Collection<GameProfile> to = GameProfileArgument.getGameProfiles(ctx, "player");
        int given = 0;
        for (GameProfile p : to) {
            int n = StoreCredit.give(src.getServer(), p.getId(), kind, count);
            if (n < 0) {
                src.sendFailure(Component.literal("No store credit kind \"" + kind + "\"."));
                return 0;
            }
            given += n;
            boolean here = src.getServer().getPlayerList().getPlayer(p.getId()) != null;
            src.sendSuccess(() -> Component.literal("Gave " + n + " " + kind + " ticket" + (n == 1 ? "" : "s") + " to " + p.getName() + (here ? "." : "; they get it at their next login.")), true);
        }
        return given;
    }

    private static int ticket(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String serial = StringArgumentType.getString(ctx, "serial").replace("#", "").toUpperCase(java.util.Locale.ROOT);
        CreditTicket t = StoreCredit.ticket(src.getServer(), serial);
        if (t == null) {
            src.sendFailure(Component.literal("No store credit ticket " + serial + "."));
            return 0;
        }
        CreditKind k = StoreCredit.kind(src.getServer(), t.kind);
        String state = t.voided ? "voided" : k == null ? "withdrawn (its kind was deleted)" : t.firstUseMs <= 0 ? "not used yet" : "first purchase " + new java.util.Date(t.firstUseMs);
        src.sendSuccess(() -> Component.literal(serial + ": " + t.kind + ", " + Bank.format(t.left) + " pieces left, " + state + ", " + (t.owner == null ? "unbound" : "bound to " + t.ownerName)), false);
        return 1;
    }

    private static int voidTicket(CommandContext<CommandSourceStack> ctx) {
        String serial = StringArgumentType.getString(ctx, "serial").replace("#", "").toUpperCase(java.util.Locale.ROOT);
        if (StoreCredit.storageDown()) {
            ctx.getSource().sendFailure(Component.literal(StoreCredit.DOWN));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        boolean started = StoreCredit.voidTicket(src.getServer(), serial, ok -> {
            if (ok) {
                src.sendSuccess(() -> Component.literal("Voided ticket " + serial + ". It spends nothing, and the item disappears the next time it is seen."), true);
            } else {
                src.sendFailure(Component.literal("The network database did not take the void of " + serial + "; it is still live. Try again shortly."));
            }
        });
        if (!started) {
            src.sendFailure(Component.literal("No store credit ticket " + serial + "."));
            return 0;
        }
        return 1;
    }
}
