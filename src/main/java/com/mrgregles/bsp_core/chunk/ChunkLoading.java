package com.mrgregles.bsp_core.chunk;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkViewPacket;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The chunk loading rules, and the only code that asks Forge to keep chunks loaded.
 *
 * <ul>
 *   <li>The Anchor upgrade gives a placed totem a number of chunks ({@code chunks.chunksPerLevel}); the
 *       totem's own chunk is always one of them. Survey widens the square the rest are picked from
 *       ({@code chunks.rangePerLevel}, in chunks either side).</li>
 *   <li>A generator can send Anchor to a projector. The projector adds no chunks: it lets some of the
 *       totem's allowance be picked around the projector instead. Like the totem's, the projector's
 *       own chunk is always one of them (as soon as the allowance has one free), so a projector that
 *       holds chunks is always running and paying RF. Its chunks are held while it is fed and powered.</li>
 *   <li>A player's main totem is remembered by its totem id ({@link ChunkLedger#mains}): the first one they placed here.
 *       It stays main while it is theirs, also while it is carried or moved. It is forgotten when it is stolen or retired, and
 *       the next one is picked (highest Anchor, then highest tier, then held longest). While the remembered one is not placed
 *       here, the same pick stands in for it. Any other totem of theirs counts as Anchor level 1 at most: one chunk, the one
 *       it stands in.</li>
 *   <li>An entry for a totem the totem ledger no longer lists as placed (its block went without the block's own removal: a
 *       world edit, a region reset, a wipe of an unloaded chunk) is dropped ({@link #prune}), so it can never stay main.</li>
 *   <li>When a totem changes owner every chunk picked for it is dropped; its own chunk stays.</li>
 * </ul>
 * Everything here runs on the server thread.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChunkLoading {
    public static final int MAX_RADIUS = 3;
    /** What Forge has been asked to hold, by the block it is held for. Rebuilt from the ledger at every server start. */
    private static final Map<GlobalPos, Set<Long>> APPLIED = new HashMap<>();

    private ChunkLoading() {}

    /** Called once at mod setup. Tickets Forge saved with the world are dropped as it loads; the ledger puts back the ones that still apply. */
    public static void init() {
        ForgeChunkManager.setForcedChunkLoadingCallback(BSPCore.MODID, (level, helper) -> {
            for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) {
                helper.removeAllTickets(owner);
            }
        });
    }

    // ------------------------------------------------------------------ rules

    public static boolean enabled() {
        return BSPConfig.getOr(BSPConfig.CHUNKS_ENABLED, true);
    }

    /** Chunks an Anchor of {@code level} keeps loaded. */
    public static int chunksAt(int level) {
        if (level <= 0) {
            return 0;
        }
        int fallback = new int[]{1, 3, 6}[Math.min(level, 3) - 1];
        return Math.max(1, Math.min(49, BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CHUNKS_PER_LEVEL, List.<Integer>of()), level, fallback)));
    }

    /** How many chunks either side of the totem (or projector) may be picked with Survey at {@code survey}. */
    public static int radiusAt(int survey) {
        return Math.max(1, Math.min(MAX_RADIUS, BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CHUNK_RANGE, List.<Integer>of()), survey + 1, survey + 1)));
    }

    private static final Comparator<Map.Entry<GlobalPos, ChunkLedger.Totem>> OLDEST_FIRST = Comparator
            .<Map.Entry<GlobalPos, ChunkLedger.Totem>>comparingLong(e -> e.getValue().since)
            .thenComparing(e -> e.getKey().dimension().location().toString()).thenComparingLong(e -> e.getKey().pos().asLong());

    /** The totems the totem ledger lists as placed for {@code owner}, or null when no server is running (nothing is left out). */
    @Nullable
    private static Set<GlobalPos> standing(@Nullable MinecraftServer server, UUID owner) {
        return server == null ? null : TotemLedger.get(server).placedFor(owner);
    }

    /** The owner's placed totems, held longest first. An entry the totem ledger does not list as placed (see {@link #prune}) is left out. */
    private static List<Map.Entry<GlobalPos, ChunkLedger.Totem>> totemsOf(ChunkLedger ledger, UUID owner) {
        List<Map.Entry<GlobalPos, ChunkLedger.Totem>> out = new ArrayList<>();
        Set<GlobalPos> standing = standing(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(), owner);
        for (Map.Entry<GlobalPos, ChunkLedger.Totem> e : ledger.totems.entrySet()) {
            if (e.getValue().owner.equals(owner) && (standing == null || standing.contains(e.getKey()))) {
                out.add(e);
            }
        }
        out.sort(OLDEST_FIRST);
        return out;
    }

    /** Which of a player's totems is picked as main when none is remembered: highest Anchor, then highest tier, then held longest. */
    private static Comparator<Map.Entry<GlobalPos, ChunkLedger.Totem>> pickOrder(@Nullable TotemLedger tiers) {
        return Comparator.<Map.Entry<GlobalPos, ChunkLedger.Totem>>comparingInt(e -> -e.getValue().anchor)
                .thenComparingInt(e -> tiers == null ? 0 : -tiers.tierAt(e.getKey()))
                .thenComparing(OLDEST_FIRST);
    }

    /**
     * The owner's main totem among those placed here: the remembered one when it stands here; otherwise the pick. A pick is
     * remembered when nothing is remembered for the owner yet (their first totem, or the main one was stolen or retired).
     */
    @Nullable
    private static GlobalPos mainOf(ChunkLedger ledger, UUID owner) {
        List<Map.Entry<GlobalPos, ChunkLedger.Totem>> mine = totemsOf(ledger, owner);
        if (mine.isEmpty()) {
            return null;
        }
        UUID remembered = ledger.mains.get(owner);
        if (remembered != null) {
            for (Map.Entry<GlobalPos, ChunkLedger.Totem> e : mine) {
                if (remembered.equals(e.getValue().id)) {
                    return e.getKey();
                }
            }
            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            TotemLedger.Sighting seen = server == null ? null : TotemLedger.get(server).sighting(remembered);
            if (seen != null && seen.where() == TotemLedger.RETIRED) {
                ledger.mains.remove(owner); // wiped or retired: it never comes back
                ledger.setDirty();
                remembered = null;
            }
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        mine.sort(pickOrder(server == null ? null : TotemLedger.get(server)));
        Map.Entry<GlobalPos, ChunkLedger.Totem> pick = mine.get(0);
        if (remembered == null && pick.getValue().id != null) {
            ledger.mains.put(owner, pick.getValue().id);
            ledger.setDirty();
        }
        return pick.getKey();
    }

    private static boolean isMain(ChunkLedger ledger, GlobalPos pos, ChunkLedger.Totem totem) {
        return pos.equals(mainOf(ledger, totem.owner));
    }

    /**
     * Whether the totem at {@code pos} is its owner's main totem (also true for a totem the ledger does not know). Server thread
     * only: the first answer for an owner with nothing remembered remembers the pick.
     */
    public static boolean isMain(ServerLevel level, BlockPos pos) {
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        ChunkLedger.Totem totem = ledger.totems.get(here);
        return totem == null || isMain(ledger, here, totem);
    }

    private static List<ChunkLedger.Projector> projectorsOf(ChunkLedger ledger, GlobalPos totem) {
        List<ChunkLedger.Projector> out = new ArrayList<>();
        for (ChunkLedger.Projector p : ledger.projectors.values()) {
            if (p.totem.equals(totem)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Chunks in use from the totem's allowance: its own picks and those of its projectors. */
    private static int used(ChunkLedger ledger, GlobalPos pos, ChunkLedger.Totem totem) {
        int n = totem.chunks.size();
        for (ChunkLedger.Projector p : projectorsOf(ledger, pos)) {
            n += p.chunks.size();
        }
        return n;
    }

    /** The totem's allowance right now: by Anchor level, one for a second totem, and within the per-player limit. */
    private static int slots(ChunkLedger ledger, GlobalPos pos, ChunkLedger.Totem totem) {
        if (!enabled() || totem.anchor <= 0) {
            return 0;
        }
        int slots = chunksAt(isMain(ledger, pos, totem) ? totem.anchor : 1), cap = BSPConfig.getOr(BSPConfig.CHUNKS_MAX_PER_PLAYER, 0);
        if (cap > 0) {
            int others = 0;
            for (Map.Entry<GlobalPos, ChunkLedger.Totem> e : totemsOf(ledger, totem.owner)) {
                if (!e.getKey().equals(pos)) {
                    others += used(ledger, e.getKey(), e.getValue());
                }
            }
            slots = Math.min(slots, Math.max(0, cap - others));
        }
        return slots;
    }

    private static int radius(ChunkLedger ledger, GlobalPos pos, ChunkLedger.Totem totem) {
        return radiusAt(isMain(ledger, pos, totem) ? totem.survey : 0);
    }

    private static long chunkOf(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean within(long chunk, long centre, int radius) {
        return Math.max(Math.abs(ChunkPos.getX(chunk) - ChunkPos.getX(centre)), Math.abs(ChunkPos.getZ(chunk) - ChunkPos.getZ(centre))) <= radius;
    }

    private static <T> void dropLast(Set<T> set) {
        Iterator<T> it = set.iterator();
        while (it.hasNext()) {
            it.next();
            if (!it.hasNext()) {
                it.remove();
            }
        }
    }

    /** Brings every choice made for the owner's totems back inside the rules after something changed. */
    private static void tidy(ChunkLedger ledger, UUID owner) {
        for (Map.Entry<GlobalPos, ChunkLedger.Totem> e : totemsOf(ledger, owner)) {
            GlobalPos pos = e.getKey();
            ChunkLedger.Totem totem = e.getValue();
            List<ChunkLedger.Projector> projectors = projectorsOf(ledger, pos);
            int slots = slots(ledger, pos, totem), radius = radius(ledger, pos, totem);
            long home = chunkOf(pos.pos());
            if (slots <= 0) {
                totem.chunks.clear();
                projectors.forEach(p -> p.chunks.clear());
                continue;
            }
            totem.chunks.removeIf(c -> !within(c, home, radius));
            if (!totem.chunks.contains(home)) { // the totem's own chunk always comes first
                List<Long> rest = new ArrayList<>(totem.chunks);
                totem.chunks.clear();
                totem.chunks.add(home);
                totem.chunks.addAll(rest);
            }
            for (Map.Entry<GlobalPos, ChunkLedger.Projector> pe : ledger.projectors.entrySet()) {
                if (pe.getValue().totem.equals(pos)) {
                    long centre = chunkOf(pe.getKey().pos());
                    Set<Long> picked = pe.getValue().chunks;
                    picked.removeIf(c -> !within(c, centre, radius));
                    if (!picked.contains(centre)) {
                        // the projector's own chunk comes with it when there is one to spare; without it, it holds nothing
                        List<Long> rest = new ArrayList<>(picked);
                        picked.clear();
                        if (used(ledger, pos, totem) < slots) {
                            picked.add(centre);
                            picked.addAll(rest);
                        }
                    }
                }
            }
            while (used(ledger, pos, totem) > slots) {
                ChunkLedger.Projector fullest = null;
                for (ChunkLedger.Projector p : projectors) {
                    if (!p.chunks.isEmpty() && (fullest == null || p.chunks.size() > fullest.chunks.size())) {
                        fullest = p;
                    }
                }
                dropLast(fullest != null ? fullest.chunks : totem.chunks);
            }
        }
        ledger.setDirty();
    }

    /** Makes Forge's loaded chunks match the ledger. {@code leaving} is a player who is logging out and so counts as offline. */
    private static void apply(MinecraftServer server, @Nullable UUID leaving) {
        ChunkLedger ledger = ChunkLedger.get(server);
        Map<GlobalPos, Set<Long>> want = new HashMap<>();
        if (enabled()) {
            boolean onlineOnly = BSPConfig.getOr(BSPConfig.CHUNKS_OWNER_ONLINE, false);
            TotemLedger placed = TotemLedger.get(server);
            for (Map.Entry<GlobalPos, ChunkLedger.Totem> e : ledger.totems.entrySet()) {
                UUID owner = e.getValue().owner;
                if (onlineOnly && (owner.equals(leaving) || server.getPlayerList().getPlayer(owner) == null)) {
                    continue;
                }
                if (!placed.placedFor(owner).contains(e.getKey())) {
                    continue; // a totem no longer standing: never load its chunks (a region reset would be generated again); prune drops it
                }
                want.put(e.getKey(), new HashSet<>(e.getValue().chunks));
                for (Map.Entry<GlobalPos, ChunkLedger.Projector> pe : ledger.projectors.entrySet()) {
                    if (pe.getValue().on && pe.getValue().totem.equals(e.getKey())) {
                        want.put(pe.getKey(), new HashSet<>(pe.getValue().chunks));
                    }
                }
            }
        }
        Set<GlobalPos> sources = new HashSet<>(APPLIED.keySet());
        sources.addAll(want.keySet());
        for (GlobalPos source : sources) {
            ServerLevel level = server.getLevel(source.dimension());
            if (level == null) {
                continue;
            }
            Set<Long> had = APPLIED.getOrDefault(source, Set.of()), now = want.getOrDefault(source, Set.of());
            for (long c : had) {
                if (!now.contains(c)) {
                    ForgeChunkManager.forceChunk(level, BSPCore.MODID, source.pos(), ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
                }
            }
            for (long c : now) {
                if (!had.contains(c)) {
                    ForgeChunkManager.forceChunk(level, BSPCore.MODID, source.pos(), ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
                }
            }
        }
        APPLIED.clear();
        want.forEach((k, v) -> {
            if (!v.isEmpty()) {
                APPLIED.put(k, v);
            }
        });
    }

    /** Re-checks everything, for example after an admin changed a chunk loading setting. */
    public static void refresh(MinecraftServer server) {
        ChunkLedger ledger = ChunkLedger.get(server);
        Set<UUID> owners = new HashSet<>();
        ledger.totems.values().forEach(t -> owners.add(t.owner));
        owners.forEach(o -> tidy(ledger, o));
        apply(server, null);
    }

    // ------------------------------------------------------------------ totems

    /** Called by a placed totem whenever its owner or upgrades may have changed, and when it loads. */
    public static void totemChanged(ShatterTotemBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (be.getOwner().isEmpty()) {
            totemRemoved(level, be.getBlockPos());
            return;
        }
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), be.getBlockPos());
        UUID owner = be.getOwner().get().uuid();
        int anchor = be.getUpgradeLevel(Buff.ANCHOR), survey = be.getUpgradeLevel(Buff.SURVEY);
        UUID id = be.totemId();
        ChunkLedger.Totem totem = ledger.totems.get(pos);
        if (totem != null && totem.owner.equals(owner) && totem.since == be.ownedSince() && totem.anchor == anchor && totem.survey == survey
                && java.util.Objects.equals(totem.id, id)) {
            return;
        }
        UUID previous = null;
        if (totem == null) {
            totem = new ChunkLedger.Totem();
            ledger.totems.put(pos, totem);
        } else if (!totem.owner.equals(owner)) {
            // stolen: the new owner starts from nothing
            previous = totem.owner;
            totem.chunks.clear();
            projectorsOf(ledger, pos).forEach(p -> p.chunks.clear());
            if (id != null && id.equals(ledger.mains.get(previous))) {
                ledger.mains.remove(previous); // their main totem was stolen: another of theirs is picked
            }
        }
        if (id != null && !id.equals(ledger.mains.get(owner))) {
            ledger.mains.values().remove(id); // a totem is nobody else's main once it is this owner's (for a remembered id whose steal was missed)
        }
        totem.owner = owner;
        totem.since = be.ownedSince();
        totem.id = id;
        totem.anchor = anchor;
        totem.survey = survey;
        tidy(ledger, owner);
        if (previous != null) {
            tidy(ledger, previous);
        }
        apply(level.getServer(), null);
    }

    /** Called when a totem is picked up, broken or unclaimed. */
    public static void totemRemoved(ServerLevel level, BlockPos blockPos) {
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), blockPos);
        ChunkLedger.Totem totem = ledger.totems.remove(pos);
        if (totem == null) {
            return;
        }
        ledger.projectors.values().removeIf(p -> p.totem.equals(pos));
        tidy(ledger, totem.owner); // another of their totems may now be the main one
        apply(level.getServer(), null);
    }

    /**
     * Drops every entry for a totem the totem ledger does not list as placed there for that owner: its block went without the
     * block's own removal (a world edit, a region reset, a wipe or a ledger check of a chunk that was not loaded). Such an entry
     * would hold chunks for nothing and, being old, stay its owner's main totem for good. Both ledgers are written together
     * whenever a totem loads, is placed, changes owner or is removed, so a standing totem is always in both. Returns how many went.
     */
    public static int prune(MinecraftServer server) {
        ChunkLedger ledger = ChunkLedger.get(server);
        TotemLedger placed = TotemLedger.get(server);
        Set<UUID> owners = new HashSet<>();
        Iterator<Map.Entry<GlobalPos, ChunkLedger.Totem>> it = ledger.totems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, ChunkLedger.Totem> e = it.next();
            if (!placed.placedFor(e.getValue().owner).contains(e.getKey())) {
                GlobalPos pos = e.getKey();
                it.remove();
                ledger.projectors.values().removeIf(p -> p.totem.equals(pos));
                owners.add(e.getValue().owner);
                if (e.getValue().id != null && e.getValue().id.equals(ledger.mains.get(e.getValue().owner))) {
                    ledger.mains.remove(e.getValue().owner); // the main totem went with it: their next one is picked
                }
                BSPCore.LOGGER.warn("The chunk ledger listed a Shatter Totem of {} at {} in {}, but the totem ledger has none placed there; the entry is dropped",
                        e.getValue().owner, pos.pos(), pos.dimension().location());
            }
        }
        if (!owners.isEmpty()) {
            owners.forEach(o -> tidy(ledger, o));
            apply(server, null);
        }
        return owners.size();
    }

    /** The chunks picked at a totem, as x and z offsets from its own chunk, so the item can carry them to where it is placed next. */
    public static int[] pattern(ServerLevel level, BlockPos blockPos) {
        ChunkLedger.Totem totem = ChunkLedger.get(level.getServer()).totems.get(GlobalPos.of(level.dimension(), blockPos));
        if (totem == null) {
            return new int[0];
        }
        long home = chunkOf(blockPos);
        List<Integer> out = new ArrayList<>();
        for (long c : totem.chunks) {
            if (c != home) {
                out.add(ChunkPos.getX(c) - ChunkPos.getX(home));
                out.add(ChunkPos.getZ(c) - ChunkPos.getZ(home));
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Puts a carried pattern back around a totem that has just been placed. Anything that no longer fits is dropped. */
    public static void restore(ServerLevel level, BlockPos blockPos, int[] pattern) {
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), blockPos);
        ChunkLedger.Totem totem = ledger.totems.get(pos);
        if (totem == null || pattern.length < 2) {
            return;
        }
        long home = chunkOf(blockPos);
        totem.chunks.add(home);
        for (int i = 0; i + 1 < pattern.length && i < 2 * 49; i += 2) {
            totem.chunks.add(ChunkPos.asLong(ChunkPos.getX(home) + pattern[i], ChunkPos.getZ(home) + pattern[i + 1]));
        }
        tidy(ledger, totem.owner);
        apply(level.getServer(), null);
    }

    // ------------------------------------------------------------------ projectors

    /** When each projector was last reported as fed, in overworld game time. Not saved: after a restart every projector gets a fresh allowance of time. */
    private static final Map<GlobalPos, Long> FED = new HashMap<>();

    /**
     * Called once a second by a projector that is being fed chunk loading from {@code totem} and has
     * the RF to run, and with null by one that has lost its signal or its RF. A projector nobody has reported for
     * longer than a full cable check is switched off by {@link #onServerTick}.
     */
    public static void projector(ServerLevel level, BlockPos blockPos, @Nullable GlobalPos totem) {
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), blockPos);
        ChunkLedger.Projector p = ledger.projectors.get(pos);
        if (totem == null) {
            if (p != null && p.on) {
                p.on = false;
                ledger.setDirty();
                apply(level.getServer(), null);
            }
            return;
        }
        FED.put(pos, level.getServer().overworld().getGameTime());
        GlobalPos from = totem;
        if (p != null && p.on && p.totem.equals(from)) {
            return;
        }
        if (p == null) {
            p = new ChunkLedger.Projector();
            ledger.projectors.put(pos, p);
        } else if (!p.totem.equals(from)) {
            p.chunks.clear();
        }
        p.totem = from;
        p.on = true;
        ChunkLedger.Totem source = ledger.totems.get(from);
        if (source != null) {
            tidy(ledger, source.owner); // gives the projector its own chunk if the totem has one left
        }
        ledger.setDirty();
        apply(level.getServer(), null);
    }

    /** Called when a projector is broken. */
    public static void projectorRemoved(ServerLevel level, BlockPos blockPos) {
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        if (ledger.projectors.remove(GlobalPos.of(level.dimension(), blockPos)) != null) {
            ledger.setDirty();
            apply(level.getServer(), null);
        }
    }

    // ------------------------------------------------------------------ the screens

    private static long[] longs(Set<Long> set) {
        return set.stream().mapToLong(Long::longValue).toArray();
    }

    /** Sends {@code player} what the chunk picker at {@code blockPos} (a totem or a projector) should show. */
    public static void sendView(ServerPlayer player, BlockPos blockPos, boolean open) {
        ServerLevel level = player.serverLevel();
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), blockPos);
        BlockEntity be = level.getBlockEntity(blockPos);
        boolean isProjector = be instanceof TotemProjectorBlockEntity;
        ChunkLedger.Projector projector = ledger.projectors.get(pos);
        GlobalPos totemPos = isProjector ? (projector == null ? null : projector.totem) : pos;
        ChunkLedger.Totem totem = totemPos == null ? null : ledger.totems.get(totemPos);
        Set<Long> mine = isProjector ? (projector == null ? Set.of() : projector.chunks) : (totem == null ? Set.of() : totem.chunks);
        Set<Long> others = new HashSet<>();
        int slots = 0, used = 0, radius = 1, flags = open ? ChunkViewPacket.OPEN : 0;
        if (totem != null) {
            slots = slots(ledger, totemPos, totem);
            used = used(ledger, totemPos, totem);
            radius = radius(ledger, totemPos, totem);
            others.addAll(totem.chunks);
            projectorsOf(ledger, totemPos).forEach(p -> others.addAll(p.chunks));
            others.removeAll(mine);
            if (isMain(ledger, totemPos, totem)) {
                flags |= ChunkViewPacket.MAIN;
            }
            if (mayEdit(player, totemPos, totem)) {
                flags |= ChunkViewPacket.EDIT;
            }
        }
        if (enabled()) {
            flags |= ChunkViewPacket.ENABLED;
        }
        if (BSPConfig.getOr(BSPConfig.CHUNKS_OWNER_ONLINE, false)) {
            flags |= ChunkViewPacket.ONLINE_ONLY;
        }
        int delivered = 0, chosen = 0, channels = 0, repeaters = 0, tank = 0;
        int[] levels = new int[TotemProjectorBlockEntity.SENDABLE.length], offered = new int[TotemProjectorBlockEntity.SENDABLE.length];
        if (be instanceof TotemProjectorBlockEntity p) {
            delivered = p.delivered();
            chosen = p.chosen();
            channels = p.channels();
            repeaters = p.repeaters();
            tank = p.tank();
            for (int i = 0; i < levels.length; i++) {
                offered[i] = p.offered(i);
                levels[i] = p.hasSignal() ? p.arriving(i) : 0;
            }
            flags |= (p.hasSignal() ? ChunkViewPacket.SIGNAL : 0) | (p.isActive() ? ChunkViewPacket.ACTIVE : 0) | (projector != null && projector.on ? ChunkViewPacket.LOADING : 0)
                    | (p.hasExpander() ? ChunkViewPacket.EXPANDER : 0) | (p.mayEdit(player) ? ChunkViewPacket.EDIT : 0);
        }
        BSPNetwork.sendTo(player, new ChunkViewPacket(blockPos, isProjector, slots, used, radius, longs(mine), longs(others), flags, delivered, levels, offered, chosen, channels, repeaters, tank));
    }

    /** The RECEIVE switch for {@link TotemProjectorBlockEntity#SENDABLE}[index] on the projector at {@code blockPos}. */
    public static void receive(ServerPlayer player, BlockPos blockPos, int index) {
        if (player.level().getBlockEntity(blockPos) instanceof TotemProjectorBlockEntity p) {
            if (!p.mayEdit(player)) {
                refuse(player, "not_owner");
                return;
            }
            p.toggle(index);
            sendView(player, blockPos, false);
        }
    }

    /** The owner, an admin, or a friend the totem's access list lets upgrade (when the totem is loaded to ask). */
    private static boolean mayEdit(ServerPlayer player, GlobalPos totemPos, ChunkLedger.Totem totem) {
        if (totem.owner.equals(player.getUUID()) || Admins.isAdmin(player)) {
            return true;
        }
        ServerLevel level = player.server.getLevel(totemPos.dimension());
        return level != null && level.isLoaded(totemPos.pos()) && level.getBlockEntity(totemPos.pos()) instanceof ShatterTotemBlockEntity be && be.mayUpgrade(player);
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.bsp_core.chunks." + key).withStyle(ChatFormatting.RED), true);
    }

    /** A click on a chunk in the picker at {@code blockPos}: load it if it is free and allowed, unload it if it was picked here. */
    public static void toggle(ServerPlayer player, BlockPos blockPos, int cx, int cz) {
        ServerLevel level = player.serverLevel();
        ChunkLedger ledger = ChunkLedger.get(level.getServer());
        GlobalPos pos = GlobalPos.of(level.dimension(), blockPos);
        boolean isProjector = level.getBlockEntity(blockPos) instanceof TotemProjectorBlockEntity;
        ChunkLedger.Projector projector = isProjector ? ledger.projectors.get(pos) : null;
        GlobalPos totemPos = isProjector ? (projector == null ? null : projector.totem) : pos;
        ChunkLedger.Totem totem = totemPos == null ? null : ledger.totems.get(totemPos);
        if (totem == null || !enabled() || (isProjector && !projector.on)) {
            refuse(player, "unavailable");
            return;
        }
        if (!mayEdit(player, totemPos, totem)) {
            refuse(player, "not_owner");
            return;
        }
        Set<Long> mine = isProjector ? projector.chunks : totem.chunks;
        long chunk = ChunkPos.asLong(cx, cz), home = chunkOf(blockPos);
        if (mine.contains(chunk)) {
            if (chunk == home) {
                refuse(player, isProjector ? "projector_home" : "home");
                return;
            }
            mine.remove(chunk);
        } else {
            if (!within(chunk, home, radius(ledger, totemPos, totem))) {
                refuse(player, "range");
                return;
            }
            if (totem.chunks.contains(chunk) || projectorsOf(ledger, totemPos).stream().anyMatch(p -> p.chunks.contains(chunk))) {
                refuse(player, "already");
                return;
            }
            if (used(ledger, totemPos, totem) >= slots(ledger, totemPos, totem)) {
                refuse(player, "full");
                return;
            }
            mine.add(chunk);
        }
        tidy(ledger, totem.owner);
        apply(level.getServer(), null);
        sendView(player, blockPos, false);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        APPLIED.clear();
        prune(event.getServer());
        refresh(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        APPLIED.clear();
        FED.clear();
    }

    /** Every five seconds: entries for totems no longer placed are dropped, and projectors that no generator has fed for a while stop holding their chunks. */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || now % 100 != 0) {
            return;
        }
        prune(server); // a totem ledger check or a wipe may have forgotten a totem since
        ChunkLedger ledger = ChunkLedger.get(server);
        long limit = (BSPConfig.getOr(BSPConfig.CABLE_CHECK_SECONDS, 30) + 15) * 20L;
        boolean changed = false;
        for (Map.Entry<GlobalPos, ChunkLedger.Projector> e : ledger.projectors.entrySet()) {
            long fed = FED.computeIfAbsent(e.getKey(), k -> now);
            if (e.getValue().on && (now - fed > limit || now < fed)) {
                e.getValue().on = false;
                changed = true;
            }
        }
        if (changed) {
            ledger.setDirty();
            apply(server, null);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && BSPConfig.getOr(BSPConfig.CHUNKS_OWNER_ONLINE, false)) {
            apply(player.server, null);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && BSPConfig.getOr(BSPConfig.CHUNKS_OWNER_ONLINE, false)) {
            apply(player.server, player.getUUID());
        }
    }
}
