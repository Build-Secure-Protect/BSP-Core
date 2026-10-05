package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every Anti Totem zone on this server. Kept apart from the blocks themselves so that a zone
 * works while its block's chunk is unloaded: a zone can reach 256 blocks from its block.
 *
 * <p>A zone is a box around its block, given as six distances (north, south, east, west, up,
 * down), and a set of rule keys for what it keeps out: {@link #TOTEM_PLACE}, {@link #TOTEM_DROP},
 * or the id path of any BSP block (for example {@code coin_vault}).
 */
public class ZoneLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_zones";
    public static final String TOTEM_PLACE = "totem_place", TOTEM_DROP = "totem_drop";
    public static final int N = 0, S = 1, E = 2, W = 3, U = 4, D = 5, MAX = 256;

    /** One zone: the six distances and the rule keys it blocks. */
    public record Zone(int[] reach, Set<String> blocked) {
        boolean contains(BlockPos centre, BlockPos p) {
            return p.getX() >= centre.getX() - reach[W] && p.getX() <= centre.getX() + reach[E]
                    && p.getY() >= centre.getY() - reach[D] && p.getY() <= centre.getY() + reach[U]
                    && p.getZ() >= centre.getZ() - reach[N] && p.getZ() <= centre.getZ() + reach[S];
        }
    }

    private final Map<GlobalPos, Zone> zones = new HashMap<>();

    public static ZoneLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ZoneLedger::load, ZoneLedger::new, DATA_NAME);
    }

    public void put(GlobalPos pos, int[] reach, Set<String> blocked) {
        zones.put(pos, new Zone(reach.clone(), Set.copyOf(blocked)));
        setDirty();
    }

    public void remove(GlobalPos pos) {
        if (zones.remove(pos) != null) {
            setDirty();
        }
    }

    /** Whether any zone covering {@code pos} keeps out the thing named by {@code key}. Overlapping zones add up: the strictest wins. */
    public boolean blocked(ServerLevel level, BlockPos pos, String key) {
        for (Map.Entry<GlobalPos, Zone> e : zones.entrySet()) {
            if (e.getKey().dimension() == level.dimension() && e.getValue().blocked.contains(key) && e.getValue().contains(e.getKey().pos(), pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBlocked(ServerLevel level, BlockPos pos, String key) {
        return get(level.getServer()).blocked(level, pos, key);
    }

    /**
     * The nearest place outside every zone that keeps placed totems out, for a totem that has to
     * be put down (its carrier logged out, or it lay on the ground too long). Leaves through the
     * closest side of the zone and lands on the surface there. Returns {@code pos} if it is already outside.
     */
    public BlockPos outside(ServerLevel level, BlockPos pos) {
        BlockPos p = pos;
        for (int tries = 0; tries < 8; tries++) { // zones can touch or overlap: keep going until clear
            BlockPos at = p;
            Map.Entry<GlobalPos, Zone> hit = zones.entrySet().stream().filter(e -> e.getKey().dimension() == level.dimension()
                    && e.getValue().blocked.contains(TOTEM_PLACE) && e.getValue().contains(e.getKey().pos(), at)).findFirst().orElse(null);
            if (hit == null) {
                return p.equals(pos) ? pos : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            }
            BlockPos c = hit.getKey().pos();
            int[] r = hit.getValue().reach;
            int toW = p.getX() - (c.getX() - r[W]), toE = (c.getX() + r[E]) - p.getX(), toN = p.getZ() - (c.getZ() - r[N]), toS = (c.getZ() + r[S]) - p.getZ();
            int least = Math.min(Math.min(toW, toE), Math.min(toN, toS)), clear = 5; // a few blocks beyond the edge, so the search for a free spot stays outside
            if (least == toW) {
                p = new BlockPos(c.getX() - r[W] - clear, p.getY(), p.getZ());
            } else if (least == toE) {
                p = new BlockPos(c.getX() + r[E] + clear, p.getY(), p.getZ());
            } else if (least == toN) {
                p = new BlockPos(p.getX(), p.getY(), c.getZ() - r[N] - clear);
            } else {
                p = new BlockPos(p.getX(), p.getY(), c.getZ() + r[S] + clear);
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        zones.forEach((pos, zone) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
            e.putIntArray("Reach", zone.reach);
            ListTag rules = new ListTag();
            zone.blocked.forEach(k -> rules.add(StringTag.valueOf(k)));
            e.put("Blocked", rules);
            list.add(e);
        });
        tag.put("Zones", list);
        return tag;
    }

    private static ZoneLedger load(CompoundTag tag) {
        ZoneLedger ledger = new ZoneLedger();
        for (Tag raw : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            int[] reach = e.getIntArray("Reach");
            if (dim == null || reach.length != 6) {
                continue;
            }
            Set<String> blocked = new HashSet<>();
            for (Tag k : e.getList("Blocked", Tag.TAG_STRING)) {
                blocked.add(k.getAsString());
            }
            ledger.zones.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos"))), new Zone(reach, blocked));
        }
        return ledger;
    }
}
