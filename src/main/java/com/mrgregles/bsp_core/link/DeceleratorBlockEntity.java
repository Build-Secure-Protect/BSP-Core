package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Plasma Decelerator's controller, in its Core block. Once a second it checks that a Decelerator Port stands directly behind
 * the core (away from its facing). While formed the port knows this block as its controller and both carry {@code formed}.
 */
public class DeceleratorBlockEntity extends BlockEntity {
    private boolean formed;
    private boolean checked;
    /** The written Link Card in the core, or empty: it names the accelerator this core receives from. */
    private ItemStack card = ItemStack.EMPTY;
    /** What arrived through the link and waits for the port to send it on, with the powers it carries. */
    private final com.mrgregles.bsp_core.plasma.PlasmaStore store = new com.mrgregles.bsp_core.plasma.PlasmaStore(AcceleratorBlockEntity::capacity);
    private final LinkEnergy energy = new LinkEnergy(DeceleratorBlockEntity::rfPerTick, this::setChanged);
    private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = net.minecraftforge.common.util.LazyOptional.of(() -> energy);
    private com.mrgregles.bsp_core.plasma.PlasmaAccess access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
    @Nullable
    private GlobalPos anchorTotem;
    private long fedAt = -1000;
    private int inRate, outRate;
    /** 0 formed and dark, 1 RF connected, 2 receiving: synced for the renderer. */
    private int clientState;

