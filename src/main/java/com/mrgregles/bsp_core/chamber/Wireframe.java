package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.WireframePacket;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.TotemAuras;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wireframe, a Mob Chamber power at a Projector (made from many spider eyes): while a team member stands in reach, every outsider in
 * reach is drawn for them as a wireframe through walls. A raider carrying a totem with Shroud in the offhand (as for a steal) stays hidden
 * for Shroud's seconds after stepping into reach. The server works out who sees whom once a second and sends each viewer the list
 * ({@link WireframePacket}); the client draws it.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class Wireframe {
    /** Per projector: when each outsider stepped into reach and when they were last in it (game time). */
    private static final Map<GlobalPos, Map<UUID, long[]>> ENTERED = new HashMap<>();
    /** How long an outsider must stay out of reach before stepping back in starts Shroud again. */
    private static final long AWAY_TICKS = 60 * 20;
    /** Most outsiders one viewer is sent at once (the packet's own limit). */
    public static final int MAX_SHOWN = 256;

    private Wireframe() {}

    /** Ticks a raider stays hidden after stepping into reach: their offhand totem's Shroud seconds, or 0. */
    public static int shroudTicks(ServerPlayer player) {
        var offhand = player.getOffhandItem();
        if (!TotemInventories.isTotem(offhand)) {
            return 0;
        }
        int lvl = TotemUpgrades.getLevel(offhand, Buff.SHROUD);
        return lvl <= 0 ? 0 : BSPConfig.levelValue(BSPConfig.SHROUD_SECONDS.get(), lvl, 0) * 20;
    }

    /**
     * Who sees whom at {@code now}, from {@code players}: each team member in reach (key) and the outsiders in reach they see. A viewer
     * sees nobody while no outsider shows; an outsider shows once its Shroud has run out since it stepped in. Keeps the step-in times.
     */
    public static Map<ServerPlayer, List<ServerPlayer>> sight(ServerLevel sl, TotemProjectorBlockEntity p, Collection<ServerPlayer> players, long now) {
        int lvl = p.level(Buff.WIREFRAME);
        GlobalPos key = GlobalPos.of(sl.dimension(), p.getBlockPos());
        Map<ServerPlayer, List<ServerPlayer>> out = new LinkedHashMap<>();
        if (lvl <= 0) {
            ENTERED.remove(key);
            return out;
        }
        BlockPos at = p.getBlockPos();
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        int r = MobPowerEffects.reach(lvl);
        Map<UUID, long[]> entered = ENTERED.computeIfAbsent(key, k -> new HashMap<>());
        List<ServerPlayer> viewers = new ArrayList<>(), shown = new ArrayList<>();
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer pl : players) {
            if (pl.isSpectator() || pl.level() != sl || !TotemAuras.inCube(pl, x, y, z, r)) {
                continue;
            }
            if (p.isTeam(pl.getUUID())) {
                viewers.add(pl);
            } else if (MobPowerEffects.outsider(p, pl)) {
                inside.add(pl.getUUID());
                long[] t = entered.computeIfAbsent(pl.getUUID(), u -> new long[] {now, now});
                t[1] = now;
                if (now - t[0] >= shroudTicks(pl) && shown.size() < MAX_SHOWN) {
                    shown.add(pl);
                }
            }
        }
        // a step out and back does not start Shroud again; a minute away does
        entered.entrySet().removeIf(e -> !inside.contains(e.getKey()) && now - e.getValue()[1] > AWAY_TICKS);
        for (ServerPlayer v : viewers) {
            out.put(v, shown);
        }
        return out;
    }

    /** A projector's second: sends each viewer in reach the outsiders they see. */
    public static void tick(ServerLevel sl, TotemProjectorBlockEntity p) {
        for (var e : sight(sl, p, sl.players(), sl.getGameTime()).entrySet()) {
            int[] ids = e.getValue().stream().mapToInt(ServerPlayer::getId).toArray();
            BSPNetwork.sendTo(e.getKey(), new WireframePacket(p.getBlockPos(), ids));
        }
    }

    /** The projector stopped giving Wireframe (or is gone): forget its step-in times. */
    public static void forget(ServerLevel sl, BlockPos projector) {
        ENTERED.remove(GlobalPos.of(sl.dimension(), projector));
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ENTERED.clear();
    }
}
