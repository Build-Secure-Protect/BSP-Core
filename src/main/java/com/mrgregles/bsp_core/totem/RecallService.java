package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.RecallOfferPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recall: when a totem with the Recall upgrade is being stolen, its owner is offered a teleport to
 * within the level's distance of it. The offer waits out the thief's Shroud and Recall Block, lasts
 * {@code effects.recallPromptSeconds}, is not made when the owner is already that close, and rests
 * {@code effects.recallCooldownMinutes} after a use.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class RecallService {
    private record Offer(ServerLevel level, BlockPos totem, int radius, long expiresAt) {
    }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    /** Owners offered a Recall while on another server: the offer waits here for their arrival. */
    private static final java.util.Set<UUID> REMOTE = new java.util.HashSet<>();
    /** Arrivals to finish: the owner logged in here with a remote offer open; the teleport runs once they are fully in. */
    private static final Map<UUID, Long> ARRIVING = new HashMap<>();
    private static final String TAG_READY = "RecallReadyAt";
    /** Extra seconds a remote offer stays open on top of the prompt time, for the server transfer. */
    private static final int REMOTE_EXTRA_SECONDS = 90;

    private RecallService() {}

    public static int radius(int level) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.RECALL_RADIUS, List.<Integer>of()), level, 150);
    }

    /** Makes the offer to {@code owner} for the totem at {@code pos} if they are online, far enough away and not resting. */
    public static void offer(ServerLevel level, BlockPos pos, UUID owner, int recallLevel) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (recallLevel <= 0) {
            return;
        }
        if (player == null) {
            offerRemote(level, pos, owner, recallLevel);
            return;
        }
        int r = radius(recallLevel);
        long now = System.currentTimeMillis(), ready = PlayerPersistent.get(player).getLong(TAG_READY);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.bsp_core.recall.resting", (ready - now) / 60_000 + 1).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (player.level() == level && player.blockPosition().distSqr(pos) <= (double) r * r) {
            return; // already close enough
        }
        int seconds = BSPConfig.getOr(BSPConfig.RECALL_PROMPT_SECONDS, 20);
        OFFERS.put(owner, new Offer(level, pos.immutable(), r, level.getGameTime() + seconds * 20L));
        double dist = player.level() == level ? Math.sqrt(player.blockPosition().distSqr(pos)) : -1;
        BSPNetwork.sendTo(player, new RecallOfferPacket(pos, level.dimension().location().toString(), seconds, (int) dist, r));
    }

    /**
     * The owner is not on this server: the offer waits here, and finishes if they arrive before it runs out. Nothing is sent
     * to them on the other server: the Recall popup is only for an owner on the totem's own server (they still get the steal
     * warning wherever they are). {@code /bsp recall <server>} stays a staff command.
     */
    private static void offerRemote(ServerLevel level, BlockPos pos, UUID owner, int recallLevel) {
        int seconds = BSPConfig.getOr(BSPConfig.RECALL_PROMPT_SECONDS, 20) + REMOTE_EXTRA_SECONDS;
        OFFERS.put(owner, new Offer(level, pos.immutable(), radius(recallLevel), level.getGameTime() + seconds * 20L));
        REMOTE.add(owner);
        BSPCore.LOGGER.info("Recall waits for {} (on another server or offline) for the totem at {}", owner, pos);
    }

    public static boolean hasRemoteOffer(UUID owner) {
        return REMOTE.contains(owner) && OFFERS.containsKey(owner);
    }

    /** The owner clicked the remote offer on the server they are on: a teleport mod is asked to take them to {@code targetServer}. */
    public static boolean acceptRemote(net.minecraft.world.entity.player.Player player, String targetServer) {
        var event = new com.mrgregles.bsp_core.api.event.RecallEvent.CrossServer(player, targetServer);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        player.displayClientMessage(Component.translatable(event.isHandled() ? "message.bsp_core.recall.remote_going" : "message.bsp_core.recall.remote_none", targetServer)
                .withStyle(event.isHandled() ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return event.isHandled();
    }

    /** An owner with a remote offer arrived on this server: finish the Recall once they are fully in. */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        UUID id = event.getEntity().getUUID();
        if (REMOTE.remove(id) && OFFERS.containsKey(id)) {
            ARRIVING.put(id, event.getEntity().level().getGameTime() + 40);
        }
    }

    /** The owner pressed accept (or decline). */
    public static void answer(ServerPlayer player, boolean accept) {
        Offer offer = OFFERS.remove(player.getUUID());
        if (offer == null) {
            return;
        }
        if (!accept) {
            BSPNetwork.sendTo(player, RecallOfferPacket.clear());
            return;
        }
        if (offer.level.getGameTime() > offer.expiresAt) {
            player.displayClientMessage(Component.translatable("message.bsp_core.recall.expired").withStyle(ChatFormatting.RED), true);
            BSPNetwork.sendTo(player, RecallOfferPacket.clear());
            return;
        }
        // land at the level's distance from the totem, on the side the owner is coming from (or any side from another dimension), on the surface, facing it
        Vec3 from = player.level() == offer.level ? player.position() : new Vec3(offer.totem.getX() + 1, 0, offer.totem.getZ());
        double dx = from.x - (offer.totem.getX() + 0.5), dz = from.z - (offer.totem.getZ() + 0.5), len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        int tx = (int) Math.round(offer.totem.getX() + 0.5 + dx / len * (offer.radius - 1)), tz = (int) Math.round(offer.totem.getZ() + 0.5 + dz / len * (offer.radius - 1));
        int ty = offer.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
        float yaw = (float) Math.toDegrees(Math.atan2(-(dx / len), dz / len)) + 180f;
        player.teleportTo(offer.level, tx + 0.5, ty, tz + 0.5, yaw, 10f);
        player.fallDistance = 0;
        PlayerPersistent.get(player).putLong(TAG_READY, System.currentTimeMillis() + BSPConfig.getOr(BSPConfig.RECALL_COOLDOWN_MINUTES, 10) * 60_000L);
        player.displayClientMessage(Component.translatable("message.bsp_core.recall.done", offer.radius).withStyle(ChatFormatting.AQUA), true);
        BSPNetwork.sendTo(player, RecallOfferPacket.clear());
        BSPCore.LOGGER.info("{} recalled to {} near the totem at {}", player.getGameProfile().getName(), new BlockPos(tx, ty, tz), offer.totem);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (!ARRIVING.isEmpty()) {
            long now = event.getServer().overworld().getGameTime();
            for (Map.Entry<UUID, Long> e : new java.util.ArrayList<>(ARRIVING.entrySet())) {
                if (now < e.getValue()) {
                    continue;
                }
                ARRIVING.remove(e.getKey());
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (player != null) {
                    answer(player, true);
                }
            }
        }
        if (event.getServer().getTickCount() % 20 == 0) {
            OFFERS.entrySet().removeIf(e -> e.getValue().level.getGameTime() > e.getValue().expiresAt);
            REMOTE.removeIf(id -> !OFFERS.containsKey(id));
        }
    }
}
