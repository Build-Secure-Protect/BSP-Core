package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The Plasma Accelerator's controller, in its Intake block. Once a second it checks the ring: seven Accelerator Frames at the
 * other cells of the 3 x 3 standing across the intake's facing, and nothing in the middle. While formed every frame knows this
 * block as its controller and carries {@code formed}, so the ring can be drawn as one shape.
 */
public class AcceleratorBlockEntity extends BlockEntity implements com.mrgregles.bsp_core.plasma.PlasmaReceiver {
    /** The ring's cells relative to the intake, as (right, up) steps: the intake is (0, 0), the hole (0, 1). */
    static final int[][] RING = {{-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}};
    static final int[] HOLE = {0, 1};

    private boolean formed;
    private boolean checked;
    /** The Decelerator Cores this accelerator sends to, by world and position, at most {@link #maxLinks()}. */
    private final List<GlobalPos> links = new ArrayList<>();
    private int expanders;
    /** Who may write Link Cards here: the users of the totem feeding it. Empty until the intake has been fed, when anyone may. */
    private com.mrgregles.bsp_core.plasma.PlasmaAccess access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
    /** What arrived and waits to go through, with the powers it carries. */
    private final com.mrgregles.bsp_core.plasma.PlasmaStore store = new com.mrgregles.bsp_core.plasma.PlasmaStore(AcceleratorBlockEntity::capacity);
    private final LinkEnergy energy = new LinkEnergy(AcceleratorBlockEntity::rfPerTick, this::setChanged);
    private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = net.minecraftforge.common.util.LazyOptional.of(() -> energy);
    /** The totem whose Anchor the plasma carries, in its own world. */
    @Nullable
    private GlobalPos anchorTotem;
    private long fedAt = -1000;
    /** This second's intake: pressure offered by the runs feeding it, and mB moved in, both summed since the last tick. */
    private int inAcc, receivedThisSecond;
    private int inRate, outRate;
    /** What each link did this second, for the screen and Jade. */
    private final java.util.Map<GlobalPos, Links.Report> reports = new java.util.LinkedHashMap<>();
    private long lastWake = Long.MIN_VALUE;
    private boolean wakeNow;
    /** 0 formed and dark, 1 RF connected, 2 a link delivering: synced for the renderer and the block's light. */
    private int clientState;
    /** Keeps a sleeping far side ticking for five seconds after the accelerator wakes it. */
    public static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> WAKE =
            net.minecraft.server.level.TicketType.create("bsp_core_link_wake", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 100);

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
    }

    public boolean isFormed() {
        return formed;
    }

    public static int baseLinks() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.ACCELERATOR_LINKS, 1);
    }

    public static int expanderMax() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_EXPANDER_MAX, 2);
    }

    /** Most mB/t the accelerator takes in over every run feeding it. */
    public static int acceleratorMax() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.ACCELERATOR_MAX, 3000);
    }

    public static int rfPerTick() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.ACCELERATOR_RF, 1000);
    }

    /** The buffer at either end: two seconds of the most the accelerator takes. */
    public static long capacity() {
        return acceleratorMax() * 20L * 2;
    }

    public static int wakeSeconds() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_WAKE_SECONDS, 60);
    }

    public static double loss() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_LOSS, 0.0);
    }

    // ---- as a receiver at the end of a run

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

    /** mB/t offered by the runs feeding the intake, last second. */
    public int inRate() {
        return inRate;
    }

    /** mB/t sent through every link together, last second. */
    public int outRate() {
        return outRate;
    }

    public boolean fedLately() {
        return level != null && level.getGameTime() - fedAt <= 45;
    }

    /** 0 formed and dark, 1 RF connected, 2 delivering: what the renderer animates. */
    public int clientState() {
        return clientState;
    }

    /** Works out the animation state after a hop, syncs it when it changes, sets the block's light, and plays the machine's sounds and particles. */
    private void show(ServerLevel sl) {
        int now = !formed ? 0 : outRate > 0 ? 2 : energy.hasSecond() ? 1 : 0;
        BlockState st = getBlockState();
        if (st.hasProperty(AcceleratorIntakeBlock.GLOW) && st.getValue(AcceleratorIntakeBlock.GLOW) != now) {
            sl.setBlock(worldPosition, st.setValue(AcceleratorIntakeBlock.GLOW, now), 3);
        }
        if (now != clientState) {
            clientState = now;
            setChanged();
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        if (now >= 1 && sl.getGameTime() % 60 == 0) {
            sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, now == 2 ? 0.6f : 0.3f, now == 2 ? 0.7f : 0.5f);
        }
        if (now == 2) {
            BlockPos h = hole();
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            if (sl.getGameTime() % 100 == 0) {
                sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 0.8f);
            }
        }
    }

    /** The state and rate of each link this second, in link order. */
    public Links.Report report(GlobalPos core) {
        return reports.getOrDefault(core, Links.Report.NONE);
    }

    @Override
    public int wanted() {
        return formed ? (int) Math.min(store.room(), Math.max(0, acceleratorMax() * 20L - receivedThisSecond)) : 0;
    }

    @Override
    public boolean accepts(BlockPos from, BlockState state, Direction d) {
        return formed;
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, com.mrgregles.bsp_core.plasma.PlasmaAccess access, @Nullable GlobalPos anchorTotem) {
        store.add(movedMB, offeredByOrdinal);
        receivedThisSecond += movedMB;
        inAcc += pressurePerTick;
        if (fedLately()) {
            this.access.addAll(access);
        } else {
            this.access = access.copy();
        }
        if (anchorTotem != null) {
            this.anchorTotem = anchorTotem;
        }
        fedAt = level == null ? 0 : level.getGameTime();
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

    // ---- the hop, once a second

    /** The next tick wakes every sleeping far side at once (the WAKE key). */
    public void wakeNow() {
        wakeNow = true;
    }

    /** Undoes link {@code index}: the far core's card comes out and drops there; a core that is gone is simply forgotten. */
    public void unlink(int index) {
        if (index < 0 || index >= links.size() || !(level instanceof ServerLevel sl)) {
            return;
        }
        GlobalPos at = links.get(index);
        ServerLevel far = sl.getServer().getLevel(at.dimension());
        if (far != null) {
            far.getChunk(at.pos());
            if (far.getBlockEntity(at.pos()) instanceof DeceleratorBlockEntity core && here().equals(core.accelerator())) {
                net.minecraft.world.item.ItemStack card = core.unplug();
                if (!card.isEmpty()) {
                    net.minecraft.world.level.block.Block.popResource(far, at.pos(), card);
                }
            }
        }
        removeLink(at);
        reports.remove(at);
    }

    /** Everything the screen shows. */
    public com.mrgregles.bsp_core.network.LinkViewPacket view() {
        List<com.mrgregles.bsp_core.network.LinkViewPacket.Power> powers = new ArrayList<>();
        int[] offered = store.offered();
        for (int i = 0; i < offered.length; i++) {
            if (offered[i] > 0) {
                powers.add(new com.mrgregles.bsp_core.network.LinkViewPacket.Power(i, offered[i]));
            }
        }
        List<com.mrgregles.bsp_core.network.LinkViewPacket.Link> out = new ArrayList<>();
        for (GlobalPos at : links) {
            Links.Report r = report(at);
            out.add(new com.mrgregles.bsp_core.network.LinkViewPacket.Link(at.dimension().location().toString(), at.pos(), r.state().ordinal(), r.rate()));
        }
        return new com.mrgregles.bsp_core.network.LinkViewPacket(worldPosition, here().dimension().location().toString(), formed, inRate, outRate,
                energy.getEnergyStored(), LinkEnergy.capacity(), maxLinks(), expanders, powers, out);
    }

    private void hop(ServerLevel sl) {
        long now = sl.getGameTime();
        inRate = inAcc;
        inAcc = 0;
        receivedThisSecond = 0;
        outRate = 0;
        boolean wake = wakeNow || (wakeSeconds() > 0 && (lastWake == Long.MIN_VALUE || now - lastWake >= wakeSeconds() * 20L));
        reports.clear();
        List<DeceleratorBlockEntity> live = new ArrayList<>();
        List<GlobalPos> liveAt = new ArrayList<>();
        for (GlobalPos at : new ArrayList<>(links)) {
            ServerLevel far = sl.getServer().getLevel(at.dimension());
            if (far == null) {
                reports.put(at, new Links.Report(Links.State.NO_WORLD, 0));
                continue;
            }
            // a far side is awake only while its chunk ticks: a chunk still in memory with nothing keeping it does not run its core
            if (!far.isLoaded(at.pos()) || !far.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(at.pos()))) {
                if (!wake) {
                    reports.put(at, new Links.Report(Links.State.ASLEEP, 0));
                    continue;
                }
                net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(at.pos());
                far.getChunkSource().addRegionTicket(WAKE, chunk, 2, chunk);
                far.getChunk(at.pos());
            }
            if (!(far.getBlockEntity(at.pos()) instanceof DeceleratorBlockEntity core) || !here().equals(core.accelerator())) {
                links.remove(at); // gone, or re-linked elsewhere: forget it
                changed();
                continue;
            }
            if (!core.isFormed()) {
                reports.put(at, new Links.Report(Links.State.UNFORMED_THERE, 0));
            } else if (!core.energy().hasSecond()) {
                reports.put(at, new Links.Report(Links.State.NO_RF_THERE, 0));
            } else if (core.store().room() <= 0) {
                reports.put(at, new Links.Report(Links.State.FULL_THERE, 0));
            } else {
                live.add(core);
                liveAt.add(at);
            }
        }
        if (wake) {
            lastWake = now;
            wakeNow = false;
        }
        Links.State idle = !formed ? Links.State.UNFORMED_HERE : !energy.hasSecond() ? Links.State.NO_RF_HERE : Links.State.IDLE;
        long available = formed ? Math.min(store.stored(), acceleratorMax() * 20L) : 0;
        if (live.isEmpty() || available <= 0 || idle != Links.State.IDLE) {
            for (GlobalPos at : liveAt) {
                reports.put(at, new Links.Report(idle, 0));
            }
            return;
        }
        // equal shares between the links that can take plasma, a link that is nearly full leaving the rest to the others
        Integer[] order = new Integer[live.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingLong(i -> live.get(i).store().room()));
        long[] share = new long[live.size()];
        long left = available;
        for (int k = 0; k < order.length; k++) {
            int i = order[k];
            share[i] = Math.min(live.get(i).store().room(), left / (order.length - k));
            left -= share[i];
        }
        int[] offered = store.offered();
        long total = 0;
        for (int i = 0; i < live.size(); i++) {
            if (share[i] <= 0) {
                reports.put(liveAt.get(i), new Links.Report(Links.State.IDLE, 0));
                continue;
            }
            long took = store.drain(share[i]);
            long arrived = Math.round(took * (1.0 - loss()));
            live.get(i).receive(arrived, offered, access.copy(), anchorTotem, here());
            total += took;
            reports.put(liveAt.get(i), new Links.Report(Links.State.DELIVERING, (int) (arrived / 20)));
        }
        if (total > 0) {
            energy.useSecond();
            outRate = (int) (total / 20);
            setChanged();
        }
    }

    // ---- links and expanders

    public GlobalPos here() {
        return GlobalPos.of(level == null ? net.minecraft.world.level.Level.OVERWORLD : level.dimension(), worldPosition);
    }

    public List<GlobalPos> links() {
        return java.util.Collections.unmodifiableList(links);
    }

    public int expanders() {
        return expanders;
    }

    /** Links this accelerator may hold: the base number plus one per fitted expander. */
    public int maxLinks() {
        return baseLinks() + expanders;
    }

    /** Fits one Link Expander; false when the intake already holds {@link #expanderMax()}. */
    public boolean fitExpander() {
        if (expanders >= expanderMax()) {
            return false;
        }
        expanders++;
        changed();
        return true;
    }

    /** Takes one expander out; false when none is fitted or a link would be left without a slot. */
    public boolean takeExpander() {
        if (expanders <= 0 || links.size() > maxLinks() - 1) {
            return false;
        }
        expanders--;
        changed();
        return true;
    }

    /** Whether {@code player} may write a Link Card here: anyone while nothing has fed the intake, else the feeding totem's users and admins. */
    public boolean mayLink(Player player) {
        return access.users.isEmpty() || access.users.contains(player.getUUID()) || com.mrgregles.bsp_core.admin.Admins.isAdmin(player);
    }

    /** A Decelerator Core at {@code core} wants plasma from here. True if it is listed (already, or now); false when every link is taken. */
    public boolean addLink(GlobalPos core) {
        if (links.contains(core)) {
            return true;
        }
        if (links.size() >= maxLinks()) {
            return false;
        }
        links.add(core);
        changed();
        return true;
    }

    public boolean hasLink(GlobalPos core) {
        return links.contains(core);
    }

    public void removeLink(GlobalPos core) {
        if (links.remove(core)) {
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Fitted expanders as items, for dropping when the intake is broken. */
    public List<net.minecraft.world.item.ItemStack> takeAllExpanders() {
        List<net.minecraft.world.item.ItemStack> out = new ArrayList<>();
        if (expanders > 0) {
            out.add(new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_EXPANDER.get(), expanders));
            expanders = 0;
        }
        return out;
    }

    /** The direction the intake faces (the ring stands across it). */
    public Direction facing() {
        return getBlockState().hasProperty(AcceleratorIntakeBlock.FACING) ? getBlockState().getValue(AcceleratorIntakeBlock.FACING) : Direction.NORTH;
    }

    /** The position {@code right} steps along the ring and {@code up} steps up from the intake. */
    public BlockPos cell(int right, int up) {
        return worldPosition.relative(facing().getClockWise(), right).above(up);
    }

    /** The seven frame positions of the ring, intake not included. */
    public List<BlockPos> framePositions() {
        List<BlockPos> out = new ArrayList<>(RING.length);
        for (int[] c : RING) {
            out.add(cell(c[0], c[1]));
        }
        return out;
    }

    /** The middle of the ring, where the black hole floats. */
    public BlockPos hole() {
        return cell(HOLE[0], HOLE[1]);
    }

    /** Frame positions that are not frames yet, for Jade and a ghost layout. */
    public List<BlockPos> missingFrames() {
        List<BlockPos> missing = new ArrayList<>();
        if (level != null) {
            for (BlockPos p : framePositions()) {
                if (!level.getBlockState(p).is(ModBlocks.ACCELERATOR_FRAME.get())) {
                    missing.add(p);
                }
            }
        }
        return missing;
    }

    private boolean ringComplete(ServerLevel sl) {
        for (BlockPos p : framePositions()) {
            if (!sl.isLoaded(p) || !sl.getBlockState(p).is(ModBlocks.ACCELERATOR_FRAME.get())) {
                return false;
            }
        }
        BlockPos hole = hole();
        return sl.isLoaded(hole) && sl.getBlockState(hole).isAir();
    }

    public void serverTick(ServerLevel sl) {
        if (checked && sl.getGameTime() % 20 != 0) {
            return;
        }
        checked = true;
        setFormed(sl, ringComplete(sl));
        hop(sl);
        show(sl);
    }

    private void setFormed(ServerLevel sl, boolean now) {
        if (now) {
            for (BlockPos p : framePositions()) {
                if (sl.getBlockEntity(p) instanceof LinkPartBlockEntity part) {
                    part.setController(worldPosition);
                }
            }
        }
        if (now == formed) {
            return;
        }
        formed = now;
        showParts(sl, now);
        setChanged();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Sets or clears {@code formed} on the intake and every frame of the ring, and the frames' controller. */
    private void showParts(ServerLevel sl, boolean now) {
        List<BlockPos> all = new ArrayList<>(framePositions());
        all.add(worldPosition);
        for (BlockPos p : all) {
            if (!sl.isLoaded(p)) {
                continue;
            }
            BlockState st = sl.getBlockState(p);
            if (st.hasProperty(StructurePartBlock.FORMED) && st.getValue(StructurePartBlock.FORMED) != now
                    && (st.is(ModBlocks.ACCELERATOR_FRAME.get()) || p.equals(worldPosition))) {
                sl.setBlock(p, st.setValue(StructurePartBlock.FORMED, now), 3);
            }
            if (!now && sl.getBlockEntity(p) instanceof LinkPartBlockEntity part) {
                part.setController(null);
            }
        }
    }

    /** A frame of the ring was broken: un-form at once rather than at the next check. */
    void partBroken() {
        if (level instanceof ServerLevel sl) {
            setFormed(sl, false);
        }
    }

    /** The intake itself is going: free the frames. */
    void unform() {
        if (level instanceof ServerLevel sl) {
            formed = false;
            showParts(sl, false);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2);
    }

    private void write(CompoundTag tag) {
        tag.putBoolean("Formed", formed);
        tag.putInt("Expanders", expanders);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("InRate", inRate);
        tag.putInt("OutRate", outRate);
        tag.putByte("Show", (byte) clientState);
        store.save(tag, "Plasma");
        Links.save(tag, "Anchor", anchorTotem);
        Links.saveAccess(tag, "Access", access);
        ListTag list = new ListTag();
        for (GlobalPos p : links) {
            CompoundTag t = new CompoundTag();
            Links.save(t, "", p);
            list.add(t);
        }
        tag.put("Links", list);
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
        expanders = tag.getInt("Expanders");
        energy.load(tag.getInt("Energy"));
        inRate = tag.getInt("InRate");
        outRate = tag.getInt("OutRate");
        clientState = tag.getByte("Show");
        store.load(tag, "Plasma");
        anchorTotem = Links.load(tag, "Anchor");
        access = Links.loadAccess(tag, "Access");
        links.clear();
        for (Tag t : tag.getList("Links", Tag.TAG_COMPOUND)) {
            GlobalPos p = Links.load((CompoundTag) t, "");
            if (p != null && !links.contains(p)) {
                links.add(p);
            }
        }
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
