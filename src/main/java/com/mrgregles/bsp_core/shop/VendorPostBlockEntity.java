package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A Vendor Post: which shop it runs, its opening hours, and which stock game mob stands on it.
 * Once a second the server checks the hours and sets the block's OPEN state, which lights the lamp
 * and powers the block beneath.
 */
public class VendorPostBlockEntity extends BlockEntity implements Assignable {
    public String shopId = "";
    public OpenHours hours = OpenHours.ALWAYS;
    /** Entity type id of the figure, for example "minecraft:villager", with optional entity NBT (a villager profession). */
    public String mob = "minecraft:villager";
    public String mobNbt = "";
    private int tick;

    public VendorPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENDOR_POST.get(), pos, state);
    }

    public boolean assigned() {
        return !shopId.isEmpty();
    }

    @Override
    public void assign(String shopId, OpenHours hours, String mob, String mobNbt) {
        set(shopId, hours, mob, mobNbt);
    }

    @Override
    public String shopId() {
        return shopId;
    }

    @Override
    public OpenHours hours() {
        return hours;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, VendorPostBlockEntity be) {
        if (++be.tick % 20 != 0) {
            return;
        }
        Shop shop = be.assigned() ? Shops.ledger(level.getServer()).shop(be.shopId) : null;
        boolean open = shop != null && shop.effectiveHours(be.hours).isOpen(level);
        if (state.getValue(VendorPostBlock.OPEN) != open) {
            level.setBlock(pos, state.setValue(VendorPostBlock.OPEN, open), 3);
            level.updateNeighborsAt(pos.below(), state.getBlock());
        }
    }

    public void set(String shopId, OpenHours hours, String mob, String mobNbt) {
        this.shopId = shopId;
        this.hours = hours;
        this.mob = mob;
        this.mobNbt = mobNbt;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            tick = 19; // check the hours on the next tick
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Shop", shopId);
        tag.put("Hours", hours.save());
        tag.putString("Mob", mob);
        tag.putString("MobNbt", mobNbt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shopId = tag.getString("Shop");
        hours = OpenHours.load(tag.getCompound("Hours"));
        mob = tag.contains("Mob") ? tag.getString("Mob") : "minecraft:villager";
        mobNbt = tag.getString("MobNbt");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2.5, 0).inflate(0.5);
    }
}
