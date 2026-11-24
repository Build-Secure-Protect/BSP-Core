package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of tagging machines and buying tag slots (owner decisions MT7, MT17, MT18, MT20, MT27 and MT30 in the vault's Machine
 * Tracking brief). Every action runs on the server thread and answers with a refusal key ({@code message.bsp_core.tracking.<key>}), or
 * null when it was done.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TrackerRules {
    /** Places where a tagged machine may have just been broken or blown up, checked at the end of the tick. */
    private static final List<Gone> GONE = new ArrayList<>();

    private record Gone(ServerLevel level, BlockPos pos) {
    }

    private TrackerRules() {}

    // ------------------------------------------------------------------ slots and prices

    public static int freeSlots() {
        return BSPConfig.getOr(BSPConfig.TRACKING_FREE_SLOTS, 2);
    }

    public static int maxSlots() {
        return Math.max(freeSlots(), BSPConfig.getOr(BSPConfig.TRACKING_MAX_SLOTS, 20));
    }

    /** How many machines the player may have tagged this season: the free slots plus those bought this season, up to the most. */
    public static int slots(TrackerRecord r, int season) {
        return Math.min(maxSlots(), freeSlots() + r.boughtIn(season));
    }

    /** Copper Shatter Coins for the next slot, or -1 when every slot is bought. Each slot costs one step more than the one before. */
    public static int nextPrice(TrackerRecord r, int season) {
        if (slots(r, season) >= maxSlots()) {
            return -1;
        }
        return BSPConfig.getOr(BSPConfig.TRACKING_SLOT_PRICE_STEP, 8) * (r.boughtIn(season) + 1);
    }

    public static int remotePrice() {
        return BSPConfig.getOr(BSPConfig.TRACKING_REMOTE_DIAMONDS, 4);
    }

    /** The {@code storage.serverId} tags made here carry ("" without one). */
    public static String serverId() {
        return BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "");
    }

    /** Buys the next tag slot with Copper Shatter Coins from the player's inventory. */
    @Nullable
    public static String buySlot(ServerPlayer player) {
        MinecraftServer server = player.server;
        int season = TrackerOwners.season(server);
        TrackerOwners owners = TrackerOwners.get(server);
        TrackerRecord r = owners.record(server, player.getUUID());
        int price = nextPrice(r, season);
        if (price < 0) {
            return "slots_max";
        }
        if (!TrackerOwners.ready(player.getUUID())) {
            return "loading";
        }
        if (!CoinWallet.take(player, CoinTier.COPPER, price)) {
            return "no_copper";
        }
        r.addPurchase(season, System.currentTimeMillis());
        owners.put(server, player.getUUID(), r);
        BSPCore.LOGGER.info("{} bought machine tag slot {} for {} Copper Shatter Coins", player.getGameProfile().getName(), slots(r, season), price);
        return null;
    }

    /** Buys the Remote Control unlock with Diamond Shatter Coins from the player's inventory. */
    @Nullable
    public static String unlockRemote(ServerPlayer player) {
        MinecraftServer server = player.server;
        int season = TrackerOwners.season(server);
        TrackerOwners owners = TrackerOwners.get(server);
        TrackerRecord r = owners.record(server, player.getUUID());
        if (r.remoteIn(season)) {
            return "remote_owned";
        }
        if (!TrackerOwners.ready(player.getUUID())) {
            return "loading";
        }
        if (!CoinWallet.take(player, CoinTier.DIAMOND, remotePrice())) {
            return "no_diamond";
        }
        r.remote = true;
        r.remoteSeason = season;
        r.remoteMs = System.currentTimeMillis();
        owners.put(server, player.getUUID(), r);
        BSPCore.LOGGER.info("{} bought the Remote Control unlock for {} Diamond Shatter Coins", player.getGameProfile().getName(), remotePrice());
        return null;
    }

    // ------------------------------------------------------------------ tags

    /** What a tag attempt gave: a refusal key, or the new tag. */
    public record TagResult(@Nullable String refusal, @Nullable TrackedTag tag) {
        static TagResult no(String key) {
            return new TagResult(key, null);
        }
    }

    /** Tags the machine at {@code pos} for the player. */
    public static TagResult tag(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!TrackerAccess.isMachine(level, pos)) {
            return TagResult.no("not_machine");
        }
        if (!TrackerAccess.mayUse(level, pos, player)) {
            return TagResult.no("no_access");
        }
        if (!TrackerOwners.ready(player.getUUID())) {
            return TagResult.no("loading");
        }
        MinecraftServer server = player.server;
        int season = TrackerOwners.season(server);
        TrackerOwners owners = TrackerOwners.get(server);
        TrackerRecord r = owners.record(server, player.getUUID());
        String here = serverId();
        ResourceLocation dim = level.dimension().location();
        if (r.tags.stream().anyMatch(t -> t.at(here, dim, pos))) {
            return TagResult.no("already_tagged");
        }
        if (r.tags.size() >= slots(r, season)) {
            return TagResult.no("slots_full");
        }
        ResourceLocation block = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        TrackedTag tag = new TrackedTag(newId(r), here, dim, pos.immutable(), "", block == null ? new ResourceLocation("minecraft", "air") : block, season,
                System.currentTimeMillis());
        r.tags.add(tag);
        owners.put(server, player.getUUID(), r);
        return new TagResult(null, tag);
    }

    /** Removes the player's tag on the machine at {@code pos}; false when there was none. */
    public static boolean untagAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String here = serverId();
        ResourceLocation dim = level.dimension().location();
        TrackerRecord r = TrackerOwners.get(player.server).record(player.server, player.getUUID());
        Optional<TrackedTag> t = r.tags.stream().filter(x -> x.at(here, dim, pos)).findFirst();
        return t.isPresent() && untag(player, t.get().id()) == null;
    }

    /** Removes one of the player's tags by its id. */
    @Nullable
    public static String untag(ServerPlayer player, String id) {
        if (!TrackerOwners.ready(player.getUUID())) {
            return "loading";
        }
        TrackerOwners owners = TrackerOwners.get(player.server);
        TrackerRecord r = owners.record(player.server, player.getUUID());
        if (!r.tags.removeIf(t -> t.id().equals(id))) {
            return "no_tag";
        }
        owners.put(player.server, player.getUUID(), r);
        return null;
    }

    /** Names one of the player's tags; an empty name gives the tag back its block's own name. */
    @Nullable
    public static String rename(ServerPlayer player, String id, String typed) {
        if (!TrackerOwners.ready(player.getUUID())) {
            return "loading";
        }
        TrackerOwners owners = TrackerOwners.get(player.server);
        TrackerRecord r = owners.record(player.server, player.getUUID());
        for (int i = 0; i < r.tags.size(); i++) {
            TrackedTag t = r.tags.get(i);
            if (t.id().equals(id)) {
                TrackedTag named = t.renamed(typed);
                if (named.name().equals(t.name())) {
                    return null; // nothing changed: nothing written, nothing sent round the network
                }
                r.tags.set(i, named);
                owners.put(player.server, player.getUUID(), r);
                return null;
            }
        }
        return "no_tag";
    }

    /** A new tag id: 8 hex characters no other tag of the player has. */
    private static String newId(TrackerRecord r) {
        while (true) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            if (r.tags.stream().noneMatch(t -> t.id().equals(id))) {
                return id;
            }
        }
    }

    // ------------------------------------------------------------------ machines that go

    /**
     * The machine at {@code pos} is gone (broken, blown up, or a different block now): every tag on it is removed, and the owner is told
     * when they are online here. Records of players who are not here are read from the network first, so this never writes back an old
     * copy over what they changed elsewhere.
     */
    public static void machineGone(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        String here = serverId();
        ResourceLocation dim = level.dimension().location();
        TrackerOwners owners = TrackerOwners.get(server);
        for (UUID id : owners.ownersAt(here, dim, pos)) {
            Runnable remove = () -> {
                TrackerRecord r = owners.record(server, id);
                List<TrackedTag> gone = r.tags.stream().filter(t -> t.at(here, dim, pos)).toList();
                if (gone.isEmpty()) {
                    return;
                }
                r.tags.removeAll(gone);
                owners.put(server, id, r);
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) {
                    for (TrackedTag t : gone) {
                        online.sendSystemMessage(Component.translatable("message.bsp_core.tracking.tag_gone", displayName(t, level)).withStyle(ChatFormatting.YELLOW));
                    }
                }
            };
            if (server.getPlayerList().getPlayer(id) != null || !NetworkStorage.enabled()) {
                remove.run();
            } else {
                NetworkStorage.trackerRead(server, id, remove);
            }
        }
    }

    /** The tag's name, or the block's own name when the player never named it. */
    public static Component displayName(TrackedTag t, @Nullable ServerLevel level) {
        if (!t.name().isEmpty()) {
            return Component.literal(t.name());
        }
        var block = ForgeRegistries.BLOCKS.getValue(t.block());
        return block == null ? Component.literal(t.block().toString()) : block.getName();
    }

    /** Whether the tag's machine is still the block it was tagged as (checked only where the chunk is loaded). */
    public static boolean stillThere(ServerLevel level, TrackedTag t) {
        if (!level.isLoaded(t.pos())) {
            return true; // unknown until it loads
        }
        ResourceLocation now = ForgeRegistries.BLOCKS.getKey(level.getBlockState(t.pos()).getBlock());
        return t.block().equals(now) && level.getBlockEntity(t.pos()) != null;
    }

    /** Checks at the end of the tick whether the machine at {@code pos} is gone, and removes its tags if so. */
    public static void queueGoneCheck(ServerLevel level, BlockPos pos) {
        if (GONE.size() < 4096) {
            GONE.add(new Gone(level, pos.immutable()));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.getBlockEntity(event.getPos()) != null) {
            queueGoneCheck(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (level.getBlockEntity(pos) != null) {
                    queueGoneCheck(level, pos);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GONE.isEmpty()) {
            return;
        }
        List<Gone> due = new ArrayList<>(GONE);
        GONE.clear();
        String here = serverId();
        for (Gone g : due) {
            if (!g.level().isLoaded(g.pos())) {
                continue;
            }
            TrackerOwners owners = TrackerOwners.get(g.level().getServer());
            ResourceLocation dim = g.level().dimension().location();
            if (owners.ownersAt(here, dim, g.pos()).isEmpty()) {
                continue;
            }
            // still the tagged block (the break was cancelled, say): nothing to do
            boolean anyGone = owners.tagsAt(here, dim, g.pos()).stream().anyMatch(t -> !stillThere(g.level(), t));
            if (anyGone) {
                machineGone(g.level(), g.pos());
            }
        }
    }
}
