package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.network.TrackerActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carries out the machine tracking actions a client asks for ({@link TrackerActionPacket}). Every action checks again on the server;
 * a refusal shows in the action bar. Layout saves are limited to {@link #LAYOUT_BURST} per {@link #LAYOUT_WINDOW_MS}, so a client cannot
 * flood the network database (the Holo Grid sends one when a drag or a choice is finished, not while dragging).
 */
public final class TrackerActions {
    public static final int LAYOUT_BURST = 20;
    public static final long LAYOUT_WINDOW_MS = 10_000L;
    private static final Map<UUID, long[]> SAVES = new HashMap<>();

    private TrackerActions() {}

    /** Every action from a client counts here: at most {@link #ACTION_BURST} in {@link #ACTION_WINDOW_MS}. */
    public static final int ACTION_BURST = 30;
    public static final long ACTION_WINDOW_MS = 5_000L;
    private static final Map<UUID, long[]> ACTIONS = new HashMap<>();

    /** Whether the player may make another action now (also counts it). */
    static boolean allow(Map<UUID, long[]> counts, UUID id, int burst, long window) {
        long now = System.currentTimeMillis();
        long[] w = counts.computeIfAbsent(id, k -> new long[]{now, 0});
        if (now - w[0] > window) {
            w[0] = now;
            w[1] = 0;
        }
        return ++w[1] <= burst;
    }

    public static void handle(ServerPlayer player, TrackerActionPacket msg) {
        if (!allow(ACTIONS, player.getUUID(), ACTION_BURST, ACTION_WINDOW_MS)) {
            return; // a client sending this fast is not a person clicking
        }
        String refusal = switch (msg.action()) {
            case TrackerActionPacket.RENAME -> TrackerRules.rename(player, msg.id(), msg.text());
            case TrackerActionPacket.UNTAG -> TrackerRules.untag(player, msg.id());
            case TrackerActionPacket.BUY_SLOT -> TrackerRules.buySlot(player);
            case TrackerActionPacket.UNLOCK_REMOTE -> TrackerRules.unlockRemote(player);
            case TrackerActionPacket.LAYOUT -> saveLayout(player, msg.layout()) ? null : "layout_busy";
            case TrackerActionPacket.OPERATE -> RemoteOperate.open(player, msg.id());
            default -> null;
        };
        if (refusal != null) {
            player.displayClientMessage(Component.translatable("message.bsp_core.tracking." + refusal).withStyle(ChatFormatting.RED), true);
            TrackerSync.send(player); // the screen shows what is true again
        }
    }

    /** Stores the player's Holo Grid layout, cut to {@link GridLayout}'s rules. False when they saved too often just now. */
    public static boolean saveLayout(ServerPlayer player, net.minecraft.nbt.CompoundTag layout) {
        if (!allow(SAVES, player.getUUID(), LAYOUT_BURST, LAYOUT_WINDOW_MS) || !TrackerOwners.ready(player.getUUID())) {
            return false;
        }
        TrackerOwners owners = TrackerOwners.get(player.server);
        TrackerRecord r = owners.record(player.server, player.getUUID());
        net.minecraft.nbt.CompoundTag clean = GridLayout.clean(layout);
        if (clean.equals(r.layout)) {
            return true;
        }
        r.layout = clean;
        owners.put(player.server, player.getUUID(), r);
        return true;
    }

    /** Forgets a player's counts when they leave. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
    public static final class Leave {
        private Leave() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onTrackerActionsLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
            SAVES.remove(event.getEntity().getUUID());
            ACTIONS.remove(event.getEntity().getUUID());
            WatchService.forget(event.getEntity().getUUID());
        }
    }
}
