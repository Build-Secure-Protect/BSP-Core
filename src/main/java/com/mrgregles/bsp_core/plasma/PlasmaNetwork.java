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
 *   <li>claim the extractor drums the group touches and add up their flow and powers,</li>
 *   <li>follow the cables from every member to the projectors on them, counting repeaters,</li>
 *   <li>move plasma from the drums to each projector: a repeater passes {@code plasma.repeaterPressure} of it.</li>
 * </ol>
 * Cables are followed through loaded chunks every second and all the way (loading chunks for a
 * moment) every {@code plasma.cableCheckSeconds}, as the generators used to.
 */
public final class PlasmaNetwork {
    private static final int SEARCH_LIMIT = 900;

    /** A receiver (projector or charger) reached by cable: how many repeaters are between it and the group, whether the run is within reach, and the last cable before it. */
    public record Found(BlockPos projector, int repeaters, boolean inReach, @javax.annotation.Nullable BlockPos via) {
    }

    private final PlasmaInterfaceBlockEntity home;
    private final Map<BlockPos, Found> routes = new LinkedHashMap<>();
    /** The cable or repeater each searched node was reached from, so a delivering run can be walked back and lit. */
    private final Map<BlockPos, BlockPos> parents = new HashMap<>();
    private final Set<BlockPos> cables = new HashSet<>();
    private boolean hitUnloaded, checked;
    // worked out each second by the master, read by screens
    private List<BlockPos> members = List.of();
    private int supply;
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

    private record Node(BlockPos pos, int run, int reach, int repeaters) {
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
                    queue.add(new Node(p.immutable(), 1, cable.kind.reach(), 0));
                    cables.add(p.immutable());
                } else if (repeaterEntered(st, d) && seen.add(p)) {
                    queue.add(new Node(p.immutable(), 0, Integer.MAX_VALUE, 1));
                    cables.add(p.immutable());
                } else if (sl.getBlockEntity(p) instanceof PlasmaReceiver) {
                    found.putIfAbsent(p.immutable(), new Found(p.immutable(), 0, true, null));
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
                if (sl.getBlockEntity(p) instanceof PlasmaReceiver) {
                    Found f = new Found(p.immutable(), n.repeaters, n.run <= n.reach, n.pos);
                    Found old = found.get(f.projector);
                    if (old == null || (f.inReach && !old.inReach) || (f.inReach == old.inReach && f.repeaters < old.repeaters)) {
                        found.put(f.projector, f);
                    }
                } else if (st.getBlock() instanceof TotemCableBlock cable && seen.add(p)) {
                    queue.add(new Node(p.immutable(), n.run + 1, Math.min(n.reach, cable.kind.reach()), n.repeaters));
                    parents.put(p.immutable(), n.pos);
                    cables.add(p.immutable());
                } else if (repeaterEntered(st, d) && seen.add(p)) {
                    queue.add(new Node(p.immutable(), 0, Integer.MAX_VALUE, n.repeaters + 1)); // a fresh run starts after the repeater
                    parents.put(p.immutable(), n.pos);
                    cables.add(p.immutable());
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
        Set<BlockPos> memberSet = new HashSet<>(members);
        for (BlockPos p : cluster) {
            mark(sl, p, !memberSet.contains(p));
        }
        // drums: claim every extractor a member touches; a drum another group holds makes that member show its seam
        List<PlasmaExtractorBlockEntity> drums = new ArrayList<>();
        Set<BlockPos> drumSet = new HashSet<>();
        supply = 0;
        java.util.Arrays.fill(offered, 0);
        access = new PlasmaAccess();
        anchorTotem = null;
        int bestAnchor = 0;
        for (BlockPos m : members) {
            boolean foreign = false;
            for (Direction d : Direction.values()) {
                BlockPos p = m.relative(d);
                if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof PlasmaExtractorBlockEntity drum && drumSet.add(p)) {
                    if (!drum.claim(master)) {
                        foreign = true;
                        continue;
                    }
                    drums.add(drum);
                    supply += drum.flow();
                    access.addAll(drum.access());
                    int[] o = drum.offered();
                    for (int i = 0; i < o.length; i++) {
                        offered[i] = Math.max(offered[i], o[i]);
                    }
                    if (o[Buff.ANCHOR.ordinal()] > bestAnchor && drum.totemPos() != null) {
                        bestAnchor = o[Buff.ANCHOR.ordinal()];
                        anchorTotem = drum.totemPos();
                    }
                }
            }
            if (foreign) {
                mark(sl, m, true);
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
        if (!drums.isEmpty()) {
            for (Found f : new ArrayList<>(routes.values())) {
                if (!f.inReach || !sl.isLoaded(f.projector) || !(sl.getBlockEntity(f.projector) instanceof PlasmaReceiver projector)) {
                    continue;
                }
                double k = pressure(f.repeaters);
                int want = (int) Math.ceil(projector.wanted() / k), took = 0;
                for (PlasmaExtractorBlockEntity drum : drums) {
                    took += drum.drain(want - took);
                    if (took >= want) {
                        break;
                    }
                }
                int delivered = (int) Math.floor(took * k);
                projector.feed(master, offered, f.repeaters, delivered / 20, access.copy(), anchorTotem);
                // the cables this delivery ran through, walked back to the interface, carry it
                BlockPos child = f.projector;
                for (BlockPos c = f.via; c != null && took > 0; child = c, c = parents.get(c)) {
                    flow.merge(c, took / 20, Integer::sum);
                    next.put(c, child);
                }
            }
        }
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
            }
        }
    }

    // ------------------------------------------------------------------ read by screens

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
