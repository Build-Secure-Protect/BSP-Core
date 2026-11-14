package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChamberRosterPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every mob a Mob Chamber could spawn on this server: where (the biomes whose lists hold it, after every mod's changes, and the structures
 * whose spawn lists do), the smallest tank it fits, and its main drop and that drop's power. The recipe viewers show a page per mob; the
 * client never sees biome spawn lists, so the server sends the roster with the datapacks, before tags and recipes, so it is there when JEI
 * and EMI load. Mobs that mods add in code at spawn time are not in any list and are left out.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChamberRoster {
    /**
     * One mob: entity id, hostile or not, biome ids and structure ids (the first {@link #LISTED} of each, to keep the packet small),
     * how many places in all, smallest tank (side and height), drop item id ("" none), power ordinal (-1 none).
     */
    public record Entry(String entity, boolean hostile, List<String> biomes, List<String> structures, int places, int side, int height, String drop, int power) {
    }

    /** Biomes and structures listed per mob; the rest are only counted. */
    public static final int LISTED = 24;

    @Nullable
    private static List<Entry> built;
    /** On the client: the last roster the server sent (empty until then). */
    private static volatile List<Entry> client = List.of();

    private ChamberRoster() {}

    public static List<Entry> client() {
        return client;
    }

    public static void receive(List<Entry> roster) {
        client = List.copyOf(roster);
    }

    /** The smallest tank a mob of {@code type} fits: {side, height}, at least 3 each. */
    public static int[] smallest(EntityType<?> type) {
        var dim = type.getDimensions();
        return new int[]{Math.max(3, (int) Math.ceil(dim.width) + 2), Math.max(3, (int) Math.ceil(dim.height) + 2)};
    }

    /** The roster, worked out once per server start and datapack reload. */
    public static synchronized List<Entry> get(MinecraftServer server) {
        if (built == null) {
            built = build(server);
        }
        return built;
    }

    private static List<Entry> build(MinecraftServer server) {
        Map<EntityType<?>, Set<String>> biomes = new LinkedHashMap<>(), structures = new LinkedHashMap<>();
        var access = server.registryAccess();
        access.registryOrThrow(Registries.BIOME).entrySet().forEach(e -> {
            for (MobCategory c : MobCategory.values()) {
                if (c == MobCategory.MISC) {
                    continue;
                }
                for (var data : e.getValue().getMobSettings().getMobs(c).unwrap()) {
                    biomes.computeIfAbsent(data.type, t -> new LinkedHashSet<>()).add(e.getKey().location().toString());
                }
            }
        });
        access.registryOrThrow(Registries.STRUCTURE).entrySet().forEach(e -> e.getValue().spawnOverrides().forEach((c, override) -> {
            if (c != MobCategory.MISC) {
                for (var data : override.spawns().unwrap()) {
                    structures.computeIfAbsent(data.type, t -> new LinkedHashSet<>()).add(e.getKey().location().toString());
                }
            }
        }));
        Set<EntityType<?>> types = new LinkedHashSet<>(biomes.keySet());
        types.addAll(structures.keySet());
        ServerLevel level = server.overworld();
        List<Entry> out = new ArrayList<>();
        for (EntityType<?> type : types) {
            if (type.getCategory() == MobCategory.MISC) {
                continue;
            }
            var drop = ChamberView.drop(level, type);
            int[] size = smallest(type);
            // sorted by id, so the same places are listed every time (the registries' own order is not fixed)
            List<String> inBiomes = biomes.getOrDefault(type, Set.of()).stream().sorted().toList(), inStructures = structures.getOrDefault(type, Set.of()).stream().sorted().toList();
            out.add(new Entry(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(type)), !type.getCategory().isFriendly(),
                    inBiomes.subList(0, Math.min(LISTED, inBiomes.size())), inStructures.subList(0, Math.min(LISTED, inStructures.size())),
                    inBiomes.size() + inStructures.size(), size[0], size[1],
                    drop.map(d -> String.valueOf(ForgeRegistries.ITEMS.getKey(d.item()))).orElse(""), drop.map(d -> d.power() == null ? -1 : d.power().ordinal()).orElse(-1)));
        }
        out.sort((a, b) -> a.entity().compareTo(b.entity()));
        return out;
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) {
            synchronized (ChamberRoster.class) {
                built = null; // a /reload: the lists and loot tables may have changed
                ChamberView.clearDrops();
            }
        }
        ChamberRosterPacket packet = new ChamberRosterPacket(get(server));
        if (event.getPlayer() != null) {
            BSPNetwork.sendTo(event.getPlayer(), packet);
        } else {
            BSPNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    /** Worked out as the server starts, so the first player to join does not wait for it. */
    @SubscribeEvent
    public static void onStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        get(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        synchronized (ChamberRoster.class) {
            built = null;
        }
    }
}
