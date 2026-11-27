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
 * admin panel's KITS tab. Staff tools read a kit whole with {@link #detail} and are told of every
 * change by {@code KitEvent.Changed} (since {@link #VERSION} 1).
 */
public final class BspKits {
    private BspKits() {}

    /** A kit as other mods see it. {@code items} are copies. {@code conditions} are the condition keys a player must meet, all of them (e.g. "no_totem"). */
    public record Info(String id, String name, String description, ItemStack icon, List<ItemStack> items, boolean inBrowser, boolean once,
                       long cooldownSeconds, long price, List<String> ranks, List<String> servers, String grant, boolean enabled,
                       List<String> conditions) {}

    /**
     * What a kit is to a player right now. {@code number} is the seconds, pieces or slots the state names; {@code text} the ranks or
     * servers. For "CONDITION", {@code text} is the unmet condition's key and {@code number} its number (for "no_totem": how many
     * totems the player owns).
     */
    public record Status(String state, long number, String text) {
        public boolean ready() {
            return "READY".equals(state);
        }
    }

    /**
     * The kit api's version for staff tools (BSP-Server Utilities' web panel): 1 is {@link #details}, {@link #detail},
     * {@link #choices}, the edit stamp and {@code KitEvent.Changed}. Read it to tell what this BSP-Core answers.
     */
    public static final int VERSION = 1;

    /** One stack as text: the registry id, the count and its NBT as SNBT ("" for none). */
    public record ItemRow(String item, int count, String nbt) {}

    /**
     * A kit whole, for staff tools. {@code icon} is the icon as set ({@code item} "" when none: the browser then shows the first
     * item). {@code grant} is {@code NONE}, {@code NETWORK} or {@code SERVERS}; {@code grantServers} the servers that hand it out at
     * first login for {@code SERVERS} (empty: every server). {@code created} and {@code edited} are epoch ms; {@code edited} is the
     * edit stamp a change names (0 for a kit not saved since stamps). {@code taken}, {@code lastTakenBy} and {@code lastTakenMs}
     * count every hand-out on the network. {@code missing} is how many saved items this server could not resolve (a mod not
     * loaded here); they are not in {@code items}.
     */
    public record Detail(String id, String name, String description, ItemRow icon, List<ItemRow> items, boolean enabled, boolean inBrowser,
                         boolean once, long cooldownSeconds, long price, List<String> ranks, List<String> servers, List<String> conditions,
                         String grant, List<String> grantServers, long created, long edited, int taken, String lastTakenBy, long lastTakenMs,
                         int missing) {}

    /** A condition a kit can ask for: its key (saved on the kit) and the editor's sentence for it. */
    public record Condition(String key, String label) {}

    /**
     * What a staff tool can pick from. {@code ranks} are RankUp's ladder in order when BSP-RankUp has one
     * ({@code ranksFromLadder}), else LuckPerms' group names (empty without LuckPerms: any name may be typed). {@code servers} are
     * the server ids the network has seen, this one ({@code thisServer}) first. {@code grants} are the grant kinds, and
     * {@code maxItems} how many stacks a kit may hold.
     */
    public record Choices(List<String> ranks, boolean ranksFromLadder, List<String> servers, String thisServer, List<Condition> conditions,
                          List<String> grants, int maxItems) {}

    static ItemRow row(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemRow("", 0, "");
        }
        Kit.ItemRow r = Kit.rowOf(stack);
        return new ItemRow(r.item(), r.count(), r.nbt());
    }

    private static Detail detailOf(Kit k) {
        List<ItemRow> items = new ArrayList<>(k.items.size());
        for (ItemStack s : k.items) {
            items.add(row(s));
        }
        return new Detail(k.id, k.name, k.description, row(k.icon), List.copyOf(items), k.enabled, k.inBrowser, k.once, k.cooldownSeconds,
                k.price, List.copyOf(k.ranks), List.copyOf(k.servers), List.copyOf(k.conditions), k.grant.name(), List.copyOf(k.grantServers),
                k.createdMs, k.edited, k.taken, k.lastTakenBy, k.lastTakenMs, k.missing);
    }

    /** Every kit whole, in the order they were made (server thread). */
    public static List<Detail> details(MinecraftServer server) {
        List<Detail> out = new ArrayList<>();
        for (Kit k : Kits.all(server)) {
            out.add(detailOf(k));
        }
        return out;
    }

    /** One kit whole, by id (server thread). */
    public static Optional<Detail> detail(MinecraftServer server, String id) {
        Kit k = Kits.kit(server, id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        return k == null ? Optional.empty() : Optional.of(detailOf(k));
    }

    /** The lists a kit's fields are picked from (server thread). */
    public static Choices choices(MinecraftServer server) {
        Kits.RankNames ranks = Kits.rankNames();
        List<Condition> conditions = new ArrayList<>();
        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
        for (com.mrgregles.bsp_core.kit.KitCondition c : com.mrgregles.bsp_core.kit.KitCondition.values()) {
            conditions.add(new Condition(c.key, lang.getOrDefault("gui.bsp_core.kit.cond." + c.key)));
        }
        List<String> grants = new ArrayList<>();
        for (Kit.Grant g : Kit.Grant.values()) {
            grants.add(g.name());
        }
        return new Choices(List.copyOf(ranks.names()), ranks.ladder(), List.copyOf(com.mrgregles.bsp_core.kit.KitAdmin.knownServers(server)),
                Kits.serverId(), List.copyOf(conditions), List.copyOf(grants), Kits.maxItems());
    }

    private static Info view(Kit k) {
        return new Info(k.id, k.name, k.description, k.iconOrFirst().copy(), k.copies(), k.inBrowser, k.once, k.cooldownSeconds, k.price,
                List.copyOf(k.ranks), List.copyOf(k.servers), k.grant.name(), k.enabled, List.copyOf(k.conditions));
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
