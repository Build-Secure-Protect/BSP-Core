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
 * forgotten (and logged). {@code /bsp totem check} also loads the chunks of the others to check them all.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LedgerCheck {
    private LedgerCheck() {}

    private static int ticks;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks >= 1200) {
            ticks = 0;
            check(event.getServer(), false);
        }
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
            if (loadChunks) {
                level.getChunk(pos.pos()); // loads it if it is not
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
