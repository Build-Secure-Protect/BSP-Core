package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dimensions where a Shatter Totem may never be, whatever {@code restrictions.allowedDimensions} says. There is one: the Compact
 * Machines rooms ({@code compactmachines:compact_world}). A room is reachable only through its machine, and a room whose machine
 * is lost is emptied for good (BSP-Server Utilities), so a totem standing in one would be lost with it. Matched by the dimension's
 * id only: Compact Machines stays optional and none of its classes are loaded.
 *
 * <p>A totem carried in a player's inventory inside a room is allowed, as in any dimension outside the list; their death and their
 * logout send it to their last spot in an allowed dimension. Every way a totem could come to stand or lie in a room is refused, and
 * one found there anyway (placed before this rule, or by a command) goes back to its owner.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TotemDimensions {
    /** The dimensions no config can allow. */
    public static final List<String> NEVER = List.of("compactmachines:compact_world");
    private static final Set<ResourceLocation> NEVER_IDS = Set.copyOf(NEVER.stream().map(ResourceLocation::tryParse).toList());

    /** Tests only: dimensions treated as never allowed besides {@link #NEVER} (Compact Machines is not loaded in most test runs). */
    private static volatile Set<ResourceLocation> standIns = Set.of();

    private TotemDimensions() {}

    /** Whether a totem may never be in this dimension. */
    public static boolean never(@Nullable ResourceLocation dimension) {
        return dimension != null && (NEVER_IDS.contains(dimension) || standIns.contains(dimension));
    }

    public static boolean never(Level level) {
        return never(level.dimension().location());
    }

    /** The names in {@code allowed} that are never allowed (and so ignored), in their order, trimmed. */
    public static List<String> ignored(List<? extends String> allowed) {
        List<String> out = new ArrayList<>();
        for (String name : allowed) {
            ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name.trim());
            if (id != null && NEVER_IDS.contains(id)) {
                out.add(name.trim());
            }
        }
        return out;
    }

    /** The warning logged at start for a never-allowed name listed in {@code restrictions.allowedDimensions}. */
    public static String ignoredWarning(String name) {
        return "restrictions.allowedDimensions lists \"" + name + "\", which is ignored: Shatter Totems are never allowed in Compact Machines"
                + " rooms (a room is reachable only through its machine, and a room whose machine is lost is emptied for good). This cannot be switched off.";
    }

    /** The red line a player is shown when placing or dropping a totem is refused in this dimension. */
    public static String refusalKey(ResourceLocation dimension) {
        if (NoTotemServers.here()) {
            return "message.bsp_core.no_totem.no_place";
        }
        return never(dimension) ? "message.bsp_core.shatter_totem.room_blocked" : "message.bsp_core.shatter_totem.dimension_blocked";
    }

    /** A live totem on its way out of a room: to {@code owner}, or (null) to the Overworld spawn. */
    public record Leaving(ItemStack totem, @Nullable TotemOwner owner, ResourceLocation from, BlockPos at) {}

    /**
     * Step one of taking a totem out of a room, before its block or entity goes: decides whether it is a live totem to hand on, and
     * counts a owned one in transit, so the block's removal is not taken for a totem lost from its place (and reissued). Null when there
     * is nothing to give back: a husk, a stale copy, one from an earlier season, or a bare block with neither identity nor owner (a
     * {@code /setblock} or a schematic paste), which is no totem at all.
     */
    @Nullable
    public static Leaving leave(MinecraftServer server, ItemStack totem, ResourceLocation from, BlockPos at) {
        if (!TotemInventories.isTotem(totem) || TotemIdentity.oldSeason(totem, server)) {
            BSPCore.LOGGER.info("A spent Shatter Totem at {} in {}, where totems may never be, was removed", at, from);
            return null;
        }
        var owner = TotemOwner.fromStack(totem).orElse(null);
        if (owner == null && TotemIdentity.id(totem) == null) {
            BSPCore.LOGGER.info("A Shatter Totem with no owner and no identity at {} in {}, where totems may never be, was removed", at, from);
            return null;
        }
        TotemIdentity.ensure(totem);
        TotemLedger ledger = TotemLedger.get(server);
        UUID id = TotemIdentity.id(totem), instance = TotemIdentity.instance(totem);
        if (ledger.stale(id, instance)) {
            BSPCore.LOGGER.info("A stale copy of Shatter Totem {} at {} in {}, where totems may never be, was removed", id, at, from);
            return null;
        }
        if (owner != null) {
            ledger.inTransit(id, instance, owner.uuid(), totem.getTag(), server.overworld().getGameTime());
        }
        return new Leaving(totem, owner, from, at);
    }

    /**
     * Step two, once the block or entity is gone: the totem goes to its owner, into their inventory now when they are here, otherwise
     * waiting for their next login (in transit meanwhile, never lost), as a totem kept on a server without totem placement does. A
     * totem nobody owns is placed at the Overworld's spawn.
     */
    public static void handOn(MinecraftServer server, Leaving leaving) {
        UUID id = TotemIdentity.id(leaving.totem());
        if (leaving.owner() == null) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, overworld.getSharedSpawnPos());
            if (!TotemPlacer.place(overworld, spawn, leaving.totem())) {
                overworld.addFreshEntity(new ShatterTotemItemEntity(overworld, spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5, leaving.totem()));
            }
            BSPCore.LOGGER.info("An unowned Shatter Totem {} at {} in {}, where totems may never be, was moved to the Overworld spawn", id, leaving.at(), leaving.from());
            return;
        }
        com.mrgregles.bsp_core.admin.AdminService.deliver(server, leaving.owner().uuid(), List.of(leaving.totem()), "message.bsp_core.shatter_totem.room_returned");
        BSPCore.LOGGER.info("{}'s Shatter Totem {} at {} in {}, where totems may never be, was given back to them", leaving.owner().name(), id, leaving.at(), leaving.from());
    }

    // ------------------------------------------------------------------ the sweep at start

    /** Server ticks since the start; -1 once the sweep has run (or before a start). */
    private static long sinceStart = -1;
    /** Longest wait, in ticks, for a network server's database before sweeping anyway (owners then collect on this server). */
    static final long NETWORK_WAIT_TICKS = 20 * 120;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        sinceStart = 0;
    }

    /**
     * After the start, once the network database is connected (a totem owed to an offline owner then waits on the network, where
     * they collect it on any server), or after {@link #NETWORK_WAIT_TICKS} without it: the sweep runs once.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sinceStart < 0) {
            return;
        }
        sinceStart++;
        boolean down = com.mrgregles.bsp_core.storage.NetworkStorage.down();
        if (!readyToSweep(down, sinceStart)) {
            return;
        }
        sinceStart = -1;
        if (down) {
            BSPCore.LOGGER.warn("BSP-Core checks for Shatter Totems in Compact Machines rooms without the network database; owners collect any found on this server");
        }
        try {
            sweep(event.getServer());
        } catch (RuntimeException e) {
            BSPCore.LOGGER.error("BSP-Core could not check for Shatter Totems in Compact Machines rooms", e);
        }
    }

    /**
     * Whether a totem found in a room may be handed on now: not in the first moments after a start, until the sweep is due (on a
     * network server, once its database is connected). Totems keep their own chunks loaded and ticking, so a room totem ticks at the
     * very first server tick; handed on then, a totem owed to an offline owner would wait on this server only, not on the network.
     */
    public static boolean settled() {
        long ticks = sinceStart;
        return ticks < 0 || readyToSweep(com.mrgregles.bsp_core.storage.NetworkStorage.down(), ticks);
    }

    /** Whether the start sweep runs now, {@code ticks} after the start. */
    public static boolean readyToSweep(boolean networkDown, long ticks) {
        return ticks >= 20 && (!networkDown || ticks >= NETWORK_WAIT_TICKS);
    }

    /**
     * Every totem the ledger has standing in a never-allowed dimension is taken out and given back (a world from before this rule, or
     * a config that once listed the dimension). A chunk saved on disk is loaded to reach it; one never saved is not generated (nothing
     * stands there). Returns how many live totems were handed on.
     */
    public static int sweep(MinecraftServer server) {
        return sweep(server, pos -> true);
    }

    /** As {@link #sweep(MinecraftServer)}, for the placed totems {@code which} accepts only (tests: their own totem, never another test's). */
    public static int sweep(MinecraftServer server, java.util.function.Predicate<GlobalPos> which) {
        List<GlobalPos> found = new ArrayList<>();
        TotemLedger.get(server).allPlaced().values().forEach(set -> set.forEach(pos -> {
            if (never(pos.dimension().location()) && which.test(pos)) {
                found.add(pos);
            }
        }));
        int handed = 0;
        for (GlobalPos pos : found) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                BSPCore.LOGGER.warn("The totem ledger lists a Shatter Totem at {} in {}, which this server does not have (Compact Machines removed?); it stays there",
                        pos.pos(), pos.dimension().location());
                continue;
            }
            LedgerCheck.Disk disk = LedgerCheck.loadIfSaved(level, pos.pos());
            if (disk == LedgerCheck.Disk.UNKNOWN) {
                continue; // the disk could not be read: its tick or the next start takes it out
            }
            if (disk != LedgerCheck.Disk.NOT_SAVED && level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity totem) {
                if (totem.leaveForbiddenDimension(level)) {
                    handed++;
                }
            } else {
                TotemLedger.get(server).recordRemoved(pos); // nothing stands there any more
            }
        }
        if (!found.isEmpty()) {
            BSPCore.LOGGER.warn("BSP-Core found {} Shatter Totem(s) listed as standing in Compact Machines rooms, where totems may never be; {} given back",
                    found.size(), handed);
        }
        return handed;
    }

    /** Tests only: as if the server had just started ({@code true}: nothing in a room is handed on yet) or had long been running. */
    public static void justStartedForTests(boolean justStarted) {
        sinceStart = justStarted ? 0 : -1;
    }

    /** Tests only: treats {@code dimension} as never allowed until {@link #clearStandIns}. */
    public static void standInForTests(ResourceLocation dimension) {
        standIns = Set.of(dimension);
    }

    public static void clearStandIns() {
        standIns = Set.of();
    }
}
