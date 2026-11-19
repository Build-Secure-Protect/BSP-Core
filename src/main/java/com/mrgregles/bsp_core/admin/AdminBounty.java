package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BountyAdminActionPacket;
import com.mrgregles.bsp_core.network.BountyAdminPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The admin panel's BOUNTIES tab on the server. Admins and moderators may look (every open bounty with its notes, placer and
 * hunters, and the newest closed ones); only those {@link Admins#canBounty} allows may change anything: add a staff bounty, remove
 * one with or without a refund, extend it, blank its notes. Each change is done by {@link Bounties} with the staff member's name;
 * the tab gets its data again with the answer.
 */
public final class AdminBounty {
    private AdminBounty() {}

    public static final int HISTORY = 60;

    public static void handle(ServerPlayer player, BountyAdminActionPacket msg) {
        if (!Admins.isModerator(player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (msg.action() == BountyAdminActionPacket.REFRESH) {
            send(player, "");
            return;
        }
        if (!Admins.canBounty(player)) {
            send(player, "no_permission");
            return;
        }
        String by = player.getGameProfile().getName();
        switch (msg.action()) {
            case BountyAdminActionPacket.REMOVE, BountyAdminActionPacket.REMOVE_NO_REFUND ->
                    Bounties.removeByStaff(server, msg.id(), msg.action() == BountyAdminActionPacket.REMOVE, by, r -> send(player, r.key()));
            case BountyAdminActionPacket.EXTEND -> Bounties.extendByStaff(server, msg.id(), msg.addMs(), by, r -> send(player, r.key()));
            case BountyAdminActionPacket.BLANK -> Bounties.blankNotes(server, msg.id(), by, r -> send(player, r.key()));
            case BountyAdminActionPacket.ADD -> Bank.resolve(server, msg.targetName(), who -> {
                if (who == null) {
                    send(player, "unknown");
                    return;
                }
                Bounties.addByStaff(server, by, new Bounties.Ask(who.getKey(), who.getValue(), msg.coinTier(), msg.amount(), msg.bonus(), msg.bonusWithinMs(),
                        msg.durationMs(), msg.notes()), r -> send(player, r.key()));
            });
            default -> send(player, "");
        }
    }

    /** The tab's data for this staff member, with the last answer's key. */
    public static void send(ServerPlayer player, String result) {
        MinecraftServer server = player.server;
        List<Bounty> open = new ArrayList<>(Bounties.open(server));
        List<BountyStore.Closed> closed = Bounties.history(server);
        BountyAdminPacket p = new BountyAdminPacket(Bounties.now(), Admins.canBounty(player), result, open.size() > 200 ? open.subList(0, 200) : open,
                closed.size() > HISTORY ? closed.subList(0, HISTORY) : closed, Bounties.maxDays());
        BSPNetwork.sendChecked(player, p, BountyAdminPacket::encode, "the BOUNTIES tab");
    }
}
