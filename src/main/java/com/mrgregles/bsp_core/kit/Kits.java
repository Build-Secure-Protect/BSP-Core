package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The rules of kits: saving and deleting a kit, what a kit is to a player right now (ready, owed,
 * redeemed, on cooldown, locked by a rank, a server, a price or the space it needs), redeeming,
 * giving (a reward, an admin, a first login) and delivering what is owed. Every change goes through
 * here and posts a {@link KitEvent}. Nothing is ever put in an inventory that cannot take it: a kit
 * that does not fit becomes owed and waits.
 */
public final class Kits {
    private Kits() {}

    /** What a rule said: ok, or a message for the player who asked. */
    public record Outcome(boolean ok, @Nullable Component message) {
        static Outcome done() {
            return new Outcome(true, null);
        }

        static Outcome done(String key, Object... args) {
            return new Outcome(true, Component.translatable("message.bsp_core.kit." + key, args).withStyle(ChatFormatting.GREEN));
        }

        static Outcome fail(String key, Object... args) {
            return new Outcome(false, Component.translatable("message.bsp_core.kit." + key, args).withStyle(ChatFormatting.RED));
        }
    }

    /** What a kit is to a player right now, in the order the rules are checked. */
    public enum State {
        /** Nothing stops a redeem. */
        READY,
        /** A kit is owed to them: TAKE IT delivers one when it fits ({@code number} = slots short, 0 when it fits). */
        OWED,
        /** Once only, and already taken. */
        REDEEMED,
        /** Taken lately; {@code number} = seconds until it is ready. */
        COOLDOWN,
        /** A rank the player lacks; {@code text} = the ranks that may. */
        RANK,
        /** Not redeemable on this server; {@code text} = the servers it may be taken on. */
        SERVER,
        /** The bank balance is short; {@code number} = pieces missing. */
        PRICE,
        /** The inventory cannot take it; {@code number} = slots short. */
        SPACE,
        /** Switched off or kept out of the browser. */
        HIDDEN
    }

    public record Status(State state, long number, String text) {
        public boolean ready() {
            return state == State.READY;
        }
    }

    /** The clock the cooldowns run on; tests move it. */
    public static LongSupplier clock = System::currentTimeMillis;
    /** The groups a player is in, lower-cased; the LuckPerms bridge sets this. Empty means no rank is known. */
    public static Function<UUID, Set<String>> rankSource = uuid -> Set.of();
    /** The rank names an admin may pick: in ladder order (lowest first) when RankUp gives them, else LuckPerms' groups in any order. */
    public record RankNames(List<String> names, boolean ladder) {}
    /** Where the rank names come from; the LuckPerms bridge and the RankUp hook set this. */
    public static java.util.function.Supplier<RankNames> rankNamesSource = () -> new RankNames(List.of(), false);

    public static RankNames rankNames() {
        try {
            RankNames r = rankNamesSource.get();
            return r == null ? new RankNames(List.of(), false) : r;
        } catch (RuntimeException e) {
            BSPCore.LOGGER.warn("Rank names lookup failed", e);
            return new RankNames(List.of(), false);
        }
    }

    /** The scope of the network-wide row: the once rule, the cooldown and the owed count live on it. */
    public static final String NETWORK = "";

    // ------------------------------------------------------------------ lookups

    public static boolean enabled() {
        return BSPConfig.getOr(BSPConfig.KIT_ENABLED, true);
    }

    public static int maxItems() {
        return Math.max(1, Math.min(36, BSPConfig.getOr(BSPConfig.KIT_MAX_ITEMS, 36)));
    }

    public static boolean showRedeemed() {
        return BSPConfig.getOr(BSPConfig.KIT_SHOW_REDEEMED, true);
    }

    static long now() {
        return clock.getAsLong();
    }

    /** This server's id on the network ("local" without network storage). */
    public static String serverId() {
        return Bank.serverId();
    }

    @Nullable
    public static Kit kit(MinecraftServer server, String id) {
        return KitLedger.get(server).kit(id);
    }

    public static Collection<Kit> all(MinecraftServer server) {
        return KitLedger.get(server).all();
    }

    /** The groups the player is in, lower-cased; empty when no rank source answers. */
    public static Set<String> ranksOf(UUID player) {
        try {
            Set<String> s = rankSource.apply(player);
            return s == null ? Set.of() : s;
        } catch (RuntimeException e) {
            BSPCore.LOGGER.warn("Rank lookup failed for {}", player, e);
            return Set.of();
        }
    }

