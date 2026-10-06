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
    private static final String TAG_READY = "RecallReadyAt";

    private RecallService() {}

    public static int radius(int level) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.RECALL_RADIUS, List.<Integer>of()), level, 150);
    }

    /** Makes the offer to {@code owner} for the totem at {@code pos} if they are online, far enough away and not resting. */
    public static void offer(ServerLevel level, BlockPos pos, UUID owner, int recallLevel) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || recallLevel <= 0) {
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
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            OFFERS.entrySet().removeIf(e -> e.getValue().level.getGameTime() > e.getValue().expiresAt);
        }
    }
}
