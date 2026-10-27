package com.mrgregles.bsp_core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mrgregles.bsp_core.machine.MachinePortBlockEntity;
import com.mrgregles.bsp_core.machine.TotemForgeBlockEntity;
import com.mrgregles.bsp_core.machine.TotemForgeLedger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * {@code /bsp totemforge ...}, operators only:
 * <ul>
 *   <li>{@code clear <player>} frees a player's pour claim on this server and on the network, for a forge that is gone
 *       without being broken (a world edit, a deleted region) and so still blocks them from pouring anywhere;</li>
 *   <li>{@code fill} fills every tank of the forge you look at, to try the pour without gathering the metal;</li>
 *   <li>{@code finish} sets the totem in the forge you look at at once.</li>
 * </ul>
 */
public final class TotemForgeCommands {
    private TotemForgeCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("totemforge").requires(src -> src.hasPermission(2))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> clear(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                .then(Commands.literal("fill").executes(ctx -> fill(ctx.getSource())))
                .then(Commands.literal("finish").executes(ctx -> finish(ctx.getSource())));
    }

    /** Frees each player's claim; says where it pointed, and warns when that forge is loaded and still holds their pour. */
    public static int clear(CommandSourceStack src, Collection<GameProfile> players) {
        var ledger = TotemForgeLedger.get(src.getServer());
        for (GameProfile p : players) {
            var was = ledger.clear(p.getId());
            String where = was.map(g -> g.pos().getX() + " " + g.pos().getY() + " " + g.pos().getZ() + " in " + g.dimension().location()).orElse("none here");
            src.sendSuccess(() -> Component.literal("Freed " + p.getName() + "'s Totem Forge pour claim (" + where + ")"
                    + (TotemForgeLedger.network().enabled() ? ", and on the network" : "") + "."), true);
            var level = was.map(g -> src.getServer().getLevel(g.dimension())).orElse(null);
            if (was.isPresent() && level != null && level.isLoaded(was.get().pos()) && level.getBlockEntity(was.get().pos()) instanceof TotemForgeBlockEntity f
                    && p.getId().equals(f.pourer()) && f.pourFraction() > 0) {
                src.sendSuccess(() -> Component.literal("That forge still holds their pour: they can now start another one elsewhere."), false);
            }
        }
        return players.size();
    }

    private static int fill(CommandSourceStack src) throws CommandSyntaxException {
        TotemForgeBlockEntity forge = lookedAt(src.getPlayerOrException());
        if (forge == null) {
            src.sendFailure(Component.literal("Look at a Totem Forge (its controller, intake or a hearth)"));
            return 0;
        }
        if (forge.pourFraction() > 0) {
            src.sendFailure(Component.literal("That forge has already poured"));
            return 0;
        }
        forge.fillTanks();
        src.sendSuccess(() -> Component.literal("Filled the Totem Forge's tanks with its whole metal bill"), true);
        return 1;
    }

    private static int finish(CommandSourceStack src) throws CommandSyntaxException {
        TotemForgeBlockEntity forge = lookedAt(src.getPlayerOrException());
        if (forge == null) {
            src.sendFailure(Component.literal("Look at a Totem Forge (its controller, intake or a hearth)"));
            return 0;
        }
        if (forge.pourFraction() <= 0) {
            src.sendFailure(Component.literal("That forge has nothing pouring or setting"));
            return 0;
        }
        forge.finishSetting();
        src.sendSuccess(() -> Component.literal("The totem in that Totem Forge has set"), true);
        return 1;
    }

    /** The forge whose controller or bottom-layer port the player looks at, within 8 blocks. */
    @Nullable
    private static TotemForgeBlockEntity lookedAt(ServerPlayer player) {
        if (player.pick(8, 0f, false) instanceof net.minecraft.world.phys.BlockHitResult hit) {
            var be = player.level().getBlockEntity(hit.getBlockPos());
            if (be instanceof MachinePortBlockEntity port && port.controller() != null) {
                be = player.level().getBlockEntity(port.controller());
            }
            return be instanceof TotemForgeBlockEntity f ? f : null;
        }
        return null;
    }
}
