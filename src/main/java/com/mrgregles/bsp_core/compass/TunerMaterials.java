package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What one tune uses ({@code compassTuner.materials}): up to {@link CompassTunerBlockEntity#MATERIAL_SLOTS} entries, each an
 * item id or a {@code #tag} and a count (1 to 64). Entry {@code i} belongs to material slot {@code i}. An entry naming an
 * item that does not exist stays in its place as null, so its slot takes nothing and asks for nothing, and the other entries
 * keep their slots.
 */
public final class TunerMaterials {
    private TunerMaterials() {}

    /** One material: an item or an item tag, and how many a tune takes. */
    public record Entry(@Nullable Item item, @Nullable TagKey<Item> tag, int count, String text) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            return tag != null ? stack.is(tag) : stack.is(item);
        }

        /** An item to show in the slot as a hint: the item, or the tag's first item. */
        public ItemStack icon() {
            if (item != null) {
                return new ItemStack(item);
            }
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) {
                for (Item i : tags.getTag(tag)) {
                    return new ItemStack(i);
                }
            }
            return ItemStack.EMPTY;
        }
    }

    private static List<? extends String> parsedFrom;
    private static List<Entry> parsed = List.of();

    /** The entries, one per material slot in order (null for a line that names nothing); at most {@link CompassTunerBlockEntity#MATERIAL_SLOTS}. */
    public static synchronized List<Entry> entries() {
        List<? extends String> raw = BSPConfig.getOr(BSPConfig.TUNER_MATERIALS,
                List.of("minecraft:lapis_lazuli 16", "minecraft:quartz 16", "minecraft:redstone 16"));
        if (raw != parsedFrom) {
            parsedFrom = raw;
            parsed = parse(raw);
        }
        return parsed;
    }

    /** Parses config lines such as {@code "minecraft:lapis_lazuli 16"} or {@code "#forge:dusts/redstone 16"}. */
    public static List<Entry> parse(List<? extends String> raw) {
        List<Entry> out = new ArrayList<>();
        for (String line : raw) {
            if (out.size() >= CompassTunerBlockEntity.MATERIAL_SLOTS) {
                BSPCore.LOGGER.warn("compassTuner.materials has more than {} entries; the rest are ignored", CompassTunerBlockEntity.MATERIAL_SLOTS);
                break;
            }
            Entry e = parseOne(line);
            if (e == null) {
                BSPCore.LOGGER.warn("compassTuner.materials: \"{}\" names no item or tag; its slot takes nothing", line);
            }
            out.add(e);
        }
        return java.util.Collections.unmodifiableList(out);
    }

    @Nullable
    private static Entry parseOne(String line) {
        String[] parts = line.trim().split(" +");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        int count = 1;
        if (parts.length > 1) {
            try {
                count = Math.max(1, Math.min(64, Integer.parseInt(parts[1])));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        boolean isTag = parts[0].startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? parts[0].substring(1) : parts[0]);
        if (id == null) {
            return null;
        }
        if (isTag) {
            return new Entry(null, TagKey.create(Registries.ITEM, id), count, line.trim());
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : new Entry(item, null, count, line.trim());
    }

    /** The entry for material slot {@code index} (0-based), or null if that slot is unused. */
    @Nullable
    public static Entry forSlot(int index) {
        List<Entry> all = entries();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }
}
