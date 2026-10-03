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
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("totem")
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
                                .executes(BSPCommands::owner))));
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
