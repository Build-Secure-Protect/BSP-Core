package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** The placed battery: its plasma tag, read from the item on placing and written back when mined. */
public class PlasmaBatteryBlockEntity extends BlockEntity {
    private CompoundTag plasma = new CompoundTag();

    public PlasmaBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_BATTERY.get(), pos, state);
    }

    public void loadFrom(ItemStack stack) {
        plasma = PlasmaItems.read(stack);
        setChanged();
    }

    public ItemStack toStack() {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        PlasmaItems.write(stack, plasma);
        return stack;
    }

    /** A stack view of the contents, for the helpers that read tags. */
    private ItemStack view() {
        return toStack();
    }

    public int stored() {
        return PlasmaItems.stored(view());
    }

    public int capacity() {
        return PlasmaItems.capacity(view());
    }

    public int[] powers() {
        return PlasmaItems.powers(view());
    }

    /** Takes up to {@code mB} and returns what was taken. */
    public int take(int mB) {
        ItemStack v = view();
        int had = PlasmaItems.stored(v), took = Math.min(mB, had);
        if (took > 0) {
            PlasmaItems.setStored(v, had - took);
            plasma = PlasmaItems.read(v);
            setChanged();
            if (level != null && !level.isClientSide && level.getGameTime() % 100 == 0) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return took;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(PlasmaItems.TAG, plasma.copy());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        plasma = tag.getCompound(PlasmaItems.TAG).copy();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(PlasmaItems.TAG, plasma.copy());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
