package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the ledger's placed totems true to the world. The ledger lists every placed totem by place (scores, the admin panel,
 * the Totem Forge's price and other mods read it); an entry whose block is gone without the ledger hearing of it would count
 * a totem nobody has. Once a minute every entry whose chunk is loaded is checked, and one with no totem block there is
 * forgotten (and logged). The entries in chunks nobody loads are swept in turn: {@code scoring.ledgerSweepPerMinute} of them
 * a minute have their chunk loaded and are checked the same way, so a totem gone with its chunk (a region reset, a deleted
 * chunk) stops counting without anyone going there. {@code /bsp totem check} loads and checks them all at once.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LedgerCheck {
    private LedgerCheck() {}

    private static int ticks;
    /** Where the sweep of unloaded entries goes on from: the place of the last entry it checked (entries are taken in place order). */
    private static String sweepAfter = "";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks >= 1200) {
            ticks = 0;
            check(event.getServer(), false);
            sweep(event.getServer(), com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.SCORE_LEDGER_SWEEP, 4));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ticks = 0;
        sweepAfter = "";
    }

    private static String key(GlobalPos pos) {
        return pos.dimension().location() + String.format(" %011d %011d %011d", (long) pos.pos().getX() + Integer.MAX_VALUE,
                (long) pos.pos().getY() + Integer.MAX_VALUE, (long) pos.pos().getZ() + Integer.MAX_VALUE);
    }

    /**
     * Checks up to {@code limit} entries whose chunk is not loaded, loading it, going on from where the last sweep stopped and
     * starting again from the first when it reaches the end. Entries in a world this server does not have are left (they count
     * for nobody). Returns what it did.
     */
    public static Result sweep(MinecraftServer server, int limit) {
        if (limit <= 0) {
            return new Result(0, 0, 0);
        }
        TotemLedger ledger = TotemLedger.get(server);
        java.util.TreeMap<String, Map.Entry<UUID, GlobalPos>> waiting = new java.util.TreeMap<>();
        for (Map.Entry<UUID, Set<GlobalPos>> e : ledger.allPlaced().entrySet()) {
            for (GlobalPos pos : e.getValue()) {
                ServerLevel level = server.getLevel(pos.dimension());
                if (level != null && !level.hasChunkAt(pos.pos())) {
                    waiting.put(key(pos), Map.entry(e.getKey(), pos));
                }
            }
        }
        List<Map.Entry<UUID, GlobalPos>> turn = new ArrayList<>(waiting.tailMap(sweepAfter, false).values());
        if (turn.size() < limit) {
            for (Map.Entry<UUID, GlobalPos> e : waiting.headMap(sweepAfter, true).values()) { // round again from the start
                turn.add(e);
            }
        }
        int checked = 0, forgotten = 0;
        for (Map.Entry<UUID, GlobalPos> e : turn.subList(0, Math.min(limit, turn.size()))) {
            GlobalPos pos = e.getValue();
            sweepAfter = key(pos);
            ServerLevel level = server.getLevel(pos.dimension());
            if (loadIfSaved(level, pos.pos()) == Disk.UNKNOWN) {
                continue; // the disk could not be read: tried again next round
            }
            checked++;
            if (!(level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity)) {
                ledger.recordRemoved(pos);
                forgotten++;
                BSPCore.LOGGER.warn("The totem ledger listed a Shatter Totem of {} at {} in {} (an unloaded chunk), but none stands there; the entry is forgotten",
                        e.getKey(), pos.pos(), pos.dimension().location());
            }
        }
        return new Result(checked, forgotten, waiting.size() - checked);
    }

    /** What the disk holds of a chunk that is not loaded. */
    public enum Disk { LOADED, SAVED, NOT_SAVED, UNKNOWN }

    /**
     * Loads the chunk at {@code pos} when it is saved on disk, never generating one that is not (a region reset or a deleted
     * chunk, the very cases looked for here): generating it would run world generation on the server thread and write new
     * terrain. {@link Disk#NOT_SAVED} means the chunk does not exist, so no totem can stand there; {@link Disk#UNKNOWN} means the
     * disk could not be read, and nothing should be decided. The read sees writes still queued for the disk.
     */
    public static Disk loadIfSaved(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (level.hasChunkAt(pos)) {
            return Disk.LOADED;
        }
        java.util.Optional<net.minecraft.nbt.CompoundTag> saved;
        try {
            saved = level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos)).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            BSPCore.LOGGER.warn("The totem ledger could not read the chunk at {} in {} from disk: {}", pos, level.dimension().location(), e.toString());
            return Disk.UNKNOWN;
        }
        if (saved.isEmpty()) {
            return Disk.NOT_SAVED;
        }
        level.getChunk(pos); // saved: loads it, nothing generated
        return Disk.SAVED;
    }

    /** What a check found: entries checked, entries forgotten, entries left unchecked (chunk not loaded, or the world unknown). */
    public record Result(int checked, int forgotten, int skipped) {}

    /** Checks every placed entry; with {@code loadChunks} the chunks not loaded are loaded to check them too. */
    public static Result check(MinecraftServer server, boolean loadChunks) {
        TotemLedger ledger = TotemLedger.get(server);
        List<Map.Entry<UUID, GlobalPos>> entries = new ArrayList<>();
        for (Map.Entry<UUID, Set<GlobalPos>> e : ledger.allPlaced().entrySet()) {
            for (GlobalPos pos : e.getValue()) {
                entries.add(Map.entry(e.getKey(), pos));
            }
        }
        int checked = 0, forgotten = 0, skipped = 0;
        for (Map.Entry<UUID, GlobalPos> e : entries) {
            GlobalPos pos = e.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null || !loadChunks && !level.hasChunkAt(pos.pos())) {
                skipped++;
                continue;
            }
            if (loadChunks && loadIfSaved(level, pos.pos()) == Disk.UNKNOWN) {
                skipped++;
                continue;
            }
            checked++;
            if (!(level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity)) {
                ledger.recordRemoved(pos);
                forgotten++;
                BSPCore.LOGGER.warn("The totem ledger listed a Shatter Totem of {} at {} in {}, but none stands there; the entry is forgotten",
                        e.getKey(), pos.pos(), pos.dimension().location());
            }
        }
        return new Result(checked, forgotten, skipped);
    }
}
