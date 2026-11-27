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
 * admin panel's KITS tab. Staff tools read a kit whole with {@link #detail}, change it as an admin
 * would ({@link #create}, {@link #copy}, {@link #set}, {@link #setItems}, {@link #setIcon},
 * {@link #setEnabled}, {@link #delete}: each change names the stamp it read) and are told of every
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
     * The kit api's version for staff tools (BSP-Server Utilities' web panel): 1 (BSP-Core 1.27.0) is {@link #details},
     * {@link #detail}, {@link #choices}, the edit stamp and {@code KitEvent.Changed}; 2 (BSP-Core 1.27.1) adds the changes ({@link #create},
     * {@link #copy}, {@link #set}, {@link #setItems}, {@link #setIcon}, {@link #setEnabled}, {@link #delete}) and the player side
     * ({@link #records}, {@link #reset}, {@link #give(MinecraftServer, UUID, String, String)}); 3 (BSP-Core 1.27.2) adds {@link #ready},
     * {@code Choices.kitsOn} and {@code Detail.owed}. Read it to tell what this BSP-Core answers before calling a newer method.
     */
    public static final int VERSION = 3;

    /**
     * True when this server can answer for every kit now (since {@link #VERSION} 3): alone, always; on a network, while its database
     * is connected and once its first full read of the kits since connecting has landed (its copy is then the network's, read
     * again at every change and every five minutes).
     */
    public static boolean ready(MinecraftServer server) {
        return !com.mrgregles.bsp_core.storage.NetworkStorage.down() && com.mrgregles.bsp_core.storage.NetworkStorage.kitsRead();
    }

    /** One stack as text: the registry id, the count and its NBT as SNBT ("" for none). */
    public record ItemRow(String item, int count, String nbt) {}

    /**
     * A kit whole, for staff tools. {@code icon} is the icon as set ({@code item} "" when none: the browser then shows the first
     * item). {@code grant} is {@code NONE}, {@code NETWORK} or {@code SERVERS}; {@code grantServers} the servers that hand it out at
     * first login for {@code SERVERS} (empty: every server). {@code created} and {@code edited} are epoch ms; {@code edited} is the
     * edit stamp a change names (0 for a kit not saved since stamps). {@code taken}, {@code lastTakenBy} and {@code lastTakenMs}
     * count every hand-out on the network. {@code missing} is how many saved items this server could not resolve (a mod not
     * loaded here); they are not in {@code items}. {@code owed} (since {@link #VERSION} 3) is how many players this server's records
     * have the kit waiting for (a gift or grant not yet delivered); on a network the records of players who never logged in here
     * since are read at their login, so it can count fewer.
     */
    public record Detail(String id, String name, String description, ItemRow icon, List<ItemRow> items, boolean enabled, boolean inBrowser,
                         boolean once, long cooldownSeconds, long price, List<String> ranks, List<String> servers, List<String> conditions,
                         String grant, List<String> grantServers, long created, long edited, int taken, String lastTakenBy, long lastTakenMs,
                         int missing, int owed) {}

    /** A condition a kit can ask for: its key (saved on the kit) and the editor's sentence for it. */
    public record Condition(String key, String label) {}

    /**
     * What a staff tool can pick from. {@code ranks} are RankUp's ladder in order when BSP-RankUp has one
     * ({@code ranksFromLadder}), else LuckPerms' group names (empty without LuckPerms: any name may be typed). {@code servers} are
     * the server ids the network has seen, this one ({@code thisServer}) first. {@code grants} are the grant kinds, and
     * {@code maxItems} how many stacks a kit may hold. {@code kitsOn} (since {@link #VERSION} 3) is false when kits are switched off
     * on this server ({@code kit.enabled} in its config): nobody redeems, receives a first-login grant or is delivered a kit here (a
     * gift is still recorded as owed and waits until kits are on, or for another server), though staff may still change them.
     */
    public record Choices(List<String> ranks, boolean ranksFromLadder, List<String> servers, String thisServer, List<Condition> conditions,
                          List<String> grants, int maxItems, boolean kitsOn) {}

    static ItemRow row(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemRow("", 0, "");
        }
        Kit.ItemRow r = Kit.rowOf(stack);
        return new ItemRow(r.item(), r.count(), r.nbt());
    }

    /** How many players this server's records have each kit owed to, by kit id. */
    private static java.util.Map<String, Integer> owedCounts(MinecraftServer server) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (List<KitLedger.Redeem> rows : KitLedger.get(server).allRedeems().values()) {
            java.util.Set<String> kits = new java.util.HashSet<>();
            for (KitLedger.Redeem r : rows) {
                if (r.owed > 0) {
                    kits.add(r.kit);
                }
            }
            for (String id : kits) {
                out.merge(id, 1, Integer::sum);
            }
        }
        return out;
    }

    private static Detail detailOf(Kit k, java.util.Map<String, Integer> owed) {
        List<ItemRow> items = new ArrayList<>(k.items.size());
        for (ItemStack s : k.items) {
            items.add(row(s));
        }
        return new Detail(k.id, k.name, k.description, row(k.icon), List.copyOf(items), k.enabled, k.inBrowser, k.once, k.cooldownSeconds,
                k.price, List.copyOf(k.ranks), List.copyOf(k.servers), List.copyOf(k.conditions), k.grant.name(), List.copyOf(k.grantServers),
                k.createdMs, k.edited, k.taken, k.lastTakenBy, k.lastTakenMs, k.missing, owed.getOrDefault(k.id, 0));
    }

    /** Every kit whole, in the order they were made (server thread). */
    public static List<Detail> details(MinecraftServer server) {
        List<Detail> out = new ArrayList<>();
        java.util.Map<String, Integer> owed = owedCounts(server);
        for (Kit k : Kits.all(server)) {
            out.add(detailOf(k, owed));
        }
        return out;
    }

    /** One kit whole, by id (server thread). */
    public static Optional<Detail> detail(MinecraftServer server, String id) {
        Kit k = Kits.kit(server, id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        return k == null ? Optional.empty() : Optional.of(detailOf(k, owedCounts(server)));
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
                Kits.serverId(), List.copyOf(conditions), List.copyOf(grants), Kits.maxItems(), Kits.enabled());
    }

    // ------------------------------------------------------------------ changes, for staff tools (VERSION 1)

    /** The longest an item's data (SNBT) may be in a change. */
    public static final int SNBT_MAX = 32_768;

    /** The longest an icon's data (SNBT) may be: the icon is kept in a smaller database column than the items. */
    public static final int ICON_SNBT_MAX = 4_096;

    /** Fields {@link #set} takes. */
    public static final List<String> FIELDS = List.of("name", "description", "inBrowser", "once", "cooldownSeconds", "price", "ranks", "servers",
            "conditions", "grant", "grantServers");

    /**
     * What a change came to. {@code key} is {@code created}, {@code saved}, {@code deleted}, {@code reset} or {@code given} when
     * {@code ok}; otherwise why not:
     * {@code stale} (the kit changed since {@code edited} was read: read it again), {@code no_kit}, {@code bad_id}, {@code taken}
     * (the id is in use), {@code bad_field}, {@code bad_value}, {@code bad_item}, {@code too_many_items}, {@code missing_items} (the
     * kit holds items from a mod this server does not have: saving it here would drop them for the whole network, so change it on a
     * server that has the mod, or delete it), {@code too_large} (the kit's items and icon together would take more than
     * {@link #KIT_BYTES_MAX} bytes). {@code kit} and
     * {@code edited} are the kit and its new stamp (its stamp now after a refusal; 0 when there is no such kit or it was deleted).
     * {@code owed} is, for a delete, how many players this server's records had it still owed to (their owed kit goes with it); for a
     * reset, how many records went; for a give, how many of the kit wait for the player afterwards; 0 otherwise.
     */
    public record Answer(boolean ok, String key, String kit, long edited, int owed) {
        static Answer no(String key, @javax.annotation.Nullable Kit k) {
            return new Answer(false, key, k == null ? "" : k.id, k == null ? 0 : k.edited, 0);
        }

        static Answer done(String key, Kit k) {
            return new Answer(true, key, k.id, k.edited, 0);
        }
    }

    /** The kit if it exists and still carries {@code edited}; otherwise the refusal in {@code why[0]}. */
    @javax.annotation.Nullable
    private static Kit stamped(MinecraftServer server, String id, long edited, Answer[] why) {
        Kit k = Kits.kit(server, id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (k == null) {
            why[0] = Answer.no("no_kit", null);
            return null;
        }
        if (k.edited != edited) {
            why[0] = Answer.no("stale", k);
            return null;
        }
        return k;
    }

    /** As {@link #stamped}, refusing a kit with items this server cannot read ({@code missing_items}): a save here would lose them. */
    @javax.annotation.Nullable
    private static Kit editable(MinecraftServer server, String id, long edited, Answer[] why) {
        Kit k = stamped(server, id, edited, why);
        if (k != null && k.missing > 0) {
            why[0] = Answer.no("missing_items", k);
            return null;
        }
        return k;
    }

    private static void log(String by, String what, String id, String detail) {
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("BSP-Core kits: {} {} {}{}", com.mrgregles.bsp_core.shop.ShopRules.clean(by), what, id,
                detail.isEmpty() ? "" : " (" + detail + ")");
    }

    /**
     * The most bytes a kit's saved form may take, so the admin panel's KITS tab can still send it back with SAVE (a packet from a
     * client carries at most 32,767 bytes).
     */
    public static final int KIT_BYTES_MAX = 30_000;

    /** How many bytes the kit's saved form takes on the wire. */
    static int bytes(Kit kit) {
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            buf.writeNbt(kit.save());
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** Saves the changed draft through the kit rules; a refusal there (an item rule) is {@code bad_item}. */
    private static Answer save(MinecraftServer server, Kit draft, String key) {
        if (bytes(draft) > KIT_BYTES_MAX) {
            return Answer.no("too_large", Kits.kit(server, draft.id));
        }
        if (Kits.check(draft) != null) {
            return Answer.no(draft.items.size() > Kits.maxItems() ? "too_many_items" : "bad_item", Kits.kit(server, draft.id));
        }
        if (!Kits.put(server, draft).ok()) {
            return Answer.no("bad_value", Kits.kit(server, draft.id));
        }
        return Answer.done(key, Kits.kit(server, draft.id));
    }

    @javax.annotation.Nullable
    private static String name(String text) {
        String n = com.mrgregles.bsp_core.shop.ShopRules.clean(text);
        return n.isEmpty() || n.length() > Kit.NAME_MAX ? null : n;
    }

    /**
     * The stack a row stands for, or null when it is not one: an unknown or air item, a count from 1 to the item's stack size, data
     * that reads as SNBT within {@link #SNBT_MAX} characters and 64 levels deep, and never an InventorySync placeholder.
     */
    @javax.annotation.Nullable
    static ItemStack stackOf(@javax.annotation.Nullable ItemRow row) {
        if (row == null || row.item() == null || row.nbt() == null || row.nbt().length() > SNBT_MAX) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(row.item());
        net.minecraft.world.item.Item item = id == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(item, 1);
        if (row.count() < 1 || row.count() > stack.getMaxStackSize()) {
            return null;
        }
        stack.setCount(row.count());
        if (!row.nbt().isEmpty()) {
            net.minecraft.nbt.CompoundTag tag;
            try {
                tag = net.minecraft.nbt.TagParser.parseTag(row.nbt());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException | StackOverflowError e) {
                return null;
            }
            if (!com.mrgregles.bsp_core.shop.ShopRules.shallow(tag)) {
                return null;
            }
            stack.setTag(tag);
        }
        return com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(stack) ? null : stack;
    }

    /**
     * Makes a kit, switched off and with no items, so players see nothing until staff fill it and turn it on. {@code id} is 2 to 24
     * of {@code a-z 0-9 _} (any case, kept lower-case); {@code name} is cleaned (control characters and colour codes become spaces)
     * and must be 1 to 24 characters. {@code by} is the staff member's name, for BSP-Core's log.
     */
    public static Answer create(MinecraftServer server, String id, String name, String by) {
        String key = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        if (!Kit.ID.matcher(key).matches()) {
            return Answer.no("bad_id", null);
        }
        if (Kits.kit(server, key) != null) {
            return Answer.no("taken", Kits.kit(server, key));
        }
        String n = name(name);
        if (n == null) {
            return Answer.no("bad_value", null);
        }
        Kit k = new Kit(key, n, System.currentTimeMillis());
        k.enabled = false;
        Answer out = save(server, k, "created");
        if (out.ok()) {
            log(by, "created", key, "");
        }
        return out;
    }

    /**
     * Copies kit {@code id} as {@code newId} named {@code name}: every setting, the icon and the items, switched off. The times taken,
     * the last taker and every player's records stay with the original. No stamp: the copy reads the kit as it is now.
     */
    public static Answer copy(MinecraftServer server, String id, String newId, String name, String by) {
        Kit from = Kits.kit(server, id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (from == null) {
            return Answer.no("no_kit", null);
        }
        if (from.missing > 0) {
            return Answer.no("missing_items", from);
        }
        String key = newId == null ? "" : newId.toLowerCase(java.util.Locale.ROOT);
        if (!Kit.ID.matcher(key).matches()) {
            return Answer.no("bad_id", null);
        }
        if (Kits.kit(server, key) != null) {
            return Answer.no("taken", Kits.kit(server, key));
        }
        String n = name(name);
        if (n == null) {
            return Answer.no("bad_value", null);
        }
        Kit k = new Kit(key, n, System.currentTimeMillis());
        k.description = from.description;
        k.icon = from.icon.copy();
        k.items.addAll(from.copies());
        k.inBrowser = from.inBrowser;
        k.once = from.once;
        k.cooldownSeconds = from.cooldownSeconds;
        k.price = from.price;
        k.ranks.addAll(from.ranks);
        k.servers.addAll(from.servers);
        k.conditions.addAll(from.conditions);
        k.grant = from.grant;
        k.grantServers.addAll(from.grantServers);
        k.enabled = false;
        Answer out = save(server, k, "created");
        if (out.ok()) {
            log(by, "copied", from.id, "as " + key);
        }
        return out;
    }

    /**
     * Deletes a kit that still carries {@code edited}, with every player's record of it on the network (as the KITS tab's DELETE
     * does): a kit still owed to someone is not delivered. {@code owed} in the answer counts the players this server's records had
     * it owed to.
     */
    public static Answer delete(MinecraftServer server, String id, long edited, String by) {
        Answer[] why = new Answer[1];
        Kit k = stamped(server, id, edited, why);
        if (k == null) {
            return why[0];
        }
        int owed = owedCounts(server).getOrDefault(k.id, 0);
        if (!Kits.delete(server, k.id).ok()) {
            return Answer.no("no_kit", null);
        }
        log(by, "deleted", k.id, owed > 0 ? owed + " players were owed it" : "");
        return new Answer(true, "deleted", k.id, 0, owed);
    }

    /** Turns a kit on or off for players (off: hidden from the browser, never redeemed or granted; gifts still go out). */
    public static Answer setEnabled(MinecraftServer server, String id, boolean on, long edited, String by) {
        Answer[] why = new Answer[1];
        Kit k = editable(server, id, edited, why);
        if (k == null) {
            return why[0];
        }
        Kit draft = k.copy();
        draft.enabled = on;
        Answer out = save(server, draft, "saved");
        if (out.ok()) {
            log(by, on ? "turned on" : "turned off", k.id, "");
        }
        return out;
    }

    /**
     * Changes one setting of a kit that still carries {@code edited}. Fields and values (text):
     * <ul>
     * <li>{@code name}: 1 to 24 characters after cleaning; {@code description}: up to 80 after cleaning;</li>
     * <li>{@code inBrowser}, {@code once}: {@code true} or {@code false};</li>
     * <li>{@code cooldownSeconds}, {@code price} (Shatter Pieces): a whole number from 0 up;</li>
     * <li>{@code ranks}, {@code servers}, {@code grantServers}: names joined by commas (empty for none: everyone, everywhere);
     * ranks are kept lower-case; up to 512 characters joined;</li>
     * <li>{@code conditions}: condition keys joined by commas, each one {@link #choices} lists;</li>
     * <li>{@code grant}: {@code NONE}, {@code NETWORK} or {@code SERVERS}.</li>
     * </ul>
     * The items, the icon and the switch have their own calls.
     */
    public static Answer set(MinecraftServer server, String id, String field, String value, long edited, String by) {
        Answer[] why = new Answer[1];
        Kit k = editable(server, id, edited, why);
        if (k == null) {
            return why[0];
        }
        if (field == null || !FIELDS.contains(field)) {
            return Answer.no("bad_field", k);
        }
        String v = value == null ? "" : value.trim();
        Kit draft = k.copy();
        switch (field) {
            case "name" -> {
                String n = name(v);
                if (n == null) {
                    return Answer.no("bad_value", k);
                }
                draft.name = n;
            }
            case "description" -> {
                String d = com.mrgregles.bsp_core.shop.ShopRules.clean(v);
                if (d.length() > Kit.DESCRIPTION_MAX) {
                    return Answer.no("bad_value", k);
                }
                draft.description = d;
            }
            case "inBrowser", "once" -> {
                if (!v.equals("true") && !v.equals("false")) {
                    return Answer.no("bad_value", k);
                }
                if (field.equals("once")) {
                    draft.once = v.equals("true");
                } else {
                    draft.inBrowser = v.equals("true");
                }
            }
            case "cooldownSeconds", "price" -> {
                long n;
                try {
                    n = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    return Answer.no("bad_value", k);
                }
                if (n < 0 || n > 1_000_000_000_000L) {
                    return Answer.no("bad_value", k);
                }
                if (field.equals("price")) {
                    draft.price = n;
                } else {
                    draft.cooldownSeconds = n;
                }
            }
            case "ranks", "servers", "grantServers" -> {
                List<String> names = new ArrayList<>();
                for (String part : Kit.split(v)) {
                    String one = com.mrgregles.bsp_core.shop.ShopRules.clean(part).replace(" ", "");
                    if (field.equals("ranks")) {
                        one = one.toLowerCase(java.util.Locale.ROOT);
                    }
                    if (!one.isEmpty() && !names.contains(one)) {
                        names.add(one);
                    }
                }
                if (Kit.joined(names).length() > 512) {
                    return Answer.no("bad_value", k);
                }
                List<String> into = field.equals("ranks") ? draft.ranks : field.equals("servers") ? draft.servers : draft.grantServers;
                into.clear();
                into.addAll(names);
            }
            case "conditions" -> {
                List<String> keys = new ArrayList<>();
                for (String part : Kit.split(v)) {
                    com.mrgregles.bsp_core.kit.KitCondition c = com.mrgregles.bsp_core.kit.KitCondition.of(part);
                    if (c == null) {
                        return Answer.no("bad_value", k);
                    }
                    keys.add(c.key);
                }
                draft.conditions.clear();
                draft.conditions.addAll(keys);
            }
            case "grant" -> {
                Kit.Grant g;
                try {
                    g = Kit.Grant.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return Answer.no("bad_value", k);
                }
                draft.grant = g;
            }
            default -> {
                return Answer.no("bad_field", k);
            }
        }
        Answer out = save(server, draft, "saved");
        if (out.ok()) {
            log(by, "set " + field + " of", k.id, field.equals("name") || field.equals("description") ? "" : com.mrgregles.bsp_core.shop.ShopRules.clean(v));
        }
        return out;
    }

    /**
     * Replaces a kit's items, in slot order (up to {@link Choices#maxItems}): each row an item id, a count from 1 to its stack size,
     * and its data as SNBT ("" for none). A Shatter Totem or store credit ticket is kept blank (each taker gets a new one made for
     * them), and a totem inside another item, an InventorySync placeholder or a row that is not an item refuses the whole change
     * ({@code bad_item}).
     */
    public static Answer setItems(MinecraftServer server, String id, List<ItemRow> items, long edited, String by) {
        Answer[] why = new Answer[1];
        Kit k = editable(server, id, edited, why);
        if (k == null) {
            return why[0];
        }
        if (items == null) {
            return Answer.no("bad_value", k);
        }
        if (items.size() > Kits.maxItems()) {
            return Answer.no("too_many_items", k);
        }
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (ItemRow row : items) {
            ItemStack s = stackOf(row);
            if (s == null) {
                return Answer.no("bad_item", k);
            }
            stacks.add(s);
        }
        Kit draft = k.copy();
        draft.items.clear();
        draft.items.addAll(stacks);
        Answer out = save(server, draft, "saved");
        if (out.ok()) {
            log(by, "set the items of", k.id, stacks.size() + " stacks");
        }
        return out;
    }

    /**
     * Sets a kit's icon from a row (count 1 is used; data up to {@link #ICON_SNBT_MAX} characters), or clears it with {@code null}
     * or an empty item id (the first item shows).
     */
    public static Answer setIcon(MinecraftServer server, String id, @javax.annotation.Nullable ItemRow icon, long edited, String by) {
        Answer[] why = new Answer[1];
        Kit k = editable(server, id, edited, why);
        if (k == null) {
            return why[0];
        }
        ItemStack s = ItemStack.EMPTY;
        if (icon != null && icon.item() != null && !icon.item().isEmpty()) {
            if (icon.nbt() != null && icon.nbt().length() > ICON_SNBT_MAX) {
                return Answer.no("bad_item", k);
            }
            s = stackOf(new ItemRow(icon.item(), 1, icon.nbt() == null ? "" : icon.nbt()));
            if (s == null) {
                return Answer.no("bad_item", k);
            }
        }
        Kit draft = k.copy();
        draft.icon = s;
        Answer out = save(server, draft, "saved");
        if (out.ok()) {
            log(by, s.isEmpty() ? "cleared the icon of" : "set the icon of", k.id, "");
        }
        return out;
    }

    // ------------------------------------------------------------------ players' records, for staff tools (VERSION 1)

    /**
     * One of a player's kit records. {@code scope} is "" for the network's record (the once rule, the cooldown, the owed count) or a
     * server id for a first-login grant on that server. {@code count} is how many times they were handed it, {@code lastMs} when it
     * last was (epoch ms; the cooldown counts from here), {@code owed} how many wait for them. {@code kitName} is "" for a kit no
     * longer here.
     */
    public record Record(String kit, String kitName, String scope, int count, long lastMs, int owed) {}

    /**
     * A player's kit records, as {@link Record}s, handed to {@code then} on the server thread. With network storage they are read
     * from the database first ({@code fresh} true); without it, or when the read fails, they are this server's copy ({@code fresh}
     * false), which is current for players who logged in here.
     */
    public static void records(MinecraftServer server, UUID player, java.util.function.BiConsumer<List<Record>, Boolean> then) {
        com.mrgregles.bsp_core.storage.NetworkStorage.kitRedeemsFresh(player, fresh -> {
            List<Record> out = new ArrayList<>();
            for (KitLedger.Redeem r : KitLedger.get(server).redeems(player)) {
                Kit k = Kits.kit(server, r.kit);
                out.add(new Record(r.kit, k == null ? "" : k.name, r.scope, r.count, r.lastMs, r.owed));
            }
            then.accept(List.copyOf(out), fresh);
        });
    }

    /**
     * Forgets a player's records of kit {@code kitId}, or of every kit when it is null, on the network (as {@code /bsp kit reset}):
     * once-only kits can be taken again, cooldowns start fresh, and kits still owed to them are owed no longer. {@code owed} in the
     * answer is how many records went (this server's copy); {@code no_kit} for an id no kit has.
     */
    public static Answer reset(MinecraftServer server, UUID player, @javax.annotation.Nullable String kitId, String by) {
        String id = kitId == null ? null : kitId.toLowerCase(java.util.Locale.ROOT);
        if (player == null) {
            return Answer.no("bad_value", null);
        }
        // a kit deleted since still leaves the player's rows that records lists: those can be forgotten by its id
        if (id != null && Kits.kit(server, id) == null && KitLedger.get(server).redeems(player).stream().noneMatch(r -> r.kit.equals(id))) {
            return Answer.no("no_kit", null);
        }
        int n = Kits.reset(server, player, id);
        log(by, "reset the kit records of", player.toString(), (id == null ? "every kit" : id) + ", " + n + " records");
        Kit still = id == null ? null : Kits.kit(server, id);
        return new Answer(true, "reset", id == null ? "" : id, still == null ? 0 : still.edited, n);
    }

    /**
     * Gives kit {@code id} to the player as an admin gift (as the KITS tab's GIVE TO and {@code /bsp kit give}): no price, rank,
     * cooldown, once, condition or server rule, the kit's switch ignored. It is owed, and delivered at once when they are online here,
     * kits are on here and it fits; otherwise it waits for their next login on a server with kits on, or their TAKE IT. {@code key} is {@code given}; {@code owed} is how many of
     * this kit wait for them afterwards (0 when it was delivered now). The staff member's name goes to BSP-Core's log.
     */
    public static Answer give(MinecraftServer server, UUID player, String id, String by) {
        Kit k = Kits.kit(server, id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (k == null || player == null) {
            return Answer.no(k == null ? "no_kit" : "bad_value", k);
        }
        if (!Kits.give(server, player, k.id, KitEvent.How.ADMIN).ok()) {
            return Answer.no("no_kit", null);
        }
        KitLedger.Redeem row = KitLedger.get(server).redeem(player, k.id, Kits.NETWORK);
        int owed = row == null ? 0 : row.owed;
        log(by, "gave", k.id, "to " + player + (owed > 0 ? ", owed" : ""));
        return new Answer(true, "given", k.id, k.edited, owed);
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
