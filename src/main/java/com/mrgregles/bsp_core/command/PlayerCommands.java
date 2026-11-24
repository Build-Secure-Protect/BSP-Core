package com.mrgregles.bsp_core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_core.admin.PlayerReset;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /bsp player reset <name>} (operators and the console): shows what resetting the player to a brand-new player takes, on
 * every server and in every BSP mod; {@code /bsp player reset <name> confirm} within 30 s does it ({@link PlayerReset}). The name is
 * looked up online, in this server's bank records, in the network's and in the profile cache, so a player who never joined this
 * server is found; a UUID works too.
 */
public final class PlayerCommands {
    private PlayerCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("player").requires(src -> src.hasPermission(2))
                .then(Commands.literal("reset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
                                .executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> reset(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true)))));
    }

    private static int reset(CommandSourceStack src, String name, boolean confirm) {
        MinecraftServer server = src.getServer();
        find(server, name, hit -> {
            if (hit == null) {
                src.sendFailure(Component.literal("No player called " + name + " is known on this server or the network."));
                return;
            }
            UUID id = hit.getKey();
            String found = hit.getValue();
            // the confirm counts only for who asked: a player by their UUID (a name is not unique: a player could be called "Server")
            String who = src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p ? p.getUUID().toString() : "console:" + src.getTextName();
            String by = src.getTextName();
            if (!confirm) {
                PlayerReset.ask(who, id, found);
                for (String line : PlayerReset.preview(server, id, found)) {
                    src.sendSuccess(() -> Component.literal(line), false);
                }
                src.sendSuccess(() -> Component.literal("Run /bsp player reset " + found + " confirm within " + PlayerReset.CONFIRM_MS / 1000 + " s to do it. It cannot be undone.")
                        .withStyle(ChatFormatting.GOLD), false);
                return;
            }
            if (!PlayerReset.confirmed(who, id)) {
                src.sendFailure(Component.literal("Run /bsp player reset " + found + " first and read what it takes; then confirm within " + PlayerReset.CONFIRM_MS / 1000 + " s."));
                return;
            }
            PlayerReset.reset(server, id, found, by, null, r -> {
                if (!r.ok()) {
                    src.sendFailure(Component.literal("Player reset of " + found + " not done: " + r.failure() + "."));
                    return;
                }
                var h = r.here();
                String line = found + " is a new player: " + (r.team().isEmpty() ? "" : r.team() + ", ") + r.lots() + " lot(s), " + r.orders() + " order(s) and "
                        + r.bounties() + " bounty(ies) ended, " + h.totems() + " totem(s) gone here, "
                        + (h.carriedCleared() ? "what they carry wiped" : h.filesMoved() > 0 ? h.filesMoved() + " file(s) of theirs moved to " + PlayerReset.BACKUP_DIR : "what they carry is wiped at their next login")
                        + (h.rankUp() ? ", RankUp reset" : "") + (h.awardsRemoved() > 0 ? ", " + h.awardsRemoved() + " award group(s) asked of LuckPerms" : "")
                        + (com.mrgregles.bsp_core.storage.NetworkStorage.enabled() ? "; every server told" : "") + "."
                        + (r.failure().isEmpty() ? "" : " Warning: " + r.failure() + ".");
                src.sendSuccess(() -> Component.literal(line), true);
            });
        });
        return 1;
    }

    /** A player's name without opening a bank account for a UUID typed wrong: online, this server's bank record, the profile cache, else the UUID. */
    private static String nameOf(MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var ledger = com.mrgregles.bsp_core.bank.BankLedger.get(server);
        if (ledger.has(id) && !ledger.account(id).name.isEmpty()) {
            return ledger.account(id).name;
        }
        if (server.getProfileCache() != null) {
            var p = server.getProfileCache().get(id);
            if (p.isPresent()) {
                return p.get().getName();
            }
        }
        return id.toString();
    }

    /** A UUID as typed, or a name looked up the way the bank does (online, known here, on the network, the profile cache). */
    private static void find(MinecraftServer server, String text, Consumer<Map.Entry<UUID, String>> then) {
        try {
            UUID id = UUID.fromString(text);
            then.accept(Map.entry(id, nameOf(server, id)));
            return;
        } catch (IllegalArgumentException notUuid) {
            // a name
        }
        com.mrgregles.bsp_core.bank.Bank.resolve(server, text, then);
    }
}
