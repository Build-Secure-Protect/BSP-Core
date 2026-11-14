package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.TankViewPacket;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a Mob Chamber's CHAMBER tab shows ({@link TankViewPacket.Chamber}): the switch, the spawn status, what can spawn here with each
 * mob's chance, main drop and that drop's power, the loot store, and the drop being fed.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChamberView {
    /** A mob's main drop as a chamber keeps it, and the power that drop gives (null: loot only). */
    public record Drop(Item item, @Nullable Buff power) {
    }

    private static final int ROLLS = 24, MAX_ROWS = 16;
    private static final Map<EntityType<?>, Optional<Drop>> DROPS = new HashMap<>();

    private ChamberView() {}

    /**
     * A mob's main drop, read by rolling its loot table a few times as a chamber kill would (as the chamber's stand-in player, no
     * looting) and remembered: of the drops that give a power, the one worth most (then by item id, so it is the same every start), else
     * the one that came up most. Empty for a mob that drops nothing.
     */
    public static Optional<Drop> drop(ServerLevel sl, EntityType<?> type) {
        Optional<Drop> known = DROPS.get(type);
        if (known != null) {
            return known;
        }
        Optional<Drop> found = Optional.empty();
        Entity e = null;
        try {
            e = type.create(sl); // a modded mob may throw here: it must not stop a player joining
            if (e instanceof LivingEntity le) {
                var table = sl.getServer().getLootData().getLootTable(le.getLootTable());
                LootParams params = new LootParams.Builder(sl).withParameter(LootContextParams.THIS_ENTITY, le).withParameter(LootContextParams.ORIGIN, le.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, ChamberSpawner.plasma(sl))
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, ChamberSpawner.killer(sl)).create(LootContextParamSets.ENTITY);
                Map<Item, Integer> seen = new LinkedHashMap<>();
                for (int i = 0; i < ROLLS; i++) {
                    for (ItemStack s : table.getRandomItems(params)) {
                        ItemStack kept = ChamberLoot.convert(le, s);
                        seen.merge(kept.getItem(), kept.getCount(), Integer::sum);
                    }
                }
                Item best = null;
                for (Item item : seen.keySet()) {
                    MobPowers.Entry entry = MobPowers.of(item), had = best == null ? null : MobPowers.of(best);
                    if (entry != null && (had == null || entry.worth() > had.worth()
                            || entry.worth() == had.worth() && String.valueOf(ForgeRegistries.ITEMS.getKey(item)).compareTo(String.valueOf(ForgeRegistries.ITEMS.getKey(best))) < 0)) {
                        best = item;
                    }
                }
                if (best == null) {
                    best = seen.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                }
                if (best != null) {
                    MobPowers.Entry entry = MobPowers.of(best);
                    found = Optional.of(new Drop(best, entry == null ? null : entry.power()));
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            BSPCore.LOGGER.debug("Could not read the drops of {}: {}", EntityType.getKey(type), ex.toString());
        } finally {
            if (e != null) {
                e.discard();
            }
        }
        DROPS.put(type, found);
        return found;
    }

    public static TankViewPacket.Chamber build(ServerLevel sl, TankPartBlockEntity m) {
        List<MobSpawnSettings.SpawnerData> pool = ChamberSpawner.pool(sl, m);
        Map<EntityType<?>, Integer> weights = new LinkedHashMap<>();
        int total = 0;
        for (MobSpawnSettings.SpawnerData d : pool) {
            weights.merge(d.type, d.getWeight().asInt(), Integer::sum);
            total += d.getWeight().asInt();
        }
        List<TankViewPacket.Spawn> spawns = new ArrayList<>();
        for (var e : weights.entrySet()) {
            Optional<Drop> drop = drop(sl, e.getKey());
            spawns.add(new TankViewPacket.Spawn(String.valueOf(EntityType.getKey(e.getKey())), total <= 0 ? 0 : Math.round(e.getValue() * 1000f / total),
                    drop.map(d -> String.valueOf(ForgeRegistries.ITEMS.getKey(d.item()))).orElse(""), drop.map(d -> d.power() == null ? -1 : d.power().ordinal()).orElse(-1)));
        }
        spawns.sort((a, b) -> Integer.compare(b.permille(), a.permille()));
        int spawnTotal = spawns.size(); // all that can spawn, though only the likeliest rows go
        if (spawns.size() > MAX_ROWS) {
            spawns = new ArrayList<>(spawns.subList(0, MAX_ROWS));
        }
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < m.loot().getSlots(); i++) {
            loot.add(m.loot().getStackInSlot(i).copy());
        }
        int alive = sl.getEntitiesOfClass(Mob.class, ChamberSpawner.inside(m), e -> e.isAlive() && ChamberSpawner.isChamberMob(e)).size();
        long now = sl.getGameTime();
        int next = m.nextSpawnAt() < 0 ? ChamberSpawner.timerSeconds(m.w(), m.d()) : (int) Math.max(0, (m.nextSpawnAt() - now + 19) / 20);
        String feed = "";
        long[] feedMb = new long[3];
        if (m.feedItem() != null) {
            feed = String.valueOf(ForgeRegistries.ITEMS.getKey(m.feedItem()));
            MobPowers.Entry entry = MobPowers.of(m.feedItem());
            if (entry != null) {
                for (int l = 1; l <= 3; l++) {
                    feedMb[l - 1] = MobPowers.mbTagged(entry, l);
                }
            }
        }
        long minute = BSPConfig.getOr(BSPConfig.PROJECTOR_USE, 20) * 20L * 60L; // what a projecting Projector burns from its base in a minute
        int mark = (int) Math.round(BSPConfig.getOr(BSPConfig.CHAMBER_START_FRACTION, 0.15) * 1000);
        String biome = sl.getBiome(m.master().offset(m.w() / 2, 1, m.d() / 2)).unwrapKey().map(k -> k.location().toString()).orElse("");
        return new TankViewPacket.Chamber(m.chamberMode().ordinal(), mark, biome, alive, ChamberSpawner.maxAtOnce(m.w(), m.d()), next,
                ChamberSpawner.liveOnServer() >= BSPConfig.getOr(BSPConfig.CHAMBER_SERVER_CAP, 200), m.h() - 2, spawns, loot, feed, m.feedLevel(), feedMb, minute, spawnTotal);
    }

    /** Forgets the drops read so far (a /reload may have changed loot tables). */
    public static void clearDrops() {
        DROPS.clear();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        DROPS.clear();
    }
}
