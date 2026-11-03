package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamPromptPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

/**
 * How alliance news reaches a player: an invite as a card in the prompt HUD (with a chime) and a chat line with clickable
 * Accept and Decline for the invited team's leader; a break as a red card with a warning sound for everyone in the alliance.
 * Card ids live above the team invites' so the two never take each other's card down.
 */
public final class AlliancePrompts {
    private AlliancePrompts() {}

    public static final String ACCEPT_COMMAND = "/bspally accept", DECLINE_COMMAND = "/bspally decline";
    private static final int ID_BASE = 1 << 24;
    private static int brokenIds;

    static void offer(ServerPlayer leader, Alliances.Invite invite, Team from, Alliance a, int seconds) {
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> leader), new TeamPromptPacket(ID_BASE + invite.id(), false, TeamPromptPacket.ALLY_INVITE, invite.inviter(),
                invite.inviterName(), from.name, from.colour.getId(), a.size(), Alliances.maxTeams(), a.name, "", seconds));
        leader.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        leader.displayClientMessage(chatLine(invite, from), false);
    }

    static void clear(MinecraftServer server, Alliances.Invite invite) {
        ServerPlayer online = server.getPlayerList().getPlayer(invite.toLeader());
        if (online != null) {
            BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> online), TeamPromptPacket.clear(ID_BASE + invite.id()));
        }
    }

    /** The red card: "{thief} · {team}" stealing {owner}'s totem, out of {alliance}. */
    static void broken(ServerPlayer p, CompoundTag card) {
        int id = 2 * ID_BASE + (++brokenIds & 0xFFFFFF);
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TeamPromptPacket(id, false, TeamPromptPacket.ALLY_BROKEN, card.hasUUID("ThiefId") ? card.getUUID("ThiefId")
                : new java.util.UUID(0L, 0L), card.getString("Thief"), card.getString("ThiefTeam"), card.getInt("Colour"), 0, 0, card.getString("Owner"), card.getString("Alliance"), 12));
        p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.35f, 1.4f);
    }

    /** "{inviter} of {team} asks your team to join {alliance} [Accept] [Decline]". */
    public static MutableComponent chatLine(Alliances.Invite invite, Team from) {
        MutableComponent line = Component.translatable("message.bsp_core.alliance.invite_chat", invite.inviterName(), Teams.styled(from), invite.allianceName()).withStyle(ChatFormatting.DARK_AQUA);
        line.append(" ").append(button("message.bsp_core.team.accept", ACCEPT_COMMAND, ChatFormatting.GREEN));
        line.append(" ").append(button("message.bsp_core.team.decline", DECLINE_COMMAND, ChatFormatting.RED));
        return line;
    }

    private static Component button(String key, String command, ChatFormatting colour) {
        return Component.translatable(key).withStyle(s -> s.withColor(colour).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
