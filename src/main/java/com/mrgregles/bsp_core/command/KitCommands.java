package com.mrgregles.bsp_core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.KitLedger;
import com.mrgregles.bsp_core.kit.Kits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * {@code /bsp kit ...}: the admin's view of kits from the console or chat. Kits are made and edited in
 * the admin panel's KITS tab; these commands list, give, reset and delete, and let an admin try a
 * kit on themselves.
 */
public final class KitCommands {
    private KitCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("kit")
                .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && Admins.isAdmin(p))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").then(kitArg().executes(KitCommands::info)))
                .then(Commands.literal("give").then(Commands.argument("player", GameProfileArgument.gameProfile()).then(kitArg().executes(KitCommands::give))))
                .then(Commands.literal("reset").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> reset(ctx, null))
                        .then(kitArg().executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, "kit"))))))
                .then(Commands.literal("delete").then(kitArg().executes(KitCommands::delete)))
                .then(Commands.literal("test").then(kitArg().executes(KitCommands::test)));
    }

    /** {@code /kits}: opens the kit browser. Any player. */
    public static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
        return Commands.literal("kits").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
                ctx.getSource().sendFailure(Component.literal("Players only."));
                return 0;
            }
            com.mrgregles.bsp_core.kit.KitActions.send(p, true);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> kitArg() {
        return Commands.argument("kit", StringArgumentType.word()).suggests((ctx, b) -> {
            for (Kit k : Kits.all(ctx.getSource().getServer())) {
                b.suggest(k.id);
            }
            return b.buildFuture();
        });
    }

    private static int list(CommandSourceStack src) {
        Collection<Kit> all = Kits.all(src.getServer());
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No kits yet. Make one in /bsp admin, KITS."), false);
            return 0;
        }
        for (Kit k : all) {
            String line = k.id + ": " + k.name + ", " + k.items.size() + " items, " + rule(k) + (k.enabled ? "" : ", off") + ", taken " + k.taken + (k.missing > 0 ? ", " + k.missing + " items this server cannot give" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    static String rule(Kit k) {
        StringBuilder b = new StringBuilder();
        b.append(switch (k.grant) {
            case NONE -> k.inBrowser ? "browser" : "reward only";
            case NETWORK -> "first join on the network";
            case SERVERS -> "first join on " + (k.grantServers.isEmpty() ? "every server" : String.join(" ", k.grantServers));
        });
        if (k.grant != Kit.Grant.NONE && k.inBrowser) {
            b.append(" and browser");
        }
        b.append(k.once ? ", once" : k.cooldownSeconds > 0 ? ", every " + Kits.span(k.cooldownSeconds) : "");
        if (k.price > 0) {
            b.append(", ").append(com.mrgregles.bsp_core.api.BspBank.format(k.price));
        }
        if (!k.ranks.isEmpty()) {
            b.append(", rank ").append(String.join(" or ", k.ranks));
        }
        if (!k.servers.isEmpty()) {
            b.append(", only on ").append(String.join(" ", k.servers));
        }
        return b.toString();
    }

    @Nullable
    private static Kit kit(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "kit");
        Kit k = Kits.kit(ctx.getSource().getServer(), id);
        if (k == null) {
            ctx.getSource().sendFailure(Component.literal("No kit called " + id + "."));
        }
        return k;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Kit k = kit(ctx);
        if (k == null) {
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(k.name + " (" + k.id + "): " + rule(k) + (k.enabled ? "" : ", off") + ". " + k.description), false);
        for (var s : k.items) {
            String line = "  " + s.getCount() + " x " + s.getHoverName().getString();
            src.sendSuccess(() -> Component.literal(line), false);
        }
        String taken = "Taken " + k.taken + " times" + (k.lastTakenBy.isEmpty() ? "" : ", last by " + k.lastTakenBy) + (k.missing > 0 ? "; " + k.missing + " items this server cannot give" : "");
        src.sendSuccess(() -> Component.literal(taken), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Kit k = kit(ctx);
        if (k == null) {
            return 0;
        }
        int n = 0;
        for (var profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            Kits.Outcome out = Kits.give(ctx.getSource().getServer(), profile.getId(), k.id, KitEvent.How.ADMIN);
            if (out.ok()) {
                n++;
                boolean online = ctx.getSource().getServer().getPlayerList().getPlayer(profile.getId()) != null;
                ctx.getSource().sendSuccess(() -> Component.literal("Gave " + k.name + " to " + profile.getName() + (online ? "." : "; it waits for their next login.")), true);
            } else {
                ctx.getSource().sendFailure(out.message());
            }
        }
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, @Nullable String kitId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (kitId != null && Kits.kit(ctx.getSource().getServer(), kitId) == null) {
            ctx.getSource().sendFailure(Component.literal("No kit called " + kitId + "."));
            return 0;
        }
        int n = 0;
        for (var profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            int rows = Kits.reset(ctx.getSource().getServer(), profile.getId(), kitId);
            n += rows;
            ctx.getSource().sendSuccess(() -> Component.literal(profile.getName() + ": " + rows + " kit records forgotten" + (kitId == null ? "." : " for " + kitId + ".")), true);
        }
        return n;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        Kit k = kit(ctx);
        if (k == null) {
            return 0;
        }
        Kits.Outcome out = Kits.delete(ctx.getSource().getServer(), k.id);
        if (out.ok()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Deleted " + k.name + "."), true);
            return 1;
        }
        ctx.getSource().sendFailure(out.message());
        return 0;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
            ctx.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
        Kit k = kit(ctx);
        if (k == null) {
            return 0;
        }
        Kits.Outcome out = Kits.test(p, k.id);
        if (!out.ok() && out.message() != null) {
            ctx.getSource().sendFailure(out.message());
        }
        return out.ok() ? 1 : 0;
    }

    /** For the admin's list: the ledger's view, used by the panel too. */
    static KitLedger ledger(CommandSourceStack src) {
        return KitLedger.get(src.getServer());
    }
}
