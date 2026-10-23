package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where each upgrade's node sits on the totem's tree and the two letters on it. No client references, so the game tests can
 * check that every node can be clicked and no two share a label.
 */
public final class TreeLayout {
    private TreeLayout() {}

    static final int CX = 85, CY = 85, NODE = 14;
    static final int[] RING = {20, 35, 50, 65, 78};
    /** Spokes are spread wide enough that neighbouring nodes on the innermost ring do not touch. */
    private static final float[] SPOKE = {-128f, -52f, 12f, 78f, 180f, 129f};
    private static final Map<Buff, int[]> POS = new EnumMap<>(Buff.class);

    static {
        for (Buff b : Buff.values()) {
            // two nodes on one ring of one path sit either side of the spoke; Extra Homes shares Sanctuary's ring on the Home
            // path, so it and Swift Travel after it sit 26 degrees off the spoke
            double a = Math.toRadians(SPOKE[b.path] + (b == Buff.BOUNCY || b == Buff.CLOAKING || b == Buff.THIEF_STEP || b == Buff.SENTINEL ? 22
                    : b == Buff.EXTRA_HOMES || b == Buff.SWIFT_TRAVEL ? 26 : b == Buff.NIGHT_SIGHT || b == Buff.DEADLOCK ? -14 : 0));
            int r = RING[Math.min(b.tier, RING.length - 1)];
            POS.put(b, new int[]{CX + (int) Math.round(Math.cos(a) * r), CY + (int) Math.round(Math.sin(a) * r)});
        }
    }

    /** The centre of an upgrade's node, relative to the tree's corner. */
    public static int[] at(Buff buff) {
        return POS.get(buff);
    }

    /** Two letters on the node; the upgrades whose first two letters repeat another's get their own. */
    public static String abbr(Buff buff) {
        return switch (buff) {
            case RECALL -> "RC";
            case RECALL_BLOCK -> "RB";
            case SWIFT_TRAVEL -> "ST";
            default -> buff.key.substring(0, 2).toUpperCase(Locale.ROOT);
        };
    }

    /** The upgrade whose node is under a point relative to the tree's corner: the nearest centre, so a corner two nodes share goes to the closer one. */
    @Nullable
    public static Buff nodeAt(double x, double y) {
        Buff best = null;
        double bestD = Double.MAX_VALUE;
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            if (x >= p[0] - NODE / 2 && x < p[0] + NODE / 2 && y >= p[1] - NODE / 2 && y < p[1] + NODE / 2) {
                double d = (x - p[0]) * (x - p[0]) + (y - p[1]) * (y - p[1]);
                if (d < bestD) {
                    bestD = d;
                    best = b;
                }
            }
        }
        return best;
    }
}
