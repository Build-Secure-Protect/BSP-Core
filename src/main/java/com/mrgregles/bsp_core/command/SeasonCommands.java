package com.mrgregles.bsp_core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.admin.SeasonReset;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /bsp season reset} (admins and the console): shows what a season reset takes and a four-digit code (the dry run, it
 * changes nothing); {@code /bsp season reset confirm <code>} within 60 s does it ({@link SeasonReset}). {@code /bsp season
 * prizegroup <place> [group]} shows or sets the LuckPerms group the winner of a place is given when a season ends ({@code -} clears it).
 */
public final class SeasonCommands {
    private SeasonCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("season").requires(SeasonCommands::allowed)
                .then(Commands.literal("reset")
                        .executes(ctx -> preview(ctx.getSource()))
                        .then(Commands.literal("confirm")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(ctx -> confirm(ctx.getSource(), StringArgumentType.getString(ctx, "code"))))))
                .then(Commands.literal("prizegroup")
                        .then(Commands.argument("place", IntegerArgumentType.integer(1, SeasonData.PLACES))
                                .executes(ctx -> showGroup(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "place")))
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .executes(ctx -> setGroup(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "place"), StringArgumentType.getString(ctx, "group"))))));
    }

    /**
     * An admin player (operator level 2 and on the admin list, as the panel's season buttons ask) or the console. Never a command block,
     * which has level 2 too: the groups set here are given through LuckPerms with the server's full rights.
     */
    static boolean allowed(CommandSourceStack src) {
        if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
            return src.hasPermission(2) && com.mrgregles.bsp_core.admin.Admins.isAdmin(p);
        }
        return src.getEntity() == null && src.hasPermission(4);
    }

    /** Who asked: a player by their UUID, else "console:<name>". */
    private static String who(CommandSourceStack src) {
        return src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p ? p.getUUID().toString() : "console:" + src.getTextName();
    }

    private static int preview(CommandSourceStack src) {
        if (SeasonReset.running()) {
            src.sendFailure(Component.literal("A season reset is running now."));
            return 0;
        }
        String code = SeasonReset.ask(who(src));
        SeasonReset.preview(src.getServer(), lines -> {
            for (String line : lines) {
                src.sendSuccess(() -> Component.literal(line), false);
            }
            src.sendSuccess(() -> Component.literal("Nothing was changed. To do it, run /bsp season reset confirm " + code + " within " + SeasonReset.CONFIRM_MS / 1000
                    + " s. It cannot be undone.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        });
        return 1;
    }

    private static int confirm(CommandSourceStack src, String code) {
        if (!SeasonReset.confirmed(who(src), code)) {
            src.sendFailure(Component.literal("Run /bsp season reset first, read what it takes, then confirm with the code it shows within "
                    + SeasonReset.CONFIRM_MS / 1000 + " s."));
            return 0;
        }
        MinecraftServer server = src.getServer();
        String by = src.getTextName();
        src.sendSuccess(() -> Component.literal("Season reset started. Every player will be sent off the network when it is done.").withStyle(ChatFormatting.GOLD), true);
        SeasonReset.run(server, by, r -> {
            if (!r.ok()) {
                src.sendFailure(Component.literal("Season reset: " + r.failure()));
                return;
            }
            // the admin was most likely sent off with everyone: the log has it too
            String line = "Season reset done: season " + r.endedSeason() + " ended; " + r.lots() + " lot(s), " + r.orders() + " order(s) and " + r.bounties()
                    + " bount" + (r.bounties() == 1 ? "y" : "ies") + " ended; " + r.here().accounts() + " bank account(s) reset; " + r.here().kicked() + " player(s) sent off here.";
            com.mrgregles.bsp_core.BSPCore.LOGGER.info(line);
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GREEN), true);
        });
        return 1;
    }

    private static int showGroup(CommandSourceStack src, int place) {
        String g = SeasonData.get(src.getServer()).prizeGroups[place - 1];
        src.sendSuccess(() -> Component.literal("Place " + place + ": " + (g.isBlank() ? "no LuckPerms group." : "LuckPerms group " + g + ".")), false);
        return 1;
    }

    private static int setGroup(CommandSourceStack src, int place, String group) {
        String g = group.equals("-") ? "" : group;
        if (!SeasonData.validGroup(g)) {
            src.sendFailure(Component.literal("A LuckPerms group name: letters, digits, _ . and - only."));
            return 0;
        }
        SeasonData.setPrizeGroup(src.getServer(), place - 1, g, src.getTextName());
        src.sendSuccess(() -> Component.literal(g.isEmpty() ? "Place " + place + " gives no LuckPerms group now."
                : "The winner of place " + place + " is given the LuckPerms group " + g + " when a season ends; it is never taken away by a reset."), true);
        return 1;
    }
}
