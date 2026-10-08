package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TankViewPacket;
import com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finding, forming and unforming Plasma Tanks. A tank is a hollow box: every edge Tank Casing, every face block Tank Casing, Tank
 * Glass or a Tank Port, nothing inside, 3 to {@code plasma.tankMax} blocks a side. The block that completes the shell finds the box
 * it belongs to by walking along the shell; a face block's own axis is tried both ways at every size.
 */
public final class TankStructure {
    private TankStructure() {}

    public static int maxSide() {
        return BSPConfig.getOr(BSPConfig.TANK_MAX, 12);
    }

    private static boolean isTank(BlockState st) {
        return st.getBlock() instanceof TankBlock;
    }

    private static boolean formed(ServerLevel sl, BlockPos p) {
        BlockState st = sl.getBlockState(p);
        return isTank(st) && st.getValue(TankBlock.FORMED);
    }

    /** How many tank blocks lie in a row from {@code p} in direction {@code d}, not counting {@code p}. */
    private static int reach(ServerLevel sl, BlockPos p, Direction d) {
        int n = 0;
        BlockPos q = p.relative(d);
        while (n < maxSide() && isTank(sl.getBlockState(q))) {
            n++;
            q = q.relative(d);
        }
        return n;
    }

    /** Called when a tank block is placed: forms the tank if this block completes one. */
    public static void tryForm(ServerLevel sl, BlockPos p) {
        if (formed(sl, p)) {
            return;
        }
        int[] lo = new int[3], hi = new int[3];
        int[] here = {p.getX(), p.getY(), p.getZ()};
        List<int[]>[] options = new List[3];
        for (Direction.Axis a : Direction.Axis.values()) {
            int i = a.ordinal();
            int neg = reach(sl, p, Direction.fromAxisAndDirection(a, Direction.AxisDirection.NEGATIVE));
            int pos = reach(sl, p, Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE));
            options[i] = new ArrayList<>();
            if (neg == 0 && pos == 0) { // a face block looking across the tank: the far side may be anywhere within reach
                for (int s = 3; s <= maxSide(); s++) {
                    options[i].add(new int[]{here[i] - s + 1, here[i]});
                    options[i].add(new int[]{here[i], here[i] + s - 1});
                }
            } else {
                options[i].add(new int[]{here[i] - neg, here[i] + pos});
            }
        }
        for (int[] x : options[0]) {
            for (int[] y : options[1]) {
                for (int[] z : options[2]) {
                    BlockPos min = new BlockPos(x[0], y[0], z[0]);
                    int w = x[1] - x[0] + 1, h = y[1] - y[0] + 1, d = z[1] - z[0] + 1;
                    if (valid(sl, min, w, h, d)) {
                        form(sl, min, w, h, d, p);
                        return;
                    }
                }
            }
        }
    }

    /** Whether {@code min..dims} is a complete, empty tank made of unformed tank blocks with casing on every edge. */
    static boolean valid(ServerLevel sl, BlockPos min, int w, int h, int d) {
        int max = maxSide();
        if (w < 3 || h < 3 || d < 3 || w > max || h > max || d > max) {
            return false;
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    BlockPos q = min.offset(x, y, z);
                    if (!sl.isLoaded(q)) {
                        return false;
                    }
                    boolean ex = x == 0 || x == w - 1, ey = y == 0 || y == h - 1, ez = z == 0 || z == d - 1;
                    int edges = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
                    BlockState st = sl.getBlockState(q);
                    if (edges == 0) {
                        if (!st.isAir()) {
                            return false;
                        }
                        continue;
                    }
                    if (!isTank(st) || st.getValue(TankBlock.FORMED)) {
                        return false;
                    }
                    if (edges >= 2 && ((TankBlock) st.getBlock()).part != TankBlock.Part.CASING) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Forms the tank over {@code min..dims}; {@code origin} is the block that completed it, where the forming sweep starts. */
    static void form(ServerLevel sl, BlockPos min, int w, int h, int d, BlockPos origin) {
        long remembered = TankLedger.get(sl).take(min, w, h, d);
        int[] dims = {w, h, d};
        for (BlockPos q : shell(min, w, h, d)) {
            BlockState st = sl.getBlockState(q).setValue(TankBlock.FORMED, true);
            if (((TankBlock) st.getBlock()).part == TankBlock.Part.CASING) {
                // which axes the tank's edge runs along through this block, and which side of the block the edge lies on
                int[] rel = {q.getX() - min.getX(), q.getY() - min.getY(), q.getZ() - min.getZ()};
                boolean[] extreme = new boolean[3];
                int edges = 0;
                for (int i = 0; i < 3; i++) {
                    extreme[i] = rel[i] == 0 || rel[i] == dims[i] - 1;
                    edges += extreme[i] ? 1 : 0;
                }
                for (int i = 0; i < 3; i++) {
                    st = st.setValue(TankBlock.ALONG[i], edges == 3 || edges == 2 && !extreme[i]).setValue(TankBlock.HI[i], rel[i] == dims[i] - 1);
                }
            }
            sl.setBlock(q, st, 3);
            if (sl.getBlockEntity(q) instanceof TankPartBlockEntity part) {
                part.setMaster(min);
            }
        }
        if (sl.getBlockEntity(min) instanceof TankPartBlockEntity master) {
            master.setDims(w, h, d);
            master.setStored(remembered);
            master.setFormed(sl.getGameTime(), origin);
            master.sync();
        }
    }

    /** A block's state with the formed look taken off. */
    private static BlockState unformed(BlockState st) {
        st = st.setValue(TankBlock.FORMED, false);
        for (int i = 0; i < 3; i++) {
            st = st.setValue(TankBlock.ALONG[i], false).setValue(TankBlock.HI[i], false);
        }
        return st;
    }

    /** Called from a tank block's onRemove: a formed tank goes dormant (its plasma remembered); a block of a dormant one counts down. */
    public static void broken(ServerLevel sl, BlockPos p, BlockState state) {
        if (!state.getValue(TankBlock.FORMED) || !(sl.getBlockEntity(p) instanceof TankPartBlockEntity part) || part.master() == null) {
            TankLedger.get(sl).blockBroken(p);
            return;
        }
        TankPartBlockEntity master = part.masterEntity();
        if (master == null) {
            return;
        }
        BlockPos min = master.master();
        int w = master.w(), h = master.h(), d = master.d();
        TankLedger.get(sl).remember(min, w, h, d, master.stored(), master.shellBlocks() - 1);
        for (BlockPos q : shell(min, w, h, d)) {
            if (q.equals(p)) {
                continue;
            }
            BlockState st = sl.getBlockState(q);
            if (isTank(st)) {
                if (sl.getBlockEntity(q) instanceof TankPartBlockEntity other) {
                    other.setMaster(null);
                    other.setDims(0, 0, 0);
                    other.setStored(0);
                }
                sl.setBlock(q, unformed(st), 3);
            }
        }
    }

    static List<BlockPos> shell(BlockPos min, int w, int h, int d) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1) {
                        out.add(min.offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /** The screen's view of the tank the block at {@code pos} belongs to: size, level, ports and the first few cables on each. */
    public static void sendView(ServerPlayer player, BlockPos pos) {
        ServerLevel sl = player.serverLevel();
        if (!(sl.getBlockEntity(pos) instanceof TankPartBlockEntity part)) {
            return;
        }
        TankPartBlockEntity master = part.masterEntity();
        if (master == null) { // not formed: a view of just this block, so the screen can say so
            BSPNetwork.sendTo(player, new TankViewPacket(pos, 0, 0, 0, 0, 0, List.of(), List.of()));
            return;
        }
        List<TankViewPacket.Port> ports = new ArrayList<>();
        List<TankViewPacket.Cable> cables = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        master.ports().forEach((p, v) -> {
            ports.add(new TankViewPacket.Port(p, v[0], v[1], v[2]));
            // up to three cables out from the port, so the screen shows where runs leave without drawing the whole network
            ArrayDeque<BlockPos[]> queue = new ArrayDeque<>();
            for (Direction dir : Direction.values()) {
                queue.add(new BlockPos[]{p.relative(dir), p});
            }
            int depth = 0;
            while (!queue.isEmpty() && depth < 3) {
                int n = queue.size();
                for (int i = 0; i < n; i++) {
                    BlockPos[] step = queue.poll();
                    BlockPos c = step[0];
                    if (!sl.isLoaded(c) || !(sl.getBlockState(c).getBlock() instanceof TotemCableBlock) || !seen.add(c)) {
                        continue;
                    }
                    int flow = sl.getBlockEntity(c) instanceof PlasmaCableBlockEntity cable ? cable.flow() : 0;
                    cables.add(new TankViewPacket.Cable(c, flow));
                    for (Direction dir : Direction.values()) {
                        BlockPos nxt = c.relative(dir);
                        if (!nxt.equals(step[1])) {
                            queue.add(new BlockPos[]{nxt, c});
                        }
                    }
                }
                depth++;
            }
        });
        BSPNetwork.sendTo(player, new TankViewPacket(master.master(), master.w(), master.h(), master.d(), master.stored(), master.capacity(), ports, cables));
    }

    /** The port the wrench points at steps In and out, Input, Output. */
    public static void cycleMode(ServerLevel sl, BlockPos pos, boolean back) {
        if (sl.getBlockEntity(pos) instanceof TankPortBlockEntity port) {
            TankPortBlockEntity.Mode[] modes = TankPortBlockEntity.Mode.values();
            int i = port.mode().ordinal();
            port.setMode(modes[Math.floorMod(i + (back ? -1 : 1), modes.length)]);
        }
    }

    @Nullable
    public static TankPortBlockEntity portAt(ServerLevel sl, BlockPos pos) {
        return sl.getBlockEntity(pos) instanceof TankPortBlockEntity port ? port : null;
    }
}
