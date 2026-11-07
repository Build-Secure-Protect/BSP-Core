package com.mrgregles.bsp_core.credit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A kind of store credit ticket, made by an admin: its name, how many Shatter Pieces a new ticket holds, its look (a dye colour
 * and a C or D mark), how long a ticket lasts once its first purchase is made, whether it binds to the player it is given to,
 * and where it may be spent (shops by id, the Land Office's plots, teleports). Every ticket of the kind follows the kind's
 * name, look, places and time limit as they are now; the value only sets what a new ticket starts with.
 */
public final class CreditKind {
    public static final int ID_MAX = 32, NAME_MAX = 32, PLACES_MAX = 64, PLACE_NAME_MAX = 64;
    public static final long VALUE_MAX = 1_000_000_000L;
    /** The longest time limit, about ten years. */
    public static final long DURATION_MAX = 3650L * 24 * 3_600_000L;

    public final String id;
    public String name = "";
    public long value;
    /** A dye colour's name ({@link DyeColor#getName()}). */
    public String colour = DyeColor.PURPLE.getName();
    /** "C" (credit), "D" (donor) or "" (no mark). */
    public String mark = "";
    /** How long a ticket lasts after its first purchase, ms; 0 = no time limit. */
    public long durationMs;
    /** A ticket binds to the player it is given to; otherwise anyone holding it may spend it until its first purchase. */
    public boolean bind;
    /** Shop ids (a shop's sub-shops come with it). */
    public final List<String> shops = new ArrayList<>();
    /** Every plot for sale and every open area at the Land Office, and enlarging a claim. */
    public boolean anyPlot;
    /** Land Office plot names: each covers every plot of that name. */
    public final List<String> plots = new ArrayList<>();
    /** Every pieces price BSP-Teleport takes. */
    public boolean teleports;
    /** Wall-clock ms of the last save. */
    public long edited;

    public CreditKind(String id) {
        this.id = id;
    }

    /** Whether a kind id is usable: 1 to 32 of a-z, 0-9, _ and -. */
    public static boolean validId(@Nullable String id) {
        return id != null && !id.isEmpty() && id.length() <= ID_MAX && id.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-');
    }

    /** A mark as stored: "C", "D" or "". Anything else is null. */
    @Nullable
    public static String mark(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toUpperCase(Locale.ROOT);
        return t.equals("C") || t.equals("D") ? t : t.isEmpty() || t.equals("NONE") ? "" : null;
    }

    public DyeColor dye() {
        DyeColor d = DyeColor.byName(colour, null);
        return d == null ? DyeColor.PURPLE : d;
    }

    /** Whether the kind spends anywhere at all. */
    public boolean anyPlace() {
        return !shops.isEmpty() || anyPlot || !plots.isEmpty() || teleports;
    }

    /** What is wrong with the kind as a sentence for an admin, or null when it may be saved. */
    @Nullable
    public String problem() {
        if (!validId(id)) {
            return "The id must be 1 to " + ID_MAX + " letters a-z, digits, _ or -.";
        }
        if (name.isBlank()) {
            return "The kind needs a name.";
        }
        if (name.length() > NAME_MAX) {
            return "The name is longer than " + NAME_MAX + " characters.";
        }
        if (value <= 0 || value > VALUE_MAX) {
            return "The value must be 1 to " + VALUE_MAX + " pieces.";
        }
        if (durationMs < 0 || durationMs > DURATION_MAX) {
            return "The time limit must be between none and 3650 days.";
        }
        if (DyeColor.byName(colour, null) == null) {
            return "The colour must be one of the 16 dye colours.";
        }
        if (mark(mark) == null) {
            return "The mark must be C, D or none.";
        }
        if (shops.size() + plots.size() > PLACES_MAX) {
            return "A kind may list at most " + PLACES_MAX + " shops and plot names.";
        }
        for (String s : shops) {
            if (s.isBlank() || s.length() > PLACE_NAME_MAX) {
                return "A shop id is empty or too long.";
            }
        }
        for (String p : plots) {
            if (p.isBlank() || p.length() > PLACE_NAME_MAX) {
                return "A plot name is empty or too long.";
            }
        }
        return null;
    }

    /** Trims names, drops blanks and repeats (plot names compared without case). */
    public void tidy() {
        name = name.trim();
        String m = mark(mark);
        mark = m == null ? "" : m;
        dedupe(shops, false);
        dedupe(plots, true);
    }

    private static void dedupe(List<String> list, boolean ignoreCase) {
        List<String> seen = new ArrayList<>(), out = new ArrayList<>();
        for (String s : list) {
            String t = s == null ? "" : s.trim();
            String key = ignoreCase ? t.toLowerCase(Locale.ROOT) : t;
            if (!t.isEmpty() && !seen.contains(key)) {
                seen.add(key);
                out.add(t);
            }
        }
        list.clear();
        list.addAll(out);
    }

    public CreditKind copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Name", name);
        t.putLong("Value", value);
        t.putString("Colour", colour);
        t.putString("Mark", mark);
        t.putLong("Duration", durationMs);
        t.putBoolean("Bind", bind);
        t.put("Shops", strings(shops));
        t.putBoolean("AnyPlot", anyPlot);
        t.put("Plots", strings(plots));
        t.putBoolean("Teleports", teleports);
        t.putLong("Edited", edited);
        return t;
    }

    public static CreditKind load(CompoundTag t) {
        CreditKind k = new CreditKind(t.getString("Id"));
        k.name = t.getString("Name");
        k.value = t.getLong("Value");
        k.colour = t.contains("Colour") ? t.getString("Colour") : DyeColor.PURPLE.getName();
        k.mark = t.getString("Mark");
        k.durationMs = t.getLong("Duration");
        k.bind = t.getBoolean("Bind");
        for (Tag s : t.getList("Shops", Tag.TAG_STRING)) {
            k.shops.add(s.getAsString());
        }
        k.anyPlot = t.getBoolean("AnyPlot");
        for (Tag s : t.getList("Plots", Tag.TAG_STRING)) {
            k.plots.add(s.getAsString());
        }
        k.teleports = t.getBoolean("Teleports");
        k.edited = t.getLong("Edited");
        return k;
    }

    private static ListTag strings(List<String> list) {
        ListTag l = new ListTag();
        list.forEach(s -> l.add(StringTag.valueOf(s)));
        return l;
    }
}
