package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Plasma Tank Mob Chamber's powers as data: which drop tags plasma with which mob power ({@link TotemUpgrades.Branch#MOB}), and how
 * much plasma one item tags. The table is the config list {@code mob_chamber.dropPowers}, read again whenever the config changes.
 *
 * <p>An item of worth 1 tags {@code mob_chamber.mbPerWorth} of plain plasma at level I; level II costs twice the items for the same plasma,
 * level III four times. A spider eye has worth 0.05, so it takes twenty of them to tag what one bone does.
 */
public final class MobPowers {
    /** Items a level costs, over level I: I 1, II 2, III 4. */
    private static final int[] LEVEL_COST = {1, 2, 4};

    /** One config line before its item is looked up. */
    public record Line(ResourceLocation item, Buff power, double worth) {
    }

    /** One drop that tags plasma: the item, the power it gives, and its worth (1 for a common drop, 5 for a rare one). */
    public record Entry(Item item, Buff power, double worth) {
    }

    @Nullable
    private static List<? extends String> source;
    private static Map<Item, Entry> table = Map.of();

    private MobPowers() {}

    /** The mob power whose key is {@code key} (for example {@code "growth"}), or null if there is none. */
    @Nullable
    public static Buff byKey(String key) {
        for (Buff b : Buff.values()) {
            if (b.branch() == TotemUpgrades.Branch.MOB && b.key.equals(key)) {
                return b;
            }
        }
        return null;
    }

    /** Reads {@code "item=power,worth"}; null if it is not that shape, the power is not a mob power, or the worth is not above 0. */
    @Nullable
    public static Line parse(String line) {
        int eq = line.indexOf('='), comma = line.lastIndexOf(',');
        if (eq <= 0 || comma <= eq + 1 || comma == line.length() - 1) {
            return null;
        }
        ResourceLocation item = ResourceLocation.tryParse(line.substring(0, eq).trim().toLowerCase(Locale.ROOT));
        Buff power = byKey(line.substring(eq + 1, comma).trim().toLowerCase(Locale.ROOT));
        double worth;
        try {
            worth = Double.parseDouble(line.substring(comma + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (item == null || power == null || !(worth > 0) || Double.isInfinite(worth)) {
            return null;
        }
        return new Line(item, power, worth);
    }

    /** The drop table, item to entry, in config order. Lines for items no mod adds are left out; a later line for the same item wins. */
    public static Map<Item, Entry> table() {
        List<? extends String> now = BSPConfig.getOr(BSPConfig.CHAMBER_DROP_POWERS, List.<String>of());
        if (now != source) {
            Map<Item, Entry> out = new LinkedHashMap<>();
            for (String s : now) {
                Line l = parse(s);
                if (l == null || !ForgeRegistries.ITEMS.containsKey(l.item())) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(l.item());
                out.put(item, new Entry(item, l.power(), l.worth()));
            }
            table = Collections.unmodifiableMap(out);
            source = now;
        }
        return table;
    }

    /** The entry for a drop, or null if it is loot only. */
    @Nullable
    public static Entry of(Item item) {
        return table().get(item);
    }

    @Nullable
    public static Entry of(ItemStack stack) {
        return stack.isEmpty() ? null : of(stack.getItem());
    }

    /** Items a level costs over level I (1, 2, 4), for level 1 to {@link Buff#MOB_LEVELS}; levels out of range are clamped. */
    public static int levelCost(int level) {
        return LEVEL_COST[Math.max(1, Math.min(LEVEL_COST.length, level)) - 1];
    }

    /** Plain plasma one item of {@code entry} tags at {@code level}, in mB. */
    public static long mbTagged(Entry entry, int level) {
        return Math.round(entry.worth() * BSPConfig.getOr(BSPConfig.CHAMBER_MB_PER_WORTH, 24_000) / levelCost(level));
    }
}
