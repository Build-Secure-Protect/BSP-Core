package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The work an interface group does once a second, run by its master block:
 * <ol>
 *   <li>find the group (touching interfaces, lowest position first, up to the limit; the rest are refused),</li>
 *   <li>claim the extractor extractors the group touches and add up their flow and powers,</li>
 *   <li>follow the cables from every member to the projectors on them, counting repeaters,</li>
 *   <li>move plasma from the extractors to each projector: a repeater passes {@code plasma.repeaterPressure} of it.</li>
 * </ol>
 * Cables are followed every second. Where a chunk is loaded the blocks are read; where it is not, the walk goes on through what it
 * last saw there (its run memory, saved with the block that owns the network), so a run through chunks nobody holds keeps working and
 * no chunk is ever loaded by the walk. Nothing on a run can change while its chunk is unloaded, and a loaded block is read again
 * every second, so a cut is seen as soon as its chunk is loaded.
 */
public final class PlasmaNetwork {
    private static final int SEARCH_LIMIT = 900;

    /** A receiver (projector or charger) reached by cable: how many repeaters are between it and the group, whether the run is within reach, and the last cable before it. */
    public record Found(BlockPos projector, int repeaters, boolean inReach, @javax.annotation.Nullable BlockPos via, int cap) {
    }

    /** Something a group draws plasma from each second: an extractor under a totem, or a Tank Port giving out of its tank. */
    public interface Reservoir {
        int flow();

        int tank();

        int drain(int mB);

        boolean claim(BlockPos by);

        PlasmaAccess access();

        int[] offered();

        /** Whether everything this reservoir gives is a Mob Chamber power's plasma (a Tank Extractor set to a mob pool). */
        default boolean sendsMob() {
            return false;
        }

        /** Takes up to {@code mB} like {@link #drain(int)}; what of it was a Mob Chamber power's plasma is added to {@code mob} ({@link MobPlasma}). */
        default int drain(int mB, long[][] mob) {
            return drain(mB);
        }

        @javax.annotation.Nullable
        BlockPos totemPos();

        int cloak();

        /**
         * The totem whose Anchor travels with this reservoir's plasma, in its own world, with who owned and could edit it: an extractor
         * under a totem names its own; a source that got it through a link or a tank passes it on.
         */
        @javax.annotation.Nullable
        default Anchor anchor() {
            return null;
        }
    }

    private final net.minecraft.world.level.block.entity.BlockEntity home;
    @javax.annotation.Nullable
    private final PlasmaInterfaceBlockEntity iface;
    /** Set for a network that pushes from one reservoir out of one block (a Tank Port) instead of an interface group. */
    @javax.annotation.Nullable
    private final Reservoir source;
    private java.util.function.Predicate<BlockPos> receiverFilter = p -> true;
    private final Map<BlockPos, Found> routes = new LinkedHashMap<>();
    /** The cable or repeater each searched node was reached from, so a delivering run can be walked back and lit. */
    private final Map<BlockPos, BlockPos> parents = new HashMap<>();
    private final Set<BlockPos> cables = new HashSet<>();
    // what the last tick worked out, for the interface screen
    private List<BlockPos> clusterAll = List.of();
    private final Map<BlockPos, Integer> extractorFlow = new HashMap<>(), lastFlow = new HashMap<>(), lastDelivered = new HashMap<>();
    /** Set once this network has walked its run, so a group member that is no longer the master may forget its memory. */
    private boolean walked;
    /**
     * Set for a block saved before the run memory existed (no "Run" tag): its first walk reads unloaded chunks as the old full cable
     * check did, once, so a remote run it fed before the update is remembered. Every later walk, and every block saved since, loads nothing.
     */
    private boolean firstWalkLoads;
    // worked out each second by the master, read by screens
    private List<BlockPos> members = List.of();
    private int supply, cloak;
    private final int[] offered = new int[Buff.values().length];
    private PlasmaAccess access = new PlasmaAccess();
    private Anchor anchor;
    /** The Anchor the group passed on last second, kept on a tie. */
    private Anchor lastAnchor;

    PlasmaNetwork(PlasmaInterfaceBlockEntity home) {
        this.home = home;
        this.iface = home;
        this.source = null;
    }

    /** A network that pushes one reservoir's plasma out of {@code home} into the runs leaving it: a Tank Port. */
    public PlasmaNetwork(net.minecraft.world.level.block.entity.BlockEntity home, Reservoir source) {
        this.home = home;
        this.iface = null;
        this.source = source;
    }

