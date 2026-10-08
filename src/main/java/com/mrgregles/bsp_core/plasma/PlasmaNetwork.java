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
 * Cables are followed through loaded chunks every second and all the way (loading chunks for a
 * moment) every {@code plasma.cableCheckSeconds}, as the generators used to.
 */
public final class PlasmaNetwork {
    private static final int SEARCH_LIMIT = 900;

    /** A receiver (projector or charger) reached by cable: how many repeaters are between it and the group, whether the run is within reach, and the last cable before it. */
    public record Found(BlockPos projector, int repeaters, boolean inReach, @javax.annotation.Nullable BlockPos via, int cap) {
    }

    private final PlasmaInterfaceBlockEntity home;
    private final Map<BlockPos, Found> routes = new LinkedHashMap<>();
    /** The cable or repeater each searched node was reached from, so a delivering run can be walked back and lit. */
    private final Map<BlockPos, BlockPos> parents = new HashMap<>();
    private final Set<BlockPos> cables = new HashSet<>();
    // what the last tick worked out, for the interface screen
    private List<BlockPos> clusterAll = List.of();
    private final Map<BlockPos, Integer> extractorFlow = new HashMap<>(), lastFlow = new HashMap<>(), lastDelivered = new HashMap<>();
    private boolean hitUnloaded, checked;
    // worked out each second by the master, read by screens
    private List<BlockPos> members = List.of();
    private int supply, cloak;
    private final int[] offered = new int[Buff.values().length];
    private PlasmaAccess access = new PlasmaAccess();
    private BlockPos anchorTotem;

