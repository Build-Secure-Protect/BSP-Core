package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ScoreSyncPacket;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The leaderboard. A player's score is the sum of their totems' tier points ("Weighted Points":
 * Tier I to V are worth {@code scoring.tierPoints}, 1, 2, 4, 7 and 10 by default), so both taking
 * totems and upgrading them count. Ties go to the player with more totems, then the higher tier.
 *
 * <p>A totem counts for whoever owns it while placed, and for whoever carries it while in an
 * inventory. Placed totems come from the {@link TotemLedger}, so they count while their chunk is
 * unloaded and their owner is offline; carried totems come from the players who are online (a
 * player who logs out has their carried totems placed, so nothing is lost).
 *
 * <p>On a single server the board is this server's own totems. With network storage on, each
 * server publishes its rows to the database and reads back the combined board. The board is
 * rebuilt every {@code scoring.refreshSeconds}, and sooner when a totem changes, and sent to all
 * clients for the Score Screens.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ScoreService {
    /** One player's line on the board. */
    public record Entry(UUID id, String name, int totems, int points, int bestTier) {
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::points).reversed()
            .thenComparing(Comparator.comparingInt(Entry::totems).reversed())
            .thenComparing(Comparator.comparingInt(Entry::bestTier).reversed())
            .thenComparing(Entry::name);
    private static final int SENT = 100;

    private static volatile List<Entry> board = List.of();
    private static boolean dirty = true;
    private static int ticks;

    private ScoreService() {}

    /** The current board, best first. */
    public static List<Entry> board() {
        return board;
    }

    /** Points a totem of {@code tier} (0 = I) is worth. */
    public static int tierPoints(int tier) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SCORE_TIER_POINTS, List.<Integer>of()), tier + 1, tier + 1);
    }

    /** Asks for a rebuild on the next tick: a totem was placed, taken, raised or changed hands. */
    public static void markDirty() {
        dirty = true;
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? id.toString().substring(0, 8)
                : server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    /** This server's own rows: placed totems from the ledger plus totems carried by online players. */
    public static List<Entry> local(MinecraftServer server) {
        Map<UUID, int[]> acc = new HashMap<>(); // totems, points, best tier
        TotemLedger ledger = TotemLedger.get(server);
        ledger.allPlaced().forEach((owner, positions) -> {
            for (GlobalPos pos : positions) {
                add(acc, owner, ledger.tierAt(pos));
            }
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (TotemInventories.isTotem(stack)) {
                    add(acc, player.getUUID(), TotemUpgrades.getTier(stack));
                }
            }
        }
        List<Entry> out = new ArrayList<>();
        acc.forEach((id, v) -> out.add(new Entry(id, name(server, id), v[0], v[1], v[2])));
        out.sort(ORDER);
        return out;
    }

    private static void add(Map<UUID, int[]> acc, UUID id, int tier) {
        int[] v = acc.computeIfAbsent(id, k -> new int[]{0, 0, -1});
        v[0]++;
        v[1] += tierPoints(tier);
        v[2] = Math.max(v[2], tier);
    }

    private static void publish(MinecraftServer server, List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(ORDER);
        board = List.copyOf(sorted);
        ScoreSyncPacket packet = new ScoreSyncPacket(sorted.subList(0, Math.min(SENT, sorted.size())));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BSPNetwork.sendTo(player, packet);
        }
    }

    /** Rebuilds the board now and sends it to everyone. */
    public static void refresh(MinecraftServer server) {
        dirty = false;
        ticks = 0;
        List<Entry> mine = local(server);
        if (NetworkStorage.enabled()) {
            NetworkStorage.syncScores(mine, combined -> publish(server, combined)); // answer arrives on the server thread
        } else {
            publish(server, mine);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        int every = Math.max(5, BSPConfig.getOr(BSPConfig.SCORE_REFRESH_SECONDS, 30)) * 20;
        // a change is picked up within a second on a single server; a network waits for its timer so the database is not hammered
        if (ticks >= every || (dirty && ticks >= 20 && !NetworkStorage.enabled())) {
            refresh(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            List<Entry> now = board;
            BSPNetwork.sendTo(player, new ScoreSyncPacket(now.subList(0, Math.min(SENT, now.size()))));
            markDirty();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        markDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        board = List.of();
        dirty = true;
        ticks = 0;
    }
}
