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
    /** The totem whose Anchor the plasma carries, in its own world, with who owned and could edit it; dropped once no stored plasma carries Anchor. */
    @Nullable
    private com.mrgregles.bsp_core.plasma.Anchor anchorTotem;
    private long fedAt = -1000;
    /** Who fed it since the last hop, and the Anchors that came with them (not saved): at each hop they become {@code access} and the anchor. */
    private com.mrgregles.bsp_core.plasma.PlasmaAccess window = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
    private final List<com.mrgregles.bsp_core.plasma.Anchor> windowAnchors = new ArrayList<>();
    private final List<Integer> windowLevels = new ArrayList<>();
    private int windowFeeds;
    /** This second's intake: pressure offered by the runs feeding it, and mB moved in, both summed since the last tick. */
    private int inAcc, receivedThisSecond;
    private int inRate, outRate;
    /** What each link did this second, for the screen and Jade. */
    private final java.util.Map<GlobalPos, Links.Report> reports = new java.util.LinkedHashMap<>();
    /** When the WAKE key last asked the far sides to load (not saved). */
    private long lastWake = Long.MIN_VALUE;
    /** Far sides the WAKE key woke, with the game time (of their own world) their wake ticket runs out: for the admin listing. */
    private final java.util.Map<GlobalPos, Long> wokenUntil = new java.util.HashMap<>();
    /** 0 formed and dark, 1 RF connected, 2 a link delivering: synced for the renderer and the block's light. */
    private int clientState;
    /** Ticks a WAKE ticket lasts: five seconds. */
    public static final int WAKE_TICKS = 100;
    /** Ticks between two presses of the WAKE key that wake anything: a minute. */
    public static final int WAKE_COOLDOWN = 1200;
    /**
     * Put on a sleeping far chunk only by the WAKE key (and only with {@code plasma.linkWake} on): the chunk loads in the background
     * and ticks for five seconds. Nothing ever loads it on the spot, and no tick of the accelerator adds it.
     */
    public static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> WAKE =
            net.minecraft.server.level.TicketType.create("bsp_core_link_wake", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), WAKE_TICKS);
    /** The accelerators loaded on this server right now, for {@code /bsp links}. Server thread only. */
    private static final java.util.Set<AcceleratorBlockEntity> LOADED = new java.util.LinkedHashSet<>();

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

    /** Whether the WAKE key may wake far sides ({@code plasma.linkWake}, off by default). */
    public static boolean wakeAllowed() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_WAKE, false);
    }

    /**
     * Whether {@code level} runs its block entities. A world with no players and no force-loaded chunks stops ticking block
     * entities 300 ticks after it empties ({@code ServerLevel.tick}); chunks loaded into it then build their machines but never run
     * them, and their tickers are never cleared, so nothing may wake a far side there.
     */
    public static boolean runsMachines(ServerLevel level) {
        return !level.players().isEmpty() || net.minecraftforge.common.world.ForgeChunkManager.hasForcedChunks(level);
    }

    /** Whether the far side at {@code at} is running now: its chunk ticks (checked without loading anything) and its world runs machines. */
    public static boolean awake(ServerLevel far, BlockPos at) {
        return far.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(at)) && runsMachines(far);
    }

    /** The accelerators loaded right now, in the order they loaded. */
    public static List<AcceleratorBlockEntity> loaded() {
        LOADED.removeIf(BlockEntity::isRemoved);
        return new ArrayList<>(LOADED);
    }

    static void forgetLoaded() {
        LOADED.clear();
    }

    /** Whether the WAKE key's ticket is on the far side at {@code at} now. */
    public boolean woken(GlobalPos at, ServerLevel far) {
        Long until = wokenUntil.get(at);
        return until != null && far.getGameTime() < until;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && !isRemoved()) { // onLoad can come late, after the chunk already went
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel) {
            LOADED.remove(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel) {
            LOADED.remove(this);
        }
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
    public com.mrgregles.bsp_core.plasma.Anchor anchorTotem() {
        return anchorTotem;
    }

    /** Once no stored plasma carries Anchor, no totem's Anchor travels on from here. */
    private void forgetAnchor() {
        if (anchorTotem != null && store.offered()[com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.ANCHOR.ordinal()] <= 0) {
            anchorTotem = null;
            setChanged();
        }
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
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, com.mrgregles.bsp_core.plasma.PlasmaAccess access, @Nullable com.mrgregles.bsp_core.plasma.Anchor anchorTotem) {
        feed(master, offeredByOrdinal, repeaters, pressurePerTick, movedMB, access, anchorTotem, null);
    }

    /** The feed with the Mob Chamber powers' plasma by amount: the store keeps it apart and the links carry it across apart. */
    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, com.mrgregles.bsp_core.plasma.PlasmaAccess access,
                     @Nullable com.mrgregles.bsp_core.plasma.Anchor anchorTotem, @Nullable long[][] mob) {
        store.add(movedMB, offeredByOrdinal, mob);
        receivedThisSecond += movedMB;
        inAcc += pressurePerTick;
        if (fedLately()) {
            this.access.addAll(access);
        } else {
            this.access = access.copy();
        }
        if (anchorTotem != null && this.anchorTotem == null) { // the first Anchor goes on at once; after that the hop picks among the second's feeds
            this.anchorTotem = anchorTotem;
        }
        // and at the next hop only the runs that fed it this second count, so a run that stopped (or a friend since removed) drops off
        window.addAll(access);
        windowFeeds++;
        if (anchorTotem != null) {
            windowAnchors.add(anchorTotem);
            int a = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.ANCHOR.ordinal();
            windowLevels.add(offeredByOrdinal != null && a < offeredByOrdinal.length ? offeredByOrdinal[a] : 0);
        }
        forgetAnchor();
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

    /** What a press of the WAKE key did. */
    public record Wake(Result result, int woken, int idleWorlds) {
        public enum Result { OFF, TOO_SOON, ASKED }
    }

    /**
     * The WAKE key. With {@code plasma.linkWake} on and a minute since the last press, puts the WAKE ticket on every sleeping far
     * chunk whose world runs machines; the chunk loads in the background and the next hops deliver once it ticks. Nothing is loaded
     * here, and a far side in a world that runs no machines is left asleep.
     */
    public Wake wakeNow() {
        if (!(level instanceof ServerLevel sl)) {
            return new Wake(Wake.Result.OFF, 0, 0);
        }
        if (!wakeAllowed()) {
            return new Wake(Wake.Result.OFF, 0, 0);
        }
        long now = sl.getGameTime();
        if (lastWake != Long.MIN_VALUE && now - lastWake < WAKE_COOLDOWN) {
            return new Wake(Wake.Result.TOO_SOON, 0, 0);
        }
        int woken = 0, idle = 0;
        for (GlobalPos at : links) {
            ServerLevel far = sl.getServer().getLevel(at.dimension());
            if (far == null || awake(far, at.pos())) {
                continue;
            }
            if (!runsMachines(far)) {
                idle++;
                continue;
            }
            net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(at.pos());
            far.getChunkSource().addRegionTicket(WAKE, chunk, 2, chunk);
            wokenUntil.put(at, far.getGameTime() + WAKE_TICKS);
            woken++;
        }
        if (woken > 0) {
            lastWake = now; // a press that woke nothing leaves the next press free
        }
        return new Wake(Wake.Result.ASKED, woken, idle);
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
        wokenUntil.keySet().retainAll(links);
        if (windowFeeds > 0) {
            access = window;
            com.mrgregles.bsp_core.plasma.Anchor picked = com.mrgregles.bsp_core.plasma.Anchor.pick(windowAnchors, windowLevels, window.editors, anchorTotem);
            if (picked != null) {
                anchorTotem = picked;
            }
            window = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            windowAnchors.clear();
            windowLevels.clear();
            windowFeeds = 0;
            setChanged();
        }
        inRate = inAcc;
        inAcc = 0;
        receivedThisSecond = 0;
        outRate = 0;
        reports.clear();
        List<DeceleratorBlockEntity> live = new ArrayList<>();
        List<GlobalPos> liveAt = new ArrayList<>();
        for (GlobalPos at : new ArrayList<>(links)) {
            ServerLevel far = sl.getServer().getLevel(at.dimension());
            if (far == null) {
                reports.put(at, new Links.Report(Links.State.NO_WORLD, 0));
                continue;
            }
            // a far side is awake only while its chunk ticks in a world that runs machines. This never loads it: the check reads
            // only chunks already in memory, and the block entity is read only once the chunk ticks
            if (!awake(far, at.pos())) {
                reports.put(at, new Links.Report(Links.State.ASLEEP, 0));
                continue;
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
            long[][] mob = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
            long took = store.drain(share[i], mob);
            long arrived = Math.round(took * (1.0 - loss()));
            live.get(i).receive(arrived, offered, com.mrgregles.bsp_core.plasma.MobPlasma.scaled(mob, 1.0 - loss()), access.copy(), anchorTotem, here());
            total += took;
            reports.put(liveAt.get(i), new Links.Report(Links.State.DELIVERING, (int) (arrived / 20)));
        }
        forgetAnchor();
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
        com.mrgregles.bsp_core.plasma.Anchor.save(tag, "Anchor", anchorTotem); // server only: the client never needs who may edit the totem
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
        anchorTotem = com.mrgregles.bsp_core.plasma.Anchor.load(tag, "Anchor");
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