    PlasmaNetwork(PlasmaInterfaceBlockEntity home) {
        this.home = home;
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

    /** Whether moving in direction {@code d} enters a valve through one of its ends. A shut valve is still seen (so it shows in the view) but nothing passes it. */
    private static boolean valveEntered(BlockState st, Direction d) {
        return st.getBlock() instanceof PlasmaValveBlock && PlasmaValveBlock.joins(st, d);
    }

    private static int valveCap(ServerLevel sl, BlockPos p) {
        return sl.getBlockEntity(p) instanceof PlasmaValveBlockEntity v ? (v.open() ? v.limit() : 0) : Integer.MAX_VALUE;
    }

    /** Shares {@code total} between runs, each taking no more than its cap, the rest going to the others (water filling). */
    static int[] fill(int total, int[] caps) {
        int n = caps.length, left = total, remaining = n;
        int[] out = new int[n];
        boolean[] done = new boolean[n];
        while (remaining > 0) {
            int each = left / remaining;
            boolean any = false;
            for (int i = 0; i < n; i++) {
                if (!done[i] && caps[i] <= each) {
                    out[i] = caps[i];
                    left -= caps[i];
                    done[i] = true;
                    remaining--;
                    any = true;
                }
            }
            if (!any) {
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        out[i] = each;
                    }
                }
                break;
            }
        }
        return out;
    }

    /** Whether moving in direction {@code d} enters the repeater through its back (its input side). */
    private static boolean repeaterEntered(BlockState st, Direction d) {
        return st.getBlock() instanceof PlasmaRepeaterBlock && PlasmaRepeaterBlock.input(st) == d.getOpposite();
    }

    /** Projectors on the cables leaving the group. Unloaded chunks are entered only when {@code load} is set. */
    private Map<BlockPos, Found> findProjectors(ServerLevel sl, List<BlockPos> group, boolean load) {
        hitUnloaded = false;
        Map<BlockPos, Found> found = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>(group);
        ArrayDeque<Node> queue = new ArrayDeque<>();
        parents.clear();
        cables.clear();
        for (BlockPos m : group) {
            for (Direction d : Direction.values()) {
                BlockPos p = m.relative(d);
                if (!load && !sl.isLoaded(p)) {
                    hitUnloaded = true;
                    continue;
                }
                BlockState st = sl.getBlockState(p);
                if (st.getBlock() instanceof TotemCableBlock cable && seen.add(p)) {
                    queue.add(new Node(p.immutable(), 1, cable.kind.reach(), 0, Integer.MAX_VALUE));
                    parents.put(p.immutable(), m);
                    cables.add(p.immutable());
                } else if (repeaterEntered(st, d) && seen.add(p)) {
                    queue.add(new Node(p.immutable(), 0, Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
                    parents.put(p.immutable(), m);
                    cables.add(p.immutable());
                } else if (valveEntered(st, d) && seen.add(p)) {
                    int cap = valveCap(sl, p);
                    parents.put(p.immutable(), m);
                    cables.add(p.immutable());
                    if (cap > 0) {
                        queue.add(new Node(p.immutable(), 1, Integer.MAX_VALUE, 0, cap));
                    }
                } else if (sl.getBlockEntity(p) instanceof PlasmaReceiver r && r.accepts(m, st, d)) {
                    found.putIfAbsent(p.immutable(), new Found(p.immutable(), 0, true, null, Integer.MAX_VALUE));
                }
            }
        }
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Node n = queue.poll();
            BlockState here = sl.getBlockState(n.pos);
            boolean repeater = here.getBlock() instanceof PlasmaRepeaterBlock;
            for (Direction d : Direction.values()) {
                if (repeater && d != PlasmaRepeaterBlock.output(here)) {
                    continue; // a repeater only passes plasma out of its front
                }
                BlockPos p = n.pos.relative(d);
                if (!load && !sl.isLoaded(p)) {
                    hitUnloaded = true;
                    continue;
                }
                BlockState st = sl.getBlockState(p);
                if (sl.getBlockEntity(p) instanceof PlasmaReceiver r && r.accepts(n.pos, st, d)) {
                    Found f = new Found(p.immutable(), n.repeaters, n.run <= n.reach, n.pos, n.cap);
                    Found old = found.get(f.projector);
                    if (old == null || (f.inReach && !old.inReach) || (f.inReach == old.inReach && f.repeaters < old.repeaters)) {
                        found.put(f.projector, f);
                    }
                } else if (st.getBlock() instanceof TotemCableBlock cable && seen.add(p)) {
                    queue.add(new Node(p.immutable(), n.run + 1, Math.min(n.reach, cable.kind.reach()), n.repeaters, n.cap));
                    parents.put(p.immutable(), n.pos);
                    cables.add(p.immutable());
                } else if (repeaterEntered(st, d) && seen.add(p)) {
                    queue.add(new Node(p.immutable(), 0, Integer.MAX_VALUE, n.repeaters + 1, n.cap)); // a fresh run starts after the repeater
                    parents.put(p.immutable(), n.pos);
                    cables.add(p.immutable());
                } else if (valveEntered(st, d) && seen.add(p)) {
                    int cap = valveCap(sl, p); // a valve counts as one cable of the run and caps what passes; shut, the run ends here
                    parents.put(p.immutable(), n.pos);
                    cables.add(p.immutable());
                    if (cap > 0) {
                        queue.add(new Node(p.immutable(), n.run + 1, n.reach, n.repeaters, Math.min(n.cap, cap)));
                    }
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ the tick

    void tick(ServerLevel sl) {
        BlockPos here = home.getBlockPos();
        List<BlockPos> cluster = cluster(sl, here);
        BlockPos master = cluster.get(0);
        home.setMaster(master);
        if (!master.equals(here)) {
            return; // the master does the work for everyone
        }
        members = membersFrom(sl, master);
        clusterAll = cluster;
        extractorFlow.clear();
        Set<BlockPos> memberSet = new HashSet<>(members);
        for (BlockPos p : cluster) {
            mark(sl, p, !memberSet.contains(p));
        }
        // extractors: claim every extractor a member touches; an extractor another group holds makes that member show its seam
        List<PlasmaExtractorBlockEntity> extractors = new ArrayList<>();
        Set<BlockPos> extractorSet = new HashSet<>();
        supply = 0;
        cloak = 0;
        java.util.Arrays.fill(offered, 0);
        access = new PlasmaAccess();
        anchorTotem = null;
        int bestAnchor = 0;
        for (BlockPos m : members) {
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
                    if (o[Buff.ANCHOR.ordinal()] > bestAnchor && extractor.totemPos() != null) {
                        bestAnchor = o[Buff.ANCHOR.ordinal()];
                        anchorTotem = extractor.totemPos();
                    }
                }
            }
            if (foreign) {
                mark(sl, m, true);
            }
        }
        for (BlockPos m : members) {
            if (sl.getBlockEntity(m) instanceof PlasmaInterfaceBlockEntity be) {
                be.setLit(supply > 0);
            }
        }
        // projectors
        int every = Math.max(1, BSPConfig.getOr(BSPConfig.CABLE_CHECK_SECONDS, 30));
        boolean full = !checked || (sl.getGameTime() / 20 + Math.floorMod(master.hashCode(), every)) % every == 0;
        checked = true;
        Map<BlockPos, Found> now = findProjectors(sl, members, full);
        if (full || !hitUnloaded) {
            routes.clear();
        }
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
            for (PlasmaExtractorBlockEntity extractor : extractors) {
                stock += extractor.tank();
            }
            // what the extractors push each tick: their flow, or, with nothing feeding them, what is left in them for a while
            int rate = supply > 0 ? supply : Math.min(stock / 20, BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100));
            int[] caps = new int[live.size()];
            for (int i = 0; i < caps.length; i++) {
                caps[i] = live.get(i).cap;
            }
            int[] share = fill(rate, caps);
            for (int i = 0; i < live.size(); i++) {
                Found f = live.get(i);
                PlasmaReceiver projector = (PlasmaReceiver) sl.getBlockEntity(f.projector);
                double k = pressure(f.repeaters);
                // the share this run would get with its own valves wide open: what shows at a valve's inlet
                int offer = share[i];
                if (f.cap != Integer.MAX_VALUE) {
                    int[] opened = caps.clone();
                    opened[i] = Integer.MAX_VALUE;
                    offer = fill(rate, opened)[i];
                }
                int arriveMax = (int) Math.floor(share[i] * k) * 20; // mB that may arrive this second
                int want = (int) Math.ceil(Math.min(projector.wanted(), arriveMax) / k), took = 0;
                for (PlasmaExtractorBlockEntity extractor : extractors) {
                    took += extractor.drain(want - took);
                    if (took >= want) {
                        break;
                    }
                }
                int moved = (int) Math.floor(took * k); // mB that actually went into the receiver this second
                // extractors met the demand: the run is at its share; extractors ran short: only what got through
                int pressure = took >= want ? (int) Math.floor(share[i] * k) : moved / 20;
                projector.feed(master, this.offered, f.repeaters, pressure, moved, access.copy(), anchorTotem);
                lastDelivered.put(f.projector, pressure);
                // walked back from the receiver: every cable carries the plasma really passing it. The figure rises by the repeater's
                // loss each time a repeater is passed (toward the interface), so the cables leaving the interface add up to the extractors'
                // supply. A valve shows what it was offered in its inlet window, but the cables before it carry only what it passes.
                double base = took >= want ? share[i] : moved / 20.0 / k, offerBase = took >= want ? offer : base;
                int passed = 0;
                BlockPos child = f.projector;
                for (BlockPos c = f.via; c != null; child = c, c = parents.get(c)) {
                    int carry = (int) Math.floor(base * pressure(f.repeaters - passed));
                    if (sl.getBlockEntity(c) instanceof PlasmaValveBlockEntity) {
                        valveOut.merge(c, carry, Integer::sum);
                        valveIn.merge(c, (int) Math.floor(offerBase * pressure(f.repeaters - passed)), Integer::sum);
                    }
                    if (carry > 0) {
                        flow.merge(c, carry, Integer::sum);
                        next.put(c, child);
                    }
                    if (sl.getBlockState(c).getBlock() instanceof PlasmaRepeaterBlock) {
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
            boolean repeater = sl.isLoaded(c) && sl.getBlockState(c).getBlock() instanceof PlasmaRepeaterBlock;
            if (sl.isLoaded(c) && sl.getBlockEntity(c) instanceof PlasmaValveBlockEntity valve) {
                out.add(new com.mrgregles.bsp_core.network.InterfaceViewPacket.Entry(c, com.mrgregles.bsp_core.network.InterfaceViewPacket.VALVE, valve.out(), valve.open() ? 0 : 1));
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
