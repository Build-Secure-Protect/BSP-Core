package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.VaultPrizeAdminPacket;
import com.mrgregles.bsp_core.network.VaultPrizeEditPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The server side of the admin panel's VAULT tab: shows the vault prize table to moderators and admins, and saves it for admins. */
public final class VaultPrizeAdmin {
    private VaultPrizeAdmin() {}

    /** The highest weight a prize may have. */
    public static final int MAX_WEIGHT = 10_000;

    /** Moderators may look and admins may save, as on the rest of the admin panel. */
    public static void act(ServerPlayer player, VaultPrizeEditPacket msg) {
        act(player, msg, Admins.isModerator(player), Admins.isAdmin(player));
    }

    /**
     * As {@link #act(ServerPlayer, VaultPrizeEditPacket)} with the permissions already decided (GameTests: no config file is
     * written). Returns the answer sent, or null when nothing was (the sender may not even look).
     */
    @Nullable
    public static String act(ServerPlayer player, VaultPrizeEditPacket msg, boolean view, boolean edit) {
        if (!view) {
            return null; // a modified client: nothing is answered
        }
        if (msg.action() == VaultPrizeEditPacket.Action.REQUEST) {
            return send(player, "", true);
        }
        if (!edit) {
            return send(player, "Only admins can change the prize table.", false);
        }
        if (com.mrgregles.bsp_core.storage.NetworkStorage.down()) {
            // a save now would stay here and be replaced by the network's table when it connects
            return send(player, "The network database is not connected, so the table cannot be saved for every server. Try again shortly.", false);
        }
        VaultPrizes table = VaultPrizes.get(player.server);
        String problem = check(msg.stacks(), msg.weights(), table.room());
        if (problem != null) {
            return send(player, problem, false);
        }
        List<VaultPrizes.Prize> list = new ArrayList<>();
        for (int i = 0; i < msg.stacks().size(); i++) {
            list.add(new VaultPrizes.Prize(msg.stacks().get(i), msg.weights().get(i)));
        }
        table.setAndShare(list);
        BSPCore.LOGGER.info("Vault prize table saved by {}: {} prizes, total weight {}", player.getGameProfile().getName(), list.size(), table.totalWeight());
        String saved = "Saved " + list.size() + (list.size() == 1 ? " prize." : " prizes.");
        return send(player, com.mrgregles.bsp_core.storage.NetworkStorage.enabled() ? saved + " Every server uses this table." : saved, true);
    }

    /** What is wrong with a table an admin sent, or null if it can be saved; {@code room} is how many prizes this server may hold. */
    @Nullable
    public static String check(List<ItemStack> stacks, List<Integer> weights, int room) {
        if (stacks.size() != weights.size()) {
            return "The table did not arrive whole. Try again.";
        }
        if (stacks.size() > room) {
            return room < VaultPrizes.MAX_PRIZES
                    ? "This server keeps " + (VaultPrizes.MAX_PRIZES - room) + " prizes for other servers, so the table holds at most " + room + " here."
                    : "A prize table holds at most " + VaultPrizes.MAX_PRIZES + " prizes.";
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isEmpty()) {
                return "Prize " + (i + 1) + " has no item.";
            }
            if (weights.get(i) < 0 || weights.get(i) > MAX_WEIGHT) {
                return "Prize " + (i + 1) + "'s weight must be 0 to " + MAX_WEIGHT + ".";
            }
        }
        return null;
    }

    private static String send(ServerPlayer player, String message, boolean ok) {
        BSPNetwork.sendTo(player, data(player, message, ok));
        return message;
    }

    /** The tab's data, with {@code message} as the answer to the last action. */
    public static VaultPrizeAdminPacket data(ServerPlayer player, String message, boolean ok) {
        VaultPrizes table = VaultPrizes.get(player.server);
        List<ItemStack> stacks = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (VaultPrizes.Prize p : table.prizes()) {
            stacks.add(p.stack().copy());
            weights.add(p.weight());
        }
        return new VaultPrizeAdminPacket(stacks, weights, table.unknownCount(), message, ok);
    }
}
