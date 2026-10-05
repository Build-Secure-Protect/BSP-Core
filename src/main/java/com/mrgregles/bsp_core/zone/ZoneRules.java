package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The things an Anti Totem zone can keep out, in the order its settings screen lists them. The two
 * totem rules come first; then every BSP block, read from the registry, so a block added to the
 * mod later appears here by itself. Blocks with a block entity count as machines.
 */
public final class ZoneRules {
    /** Blocks that are never listed: the totem has its own rules, and the admin blocks are placed by admins only. */
    private static final Set<String> SKIP = Set.of("shatter_totem", "admin_rack", "anti_totem");

    private ZoneRules() {}

    public static List<String> totems() {
        return List.of(ZoneLedger.TOTEM_PLACE, ZoneLedger.TOTEM_DROP);
    }

    private static List<String> blocks(boolean machines) {
        List<String> out = new ArrayList<>();
        for (RegistryObject<Block> entry : ModBlocks.BLOCKS.getEntries()) {
            String path = entry.getId().getPath();
            if (!SKIP.contains(path) && (entry.get() instanceof EntityBlock) == machines) {
                out.add(path);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    public static List<String> machines() {
        return blocks(true);
    }

    public static List<String> others() {
        return blocks(false);
    }

    /** What a newly placed Anti Totem block keeps out: totems and every machine. */
    public static Set<String> defaults() {
        Set<String> out = new HashSet<>(totems());
        out.addAll(machines());
        return out;
    }

    public static boolean known(String key) {
        return totems().contains(key) || machines().contains(key) || others().contains(key);
    }
}
