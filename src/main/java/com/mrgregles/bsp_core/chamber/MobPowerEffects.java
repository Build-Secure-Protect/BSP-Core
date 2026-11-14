package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.TotemAuras;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The Mob Chamber's team and area powers, given out by a Projector fed with their plasma (no channel needed), in a cube round it whose
 * size is the power's level ({@code mob_chamber.powerReach}: 16, 24, 32). The team is everyone the projector's Ward leaves alone.
 * <ul>
 *   <li>Team: <b>Haste</b> (Haste I to III), <b>Fire Ward</b> (fire resistance), <b>Tide</b> (water breathing), <b>Mending</b> (held tools
 *   mend {@code mendPerSecond}).</li>
 *   <li>Area: <b>Growth</b> (extra random ticks on blocks in {@code #bsp_core:growth}), <b>Keep</b> (items dropped in reach last
 *   {@code keepMinutes}), <b>Undead Ward</b> (no natural spawns of {@code #bsp_core:undead_ward}), <b>Lantern</b> (no natural monster spawns).</li>
 *   <li>Outsiders: <b>Wither</b> (Wither I to III), <b>Snare</b> (Slowness I to III), <b>Scorch</b> (set alight for a second, every second).
 *   An outsider is anyone not on the team, except a player new-player protection still covers, and players in creative or spectator.</li>
 * </ul>
 * Undead Ward and Lantern do not stop a Mob Chamber's own spawns (those are not natural).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class MobPowerEffects {
    public static final TagKey<Block> GROWS = TagKey.create(Registries.BLOCK, new ResourceLocation(BSPCore.MODID, "growth"));
    public static final TagKey<EntityType<?>> UNDEAD = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(BSPCore.MODID, "undead_ward"));
    /** Most extra random ticks one projector gives in a second, whatever its reach. */
    private static final int MAX_SAMPLES = 20_000;

    private MobPowerEffects() {}

    /** How far a mob power reaches at {@code level}. */
    public static int reach(int level) {
        return Buff.GROWTH.reach(level);
    }

    /** A projector's second: team powers on every player in the world, then Growth. */
    public static void tick(ServerLevel sl, TotemProjectorBlockEntity p) {
        int far = 0; // the widest reach of any mob power the projector gives
        for (Buff b : Buff.values()) {
            if (!b.onTotem() && p.level(b) > 0) {
                far = Math.max(far, reach(p.level(b)));
            }
        }
        if (far <= 0) {
            Wireframe.forget(sl, p.getBlockPos());
            return;
        }
        BlockPos at = p.getBlockPos();
        for (ServerPlayer player : sl.players()) {
            if (TotemAuras.inCube(player, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, far)) {
                onPlayer(p, player);
            }
        }
        if (p.level(Buff.WIREFRAME) > 0) {
            Wireframe.tick(sl, p);
        } else {
            Wireframe.forget(sl, p.getBlockPos());
        }
        int growth = p.level(Buff.GROWTH);
        if (growth > 0) {
            int r = reach(growth);
            grow(sl, p.getBlockPos(), r, samples(sl, r, growth), sl.getRandom());
        }
    }

    /** Whether {@code player} is an outsider to the projector: not its team, not protected as a new player, not in creative or spectator. */
    public static boolean outsider(TotemProjectorBlockEntity p, ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() || p.isTeam(player.getUUID())) {
            return false;
        }
        var server = player.getServer();
        return server == null || !com.mrgregles.bsp_core.totem.NewPlayerProtection.get(server).protects(server, player.getUUID());
    }

    /** The powers for one player, each while they stand in its reach: the team powers for the team, the outsider powers for outsiders. */
    public static void onPlayer(TotemProjectorBlockEntity p, ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        BlockPos at = p.getBlockPos();
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        if (!p.isTeam(player.getUUID())) {
            if (outsider(p, player)) {
                give(p, player, Buff.WITHER, MobEffects.WITHER, true, x, y, z);
                give(p, player, Buff.SNARE, MobEffects.MOVEMENT_SLOWDOWN, true, x, y, z);
                int scorch = p.level(Buff.SCORCH);
                if (scorch > 0 && TotemAuras.inCube(player, x, y, z, reach(scorch)) && !player.fireImmune()) {
                    // set straight, not through setSecondsOnFire, which Fire Protection shortens below the one burn a second
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 20));
                }
            }
            return;
        }
        give(p, player, Buff.HASTE, MobEffects.DIG_SPEED, true, x, y, z);
        give(p, player, Buff.FIRE_WARD, MobEffects.FIRE_RESISTANCE, false, x, y, z);
        give(p, player, Buff.TIDE, MobEffects.WATER_BREATHING, false, x, y, z);
        int mend = p.level(Buff.MENDING);
        if (mend > 0 && TotemAuras.inCube(player, x, y, z, reach(mend))) {
            int points = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.MOB_MEND_PER_SECOND, List.<Integer>of()), mend, 1 << (mend - 1));
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.isDamageableItem() && held.isDamaged()) {
                    held.setDamageValue(Math.max(0, held.getDamageValue() - points));
                }
            }
        }
    }

    private static void give(TotemProjectorBlockEntity p, ServerPlayer player, Buff buff, MobEffect effect, boolean levelled, double x, double y, double z) {
        int lvl = p.level(buff);
        if (lvl > 0 && TotemAuras.inCube(player, x, y, z, reach(lvl))) {
            // Wither hurts only on some ticks of its time left (every 40 at I): 81 keeps a hurting tick in each second's refresh
            player.addEffect(new MobEffectInstance(effect, buff == Buff.WITHER ? 81 : 60, levelled ? lvl - 1 : 0, true, false, true));
        }
    }

    /** Extra random ticks a second for Growth at {@code level} over a cube of reach {@code r}: the world's own rate times the boost. */
    public static int samples(ServerLevel sl, int r, int level) {
        double side = 2.0 * r + 1, perBlock = sl.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) * 20.0 / 4096.0;
        double boost = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.MOB_GROWTH_BOOST, List.<Double>of()), level, 0.5 * (1 << (level - 1)));
        return (int) Math.min(MAX_SAMPLES, Math.round(side * side * side * perBlock * boost));
    }

    /** Random-ticks {@code samples} random spots of the cube; those holding a growing block grow as the world would grow them. Returns how many grew. */
    public static int grow(ServerLevel sl, BlockPos centre, int r, int samples, RandomSource random) {
        int ticked = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            q.set(centre.getX() + random.nextIntBetweenInclusive(-r, r), centre.getY() + random.nextIntBetweenInclusive(-r, r), centre.getZ() + random.nextIntBetweenInclusive(-r, r));
            if (!sl.shouldTickBlocksAt(q)) {
                continue; // as the world does: only chunks that tick, so a tree never grows into an unloaded one
            }
            BlockState st = sl.getBlockState(q);
            if (st.is(GROWS) && st.isRandomlyTicking()) {
                st.randomTick(sl, q.immutable(), random);
                ticked++;
            }
        }
        return ticked;
    }

    /** Keep: an item dropped in reach lies {@code keepMinutes} before it despawns. */
    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && event.getLevel() instanceof ServerLevel sl && !event.loadedFromDisk()
                && TotemAuras.levelInReach(sl, item.blockPosition(), Buff.KEEP) > 0) {
            item.lifespan = Math.max(item.lifespan, BSPConfig.getOr(BSPConfig.MOB_KEEP_MINUTES, 20) * 60 * 20);
        }
    }

    /** Undead Ward and Lantern: natural spawns refused in reach. */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL && event.getLevel() instanceof ServerLevel sl && warded(sl, event.getEntityType(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** The same for spawns that skip the placement check: patrols, phantoms. A chamber's own mobs come as SPAWNER and are never stopped. */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.PATROL) && !event.getLevel().isClientSide()
                && warded(event.getLevel().getLevel(), event.getEntity().getType(), event.getEntity().blockPosition())) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean warded(ServerLevel sl, EntityType<?> kind, BlockPos pos) {
        boolean undead = kind.is(UNDEAD), monster = kind.getCategory() == MobCategory.MONSTER;
        if (!undead && !monster) {
            return false;
        }
        return monster && TotemAuras.levelInReach(sl, pos, Buff.LANTERN) > 0 || undead && TotemAuras.levelInReach(sl, pos, Buff.UNDEAD_WARD) > 0;
    }
}
