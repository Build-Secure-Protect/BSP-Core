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

    /** Dropped: the ledger hears it at once, not at the first tick (a chunk whose entities are still loading does not tick it). */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            report();
        }
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

    /** Tells the ledger this totem is lying in the world, so it is not taken for lost. A husk is never reported; a stale copy becomes one. */
    private void report() {
        ItemStack stack = getItem();
        if (!(level() instanceof ServerLevel sl) || stack.isEmpty() || TotemIdentity.isSpent(stack)) {
            return;
        }
        TotemIdentity.ensure(stack);
        UUID id = TotemIdentity.id(stack), instance = TotemIdentity.instance(stack);
        if (TotemLedger.get(sl.getServer()).stale(id, instance) || TotemIdentity.oldSeason(stack, sl.getServer())) {
            TotemIdentity.spend(stack); // reissued, retired or from an earlier season: this copy is a husk, never a second totem
        }
        setItem(stack);
        if (id != null && instance != null && !TotemIdentity.isSpent(stack)) {
            TotemLedger.get(sl.getServer()).seen(id, instance, TotemOwner.fromStack(stack).map(TotemOwner::uuid).orElse(null), TotemLedger.DROPPED, stack.getTag(), sl.getServer().overworld().getGameTime());
        }
    }

    /** Whether this is a live totem: not a husk, and not a stale copy of one reissued or retired since. */
    private boolean live() {
        ItemStack stack = getItem();
        return TotemInventories.isTotem(stack) && !(level() instanceof ServerLevel sl
                && (TotemLedger.get(sl.getServer()).stale(TotemIdentity.id(stack), TotemIdentity.instance(stack)) || TotemIdentity.oldSeason(stack, sl.getServer())));
    }

    private void tryPlace() {
        ServerLevel level = (ServerLevel) level();
        if (!live()) {
            // a husk is never placed: it lies as a plain item instead, which despawns and anyone may pick up
            ItemStack husk = getItem().copy();
            TotemIdentity.spend(husk);
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, getX(), getY(), getZ(), husk));
            destroy();
            return;
        }
        if (NoTotemServers.here()) {
            // no placement on this server: the totem goes to its owner (into their hands here, or waiting for their next login)
            var owner = TotemOwner.fromStack(getItem());
            if (owner.isPresent()) {
                com.mrgregles.bsp_core.admin.AdminService.deliver(level.getServer(), owner.get().uuid(), java.util.List.of(getItem().copy()), "message.bsp_core.transfer.returned");
                destroy();
                return;
            }
            return; // an ownerless totem stays on the ground here: never placed on a server without totem placement
        }
        if (TotemPlacer.place(level, blockPosition(), getItem())) {
            destroy();
        } else {
            groundTicks -= 20; // nowhere to go right now (rare); retry in a second
            BSPCore.LOGGER.warn("Could not find a spot to place a Shatter Totem near {} in {}", blockPosition(), level.dimension().location());
        }
    }

    // --- indestructible, immovable ---

    /** Set when this entity is removed on purpose (placed, picked up, delivered, wiped): its removal then rescues nothing. */
    private boolean handled;

    /** Removes the entity on purpose: its totem was placed, picked up or delivered, or it is being wiped. */
    public void destroy() {
        handled = true;
        discard();
    }

    /**
     * Removed by anything else ({@code /kill}, an entity-clearing mod, a discard by another mod): a live totem is not lost with it;
     * it is placed where it lay, or given to its owner when no spot is free. Unloading and changing dimension are not removals.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!handled && !level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && live()) {
            handled = true;
            ServerLevel level = (ServerLevel) level();
            ItemStack totem = getItem().copy();
            if (NoTotemServers.here() || !TotemPlacer.place(level, blockPosition(), totem)) {
                TotemOwner.fromStack(totem).ifPresent(o -> com.mrgregles.bsp_core.admin.AdminService.deliver(level.getServer(), o.uuid(), java.util.List.of(totem),
                        "message.bsp_core.transfer.returned"));
            }
            BSPCore.LOGGER.info("A dropped Shatter Totem at {} in {} was removed ({}); it was placed or given to its owner", blockPosition(), level.dimension().location(), reason);
        }
        super.remove(reason);
    }

    /** Never through a portal: it would land in a dimension totems may not be in, and place itself there. */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

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
            destroy();
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
