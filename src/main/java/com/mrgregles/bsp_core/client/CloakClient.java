package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.CloakSnapshot;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cloaking on the client: while this player is outside a cloaked cube and not on its access list, the
 * blocks inside it are swapped, in this client's copy of the world only, for the cube's snapshot.
 * Every two seconds the swap is checked again (the server keeps sending the real blocks) and the
 * real blocks are remembered so they can be put back when the player walks in or the cloak ends.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class CloakClient {
    private static final Set<ShatterTotemBlockEntity> TOTEMS = new HashSet<>();
    /** Real blocks under the swap, by cloak origin. */
    private static final Map<BlockPos, Map<BlockPos, BlockState>> REAL = new HashMap<>();

    private CloakClient() {}

    public static void register(ShatterTotemBlockEntity totem) {
        TOTEMS.add(totem);
    }

    public static void unregister(ShatterTotemBlockEntity totem) {
        TOTEMS.remove(totem);
        restore(totem.getBlockPos());
    }

    /** Whether {@code pos} is inside a cloaked cube this player is being kept out of: X-ray leaves those blocks alone. */
    public static boolean hiddenFromMe(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        for (ShatterTotemBlockEntity totem : TOTEMS) {
            CloakSnapshot snap = totem.isRemoved() ? null : totem.cloak();
            if (snap != null && snap.contains(pos) && !snap.contains(mc.player.blockPosition()) && !totem.seesThroughCloak(mc.player)) {
                return true;
            }
        }
        return false;
    }

    private static void restore(BlockPos origin) {
        Map<BlockPos, BlockState> real = REAL.remove(origin);
        Minecraft mc = Minecraft.getInstance();
        if (real == null || mc.level == null) {
            return;
        }
        real.forEach((pos, st) -> mc.level.setBlock(pos, st, 3));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.level.getGameTime() % 40 != 0) {
            return;
        }
        for (ShatterTotemBlockEntity totem : new HashSet<>(TOTEMS)) {
            CloakSnapshot snap = totem.isRemoved() ? null : totem.cloak();
            BlockPos origin = totem.getBlockPos();
            boolean hide = snap != null && !snap.contains(mc.player.blockPosition()) && !totem.seesThroughCloak(mc.player);
            if (!hide) {
                restore(origin);
                continue;
            }
            Map<BlockPos, BlockState> real = REAL.computeIfAbsent(origin, k -> new HashMap<>());
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int r = snap.radius;
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!mc.level.isLoaded(p)) {
                            continue;
                        }
                        BlockState want = snap.at(p), now = mc.level.getBlockState(p);
                        if (now != want) {
                            real.put(p.immutable(), now); // the server told us something new: remember it under the swap
                            mc.level.setBlock(p, want, 3);
                        }
                    }
                }
            }
        }
    }
}
