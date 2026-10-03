package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The dropped form of the Shatter Totem.
 *
 * <ul>
 *   <li>Never despawns, never takes damage, immune to fire, lava and explosions.</li>
 *   <li>Falling out of the world teleports it back to the last ground position it saw (or the
 *       dimension's spawn) instead of deleting it.</li>
 *   <li>After {@code restrictions.groundSecondsBeforePlace} seconds it places itself as a block.</li>
 *   <li>Only its owner can pick it up. Everyone else must wait for it to place and steal it.</li>
 * </ul>
 */
public class ShatterTotemItemEntity extends ItemEntity {
    private static final String TAG_GROUND_TICKS = "GroundTicks";
    private static final String TAG_LAST_GROUND = "LastGroundPos";

    private int groundTicks;
    private BlockPos lastGroundPos;

    public ShatterTotemItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        setUnlimitedLifetime();
    }

    public ShatterTotemItemEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(ModEntities.SHATTER_TOTEM_ITEM.get(), level);
        setPos(x, y, z);
        setItem(stack);
        setDeltaMovement(level.random.nextDouble() * 0.2 - 0.1, 0.2, level.random.nextDouble() * 0.2 - 0.1);
    }

    /** Builds the custom entity from the vanilla one Minecraft created for a drop. */
    public static ShatterTotemItemEntity from(Level level, ItemEntity vanilla, ItemStack stack) {
        ShatterTotemItemEntity entity = new ShatterTotemItemEntity(ModEntities.SHATTER_TOTEM_ITEM.get(), level);
        entity.setPos(vanilla.getX(), vanilla.getY(), vanilla.getZ());
        entity.setDeltaMovement(vanilla.getDeltaMovement());
        entity.setItem(stack);
        entity.setPickUpDelay(10);
        return entity;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (onGround()) {
            lastGroundPos = blockPosition();
        }
        groundTicks++;
        int limit = BSPConfig.GROUND_SECONDS_BEFORE_PLACE.get() * 20;
        // Place once the timer is up and the totem has settled; never wait more than double the timer.
        if (groundTicks >= limit && (onGround() || groundTicks >= limit * 2)) {
            tryPlace();
        }
    }

    private void tryPlace() {
        ServerLevel level = (ServerLevel) level();
        if (TotemPlacer.place(level, blockPosition(), getItem())) {
            discard();
        } else {
            // Nowhere to go right now (should be rare); retry in a second.
            groundTicks -= 20;
            BSPCore.LOGGER.warn("Could not find a spot to place a Shatter Totem near {} in {}", blockPosition(), level.dimension().location());
        }
    }

    // --- indestructible ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    protected void onBelowWorld() {
        if (level().isClientSide) {
            return;
        }
        Vec3 target;
        if (lastGroundPos != null) {
            target = Vec3.atBottomCenterOf(lastGroundPos.above());
        } else {
            BlockPos spawn = level().getSharedSpawnPos();
            BlockPos top = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
            target = Vec3.atBottomCenterOf(top);
        }
        setDeltaMovement(Vec3.ZERO);
        teleportTo(target.x, target.y, target.z);
        fallDistance = 0;
    }

    // --- pickup: owner only ---

    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide) {
            return;
        }
        TotemOwner owner = TotemOwner.fromStack(getItem()).orElse(null);
        if (owner != null && !owner.uuid().equals(player.getUUID())) {
            return;
        }
        super.playerTouch(player);
    }

    // --- persistence ---

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_GROUND_TICKS, groundTicks);
        if (lastGroundPos != null) {
            tag.putLong(TAG_LAST_GROUND, lastGroundPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        groundTicks = tag.getInt(TAG_GROUND_TICKS);
        lastGroundPos = tag.contains(TAG_LAST_GROUND) ? BlockPos.of(tag.getLong(TAG_LAST_GROUND)) : null;
        setUnlimitedLifetime();
    }
}
