package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamActionPacket;
import com.mrgregles.bsp_core.network.TeamViewPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Runs what the Team screen asked for through {@link Teams}, tells the player the outcome and sends the fresh view back. */
public final class TeamActions {
    private TeamActions() {}

    public static void apply(ServerPlayer p, TeamActionPacket m) {
        Teams.Outcome out = null;
        boolean open = m.action() == TeamActionPacket.Action.VIEW;
        switch (m.action()) {
            case VIEW, REFRESH -> { }
            case CREATE -> out = Teams.create(p, m.text(), colour(m.value()));
            case INVITE -> {
                ServerPlayer invitee = p.server.getPlayerList().getPlayer(m.target());
                out = invitee == null ? new Teams.Outcome(false, Component.translatable("message.bsp_core.access.not_online").withStyle(ChatFormatting.RED)) : Teams.invite(p, invitee);
            }
            case ACCEPT -> out = Teams.accept(p);
            case DECLINE -> out = Teams.decline(p);
            case LEAVE -> out = Teams.leave(p);
            case KICK -> out = Teams.kick(p, m.target());
            case TRANSFER -> out = Teams.transfer(p, m.target());
            case RENAME -> out = Teams.rename(p, m.text());
            case RECOLOUR -> out = Teams.recolour(p, colour(m.value()));
            case DISBAND -> out = Teams.disband(p);
            case PIN -> out = Teams.pin(p, m.text());
            case POST -> out = Teams.post(p, m.text());
            case MARK_READ -> Teams.markRead(p.server, p.getUUID());
            case CHAT_TOGGLE -> TeamChat.toggle(p);
            case TELEPORT -> out = Teams.requestTeleport(p, m.target());
        }
        if (out != null && out.message() != null) {
            p.displayClientMessage(out.message(), false);
        }
        send(p, open);
    }

    /** Sends the player their current view. */
    public static void send(ServerPlayer p, boolean open) {
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), TeamViewPacket.of(p, open));
    }

    static ChatFormatting colour(int id) {
        ChatFormatting c = ChatFormatting.getById(id);
        return c == null || !c.isColor() ? ChatFormatting.AQUA : c;
    }
}
