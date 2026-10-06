package com.mrgregles.bsp_core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.event.FirstJoinHandler;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Operator commands:
 * <pre>
 * /bsp totem give &lt;player&gt;     give a fresh totem owned by that player (ignores the first-join record)
 * /bsp totem reset &lt;player&gt;    forget that the player was granted, so first join grants again
 * /bsp totem locate &lt;player&gt;   list where that player's placed totems stand
 * /bsp totem owner              show the owner of the totem in your main hand
 * /bsp admin                    open the admin panel (admins and moderators; no Admin Rack needed)
 * /bsp score top                the top ten of the leaderboard
 * /bsp storage status           where records are kept
 * /bsp storage test             try the MySQL connection from the config
 * /bsp storage migrate [force]  copy this server's local records into the MySQL database
 * </pre>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BSPCommands {
    private BSPCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bsp")
                .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && com.mrgregles.bsp_core.admin.Admins.isModerator(p))
                .then(Commands.literal("admin").executes(ctx -> {
                    com.mrgregles.bsp_core.admin.AdminService.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("totem").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BSPCommands::give)))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BSPCommands::reset)))
                        .then(Commands.literal("locate")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BSPCommands::locate)))
                        .then(Commands.literal("owner")
                                .executes(BSPCommands::owner))
                        // testing: set a power's level or the tier on the totem you hold (either hand) or, with none held, the placed one you look at
                        .then(Commands.literal("buff")
                                .then(Commands.argument("power", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((ctx, b) -> { for (var buff : com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.values()) b.suggest(buff.key); return b.buildFuture(); })
                                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 9))
                                                .executes(ctx -> setBuff(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "power"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(Commands.literal("tier")
                                .then(Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> setTier(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "tier")))))
                        .then(Commands.literal("recloak").executes(ctx -> recloak(ctx.getSource()))))
                .then(Commands.literal("score")
                        .then(Commands.literal("top").executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            com.mrgregles.bsp_core.score.ScoreService.refresh(src.getServer());
                            var board = com.mrgregles.bsp_core.score.ScoreService.board();
                            if (board.isEmpty()) {
                                src.sendSuccess(() -> Component.literal("No totems are owned yet."), false);
                            }
                            for (int i = 0; i < Math.min(10, board.size()); i++) {
                                var e = board.get(i);
                                String line = (i + 1) + ". " + e.name() + ": " + e.points() + " points, " + e.totems() + " totems, best tier "
                                        + com.mrgregles.bsp_core.totem.TotemUpgrades.roman(e.bestTier());
                                src.sendSuccess(() -> Component.literal(line), false);
                            }
                            return board.size();
                        })))
                .then(Commands.literal("storage").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(com.mrgregles.bsp_core.storage.NetworkStorage.status()), false);
                            return 1;
                        }))
                        .then(Commands.literal("test").executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            src.sendSuccess(() -> Component.literal("Trying the database connection..."), false);
                            com.mrgregles.bsp_core.storage.NetworkStorage.test(src.getServer(), line -> src.sendSuccess(() -> Component.literal(line), true));
                            return 1;
                        }))
                        .then(Commands.literal("migrate")
                                .executes(ctx -> migrate(ctx.getSource(), false))
                                .then(Commands.literal("force").executes(ctx -> migrate(ctx.getSource(), true))))));
    }

    /** The placed totem the player is looking at, within 8 blocks, or null. */
    @javax.annotation.Nullable
    private static com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity lookedAt(ServerPlayer player) {
        net.minecraft.world.phys.HitResult hit = player.pick(8, 0f, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bh && player.level().getBlockEntity(bh.getBlockPos()) instanceof com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity totem) {
            return totem;
        }
        return null;
    }

    private static int setBuff(CommandSourceStack src, String key, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.mrgregles.bsp_core.totem.TotemUpgrades.Buff buff = null;
        for (var b : com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.values()) {
            if (b.key.equalsIgnoreCase(key)) {
                buff = b;
            }
        }
        if (buff == null) {
            src.sendFailure(Component.literal("No power called " + key));
            return 0;
        }
        int lvl = Math.min(level, buff.maxLevel());
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (com.mrgregles.bsp_core.totem.TotemInventories.isTotem(held)) {
                com.mrgregles.bsp_core.totem.TotemUpgrades.setLevel(held, buff, lvl);
                final var fb = buff;
                src.sendSuccess(() -> Component.literal("Held totem: " + fb.key + " set to " + lvl), true);
                return 1;
            }
        }
        var totem = lookedAt(player);
        if (totem == null) {
            src.sendFailure(Component.literal("Hold a totem, or look at a placed one"));
            return 0;
        }
        totem.setUpgradeLevel(buff, lvl);
        final var fb = buff;
        src.sendSuccess(() -> Component.literal("Placed totem: " + fb.key + " set to " + lvl), true);
        return 1;
    }

    private static int setTier(CommandSourceStack src, int tier) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (com.mrgregles.bsp_core.totem.TotemInventories.isTotem(held)) {
                com.mrgregles.bsp_core.totem.TotemUpgrades.setTier(held, tier - 1);
                src.sendSuccess(() -> Component.literal("Held totem set to Tier " + com.mrgregles.bsp_core.totem.TotemUpgrades.roman(tier - 1)), true);
                return 1;
            }
        }
        var totem = lookedAt(player);
        if (totem == null) {
            src.sendFailure(Component.literal("Hold a totem, or look at a placed one"));
            return 0;
        }
        totem.setTier(tier - 1);
        src.sendSuccess(() -> Component.literal("Placed totem set to Tier " + com.mrgregles.bsp_core.totem.TotemUpgrades.roman(tier - 1)), true);
        return 1;
    }

    /** Takes the Cloaking snapshot again on the totem looked at, so a test base built after the cloak came on can be hidden. */
    private static int recloak(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var totem = lookedAt(src.getPlayerOrException());
        if (totem == null) {
            src.sendFailure(Component.literal("Look at a placed totem"));
            return 0;
        }
        totem.recloak();
        src.sendSuccess(() -> Component.literal(totem.cloak() == null ? "That totem has no Cloaking" : "Cloak snapshot taken again"), true);
        return 1;
    }

    private static int migrate(CommandSourceStack src, boolean force) {
        src.sendSuccess(() -> Component.literal("Copying this server's local records to the database..."), true);
        com.mrgregles.bsp_core.storage.NetworkStorage.migrate(src.getServer(), force,
                lines -> lines.forEach(line -> src.sendSuccess(() -> Component.literal(line), true)));
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        FirstJoinHandler.grant(target, TotemLedger.get(ctx.getSource().getServer()));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bsp_core.totem.give", target.getDisplayName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        TotemLedger.get(ctx.getSource().getServer()).clearGranted(target.getUUID());
        com.mrgregles.bsp_core.storage.NetworkStorage.clearGrant(target.getUUID());
        PlayerPersistent.setTotemGranted(target, false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bsp_core.totem.reset", target.getDisplayName()), true);
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        var positions = TotemLedger.get(ctx.getSource().getServer()).placedFor(target.getUUID());
        if (positions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.bsp_core.totem.locate.none", target.getDisplayName()), false);
            return 0;
        }
        for (GlobalPos pos : positions) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.bsp_core.totem.locate.entry",
                    target.getDisplayName(), pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(), pos.dimension().location().toString()), false);
        }
        return positions.size();
    }

    private static int owner(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        ItemStack held = self.getMainHandItem();
        if (!TotemInventories.isTotem(held)) {
            ctx.getSource().sendFailure(Component.translatable("command.bsp_core.totem.owner.not_holding"));
            return 0;
        }
        Component msg = TotemOwner.fromStack(held)
                .map(o -> Component.translatable("command.bsp_core.totem.owner.result", o.name(), o.uuid().toString()))
                .orElse(Component.translatable("tooltip.bsp_core.shatter_totem.unowned"));
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }
}
