package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who killed a player, for bounties. Only a player's own blow counts: their hit, or a projectile they shot or threw (arrow,
 * trident, crossbow firework). A death by a fall, lava, fire or the void counts for the player who last hit the victim if that
 * was within {@link #ASSIST_TICKS} and no mob hit them since. A mob's blow never counts, even right after a player's hit; nor do
 * a tamed wolf, TNT, an end crystal, a bed or anything a machine (a fake player) does.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BountyKills {
    private BountyKills() {}

    /** How long after a player's hit a fall, lava, fire or void death still counts for them: 5 seconds. */
    public static final int ASSIST_TICKS = 100;

    /** The last hit a player took that matters here: from a player ({@code attacker} set) or a mob ({@code attacker} null), at a server tick. */
    public record Hit(@Nullable UUID attacker, String attackerName, long tick) {}

    private static final Map<UUID, Hit> HITS = new ConcurrentHashMap<>();
    private static final List<ResourceKey<DamageType>> ENVIRONMENT = List.of(DamageTypes.FALL, DamageTypes.LAVA, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE,
            DamageTypes.FELL_OUT_OF_WORLD);

    /** The player whose own blow this is (their hit, or a projectile they own), or null. Never a fake player, never the victim. */
    @Nullable
    public static ServerPlayer playerBlow(DamageSource source, LivingEntity victim) {
        Entity by = source.getEntity(), direct = source.getDirectEntity();
        if (!(by instanceof ServerPlayer p) || p instanceof FakePlayer || p == victim || p.getUUID().equals(victim.getUUID())) {
            return null;
        }
        if (direct == p) {
            return p;
        }
        if (direct instanceof Projectile pr && pr.getOwner() == p) {
            return p;
        }
        return null;
    }

    /** True when a mob (anything living but a real player: a tamed wolf, a golem, a fake player) dealt this. */
    public static boolean mobBlow(DamageSource source) {
        Entity by = source.getEntity(), direct = source.getDirectEntity();
        return isMob(by) || isMob(direct);
    }

    private static boolean isMob(@Nullable Entity e) {
        return e instanceof LivingEntity && (!(e instanceof Player) || e instanceof FakePlayer);
    }

    public static boolean environmental(DamageSource source) {
        if (source.getEntity() != null) {
            return false;
        }
        for (ResourceKey<DamageType> k : ENVIRONMENT) {
            if (source.is(k)) {
                return true;
            }
        }
        return false;
    }

    /** The hit to remember for {@code victim} after this damage, or {@code last} unchanged when it neither comes from a player nor a mob. */
    @Nullable
    public static Hit afterDamage(LivingEntity victim, DamageSource source, @Nullable Hit last, long tick) {
        ServerPlayer p = playerBlow(source, victim);
        if (p != null) {
            return new Hit(p.getUUID(), p.getGameProfile().getName(), tick);
        }
        if (mobBlow(source)) {
            return new Hit(null, "", tick);
        }
        return last;
    }

    /**
     * Who gets the kill when {@code victim} dies of {@code source} at {@code tick}, with {@code last} the hit remembered before it: the
     * player whose own blow it is; or, for a fall, lava, fire or void death, the player who hit last within {@link #ASSIST_TICKS}
     * with no mob since. Null for anything else.
     */
    @Nullable
    public static Hit killer(LivingEntity victim, DamageSource source, @Nullable Hit last, long tick) {
        ServerPlayer p = playerBlow(source, victim);
        if (p != null) {
            return new Hit(p.getUUID(), p.getGameProfile().getName(), tick);
        }
        if (mobBlow(source) || !environmental(source)) {
            return null;
        }
        if (last != null && last.attacker() != null && tick - last.tick() <= ASSIST_TICKS && !last.attacker().equals(victim.getUUID())) {
            return last;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) {
            return;
        }
        long tick = victim.server.getTickCount();
        Hit after = afterDamage(victim, event.getSource(), HITS.get(victim.getUUID()), tick);
        if (after != null) {
            HITS.put(victim.getUUID(), after);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer dead) || dead instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = dead.server;
        Hit last = HITS.remove(dead.getUUID());
        Hit k = killer(dead, event.getSource(), last, server.getTickCount());
        if (k != null) {
            Bounties.claimKill(server, dead.getUUID(), dead.getGameProfile().getName(), k.attacker(), k.attackerName(), Bounties.now(), n -> {});
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HITS.remove(event.getEntity().getUUID());
    }
}