    /** Receivers the search may feed; a tank uses it to keep from feeding its own ports. */
    public void receiverFilter(java.util.function.Predicate<BlockPos> filter) {
        receiverFilter = filter;
    }

    /** mB/t delivered to every receiver together, from the last tick. */
    public int lastDeliveredTotal() {
        int sum = 0;
        for (int v : lastDelivered.values()) {
            sum += v;
        }
        return sum;
    }

    public static int maxBlocks() {
        return BSPConfig.getOr(BSPConfig.INTERFACE_MAX, 12);
    }

    public static double pressurePerRepeater() {
        return BSPConfig.getOr(BSPConfig.REPEATER_PRESSURE, 0.9);
    }

    /** Fraction of the plasma that reaches a projector behind {@code repeaters} repeaters. */
    public static double pressure(int repeaters) {
        return Math.pow(pressurePerRepeater(), repeaters);
    }

    // ------------------------------------------------------------------ the group

    private static boolean isInterface(ServerLevel sl, BlockPos p) {
        return sl.isLoaded(p) && sl.getBlockState(p).getBlock() instanceof PlasmaInterfaceBlock;
    }

    /** Every interface touching {@code start}, directly or through others, lowest position first. */
    static List<BlockPos> cluster(ServerLevel sl, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && seen.size() < 4 * maxBlocks() + 16) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (isInterface(sl, n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        List<BlockPos> out = new ArrayList<>(seen);
        out.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        return out;
    }

    /** The members of the group that contains {@code master}: a search from the master that stops at the limit. */
    static List<BlockPos> membersFrom(ServerLevel sl, BlockPos master) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(master);
        seen.add(master);
        while (!queue.isEmpty() && out.size() < maxBlocks()) {
            BlockPos p = queue.poll();
            out.add(p);
            List<BlockPos> next = new ArrayList<>();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (isInterface(sl, n) && seen.add(n)) {
                    next.add(n);
                }
            }
            next.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
            queue.addAll(next);
        }
        return out;
    }

    private static void mark(ServerLevel sl, BlockPos p, boolean refused) {
        BlockState st = sl.getBlockState(p);
        if (st.getBlock() instanceof PlasmaInterfaceBlock && st.getValue(PlasmaInterfaceBlock.REFUSED) != refused) {
            sl.setBlock(p, st.setValue(PlasmaInterfaceBlock.REFUSED, refused), 3);
        }
    }

    // ------------------------------------------------------------------ the cables

    private record Node(BlockPos pos, int run, int reach, int repeaters, int cap) {
    }

    /** What each block on a run may carry in total, over every run through it: an interface face, a cable kind, a valve. */
    private final Map<BlockPos, Integer> nodeCap = new HashMap<>();

    /**
     * Max-min fair shares of {@code rate} between runs, where no block carries more than its cap over all the runs through it.
     * Every run grows at the same speed until a block it uses is full, and the runs through that block stop; the rest go on.
     */
    public static double[] allocate(int rate, List<List<BlockPos>> paths, Map<BlockPos, Integer> caps) {
        int n = paths.size();
        double[] share = new double[n];
        boolean[] frozen = new boolean[n];
        Map<BlockPos, Double> used = new HashMap<>();
        double left = rate;
        for (int round = 0; round < n + 2; round++) {
            int active = 0;
            for (boolean f : frozen) {
                if (!f) {
                    active++;
                }
            }
            if (active == 0 || left <= 1e-9) {
                break;
            }
            // how many active runs pass each block, and so the smallest step before some block fills
            Map<BlockPos, Integer> through = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (!frozen[i]) {
                    for (BlockPos q : paths.get(i)) {
                        through.merge(q, 1, Integer::sum);
                    }
                }
            }
            double step = left / active;
            for (Map.Entry<BlockPos, Integer> e : through.entrySet()) {
                int cap = caps.getOrDefault(e.getKey(), Integer.MAX_VALUE);
                if (cap != Integer.MAX_VALUE) {
                    step = Math.min(step, Math.max(0, cap - used.getOrDefault(e.getKey(), 0.0)) / e.getValue());
                }
            }
            for (int i = 0; i < n; i++) {
                if (!frozen[i]) {
                    share[i] += step;
                    left -= step;
                    for (BlockPos q : paths.get(i)) {
                        used.merge(q, step, Double::sum);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (!frozen[i]) {
                    for (BlockPos q : paths.get(i)) {
                        int cap = caps.getOrDefault(q, Integer.MAX_VALUE);
                        if (cap != Integer.MAX_VALUE && used.getOrDefault(q, 0.0) >= cap - 1e-6) {
                            frozen[i] = true;
                            break;
                        }
                    }
                }
            }
        }
        return share;
    }

    /** Whether moving in direction {@code d} enters a valve through one of its ends. A shut valve is still seen (so it shows in the view) but nothing passes it. */
    private static boolean valveEntered(BlockState st, Direction d) {
        return st.getBlock() instanceof PlasmaValveBlock && PlasmaValveBlock.joins(st, d);
    }

    private static int valveCap(ServerLevel sl, BlockPos p) {
        return sl.getBlockEntity(p) instanceof PlasmaValveBlockEntity v ? (v.open() ? v.limit() : 0) : Integer.MAX_VALUE;
    }

    /** Whether moving in direction {@code d} enters the repeater through its back (its input side). */
    private static boolean repeaterEntered(BlockState st, Direction d) {
        return st.getBlock() instanceof PlasmaRepeaterBlock && PlasmaRepeaterBlock.input(st) == d.getOpposite();
    }

    // ------------------------------------------------------------------ the run memory

    /** What a position on the run is: a face of the group (an interface, or the block a port pushes from), a cable, a repeater, a valve. */
    static final byte FACE = 0, CABLE = 1, REPEATER = 2, VALVE = 3;

    /**
     * What the walk last saw at a position it went through: the block, what it takes to walk it while its chunk is not loaded (a cable's
     * reach and throughput, a repeater's front, a valve's cap, 0 when shut), and which of its six sides lead on: {@code enter} into the
     * next cable, repeater or valve, {@code feeds} into a receiver that accepts from that side (bits by {@link Direction#ordinal()}).
     * {@code stamp} is the {@link RunChanges} count of its chunk when it was read: the memory is trusted only while that is unchanged.
     */
    record Seen(byte kind, int reach, int throughput, byte out, int cap, byte enter, byte feeds, int stamp) {
        Seen withSides(byte enter, byte feeds) {
            return new Seen(kind, reach, throughput, out, cap, enter, feeds, stamp);
        }
    }

    /** What one side of a position leads to: a receiver ({@code target} null) or the node it enters. */
    private record Step(@javax.annotation.Nullable Seen target) {
    }

    private static final Step FEEDS = new Step(null);

    /** The run as the walk last saw it, read only where a chunk is not loaded. Saved with the owning block entity. */
    private Map<BlockPos, Seen> memory = new HashMap<>();
    /** The kind of every node the last walk went through, for the tick (which must not read an unloaded chunk). */
    private final Map<BlockPos, Byte> kinds = new HashMap<>();
    /** The world's run change counts, for the walk under way. */
    private RunChanges changes;

    /** Whether what is remembered at {@code p} still holds: nothing on a run in its chunk has changed since it was read. */
    private boolean fresh(Seen s, BlockPos p) {
        return s.stamp == changes.count(p);
    }

    /** The node at {@code p}, read from the world, if entering it by moving in direction {@code d} is allowed. */
    @javax.annotation.Nullable
    private Seen live(ServerLevel sl, BlockState st, BlockPos p, Direction d) {
        int stamp = changes.count(p);
        if (st.getBlock() instanceof TotemCableBlock cable) {
            return new Seen(CABLE, cable.kind.reach(), cable.kind.throughput(), (byte) 0, 0, (byte) 0, (byte) 0, stamp);
        }
        if (repeaterEntered(st, d)) {
            return new Seen(REPEATER, 0, 0, (byte) PlasmaRepeaterBlock.output(st).ordinal(), 0, (byte) 0, (byte) 0, stamp);
        }
        if (valveEntered(st, d)) {
            return new Seen(VALVE, 0, 0, (byte) 0, valveCap(sl, p), (byte) 0, (byte) 0, stamp);
        }
        return null;
    }

    private static boolean receives(ServerLevel sl, BlockPos from, BlockState st, BlockPos p, Direction d) {
        return sl.getBlockEntity(p) instanceof PlasmaReceiver r && r.accepts(from, st, d);
    }

    /**
     * What lies on side {@code d} of {@code from}: null for nothing, {@link #FEEDS} for a receiver, else the node entered. Read from the
     * world when both blocks are loaded; else from what {@code fromOld} (the memory of {@code from}) says, checking the side that is loaded
     * still agrees. Never reads an unloaded position. A group face checks for a cable, repeater or valve before a receiver, a node the
     * other way round, as the search always has.
     */
    @javax.annotation.Nullable
    private Step step(ServerLevel sl, BlockPos from, boolean fromLoaded, @javax.annotation.Nullable Seen fromOld, BlockPos p, Direction d, boolean face) {
        boolean pLoaded = firstWalkLoads || sl.isLoaded(p);
        if (fromLoaded && pLoaded) {
            if (!TotemCableBlock.passes(sl, from, p, d)) {
                return null; // an end set to Input, Off, or the wrong colour
            }
            BlockState st = sl.getBlockState(p);
            if (!face && receives(sl, from, st, p, d)) {
                return FEEDS;
            }
            Seen t = live(sl, st, p, d);
            if (t != null) {
                return new Step(t);
            }
            return face && receives(sl, from, st, p, d) ? FEEDS : null;
        }
        int bit = 1 << d.ordinal();
        boolean entered = fromOld != null && (fromOld.enter & bit) != 0, fed = fromOld != null && (fromOld.feeds & bit) != 0;
        if (!entered && !fed) {
            return null; // nothing known past here: a run nobody has walked while it was loaded
        }
        if (fromLoaded && !TotemCableBlock.end(sl, from, d).out()) {
            return null; // the loaded side's end was shut since
        }
        if (pLoaded) { // the far block is loaded and this one is not: it must still be what it was
            if (!TotemCableBlock.end(sl, p, d.getOpposite()).in()) {
                return null;
            }
            BlockState st = sl.getBlockState(p);
            if (fed) {
                return receives(sl, from, st, p, d) ? FEEDS : null;
            }
            Seen t = live(sl, st, p, d);
            return t == null ? null : new Step(t);
        }
        if (fed) {
            return FEEDS;
        }
        Seen t = memory.get(p);
        return t == null || t.kind == FACE || !fresh(t, p) ? null : new Step(t); // a chunk changed since it was read is not walked blind
    }

    /** {@code mask} with side {@code d} set. */
    private static byte with(byte mask, Direction d) {
        return (byte) (mask | (1 << d.ordinal()));
    }

    /** Projectors on the cables leaving the group, walked through loaded chunks from the world and through unloaded ones from memory. */
    private Map<BlockPos, Found> findProjectors(ServerLevel sl, List<BlockPos> group) {
        Map<BlockPos, Found> found = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>(group);
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Map<BlockPos, Seen> saw = new HashMap<>(); // the memory this walk leaves
        parents.clear();
        cables.clear();
        nodeCap.clear();
        kinds.clear();
        changes = RunChanges.get(sl);
        int sideMax = BSPConfig.getOr(BSPConfig.INTERFACE_SIDE_MAX, 1000); // one face of the group sends at most this into its run
        for (BlockPos m : group) {
            Seen old = memory.get(m);
            if (old != null && old.kind != FACE) {
                old = null; // it was a cable or such when remembered: its sides say nothing about the face
            }
            byte enter = 0, feeds = 0;
            for (Direction d : Direction.values()) {
                BlockPos p = m.relative(d).immutable();
                Step s = step(sl, m, true, old, p, d, true);
                if (s == null) {
                    continue;
                }
                if (s.target == null) {
                    feeds = with(feeds, d);
                    if (receiverFilter.test(p)) {
                        found.putIfAbsent(p, new Found(p, 0, true, null, sideMax));
                        nodeCap.put(p, sideMax); // fed straight from a face: the face's worth
                    }
                    continue;
                }
                enter = with(enter, d);
                if (!seen.add(p)) {
                    continue;
                }
                Seen t = s.target;
                enqueue(saw, p, t);
                parents.put(p, m);
                if (t.kind == CABLE) {
                    int cap = Math.min(sideMax, t.throughput);
                    queue.add(new Node(p, 1, t.reach, 0, cap));
                    nodeCap.put(p, cap);
                } else if (t.kind == REPEATER) {
                    queue.add(new Node(p, 0, Integer.MAX_VALUE, 1, sideMax));
                    nodeCap.put(p, sideMax);
                } else {
                    nodeCap.put(p, Math.min(sideMax, t.cap));
                    if (t.cap > 0) {
                        queue.add(new Node(p, 1, Integer.MAX_VALUE, 0, Math.min(sideMax, t.cap)));
                    }
                }
            }
            if (enter != 0 || feeds != 0) {
                saw.put(m.immutable(), new Seen(FACE, 0, 0, (byte) 0, 0, enter, feeds, 0));
            }
        }
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Node n = queue.poll();
            Seen self = saw.get(n.pos);
            boolean loaded = firstWalkLoads || sl.isLoaded(n.pos);
            Seen old = memory.get(n.pos);
            if (old != null && (old.kind != self.kind || (!loaded && !fresh(old, n.pos)))) {
                old = null; // a different block was remembered here, or its chunk changed since: none of its sides are known
            }
            byte enter = 0, feeds = 0;
            for (Direction d : Direction.values()) {
                if (self.kind == REPEATER && d.ordinal() != self.out) {
                    continue; // a repeater only passes plasma out of its front
                }
                BlockPos p = n.pos.relative(d).immutable();
                Step s = step(sl, n.pos, loaded, old, p, d, false);
                if (s == null) {
                    continue;
                }
                if (s.target == null) {
                    feeds = with(feeds, d);
                    if (receiverFilter.test(p)) {
                        Found f = new Found(p, n.repeaters, n.run <= n.reach, n.pos, n.cap);
                        Found was = found.get(p);
                        if (was == null || (f.inReach && !was.inReach) || (f.inReach == was.inReach && f.repeaters < was.repeaters)) {
                            found.put(p, f);
                        }
                    }
                    continue;
                }
                enter = with(enter, d);
                if (!seen.add(p)) {
                    continue;
                }
                Seen t = s.target;
                enqueue(saw, p, t);
                parents.put(p, n.pos);
                if (t.kind == CABLE) {
                    queue.add(new Node(p, n.run + 1, Math.min(n.reach, t.reach), n.repeaters, Math.min(n.cap, t.throughput))); // held to its weakest cable
                    nodeCap.put(p, t.throughput);
                } else if (t.kind == REPEATER) {
                    queue.add(new Node(p, 0, Integer.MAX_VALUE, n.repeaters + 1, n.cap)); // a fresh run starts after the repeater
                } else {
                    nodeCap.put(p, t.cap); // a valve counts as one cable of the run and caps what passes; shut, the run ends here
                    if (t.cap > 0) {
                        queue.add(new Node(p, n.run + 1, n.reach, n.repeaters, Math.min(n.cap, t.cap)));
                    }
                }
            }
            saw.put(n.pos, self.withSides(enter, feeds));
        }
        walked = true;
        if (firstWalkLoads) {
            firstWalkLoads = false;
            home.setChanged(); // saved with its "Run" tag from now on, so it never loads again
        }
        if (!saw.equals(memory)) {
            memory = saw;
            home.setChanged();
        }
        return found;
    }

    /** Records a node the walk reached; until it is walked itself it keeps the sides it had before (a walk cut short by the limit). */
    private void enqueue(Map<BlockPos, Seen> saw, BlockPos p, Seen t) {
        Seen old = memory.get(p);
        saw.put(p, old != null && old.kind == t.kind && old.stamp == t.stamp ? t.withSides(old.enter, old.feeds) : t);
        cables.add(p);
        kinds.put(p, t.kind);
    }

    /** Saves the run memory into {@code tag} (under "Run"), with the owning block entity. */
    public void save(net.minecraft.nbt.CompoundTag tag) {
        int n = memory.size(), i = 0;
        long[] pos = new long[n];
        int[] flags = new int[n], a = new int[n], b = new int[n], v = new int[n];
        for (Map.Entry<BlockPos, Seen> e : memory.entrySet()) {
            Seen s = e.getValue();
            pos[i] = e.getKey().asLong();
            flags[i] = (s.kind & 3) | ((s.out & 7) << 2) | ((s.enter & 63) << 5) | ((s.feeds & 63) << 11);
            a[i] = s.reach;
            b[i] = s.kind == VALVE ? s.cap : s.throughput;
            v[i] = s.stamp;
            i++;
        }
        net.minecraft.nbt.CompoundTag run = new net.minecraft.nbt.CompoundTag();
        run.putLongArray("P", pos);
        run.putIntArray("F", flags);
        run.putIntArray("A", a);
        run.putIntArray("B", b);
        run.putIntArray("V", v);
        tag.put("Run", run); // written even when empty: a tag without it is a block saved before the run memory
    }

    /** Reads what {@link #save} wrote; a block saved before the run memory existed starts with none and walks once with loading. */
    public void load(net.minecraft.nbt.CompoundTag tag) {
        memory = new HashMap<>();
        firstWalkLoads = !tag.contains("Run");
        if (firstWalkLoads) {
            return;
        }
        net.minecraft.nbt.CompoundTag run = tag.getCompound("Run");
        long[] pos = run.getLongArray("P");
        int[] flags = run.getIntArray("F"), a = run.getIntArray("A"), b = run.getIntArray("B"), v = run.getIntArray("V");
        for (int i = 0; i < pos.length && i < flags.length && i < a.length && i < b.length && i < v.length && i < SEARCH_LIMIT; i++) {
            byte kind = (byte) (flags[i] & 3);
            memory.put(BlockPos.of(pos[i]), new Seen(kind, a[i], kind == VALVE ? 0 : b[i], (byte) ((flags[i] >> 2) & 7), kind == VALVE ? b[i] : 0,
                    (byte) ((flags[i] >> 5) & 63), (byte) ((flags[i] >> 11) & 63), v[i]));
        }
    }

    /** How many positions the run memory holds (for tests and the admin view). */
    public int remembered() {
        return memory.size();
    }

    // ------------------------------------------------------------------ the tick

    public void tick(ServerLevel sl) {
        BlockPos here = home.getBlockPos();
        List<Reservoir> extractors = new ArrayList<>();
        BlockPos master = here;
        supply = 0;
        cloak = 0;
        java.util.Arrays.fill(offered, 0);
        access = new PlasmaAccess();
        anchor = null;
        extractorFlow.clear();
        if (source != null) { // a port: one reservoir, one member, no group to work out
            members = List.of(here);
            clusterAll = members;
            extractors.add(source);
            supply = source.flow();
            anchor = source.anchor();
            access.addAll(source.access()); // who the plasma's powers know: a tank's feeders, a Tank Extractor's placer
            int[] o = source.offered(); // a tank passes on the powers its plasma still carries
            for (int i = 0; i < o.length && i < offered.length; i++) {
                offered[i] = Math.max(offered[i], o[i]);
            }
        } else if (iface != null) {
            List<BlockPos> cluster = cluster(sl, here);
            master = cluster.get(0);
            iface.setMaster(master);
            if (!master.equals(here)) {
                if (firstWalkLoads) { // the master walks for the group: a member saved before the run memory never needs the loading walk
                    firstWalkLoads = false;
                    home.setChanged();
                }
                // the master does the work for everyone; once it has walked, what this one remembered from its own time as master goes
                if (!memory.isEmpty() && sl.getBlockEntity(master) instanceof PlasmaInterfaceBlockEntity m && m.network().walked) {
                    memory = new HashMap<>();
                    home.setChanged();
                }
                return;
            }
            if (memory.isEmpty()) { // a new master (a lower interface was added) takes over the run memory of the one before
                for (BlockPos p : cluster) {
                    if (!p.equals(here) && sl.getBlockEntity(p) instanceof PlasmaInterfaceBlockEntity o && !o.network().memory.isEmpty()) {
                        memory = new HashMap<>(o.network().memory);
                        break;
                    }
                }
            }
            members = membersFrom(sl, master);
            clusterAll = cluster;
            Set<BlockPos> memberSet = new HashSet<>(members);
            for (BlockPos p : cluster) {
                mark(sl, p, !memberSet.contains(p));
            }
        }
        // extractors: claim every extractor a member touches; an extractor another group holds makes that member show its seam
        Set<BlockPos> extractorSet = new HashSet<>();
        List<Anchor> anchors = new ArrayList<>();
        List<Integer> anchorLevels = new ArrayList<>();
        for (BlockPos m : source != null ? List.<BlockPos>of() : members) {
            boolean foreign = false;
            for (Direction d : Direction.values()) {
                BlockPos p = m.relative(d);
                if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof PlasmaExtractorBlockEntity extractor && extractorSet.add(p)) {
                    if (!extractor.claim(master)) {
                        foreign = true;
                        continue;
                    }
                    extractors.add(extractor);
                    extractorFlow.put(p.immutable(), extractor.flow());
                    supply += extractor.flow();
                    cloak += extractor.cloak();
                    access.addAll(extractor.access());
                    int[] o = extractor.offered();
                    for (int i = 0; i < o.length; i++) {
                        offered[i] = Math.max(offered[i], o[i]);
                    }
                    Anchor a = extractor.anchor();
                    if (a != null) {
                        anchors.add(a);
                        anchorLevels.add(o[Buff.ANCHOR.ordinal()]);
                    }
                }
            }
            if (foreign) {
                mark(sl, m, true);
            }
        }
        if (source == null) {
            // several totems on one group: the Anchor whose editors are everyone the group's plasma knows, if there is one
            anchor = Anchor.pick(anchors, anchorLevels, access.editors, lastAnchor);
            lastAnchor = anchor;
        }
        for (BlockPos m : members) {
            if (sl.getBlockEntity(m) instanceof PlasmaInterfaceBlockEntity be) {
                be.setLit(supply > 0);
            }
        }
        // projectors
        Map<BlockPos, Found> now = findProjectors(sl, members);
        routes.clear();
        routes.putAll(now);
        Map<BlockPos, Integer> flow = new HashMap<>();
        Map<BlockPos, BlockPos> next = new HashMap<>(); // the node after each one on a delivering run, toward the receiver
        lastDelivered.clear();
        Map<BlockPos, Integer> valveIn = new HashMap<>(), valveOut = new HashMap<>();
        if (!extractors.isEmpty()) {
            // fair share: the extractors' flow is split equally between the runs in use, each run taking no more than its valves allow and
            // leaving the rest to the others. A run's pressure is that share, not what its far end happens to burn, so a full base
            // still reads the whole figure on its cables as long as the extractors could give it
            List<Found> live = new ArrayList<>();
            for (Found f : routes.values()) {
                if (f.inReach && sl.isLoaded(f.projector) && sl.getBlockEntity(f.projector) instanceof PlasmaReceiver) {
                    live.add(f);
                }
            }
            int stock = 0;
            boolean mobOnly = true;
            for (Reservoir extractor : extractors) {
                stock += extractor.tank();
                mobOnly &= extractor.sendsMob();
            }
            // what the extractors push each tick: their flow, or, with nothing feeding them, what is left in them for a while
            int rate = supply > 0 ? supply : Math.min(stock / 20, BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100));
            // every run's path back to the interface, so blocks shared by several runs (a trunk, a face) are held to their cap over all of them
            List<List<BlockPos>> paths = new ArrayList<>();
            for (Found f : live) {
                List<BlockPos> path = new ArrayList<>();
                path.add(f.projector);
                for (BlockPos c = f.via; c != null; c = parents.get(c)) {
                    path.add(c);
                }
                paths.add(path);
            }
            double[] shareD = allocate(rate, paths, nodeCap);
            int[] share = new int[live.size()];
            for (int i = 0; i < share.length; i++) {
                share[i] = (int) Math.floor(shareD[i]);
            }
            for (int i = 0; i < live.size(); i++) {
                Found f = live.get(i);
                PlasmaReceiver projector = (PlasmaReceiver) sl.getBlockEntity(f.projector);
                double k = pressure(f.repeaters);
                // the share this run would get with its own valves wide open: what shows at a valve's inlet
                int offer = share[i];
                boolean hasValve = false;
                for (BlockPos c : paths.get(i)) {
                    if (kinds.get(c) != null && kinds.get(c) == VALVE) {
                        hasValve = true;
                        break;
                    }
                }
                if (hasValve) {
                    Map<BlockPos, Integer> opened = new HashMap<>(nodeCap);
                    for (BlockPos c : paths.get(i)) {
                        if (kinds.get(c) != null && kinds.get(c) == VALVE) {
                            opened.put(c, Integer.MAX_VALUE);
                        }
                    }
                    offer = (int) Math.floor(allocate(rate, paths, opened)[i]);
                }
                int arriveMax = (int) Math.floor(share[i] * k) * 20; // mB that may arrive this second
                // a run that carries only mob powers' plasma asks for the room a receiver keeps for it
                int want = (int) Math.ceil(Math.min(mobOnly ? projector.wantedMob() : projector.wanted(), arriveMax) / k), took = 0;
                long[][] mob = MobPlasma.empty(); // the Mob Chamber powers' plasma among what was taken, by amount
                for (Reservoir extractor : extractors) {
                    took += extractor.drain(want - took, mob);
                    if (took >= want) {
                        break;
                    }
                }
                int moved = (int) Math.floor(took * k); // mB that actually went into the receiver this second
                long[][] mobMoved = MobPlasma.scaled(mob, k);
                // extractors met the demand: the run is at its share; extractors ran short: only what got through
                int pressure = took >= want ? (int) Math.floor(share[i] * k) : moved / 20;
                // mob powers go by amount beside the list, never in it: a little of them must not label the rest
                projector.feed(master, MobPlasma.withoutMob(this.offered), f.repeaters, pressure, moved, access.copy(), anchor, mobMoved);
                lastDelivered.put(f.projector, pressure);
                // walked back from the receiver: every cable carries the plasma really passing it. The figure rises by the repeater's
                // loss each time a repeater is passed (toward the interface), so the cables leaving the interface add up to the extractors'
                // supply. A valve shows what it was offered in its inlet window, but the cables before it carry only what it passes.
                double base = took >= want ? share[i] : moved / 20.0 / k, offerBase = took >= want ? offer : base;
                int passed = 0;
                BlockPos child = f.projector;
                for (BlockPos c = f.via; c != null; child = c, c = parents.get(c)) {
                    int carry = (int) Math.floor(base * pressure(f.repeaters - passed));
                    Byte kind = kinds.get(c); // never read from the world: a run may pass through chunks that are not loaded
                    if (kind != null && kind == VALVE) {
                        valveOut.merge(c, carry, Integer::sum);
                        valveIn.merge(c, (int) Math.floor(offerBase * pressure(f.repeaters - passed)), Integer::sum);
                    }
                    if (carry > 0) {
                        flow.merge(c, carry, Integer::sum);
                        next.put(c, child);
                    }
                    if (kind != null && kind == REPEATER) {
                        passed++; // before this repeater the run carried a tenth more
                    }
                }
            }
        }
        lastFlow.clear();
        lastFlow.putAll(flow);
        // tell every cable and repeater on the search what it carries and which way, so they can show it
        for (BlockPos c : cables) {
            int f = flow.getOrDefault(c, 0);
            if (f > 0) {
                PlasmaFlow.put(sl, c, f);
            }
            if (!sl.isLoaded(c)) {
                continue;
            }
            if (sl.getBlockEntity(c) instanceof com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity cable) {
                BlockPos from = parents.get(c), to = next.get(c);
                cable.report(f, from == null ? null : Direction.fromDelta(from.getX() - c.getX(), from.getY() - c.getY(), from.getZ() - c.getZ()),
                        to == null ? null : Direction.fromDelta(to.getX() - c.getX(), to.getY() - c.getY(), to.getZ() - c.getZ()));
            } else if (sl.getBlockEntity(c) instanceof PlasmaRepeaterBlockEntity repeater) {
                repeater.report(f);
            } else if (sl.getBlockEntity(c) instanceof PlasmaValveBlockEntity valve) {
                BlockPos from = parents.get(c);
                valve.report(valveIn.getOrDefault(c, 0), valveOut.getOrDefault(c, 0),
                        from == null ? null : Direction.fromDelta(from.getX() - c.getX(), from.getY() - c.getY(), from.getZ() - c.getZ()), access.copy());
            }
        }
    }

    // ------------------------------------------------------------------ read by screens

    /** Everything the interface screen shows, from the master's last tick. */
    public com.mrgregles.bsp_core.network.InterfaceViewPacket view(ServerLevel sl) {
        List<com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry> out = new ArrayList<>();
        Set<BlockPos> memberSet = new HashSet<>(members);
        for (BlockPos p : clusterAll) {
            out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(p, memberSet.contains(p) ? com.mrgregles.bsp_core.network.InterfaceViewPacket.INTERFACE : com.mrgregles.bsp_core.network.InterfaceViewPacket.REFUSED, 0, 0));
        }
        extractorFlow.forEach((p, f) -> out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(p, com.mrgregles.bsp_core.network.InterfaceViewPacket.EXTRACTOR, f, 0)));
        for (BlockPos c : cables) {
            boolean repeater = kinds.get(c) != null && kinds.get(c) == REPEATER;
            if (sl.isLoaded(c) && sl.getBlockEntity(c) instanceof PlasmaValveBlockEntity valve) {
                out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(c, com.mrgregles.bsp_core.network.InterfaceViewPacket.VALVE, valve.out(), valve.open() ? 0 : 1));
                continue;
            }
            if (kinds.get(c) != null && kinds.get(c) == VALVE) { // in a chunk that is not loaded: as the walk remembers it
                out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(c, com.mrgregles.bsp_core.network.InterfaceViewPacket.VALVE, lastFlow.getOrDefault(c, 0),
                        nodeCap.getOrDefault(c, 0) > 0 ? 0 : 1));
                continue;
            }
            out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(c, repeater ? com.mrgregles.bsp_core.network.InterfaceViewPacket.REPEATER : com.mrgregles.bsp_core.network.InterfaceViewPacket.CABLE, lastFlow.getOrDefault(c, 0), 0));
        }
        for (Found f : routes.values()) {
            out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(f.projector, com.mrgregles.bsp_core.network.InterfaceViewPacket.RECEIVER, lastDelivered.getOrDefault(f.projector, 0), f.repeaters));
        }
        return new com.mrgregles.bsp_core.network.InterfaceViewPacket(home.getBlockPos(), supply, cloak, members.size(), maxBlocks(), out);
    }

    public List<BlockPos> members() {
        return members;
    }

    public int supply() {
        return supply;
    }

    public int[] offered() {
        return offered;
    }

    public PlasmaAccess access() {
        return access;
    }
}
