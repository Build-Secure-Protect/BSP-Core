package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Mob Chamber's own spawning, once a second from the tank's master ({@link #tick}). Like a spawner, it picks from the mobs its biome
 * (and any structure it stands in) would spawn, outside the world's mob cap and the usual light and floor rules, limited to mobs that fit
 * inside and to the chamber's ALL / HOSTILE / PASSIVE setting. Mobs in a chamber have no AI while the tank stands (they are a picture of
 * what spawned, at little cost) and the plasma burns them; breaking the tank wakes them ({@link #wake}).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChamberSpawner {
    /** Persistent-data key on a chamber's mob: the chamber's master, as a long. */
    public static final String TAG = "bsp_core:chamber";
    public static final ResourceKey<DamageType> PLASMA = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(BSPCore.MODID, "plasma"));
    /** Chamber mobs alive in loaded levels, server-wide. */
    private static final Set<UUID> LIVE = new HashSet<>();
    /** Before a mob was claimed, what it was: its AI on and whether it was kept from despawning; put back when it wakes. */
    private static final String WAS = "bsp_core:chamber_was";
    /** Who a chamber kill counts as for drops that need a player's kill (blaze rods, spider eyes): never a real player, no XP. */
    private static final com.mojang.authlib.GameProfile KILLER = new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("bsp_core:chamber".getBytes()), "[BSP Chamber]");

    /** The chamber as a player, for the drops that need a player's kill. */
    public static net.minecraft.world.entity.player.Player killer(ServerLevel sl) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(sl, KILLER);
    }

    /** Which mobs a chamber spawns: every kind, monsters only, or everything but monsters. */
    public enum Mode { ALL, HOSTILE, PASSIVE }

    private ChamberSpawner() {}

    // ------------------------------------------------------------------ numbers

    /** Seconds between spawns for a chamber {@code w} by {@code d}: 30 at 4 wide, 2 less per block wider, never under 14 (config). */
    public static int timerSeconds(int w, int d) {
        int base = BSPConfig.getOr(BSPConfig.CHAMBER_TIMER_SECONDS, 30), step = BSPConfig.getOr(BSPConfig.CHAMBER_TIMER_STEP, 2), min = BSPConfig.getOr(BSPConfig.CHAMBER_TIMER_MIN, 14);
        return Math.max(Math.min(min, base), Math.min(base, base - step * (Math.max(w, d) - 4)));
    }

    /** Mobs alive at once: one per {@code floorPerMob} blocks of inside floor (one per 2x2), at least one, at most the chamber cap. */
    public static int maxAtOnce(int w, int d) {
        int floor = Math.max(0, w - 2) * Math.max(0, d - 2);
        return Math.max(1, Math.min(BSPConfig.getOr(BSPConfig.CHAMBER_CAP, 16), floor / BSPConfig.getOr(BSPConfig.CHAMBER_FLOOR_PER_MOB, 4)));
    }

    /** Whether the tank is full enough to spawn. */
    public static boolean fullEnough(TankPartBlockEntity m) {
        return m.capacity() > 0 && m.stored() >= Math.ceil(m.capacity() * BSPConfig.getOr(BSPConfig.CHAMBER_START_FRACTION, 0.15));
    }

    /** The space inside the shell. */
    public static AABB inside(TankPartBlockEntity m) {
        BlockPos min = m.master();
        return new AABB(min.getX() + 1, min.getY() + 1, min.getZ() + 1, min.getX() + m.w() - 1, min.getY() + m.h() - 1, min.getZ() + m.d() - 1);
    }

    public static boolean isChamberMob(Entity e) {
        return e instanceof Mob && e.getPersistentData().contains(TAG);
    }

    public static int liveOnServer() {
        return LIVE.size();
    }

    public static DamageSource plasma(ServerLevel sl) {
        Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PLASMA);
        return new DamageSource(type);
    }

    // ------------------------------------------------------------------ what can spawn

    private static boolean wanted(MobCategory c, Mode mode, boolean peaceful) {
        if (c == MobCategory.MISC || peaceful && !c.isFriendly()) {
            return false;
        }
        return switch (mode) {
            case ALL -> true;
            case HOSTILE -> !c.isFriendly();
            case PASSIVE -> c.isFriendly();
        };
    }

    /** Whether a mob of {@code type} fits inside: as wide as the inside floor allows and no taller than the inside. */
    public static boolean fits(EntityType<?> type, TankPartBlockEntity m) {
        EntityDimensions dim = type.getDimensions();
        return dim.width <= Math.min(m.w(), m.d()) - 2 && dim.height <= m.h() - 2;
    }

    /** What this chamber can spawn now: its biome's and structure's lists for the chamber's setting, mobs that fit, with their weights. */
    public static List<MobSpawnSettings.SpawnerData> pool(ServerLevel sl, TankPartBlockEntity m) {
        return pool(sl, m, null);
    }

    /** {@link #pool(ServerLevel, TankPartBlockEntity)} as if the chamber stood in {@code biome} (null: its own). */
    public static List<MobSpawnSettings.SpawnerData> pool(ServerLevel sl, TankPartBlockEntity m, @Nullable Holder<Biome> biomeOverride) {
        BlockPos at = m.master().offset(m.w() / 2, 1, m.d() / 2);
        Holder<Biome> biome = biomeOverride != null ? biomeOverride : sl.getBiome(at);
        boolean peaceful = sl.getDifficulty() == Difficulty.PEACEFUL;
        List<MobSpawnSettings.SpawnerData> out = new ArrayList<>();
        for (MobCategory c : MobCategory.values()) {
            if (!wanted(c, m.chamberMode(), peaceful)) {
                continue;
            }
            var list = sl.getChunkSource().getGenerator().getMobsAt(biome, sl.structureManager(), c, at);
            list = ForgeEventFactory.getPotentialSpawns(sl, c, at, list); // mods that add spawns in code
            for (MobSpawnSettings.SpawnerData data : list.unwrap()) {
                if (data.getWeight().asInt() > 0 && fits(data.type, m)) {
                    out.add(data);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the chamber's second

    /** The master of a chamber, once a second: claims and burns the mobs inside, and spawns when the timer is up. */
    public static void tick(ServerLevel sl, TankPartBlockEntity m) {
        List<Mob> mobs = sl.getEntitiesOfClass(Mob.class, inside(m), Mob::isAlive);
        float burn = (float) (double) BSPConfig.getOr(BSPConfig.CHAMBER_BURN, 4.0);
        // the plasma left on the walls burns them even in a tank drained empty, so a dry chamber never holds its mobs for ever
        DamageSource source = burn > 0 ? plasma(sl) : null;
        int here = (int) mobs.stream().filter(ChamberSpawner::isChamberMob).count(), most = maxAtOnce(m.w(), m.d()),
                cap = BSPConfig.getOr(BSPConfig.CHAMBER_SERVER_CAP, 200);
        for (Mob mob : mobs) {
            if (!isChamberMob(mob)) {
                if (mob instanceof net.minecraft.world.entity.monster.Slime && (here >= most || LIVE.size() >= cap)) {
                    mob.discard(); // a split slime past the chamber's or the server's limit
                    continue;
                }
                here++;
            }
            claim(mob, m.master());
            if (source != null) {
                mob.setLastHurtByPlayer(killer(sl)); // drops that need a player's kill come as from one; XP never does
                mob.hurt(source, burn);
            }
        }
        long now = sl.getGameTime();
        if (m.nextSpawnAt() < 0) {
            m.setNextSpawnAt(now + timerSeconds(m.w(), m.d()) * 20L);
        } else if (now >= m.nextSpawnAt()) {
            m.setNextSpawnAt(now + timerSeconds(m.w(), m.d()) * 20L);
            spawnOnce(sl, m, sl.getRandom());
        }
    }

    /** Why a chamber did not spawn, or {@link #SPAWNED}. */
    public static final String SPAWNED = "spawned", NOT_CHAMBER = "not a chamber", LOW = "below the start mark", FULL = "full", SERVER_FULL = "server full",
            NOTHING = "nothing can spawn here", NO_ROOM = "no room", CANCELLED = "cancelled";

    /** One spawn attempt, whatever the timer says: returns {@link #SPAWNED} or why not. */
    public static String spawnOnce(ServerLevel sl, TankPartBlockEntity m, RandomSource random) {
        return spawnOnce(sl, m, random, null);
    }

    /** {@link #spawnOnce(ServerLevel, TankPartBlockEntity, RandomSource)} as if the chamber stood in {@code biome} (null: its own). */
    public static String spawnOnce(ServerLevel sl, TankPartBlockEntity m, RandomSource random, @Nullable Holder<Biome> biome) {
        if (!m.isMaster() || !m.chamber()) {
            return NOT_CHAMBER;
        }
        if (!fullEnough(m)) {
            return LOW;
        }
        int here = sl.getEntitiesOfClass(Mob.class, inside(m), e -> e.isAlive() && isChamberMob(e)).size();
        if (here >= maxAtOnce(m.w(), m.d())) {
            return FULL;
        }
        if (LIVE.size() >= BSPConfig.getOr(BSPConfig.CHAMBER_SERVER_CAP, 200)) {
            return SERVER_FULL;
        }
        List<MobSpawnSettings.SpawnerData> pool = pool(sl, m, biome);
        int total = pool.stream().mapToInt(d -> d.getWeight().asInt()).sum();
        if (total <= 0) {
            return NOTHING;
        }
        int roll = random.nextInt(total);
        MobSpawnSettings.SpawnerData pick = pool.get(0);
        for (MobSpawnSettings.SpawnerData d : pool) {
            roll -= d.getWeight().asInt();
            if (roll < 0) {
                pick = d;
                break;
            }
        }
        if (spawn(sl, m, pick.type, random) == null) {
            return NO_ROOM;
        }
        m.markSpawned(sl.getGameTime());
        return SPAWNED;
    }

    /** Spawns a mob of {@code type} on the chamber's floor, claimed (no AI), or null if it does not fit or will not spawn. */
    @Nullable
    public static Mob spawn(ServerLevel sl, TankPartBlockEntity m, EntityType<?> type, RandomSource random) {
        if (!fits(type, m)) {
            return null;
        }
        AABB box = inside(m);
        float hw = type.getDimensions().width / 2f;
        double x0 = box.minX + hw, x1 = box.maxX - hw, z0 = box.minZ + hw, z1 = box.maxZ - hw;
        for (int attempt = 0; attempt < 4; attempt++) {
            double x = x0 + random.nextDouble() * Math.max(0, x1 - x0), z = z0 + random.nextDouble() * Math.max(0, z1 - z0), y = box.minY;
            if (!sl.noCollision(type.getAABB(x, y, z)) || !(type.create(sl) instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(x, y, z, random.nextFloat() * 360f, 0f);
            ForgeEventFactory.onFinalizeSpawn(mob, sl, sl.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWNER, null, null);
            if (mob.isSpawnCancelled()) {
                return null;
            }
            claim(mob, m.master());
            for (Entity rider : mob.getIndirectPassengers()) {
                if (rider instanceof Mob r) {
                    claim(r, m.master());
                }
            }
            if (!sl.tryAddFreshEntityWithPassengers(mob)) {
                return null; // another mod refused it
            }
            return mob;
        }
        return null;
    }

    /** Makes a mob inside a chamber the chamber's: no AI, kept loaded (outside the mob cap), marked. */
    static void claim(Mob mob, BlockPos master) {
        if (!isChamberMob(mob)) {
            CompoundTag was = new CompoundTag();
            was.putBoolean("NoAI", mob.isNoAi());
            was.putBoolean("Persist", mob.isPersistenceRequired());
            mob.getPersistentData().put(WAS, was);
        }
        if (!mob.isNoAi()) {
            mob.setNoAi(true);
        }
        if (!mob.isPersistenceRequired()) {
            mob.setPersistenceRequired();
        }
        mob.getPersistentData().putLong(TAG, master.asLong());
        if (mob.isAddedToWorld()) {
            LIVE.add(mob.getUUID());
        }
    }

    /** The tank came apart: its chamber's mobs get their AI back, and become ordinary mobs that may despawn. */
    public static void wake(ServerLevel sl, BlockPos min, int w, int h, int d) {
        AABB box = new AABB(min.getX() + 1, min.getY() + 1, min.getZ() + 1, min.getX() + w - 1, min.getY() + h - 1, min.getZ() + d - 1);
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, box, ChamberSpawner::isChamberMob)) {
            wakeOne(mob);
        }
    }

    /** One chamber mob back to what it was before the chamber took it (a chamber's own: AI on, free to despawn), unmarked. */
    static void wakeOne(Mob mob) {
        CompoundTag was = mob.getPersistentData().getCompound(WAS);
        mob.getPersistentData().remove(TAG);
        mob.getPersistentData().remove(WAS);
        LIVE.remove(mob.getUUID());
        boolean noAi = was.getBoolean("NoAI"), persist = was.getBoolean("Persist");
        mob.setNoAi(noAi);
        CompoundTag saved = mob.saveWithoutId(new CompoundTag()); // there is no setter that clears persistence: save, edit, load
        saved.putBoolean("PersistenceRequired", persist);
        saved.putBoolean("NoAI", noAi);
        mob.load(saved);
    }

    // ------------------------------------------------------------------ events

    /** Last, and never for a join another mod cancelled, so the server's count holds only mobs that are really there. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && isChamberMob(event.getEntity()) && event.getEntity() instanceof Mob mob) {
            // a chamber's mob loaded with its chunk after its tank came apart while unloaded: it wakes now
            BlockPos master = BlockPos.of(mob.getPersistentData().getLong(TAG));
            if (event.getLevel().isLoaded(master) && !(event.getLevel().getBlockEntity(master) instanceof TankPartBlockEntity m && m.isMaster() && m.chamber())) {
                wakeOne(mob);
                return;
            }
            LIVE.add(mob.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            LIVE.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LIVE.clear();
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        com.mrgregles.bsp_core.tank.TankStructure.closed(event.getEntity().getUUID());
    }

    /** An enderman (or shulker) hurt by the plasma would teleport out of the tank: a chamber's mob stays. */
    @SubscribeEvent
    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (isChamberMob(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
