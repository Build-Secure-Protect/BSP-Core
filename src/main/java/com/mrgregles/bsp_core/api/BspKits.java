package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.KitLedger;
import com.mrgregles.bsp_core.kit.Kits;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Kits for other mods: read the kits, ask what one is to a player, redeem one for them under every
 * rule, or give one as a reward (no price, rank, cooldown, once or server rule: it is owed and
 * delivered when it fits). {@code KitEvent.Given} says when a kit landed. Admins make kits in the
 * admin panel's KITS tab; nothing here edits one.
 */
public final class BspKits {
    private BspKits() {}

    /** A kit as other mods see it. {@code items} are copies. */
    public record Info(String id, String name, String description, ItemStack icon, List<ItemStack> items, boolean inBrowser, boolean once,
                       long cooldownSeconds, long price, List<String> ranks, List<String> servers, String grant, boolean enabled) {}

    /** What a kit is to a player right now. {@code number} is the seconds, pieces or slots the state names; {@code text} the ranks or servers. */
    public record Status(String state, long number, String text) {
        public boolean ready() {
            return "READY".equals(state);
        }
    }

    private static Info view(Kit k) {
        return new Info(k.id, k.name, k.description, k.iconOrFirst().copy(), k.copies(), k.inBrowser, k.once, k.cooldownSeconds, k.price,
                List.copyOf(k.ranks), List.copyOf(k.servers), k.grant.name(), k.enabled);
    }

    /** Every kit this server knows, in the order they were made. */
    public static List<Info> kits(MinecraftServer server) {
        List<Info> out = new ArrayList<>();
        for (Kit k : Kits.all(server)) {
            out.add(view(k));
        }
        return out;
    }

    public static Optional<Info> kit(MinecraftServer server, String id) {
        Kit k = Kits.kit(server, id);
        return k == null ? Optional.empty() : Optional.of(view(k));
    }

    /** The player's status for a kit; empty for an unknown kit. */
    public static Optional<Status> status(ServerPlayer player, String id) {
        Kit k = Kits.kit(player.server, id);
        if (k == null) {
            return Optional.empty();
        }
        Kits.Status s = Kits.status(player, k);
        return Optional.of(new Status(s.state().name(), s.number(), s.text()));
    }

    /**
     * Redeems the kit for the player as the browser would: every rule applies and the price is
     * charged. {@code then} gets true when the items landed; the player is told why otherwise.
     */
    public static void redeem(ServerPlayer player, String id, Consumer<Boolean> then) {
        Kits.redeem(player, id, out -> {
            if (!out.ok() && out.message() != null) {
                player.displayClientMessage(out.message(), false);
            }
            then.accept(out.ok());
        });
    }

    /**
     * Gives the kit to the player as a reward: no price, rank, cooldown, once or server rule. It is
     * delivered at once when they are online here and it fits, otherwise it waits for them (a line
     * in chat and a WAITING card in their browser). False only for an unknown kit.
     */
    public static boolean give(MinecraftServer server, UUID player, String id) {
        return Kits.give(server, player, id, KitEvent.How.REWARD).ok();
    }

    /** The ids of kits waiting for the player. */
    public static List<String> owed(MinecraftServer server, UUID player) {
        return Kits.owed(server, player);
    }

    /** How many times the player took the kit (redeems and grants together). */
    public static int timesTaken(MinecraftServer server, UUID player, String id) {
        KitLedger.Redeem row = KitLedger.get(server).redeem(player, id, Kits.NETWORK);
        return row == null ? 0 : row.count;
    }
}