    public DeceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECELERATOR_CORE.get(), pos, state);
    }

    public boolean isFormed() {
        return formed;
    }

    public static int rfPerTick() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.DECELERATOR_RF, 100);
    }

    // ---- what arrives

    public com.mrgregles.bsp_core.plasma.PlasmaStore store() {
        return store;
    }

    public LinkEnergy energy() {
        return energy;
    }

    public com.mrgregles.bsp_core.plasma.PlasmaAccess access() {
        return access;
    }

    @Nullable
    public GlobalPos anchorTotem() {
        return anchorTotem;
    }

    /** mB/t that came through the link last second. */
    public int inRate() {
        return inRate;
    }

    public boolean fedLately() {
        return level != null && level.getGameTime() - fedAt <= 45;
    }

    public int clientState() {
        return clientState;
    }

    private void show(ServerLevel sl) {
        int now = !formed ? 0 : fedLately() ? 2 : energy.hasSecond() ? 1 : 0;
        if (now != clientState) {
            clientState = now;
            setChanged();
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        if (now == 2) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** mB/t the port sent on into its cables, last second. */
    public int outRate() {
        return outRate;
    }

    void portReport(int out) {
        if ((outRate > 0) != (out > 0) && level != null && !level.isClientSide) {
            outRate = out;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        } else if (outRate != out) {
            outRate = out;
            setChanged();
        }
    }

    /** The accelerator hands over a second's worth of plasma with what it carries; the core pays a second of RF for it. */
    public void receive(long mB, int[] offered, com.mrgregles.bsp_core.plasma.PlasmaAccess access, @Nullable GlobalPos anchorTotem, GlobalPos from) {
        store.add(mB, offered);
        this.access = access;
        if (anchorTotem != null) {
            this.anchorTotem = anchorTotem;
        }
        fedAt = level == null ? 0 : level.getGameTime();
        inRate = (int) (mB / 20);
        energy.useSecond();
        setChanged();
    }

    @Nonnull
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(@Nonnull net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    // ---- the link

    public GlobalPos here() {
        return GlobalPos.of(level == null ? net.minecraft.world.level.Level.OVERWORLD : level.dimension(), worldPosition);
    }

    public boolean isLinked() {
        return LinkCardItem.isWritten(card);
    }

    /** The accelerator this core is linked to, from its card, or null. */
    @Nullable
    public GlobalPos accelerator() {
        return LinkCardItem.target(card);
    }

    public ItemStack card() {
        return card;
    }

    /**
     * The accelerator block entity the card names, if that world and chunk are loaded, or, with {@code load}, loading the chunk for a moment.
     * Null when the world is missing, the chunk is not loaded (and not loaded now) or no intake stands there.
     */
    @Nullable
    public AcceleratorBlockEntity acceleratorEntity(boolean load) {
        GlobalPos at = accelerator();
        if (at == null || !(level instanceof ServerLevel sl) || sl.getServer() == null) {
            return null;
        }
        ServerLevel far = sl.getServer().getLevel(at.dimension());
        if (far == null || (!load && !far.isLoaded(at.pos()))) {
            return null;
        }
        if (load) {
            far.getChunk(at.pos()); // loads the chunk if it is not loaded
        }
        return far.getBlockEntity(at.pos()) instanceof AcceleratorBlockEntity intake ? intake : null;
    }

    /**
     * Puts one written Link Card into the core and registers this core with the accelerator it names. The card is taken from
     * {@code stack} only on {@link Links.Plug#LINKED}.
     */
    public Links.Plug plug(ItemStack stack) {
        GlobalPos at = LinkCardItem.target(stack);
        if (at == null) {
            return Links.Plug.BLANK;
        }
        if (isLinked()) {
            return Links.Plug.ALREADY;
        }
        if (!(level instanceof ServerLevel sl)) {
            return Links.Plug.NO_WORLD;
        }
        if (at.dimension().equals(sl.dimension()) && at.pos().equals(worldPosition)) {
            return Links.Plug.SELF;
        }
        if (!Links.allowed(at.dimension()) || !Links.allowed(sl.dimension())) {
            return Links.Plug.BLACKLISTED;
        }
        ItemStack one = stack.copyWithCount(1);
        card = one;
        AcceleratorBlockEntity intake = acceleratorEntity(true);
        if (intake == null) {
            card = ItemStack.EMPTY;
            return sl.getServer().getLevel(at.dimension()) == null ? Links.Plug.NO_WORLD : Links.Plug.NO_ACCELERATOR;
        }
        if (!intake.addLink(here())) {
            card = ItemStack.EMPTY;
            return Links.Plug.FULL;
        }
        stack.shrink(1);
        changed();
        return Links.Plug.LINKED;
    }

    /** Takes the card out and tells the accelerator (if it can be reached) to forget this core. The card, or empty. */
    public ItemStack unplug() {
        if (!isLinked()) {
            return ItemStack.EMPTY;
        }
        AcceleratorBlockEntity intake = acceleratorEntity(true);
        if (intake != null) {
            intake.removeLink(here());
        }
        ItemStack out = card;
        card = ItemStack.EMPTY;
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public Direction facing() {
        return getBlockState().hasProperty(DeceleratorCoreBlock.FACING) ? getBlockState().getValue(DeceleratorCoreBlock.FACING) : Direction.NORTH;
    }

    /** Where the port must stand: directly behind the core. */
    public BlockPos portPos() {
        return worldPosition.relative(facing().getOpposite());
    }

    private boolean complete(ServerLevel sl) {
        BlockPos p = portPos();
        return sl.isLoaded(p) && sl.getBlockState(p).is(ModBlocks.DECELERATOR_PORT.get());
    }

    public void serverTick(ServerLevel sl) {
        if (checked && sl.getGameTime() % 20 != 0) {
            return;
        }
        checked = true;
        setFormed(sl, complete(sl));
        if (!fedLately() && inRate != 0) {
            inRate = 0;
            setChanged();
        }
        show(sl);
    }

    private void setFormed(ServerLevel sl, boolean now) {
        if (now && sl.getBlockEntity(portPos()) instanceof LinkPartBlockEntity port) {
            port.setController(worldPosition);
        }
        if (now == formed) {
            return;
        }
        formed = now;
        showParts(sl, now);
        setChanged();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void showParts(ServerLevel sl, boolean now) {
        for (BlockPos p : new BlockPos[]{worldPosition, portPos()}) {
            if (!sl.isLoaded(p)) {
                continue;
            }
            BlockState st = sl.getBlockState(p);
            if (st.hasProperty(StructurePartBlock.FORMED) && st.getValue(StructurePartBlock.FORMED) != now
                    && (st.is(ModBlocks.DECELERATOR_PORT.get()) || p.equals(worldPosition))) {
                sl.setBlock(p, st.setValue(StructurePartBlock.FORMED, now), 3);
            }
            if (!now && sl.getBlockEntity(p) instanceof LinkPartBlockEntity part) {
                part.setController(null);
            }
        }
    }

    void partBroken() {
        if (level instanceof ServerLevel sl) {
            setFormed(sl, false);
        }
    }

    void unform() {
        if (level instanceof ServerLevel sl) {
            formed = false;
            showParts(sl, false);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }

    private void write(CompoundTag tag) {
        tag.putBoolean("Formed", formed);
        if (!card.isEmpty()) {
            tag.put("Card", card.save(new CompoundTag()));
        }
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("InRate", inRate);
        tag.putInt("OutRate", outRate);
        tag.putByte("Show", (byte) clientState);
        store.save(tag, "Plasma");
        Links.save(tag, "Anchor", anchorTotem);
        Links.saveAccess(tag, "Access", access);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        formed = tag.getBoolean("Formed");
        card = tag.contains("Card") ? ItemStack.of(tag.getCompound("Card")) : ItemStack.EMPTY;
        energy.load(tag.getInt("Energy"));
        inRate = tag.getInt("InRate");
        outRate = tag.getInt("OutRate");
        clientState = tag.getByte("Show");
        store.load(tag, "Plasma");
        anchorTotem = Links.load(tag, "Anchor");
        access = Links.loadAccess(tag, "Access");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        write(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
