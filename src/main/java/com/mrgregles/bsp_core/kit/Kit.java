package com.mrgregles.bsp_core.kit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One kit: an id, a name, a line of description, an icon, up to {@link Kits#maxItems()} stacks with
 * their full NBT in slot order, and the rules that say who gets it and when. The rules themselves
 * run in {@link Kits}; this class only holds and saves the kit. Items a server cannot resolve (a
 * mod missing there) are dropped at load and counted in {@link #missing}.
 */
public final class Kit {
    /** When a kit is handed out by itself. */
    public enum Grant {
        /** Never by itself: players redeem it, or a mod or an admin gives it. */
        NONE,
        /** Once, on the player's first login anywhere on the network. */
        NETWORK,
        /** On the player's first login on each server in {@link #grantServers} (every server when the list is empty). */
        SERVERS;

        public static Grant of(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    public static final Pattern ID = Pattern.compile("[a-z0-9_]{2,24}");
    public static final int NAME_MAX = 24, DESCRIPTION_MAX = 80;

    public final String id;
    public String name;
    public String description = "";
    /** The icon shown for the kit; empty means the first item. */
    public ItemStack icon = ItemStack.EMPTY;
    /** The stacks, in slot order, never empty entries. */
    public final List<ItemStack> items = new ArrayList<>();
    /** Shown in the players' browser. */
    public boolean inBrowser = true;
    /** Redeemable once ever (per player, network-wide). */
    public boolean once;
    /** Seconds between redeems; 0 means none. */
    public long cooldownSeconds;
    /** Price in Shatter Pieces from the bank; 0 is free. */
    public long price;
    /** LuckPerms group names, lower-case; any of them lets a player redeem. Empty means everyone. */
    public final List<String> ranks = new ArrayList<>();
    /** Server ids the kit may be redeemed on; empty means everywhere. */
    public final List<String> servers = new ArrayList<>();
    public Grant grant = Grant.NONE;
    /** For {@link Grant#SERVERS}: the servers that hand it out at first login; empty means every server. */
    public final List<String> grantServers = new ArrayList<>();
    public boolean enabled = true;
    public final long createdMs;
    /** How many times it was handed out, and the last player it went to (kept for the admin's list). */
    public int taken;
    public String lastTakenBy = "";
    public long lastTakenMs;
    /** Items this server could not resolve when the kit was loaded; transient. */
    public int missing;

    public Kit(String id, String name, long createdMs) {
        this.id = id;
        this.name = name;
        this.createdMs = createdMs;
    }

    /** The icon to draw: the one set, else the first item, else nothing. */
    public ItemStack iconOrFirst() {
        if (!icon.isEmpty()) {
            return icon;
        }
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    /** Whether the kit may be redeemed on the server with this id. */
    public boolean redeemableOn(String serverId) {
        return servers.isEmpty() || servers.contains(serverId);
    }

    /** Whether the kit is handed out at first login on the server with this id. */
    public boolean grantsOn(String serverId) {
        return grant == Grant.SERVERS && (grantServers.isEmpty() || grantServers.contains(serverId));
    }

    /** Copies of the stacks, so a caller may hand them out. */
    public List<ItemStack> copies() {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemStack s : items) {
            out.add(s.copy());
        }
        return out;
    }

    /** The same kit with the same items and rules, a fresh object (for the editor's draft). */
    public Kit copy() {
        Kit k = new Kit(id, name, createdMs);
        k.description = description;
        k.icon = icon.copy();
        for (ItemStack s : items) {
            k.items.add(s.copy());
        }
        k.inBrowser = inBrowser;
        k.once = once;
        k.cooldownSeconds = cooldownSeconds;
        k.price = price;
        k.ranks.addAll(ranks);
        k.servers.addAll(servers);
        k.grant = grant;
        k.grantServers.addAll(grantServers);
        k.enabled = enabled;
        k.taken = taken;
        k.lastTakenBy = lastTakenBy;
        k.lastTakenMs = lastTakenMs;
        k.missing = missing;
        return k;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Name", name);
        t.putString("Description", description);
        if (!icon.isEmpty()) {
            t.put("Icon", icon.save(new CompoundTag()));
        }
        ListTag list = new ListTag();
        for (ItemStack s : items) {
            list.add(s.save(new CompoundTag()));
        }
        t.put("Items", list);
        t.putBoolean("InBrowser", inBrowser);
        t.putBoolean("Once", once);
        t.putLong("Cooldown", cooldownSeconds);
        t.putLong("Price", price);
        t.put("Ranks", strings(ranks));
        t.put("Servers", strings(servers));
        t.putString("Grant", grant.name());
        t.put("GrantServers", strings(grantServers));
        t.putBoolean("Enabled", enabled);
        t.putLong("Created", createdMs);
        t.putInt("Taken", taken);
        t.putString("LastBy", lastTakenBy);
        t.putLong("LastMs", lastTakenMs);
        return t;
    }

    public static Kit load(CompoundTag t) {
        Kit k = new Kit(t.getString("Id"), t.getString("Name"), t.getLong("Created"));
        k.description = t.getString("Description");
        if (t.contains("Icon")) {
            k.icon = ItemStack.of(t.getCompound("Icon"));
        }
        for (Tag x : t.getList("Items", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.of((CompoundTag) x);
            if (s.isEmpty()) {
                k.missing++;
            } else {
                k.items.add(s);
            }
        }
        k.inBrowser = t.getBoolean("InBrowser");
        k.once = t.getBoolean("Once");
        k.cooldownSeconds = t.getLong("Cooldown");
        k.price = t.getLong("Price");
        k.ranks.addAll(strings(t.getList("Ranks", Tag.TAG_STRING)));
        k.servers.addAll(strings(t.getList("Servers", Tag.TAG_STRING)));
        k.grant = Grant.of(t.getString("Grant"));
        k.grantServers.addAll(strings(t.getList("GrantServers", Tag.TAG_STRING)));
        k.enabled = !t.contains("Enabled") || t.getBoolean("Enabled");
        k.taken = t.getInt("Taken");
        k.lastTakenBy = t.getString("LastBy");
        k.lastTakenMs = t.getLong("LastMs");
        return k;
    }

    /** One item as a database row: id, count and the NBT as SNBT text ("" for none). */
    public record ItemRow(String item, int count, String nbt) {}

    public static ItemRow rowOf(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return new ItemRow(id == null ? "" : id.toString(), stack.getCount(), stack.hasTag() ? stack.getTag().toString() : "");
    }

    /** The stack a row describes; empty when the item is unknown here or the NBT does not parse. */
    public static ItemStack stackOf(ItemRow row) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(row.item());
        net.minecraft.world.item.Item item = id == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR || row.count() <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, row.count());
        if (!row.nbt().isEmpty()) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(row.nbt()));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /** Comma-joined list columns. */
    public static String joined(List<String> values) {
        return String.join(",", values);
    }

    public static List<String> split(String column) {
        List<String> out = new ArrayList<>();
        for (String v : column.split(",")) {
            if (!v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }

    static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        for (String v : values) {
            list.add(StringTag.valueOf(v));
        }
        return list;
    }

    static List<String> strings(ListTag list) {
        List<String> out = new ArrayList<>();
        for (Tag x : list) {
            out.add(x.getAsString());
        }
        return out;
    }
}
