package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The dropped form of the Shatter Totem. It is not an {@link ItemEntity} on purpose: hoppers, Create funnels, Mekanism
 * collectors, item magnets and every other kind of automation look for item entities, so none of them can carry a totem off.
 *
 * <ul>
 *   <li>Never despawns, never takes damage, immune to fire, lava and explosions, cannot be pushed.</li>
 *   <li>Falling out of the world teleports it back to the last ground position it saw (or the dimension's spawn).</li>
 *   <li>After {@code restrictions.groundSecondsBeforePlace} seconds it places itself as a block.</li>
 *   <li>Only its owner can pick it up, by walking into it. Everyone else must wait for it to place and steal it.</li>
 *   <li>Reports itself to the totem ledger every second, so the ledger knows the totem is in the world and not lost.</li>
 * </ul>
 */
public class ShatterTotemItemEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ShatterTotemItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final String TAG_GROUND_TICKS = "GroundTicks", TAG_LAST_GROUND = "LastGroundPos", TAG_PICKUP_DELAY = "PickupDelay", TAG_ITEM = "Item";

    private int groundTicks, pickupDelay;
    private BlockPos lastGroundPos;

    public ShatterTotemItemEntity(EntityType<? extends Entity> type, Level level) {
        super(type, level);
    }

    public ShatterTotemItemEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(ModEntities.SHATTER_TOTEM_ITEM.get(), level);
        setPos(x, y, z);
        setItem(stack);
        setDeltaMovement(level.random.nextDouble() * 0.2 - 0.1, 0.2, level.random.nextDouble() * 0.2 - 0.1);
    }

    /** Builds the totem entity from the vanilla one Minecraft created for a drop. */
    public static ShatterTotemItemEntity from(Level level, ItemEntity vanilla, ItemStack stack) {
        ShatterTotemItemEntity entity = new ShatterTotemItemEntity(ModEntities.SHATTER_TOTEM_ITEM.get(), level);
        entity.setPos(vanilla.getX(), vanilla.getY(), vanilla.getZ());
        entity.setDeltaMovement(vanilla.getDeltaMovement());
        entity.setItem(stack);
        entity.setPickUpDelay(10);
        return entity;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    public void setPickUpDelay(int ticks) {
        pickupDelay = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        // the physics of a dropped item, without being one
        Vec3 d = getDeltaMovement();
        if (isInWater() && getFluidHeight(net.minecraft.tags.FluidTags.WATER) > 0.1) {
            d = new Vec3(d.x * 0.99, d.y + (d.y < 0.06 ? 5.0E-4 : 0), d.z * 0.99);
        } else if (isInLava() && getFluidHeight(net.minecraft.tags.FluidTags.LAVA) > 0.1) {
            d = new Vec3(d.x * 0.95, d.y + (d.y < 0.06 ? 5.0E-4 : 0), d.z * 0.95);
        } else if (!isNoGravity()) {
            d = d.add(0, -0.04, 0);
        }
        setDeltaMovement(d);
        move(MoverType.SELF, getDeltaMovement());
        float friction = 0.98F;
        if (onGround()) {
            friction = level().getBlockState(BlockPos.containing(getX(), getY() - 1, getZ())).getFriction(level(), BlockPos.containing(getX(), getY() - 1, getZ()), this) * 0.98F;
        }
        d = getDeltaMovement().multiply(friction, 0.98, friction);
        if (onGround() && d.y < 0) {
            d = d.multiply(1, -0.5, 1);
        }
        setDeltaMovement(d);
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (onGround()) {
            lastGroundPos = blockPosition();
        }
        if (tickCount % 20 == 0) {
            report();
        }
        groundTicks++;
        int limit = BSPConfig.GROUND_SECONDS_BEFORE_PLACE.get() * 20;
        // place once the timer is up and the totem has settled; never wait more than double the timer
        if (groundTicks >= limit && (onGround() || groundTicks >= limit * 2)) {
            tryPlace();
        }
    }

    /** Tells the ledger this totem is lying in the world, so it is not taken for lost. */
    private void report() {
        ItemStack stack = getItem();
        if (!(level() instanceof ServerLevel sl) || stack.isEmpty()) {
            return;
        }
        TotemIdentity.ensure(stack);
        setItem(stack);
        UUID id = TotemIdentity.id(stack), instance = TotemIdentity.instance(stack);
        if (id != null && instance != null) {
            TotemLedger.get(sl.getServer()).seen(id, instance, TotemOwner.fromStack(stack).map(TotemOwner::uuid).orElse(null), TotemLedger.DROPPED, stack.getTag(), sl.getServer().overworld().getGameTime());
        }
    }

    private void tryPlace() {
        ServerLevel level = (ServerLevel) level();
        if (TotemPlacer.place(level, blockPosition(), getItem())) {
            discard();
        } else {
            groundTicks -= 20; // nowhere to go right now (rare); retry in a second
            BSPCore.LOGGER.warn("Could not find a spot to place a Shatter Totem near {} in {}", blockPosition(), level.dimension().location());
        }
    }

    // --- indestructible, immovable ---

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
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // explosions and crowds do not move it
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

    // --- pickup: the owner, walking into it ---

    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide || pickupDelay > 0 || isRemoved()) {
            return;
        }
        ItemStack stack = getItem();
        TotemOwner owner = TotemOwner.fromStack(stack).orElse(null);
        if (owner != null && !owner.uuid().equals(player.getUUID())) {
            return;
        }
        if (player.getInventory().add(stack.copy())) {
            player.take(this, 1);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            discard();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return getItem().copy();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_ITEM)) {
            setItem(ItemStack.of(tag.getCompound(TAG_ITEM)));
        }
        groundTicks = tag.getInt(TAG_GROUND_TICKS);
        pickupDelay = tag.getInt(TAG_PICKUP_DELAY);
        lastGroundPos = tag.contains(TAG_LAST_GROUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_LAST_GROUND)) : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put(TAG_ITEM, getItem().save(new CompoundTag()));
        tag.putInt(TAG_GROUND_TICKS, groundTicks);
        tag.putInt(TAG_PICKUP_DELAY, pickupDelay);
        if (lastGroundPos != null) {
            tag.put(TAG_LAST_GROUND, NbtUtils.writeBlockPos(lastGroundPos));
        }
    }
}
