package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamPromptPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * How an invite reaches the invited player: the card in the prompt HUD (with a quiet chime) and a
 * chat line with clickable Accept and Decline, which is how a player inside another screen answers.
 */
public final class TeamPrompts {
    private TeamPrompts() {}

    public static final String ACCEPT_COMMAND = "/bspteam accept", DECLINE_COMMAND = "/bspteam decline";

    static void offer(ServerPlayer invitee, Teams.Invite invite, Team team, int seconds) {
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> invitee), new TeamPromptPacket(invite.id(), false, invite.inviter(), invite.inviterName(), team.name, team.colour.getId(),
                team.size(), Teams.maxMembers(), Teams.nameOf(invitee.server, team.leader), seconds));
        invitee.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        invitee.displayClientMessage(chatLine(invite, team), false);
    }

    /** Takes the card down for the invitee if they are online here. */
    static void clear(MinecraftServer server, Teams.Invite invite) {
        ServerPlayer online = server.getPlayerList().getPlayer(invite.invitee());
        if (online != null) {
            BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> online), TeamPromptPacket.clear(invite.id()));
        }
    }

    /** "{inviter} invited you to join {team} [Accept] [Decline]" with the clicks wired to {@code /bspteam}. */
    public static MutableComponent chatLine(Teams.Invite invite, Team team) {
        MutableComponent line = Component.translatable("message.bsp_core.team.invite_chat", invite.inviterName(), Teams.styled(team)).withStyle(ChatFormatting.AQUA);
        line.append(" ").append(button("message.bsp_core.team.accept", ACCEPT_COMMAND, ChatFormatting.GREEN));
        line.append(" ").append(button("message.bsp_core.team.decline", DECLINE_COMMAND, ChatFormatting.RED));
        return line;
    }

    private static Component button(String key, String command, ChatFormatting colour) {
        return Component.translatable(key).withStyle(s -> s.withColor(colour).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** For other callers that only have the id of the invited player. */
    public static boolean hasPrompt(UUID player) {
        return Teams.pending(player).isPresent();
    }
}