    /** Whether the player's ranks let them take the kit (any of the kit's ranks; every kit with none listed). */
    public static boolean hasRank(UUID player, Kit kit) {
        if (kit.ranks.isEmpty()) {
            return true;
        }
        Set<String> mine = ranksOf(player);
        for (String r : kit.ranks) {
            if (mine.contains(r.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** "6 h 12 m", "45 s", "3 d 2 h": a span of seconds for a message. */
    public static String span(long seconds) {
        if (seconds < 60) {
            return Math.max(1, seconds) + " s";
        }
        long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60;
        if (d > 0) {
            return d + " d" + (h > 0 ? " " + h + " h" : "");
        }
        if (h > 0) {
            return h + " h" + (m > 0 ? " " + m + " m" : "");
        }
        return m + " m";
    }

    // ------------------------------------------------------------------ the kits

    /** Null when the kit may be saved, otherwise why not. */
    @Nullable
    public static Component check(Kit kit) {
        if (!Kit.ID.matcher(kit.id).matches()) {
            return Outcome.fail("bad_id").message();
        }
        String name = kit.name.trim();
        if (name.isEmpty() || name.length() > Kit.NAME_MAX) {
            return Outcome.fail("bad_name", Kit.NAME_MAX).message();
        }
        if (kit.description.length() > Kit.DESCRIPTION_MAX) {
            return Outcome.fail("bad_description", Kit.DESCRIPTION_MAX).message();
        }
        if (kit.items.size() > maxItems()) {
            return Outcome.fail("too_many_items", maxItems()).message();
        }
        for (ItemStack s : kit.items) {
            if (s.isEmpty()) {
                return Outcome.fail("empty_item").message();
            }
        }
        if (kit.cooldownSeconds < 0 || kit.price < 0) {
            return Outcome.fail("negative").message();
        }
        return null;
    }

    /** Saves a kit (new or changed), keeping the taken counts of the one it replaces. */
    public static Outcome put(MinecraftServer server, Kit kit) {
        Component why = check(kit);
        if (why != null) {
            return new Outcome(false, why);
        }
        kit.name = kit.name.trim();
        for (int i = 0; i < kit.ranks.size(); i++) {
            kit.ranks.set(i, kit.ranks.get(i).toLowerCase(Locale.ROOT).trim());
        }
        kit.ranks.removeIf(String::isEmpty);
        kit.servers.removeIf(String::isBlank);
        kit.grantServers.removeIf(String::isBlank);
        KitLedger ledger = KitLedger.get(server);
        Kit old = ledger.kit(kit.id);
        if (old != null && old != kit) {
            kit.taken = old.taken;
            kit.lastTakenBy = old.lastTakenBy;
            kit.lastTakenMs = old.lastTakenMs;
        }
        ledger.put(kit);
        KitSync.written(kit);
        return Outcome.done("saved", kit.name);
    }

    public static Outcome delete(MinecraftServer server, String id) {
        Kit k = KitLedger.get(server).remove(id);
        if (k != null) {
            KitSync.removed(id);
        }
        return k == null ? Outcome.fail("no_kit", id) : Outcome.done("deleted", k.name);
    }

    // ------------------------------------------------------------------ what a kit is to a player

    /** The player's status for a kit, checked in the order the rules apply; one reason at a time. */
    public static Status status(ServerPlayer player, Kit kit) {
        KitLedger ledger = KitLedger.get(player.server);
        KitLedger.Redeem row = ledger.redeem(player.getUUID(), kit.id, NETWORK);
        if (row != null && row.owed > 0) {
            return new Status(State.OWED, KitFit.slotsShort(player.getInventory(), kit.items), String.valueOf(row.owed));
        }
        if (!enabled() || !kit.enabled || !kit.inBrowser) {
            return new Status(State.HIDDEN, 0, "");
        }
        if (!kit.redeemableOn(serverId())) {
            return new Status(State.SERVER, 0, String.join(", ", kit.servers));
        }
        if (!hasRank(player.getUUID(), kit)) {
            return new Status(State.RANK, 0, String.join(", ", kit.ranks));
        }
        if (row != null && row.count > 0) {
            if (kit.once) {
                return new Status(State.REDEEMED, 0, "");
            }
            long left = kit.cooldownSeconds - (now() - row.lastMs) / 1000;
            if (kit.cooldownSeconds > 0 && left > 0) {
                return new Status(State.COOLDOWN, left, "");
            }
        }
        if (kit.price > 0) {
            long have = BspBank.balance(player.server, player.getUUID());
            if (have < kit.price) {
                return new Status(State.PRICE, kit.price - have, "");
            }
        }
        int short_ = KitFit.slotsShort(player.getInventory(), kit.items);
        if (short_ > 0) {
            return new Status(State.SPACE, short_, "");
        }
        return new Status(State.READY, 0, "");
    }

    /** The status as a message for the player, or null when READY. */
    @Nullable
    public static Component explain(ServerPlayer player, Kit kit, Status s) {
        return switch (s.state()) {
            case READY -> null;
            case OWED -> s.number() > 0 ? Outcome.fail("owed_space", kit.name, s.number()).message() : Outcome.done("owed_ready", kit.name).message();
            case REDEEMED -> Outcome.fail("redeemed", kit.name).message();
            case COOLDOWN -> Outcome.fail("cooldown", kit.name, span(s.number())).message();
            case RANK -> Outcome.fail("rank", kit.name, s.text()).message();
            case SERVER -> Outcome.fail("server", kit.name, s.text()).message();
            case PRICE -> Outcome.fail("price", kit.name, BspBank.format(kit.price), BspBank.format(BspBank.balance(player.server, player.getUUID()))).message();
            case SPACE -> Outcome.fail("space", kit.name, s.number()).message();
            case HIDDEN -> Outcome.fail("hidden", kit.name).message();
        };
    }

    // ------------------------------------------------------------------ redeeming

    /**
     * Redeems a kit from the browser: every rule is checked, the price is taken last (after the space
     * check, so a short inventory never costs anything), and the items are given. The answer comes
     * through {@code then} on the server thread, at once on a single server and when the bank has
     * answered on a network.
     */
    public static void redeem(ServerPlayer player, String id, Consumer<Outcome> then) {
        Kit kit = kit(player.server, id);
        if (kit == null) {
            then.accept(Outcome.fail("no_kit", id));
            return;
        }
        Status s = status(player, kit);
        if (s.state() == State.OWED) {
            then.accept(take(player, id));
            return;
        }
        if (!s.ready()) {
            then.accept(new Outcome(false, explain(player, kit, s)));
            return;
        }
        if (kit.price <= 0) {
            then.accept(hand(player, kit, KitEvent.How.REDEEMED, true));
            return;
        }
        BspBank.charge(player.server, player.getUUID(), kit.price, "Kits", kit.name, paid -> {
            if (!paid) {
                then.accept(Outcome.fail("price", kit.name, BspBank.format(kit.price), BspBank.format(BspBank.balance(player.server, player.getUUID()))));
                return;
            }
            if (!player.isAlive() || player.hasDisconnected()) {
                BspBank.credit(player.server, player.getUUID(), kit.price, "Kits", kit.name + " (refund)", ok -> {});
                then.accept(Outcome.fail("gone"));
                return;
            }
            int short_ = KitFit.slotsShort(player.getInventory(), kit.items);
            if (short_ > 0) {
                BspBank.credit(player.server, player.getUUID(), kit.price, "Kits", kit.name + " (refund)", ok -> {});
                then.accept(Outcome.fail("space", kit.name, short_));
                return;
            }
            then.accept(hand(player, kit, KitEvent.How.REDEEMED, true));
        });
    }

    /** TAKE IT: delivers one owed kit if it fits. */
    public static Outcome take(ServerPlayer player, String id) {
        Kit kit = kit(player.server, id);
        if (kit == null) {
            return Outcome.fail("no_kit", id);
        }
        KitLedger ledger = KitLedger.get(player.server);
        KitLedger.Redeem row = ledger.redeem(player.getUUID(), kit.id, NETWORK);
        if (row == null || row.owed <= 0) {
            return Outcome.fail("not_owed", kit.name);
        }
        int short_ = KitFit.slotsShort(player.getInventory(), kit.items);
        if (short_ > 0) {
            return Outcome.fail("space", kit.name, short_);
        }
        row.owed--;
        ledger.changed();
        KitSync.row(player.getUUID(), row);
        return hand(player, kit, KitEvent.How.REWARD, true);
    }

    /** TEST ON ME: the kit at once, no record kept, no price; fails when it does not fit. */
    public static Outcome test(ServerPlayer player, String id) {
        Kit kit = kit(player.server, id);
        if (kit == null) {
            return Outcome.fail("no_kit", id);
        }
        int short_ = KitFit.slotsShort(player.getInventory(), kit.items);
        if (short_ > 0) {
            return Outcome.fail("space", kit.name, short_);
        }
        return hand(player, kit, KitEvent.How.TEST, false);
    }

    // ------------------------------------------------------------------ giving

    /**
     * Makes a kit owed to a player (a reward, an admin's gift, a first login) and delivers it at once
     * when they are online here and it fits. Price, rank, cooldown, once and server rules do not
     * apply. {@code how} is what the event says. Fails only for an unknown kit.
     */
    public static Outcome give(MinecraftServer server, UUID player, String id, KitEvent.How how) {
        return give(server, player, id, how, true);
    }

    /** As {@link #give}, with {@code deliverNow} false to leave the kit owed even for a player online here (the login path, which delivers once the inventory is settled). */
    public static Outcome give(MinecraftServer server, UUID player, String id, KitEvent.How how, boolean deliverNow) {
        Kit kit = kit(server, id);
        if (kit == null) {
            return Outcome.fail("no_kit", id);
        }
        KitLedger ledger = KitLedger.get(server);
        KitLedger.Redeem row = ledger.redeemOrNew(player, kit.id, NETWORK);
        row.owed++;
        ledger.changed();
        KitSync.row(player, row);
        MinecraftForge.EVENT_BUS.post(new KitEvent.Owed(server, kit.id, kit.name, player, how));
        ServerPlayer online = deliverNow ? server.getPlayerList().getPlayer(player) : null;
        if (online != null) {
            deliverOwed(online, how);
        }
        return Outcome.done("gave", kit.name);
    }

    /**
     * Delivers every kit owed to the player that fits, in the order they were owed, and tells them
     * about any that still waits. Returns how many kits were handed over. Called after a login once
     * the inventory is settled, and by {@link #give} for a player online here.
     */
    public static int deliverOwed(ServerPlayer player, KitEvent.How how) {
        return deliverOwed(player, id -> how);
    }

    /** As above, with the event's {@code how} chosen per kit id. */
    public static int deliverOwed(ServerPlayer player, Function<String, KitEvent.How> how) {
        if (!enabled() || player.isDeadOrDying()) {
            return 0; // a dead player's inventory is not carried to the respawned one: the kits stay owed until the respawn
        }
        KitLedger ledger = KitLedger.get(player.server);
        int delivered = 0;
        for (KitLedger.Redeem row : ledger.redeems(player.getUUID())) {
            if (row.owed <= 0 || !row.scope.equals(NETWORK)) {
                continue;
            }
            Kit kit = ledger.kit(row.kit);
            if (kit == null) {
                row.owed = 0;
                ledger.changed();
                KitSync.row(player.getUUID(), row);
                continue;
            }
            while (row.owed > 0) {
                int short_ = KitFit.slotsShort(player.getInventory(), kit.items);
                if (short_ > 0) {
                    player.displayClientMessage(Outcome.fail("owed_space", kit.name, short_).message(), false);
                    break;
                }
                row.owed--;
                ledger.changed();
                hand(player, kit, how.apply(kit.id), true);
                KitSync.row(player.getUUID(), row);
                delivered++;
            }
        }
        return delivered;
    }

    /** Kits owed to a player, by id, with how many of each. */
    public static List<String> owed(MinecraftServer server, UUID player) {
        List<String> out = new ArrayList<>();
        for (KitLedger.Redeem row : KitLedger.get(server).redeems(player)) {
            if (row.owed > 0 && row.scope.equals(NETWORK)) {
                out.add(row.kit);
            }
        }
        return out;
    }

    /** Forgets a player's rows for one kit (or every kit when null): cooldowns, once, owed. */
    public static int reset(MinecraftServer server, UUID player, @Nullable String kit) {
        int n = KitLedger.get(server).reset(player, kit);
        NetworkStorage.kitRedeemReset(player, kit);
        return n;
    }

    /** Puts the items in the inventory (checked to fit by the caller), records it when asked, posts the event, tells the player. */
    private static Outcome hand(ServerPlayer player, Kit kit, KitEvent.How how, boolean record) {
        for (ItemStack s : kit.copies()) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        if (record) {
            KitLedger ledger = KitLedger.get(player.server);
            KitLedger.Redeem row = ledger.redeemOrNew(player.getUUID(), kit.id, NETWORK);
            row.count++;
            row.lastMs = now();
            kit.taken++;
            kit.lastTakenBy = player.getGameProfile().getName();
            kit.lastTakenMs = row.lastMs;
            ledger.changed();
            KitSync.row(player.getUUID(), row);
            KitSync.taken(kit);
        }
        MinecraftForge.EVENT_BUS.post(new KitEvent.Given(player.server, kit.id, kit.name, player.getUUID(), how));
        Component note = Outcome.done(how == KitEvent.How.TEST ? "tested" : "given", kit.name).message();
        player.displayClientMessage(note, false);
        return new Outcome(true, note);
    }
}
