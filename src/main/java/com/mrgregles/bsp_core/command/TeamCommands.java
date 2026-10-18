package com.mrgregles.bsp_core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.TeamLedger;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * {@code /bsp team ...}: the admin's view of teams. Players never need these; they make, join and
 * run a team from the Team screen. Team names with spaces go in quotes.
 */
public final class TeamCommands {
    private TeamCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("team")
                .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && Admins.isAdmin(p))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").then(Commands.argument("team", StringArgumentType.string()).executes(ctx -> info(ctx))))
                .then(Commands.literal("disband").then(Commands.argument("team", StringArgumentType.string()).executes(ctx -> disband(ctx))))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> kick(ctx))))
                .then(Commands.literal("rename").then(Commands.argument("team", StringArgumentType.string())
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> rename(ctx)))))
                .then(Commands.literal("lead").then(Commands.argument("team", StringArgumentType.string())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> lead(ctx)))));
    }

    /** {@code /bspteam accept | decline}: what the chat line's Accept and Decline click. Any player. */
    public static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
        return Commands.literal("bspteam")
                .then(Commands.literal("accept").executes(ctx -> answer(ctx.getSource(), true)))
                .then(Commands.literal("decline").executes(ctx -> answer(ctx.getSource(), false)));
    }

    /** {@code /t <message>}: one line to the team without switching the channel. */
    public static LiteralArgumentBuilder<CommandSourceStack> chatTree() {
        return Commands.literal("t").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
                ctx.getSource().sendFailure(Component.literal("Players only."));
                return 0;
            }
            Teams.Outcome out = Teams.post(p, StringArgumentType.getString(ctx, "message"));
            if (out.message() != null) {
                p.displayClientMessage(out.message(), false);
            }
            return out.ok() ? 1 : 0;
        }));
    }

    private static int answer(CommandSourceStack src, boolean accept) {
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Players only."));
            return 0;
        }
        Teams.Outcome out = accept ? Teams.accept(p) : Teams.decline(p);
        if (out.message() != null) {
            p.displayClientMessage(out.message(), false);
        }
        com.mrgregles.bsp_core.team.TeamActions.send(p, false);
        return out.ok() ? 1 : 0;
    }

    private static int list(CommandSourceStack src) {
        var all = TeamLedger.get(src.getServer()).all();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No teams yet."), false);
            return 0;
        }
        for (Team t : all) {
            String line = t.name + " (" + t.colour.getName() + "): " + t.size() + " of " + Teams.maxMembers() + ", leader " + Teams.nameOf(src.getServer(), t.leader);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    @Nullable
    private static Team team(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "team");
        Team t = TeamLedger.get(ctx.getSource().getServer()).byName(name);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("No team called " + name + "."));
        }
        return t;
    }

    /** A player by name: online first, then any team's record of them. */
    @Nullable
    private static UUID player(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        for (Team t : TeamLedger.get(server).all()) {
            for (var e : t.members().entrySet()) {
                if (e.getValue().name.equalsIgnoreCase(name)) {
                    return e.getKey();
                }
            }
        }
        ctx.getSource().sendFailure(Component.literal("No player called " + name + " is online or in a team."));
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Team t = team(ctx);
        if (t == null) {
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        src.sendSuccess(() -> Component.literal(t.name + " (" + t.colour.getName() + "), " + t.size() + " of " + Teams.maxMembers() + " members, " + t.messages().size() + " lines on the board"
                + (t.pinned() == null ? "" : ", pinned: " + t.pinned().text())), false);
        for (var e : t.members().entrySet()) {
            boolean online = server.getPlayerList().getPlayer(e.getKey()) != null;
            String line = "  " + e.getValue().name + (t.isLeader(e.getKey()) ? " (leader)" : "") + (online ? ", online" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return t.size();
    }

    private static int report(CommandSourceStack src, Teams.Outcome outcome, String done) {
        if (outcome.ok()) {
            src.sendSuccess(() -> Component.literal(done), true);
            return 1;
        }
        src.sendFailure(outcome.message() == null ? Component.literal("Refused.") : outcome.message());
        return 0;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) {
        Team t = team(ctx);
        return t == null ? 0 : report(ctx.getSource(), Teams.adminDisband(ctx.getSource().getServer(), t), "Disbanded " + t.name + ".");
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) {
        UUID p = player(ctx);
        return p == null ? 0 : report(ctx.getSource(), Teams.adminKick(ctx.getSource().getServer(), p), "Removed " + StringArgumentType.getString(ctx, "player") + " from their team.");
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) {
        Team t = team(ctx);
        if (t == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        return report(ctx.getSource(), Teams.adminRename(ctx.getSource().getServer(), t, name), "Renamed to " + Teams.clean(name) + ".");
    }

    private static int lead(CommandContext<CommandSourceStack> ctx) {
        Team t = team(ctx);
        if (t == null) {
            return 0;
        }
        UUID p = player(ctx);
        return p == null ? 0 : report(ctx.getSource(), Teams.adminLead(ctx.getSource().getServer(), t, p), StringArgumentType.getString(ctx, "player") + " now leads " + t.name + ".");
    }
}
