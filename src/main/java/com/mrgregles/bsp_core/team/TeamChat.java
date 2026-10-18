package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamChatPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Team chat: while a member's TEAM CHAT is on, what they type goes to the team's board (and so to
 * the members online) instead of to everyone; {@code /t} sends one line without switching. With
 * {@code team.chatTag} on, a member's lines to everyone carry the team's name in its colour. At
 * login a member hears how many lines are new and sees the pinned note once.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TeamChat {
    private TeamChat() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        if (take(p, event.getRawText())) {
            event.setCanceled(true);
            return;
        }
        if (BSPConfig.getOr(BSPConfig.TEAM_CHAT_TAG, false)) {
            Team team = Teams.teamOf(p.server, p.getUUID());
            if (team != null) {
                event.setMessage(Component.literal("[" + team.name + "] ").withStyle(team.colour).append(event.getMessage()));
            }
        }
    }

    /** Posts the line to the player's team when their chat is switched to it; true when the line was taken. */
    public static boolean take(ServerPlayer p, String raw) {
        if (!Teams.isChatOn(p.getUUID()) || !Teams.enabled()) {
            return false;
        }
        Teams.Outcome out = Teams.post(p, raw);
        if (!out.ok()) {
            if (out.message() != null) {
                p.displayClientMessage(out.message(), false);
            }
            // the channel is on but the line could not go: the player is told, and nothing reaches everyone
        }
        return true;
    }

    /** Switches the channel and tells the client, so the badge follows. */
    public static boolean toggle(ServerPlayer p) {
        boolean on = Teams.toggleChat(p.server, p.getUUID());
        p.displayClientMessage(Component.translatable(on ? "message.bsp_core.team.chat_on" : "message.bsp_core.team.chat_off").withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), false);
        sendState(p);
        return on;
    }

    /** Tells the client whether the channel is on, with the team's colour for the badge. */
    public static void sendState(ServerPlayer p) {
        Team team = Teams.teamOf(p.server, p.getUUID());
        boolean on = team != null && Teams.isChatOn(p.getUUID());
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TeamChatPacket(on, team == null ? ChatFormatting.AQUA.getId() : team.colour.getId(), team == null ? "" : team.name));
    }

    /** What a member hears on login: the count of new lines, and the pinned note. */
    public static List<Component> loginSummary(Team team, java.util.UUID player) {
        List<Component> out = new ArrayList<>();
        int unread = team.unread(player);
        if (unread > 0) {
            out.add(Component.translatable("message.bsp_core.team.unread", unread).withStyle(ChatFormatting.AQUA));
        }
        Team.Note pin = team.pinned();
        if (pin != null) {
            out.add(Component.translatable("message.bsp_core.team.pinned_login", pin.byName(), pin.text()).withStyle(team.colour));
        }
        return out;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        Team team = Teams.teamOf(p.server, p.getUUID());
        if (team == null) {
            return;
        }
        for (Component line : loginSummary(team, p.getUUID())) {
            p.displayClientMessage(line, false);
        }
        sendState(p);
    }
}
