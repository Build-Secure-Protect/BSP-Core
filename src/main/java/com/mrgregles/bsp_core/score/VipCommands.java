package com.mrgregles.bsp_core.score;

import com.mojang.brigadier.CommandDispatcher;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Staff commands for the VIPs board, under {@code /bsp vips} (operators and moderators):
 * <pre>
 *   /bsp vips          who is on the board, and the LuckPerms groups it reads
 *   /bsp vips refresh  ask LuckPerms again now
 * </pre>
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class VipCommands {
    private VipCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bsp")
                .then(Commands.literal("vips").requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer p && Admins.isModerator(p))
                        .executes(ctx -> list(ctx.getSource()))
                        .then(Commands.literal("refresh").executes(ctx -> refresh(ctx.getSource())))));
    }

    static int list(CommandSourceStack src) {
        List<VipList.Vip> vips = VipList.vips();
        String groups = String.join(", ", VipList.groups());
        if (vips.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No VIPs found in the LuckPerms groups: " + (groups.isEmpty() ? "(none set)" : groups) + "."), false);
        } else {
            StringBuilder names = new StringBuilder();
            for (VipList.Vip v : vips) {
                names.append(names.isEmpty() ? "" : ", ").append(v.name());
            }
            src.sendSuccess(() -> Component.literal(vips.size() + (vips.size() == 1 ? " VIP" : " VIPs") + " (groups: " + groups + "): ").append(Component.literal(names.toString()).withStyle(ChatFormatting.GOLD)), false);
        }
        return vips.size();
    }

    static int refresh(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Asking LuckPerms for the VIPs..."), false);
        VipList.refresh(src.getServer(), ok -> {
            if (ok) {
                list(src);
            } else {
                src.sendFailure(Component.literal("LuckPerms could not list the VIPs; the board keeps its last list. See the server log."));
            }
        });
        return 1;
    }
}
