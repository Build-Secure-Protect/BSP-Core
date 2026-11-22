package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LookDataPacket;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the LOOKS tab asks of the server, checked here: put a look on a totem or take it off, keep a look in the saved list or drop
 * one, put a saved look on, pick the hat for undressed totems, or just send the tab its data. Putting a look on and saving one wait
 * {@link #COOLDOWN_MS} between tries, and both are logged, so paintings can be traced to who made them.
 */
public final class LookActions {
    private LookActions() {}

    public enum Action { REQUEST, PUT, TAKE_OFF, SAVE, DELETE, PUT_SAVED, CHOOSE_HAT }

    /** Milliseconds a player waits between putting looks on or saving them. */
    public static final long COOLDOWN_MS = 10_000;
    private static final Map<UUID, Long> lastWrite = new HashMap<>();
    /** Milliseconds between any two requests (hat picks, deletes, taking off), so a client cannot flood the network with them. */
    public static long gapMs = 250;
    private static final Map<UUID, Long> lastAction = new HashMap<>();

    /** Test hook: forget every player's wait. */
    public static void resetCooldowns() {
        lastWrite.clear();
        lastAction.clear();
    }

    /** A player leaving: forget their waits. */
    public static void forget(UUID player) {
        lastWrite.remove(player);
        lastAction.remove(player);
    }

    /**
     * Does what the player asked at the totem at {@code pos} and returns the refusal ({@code message.bsp_core.looks.<reason>}), or null.
     * The player must be within reach of an existing totem for the actions that touch one.
     */
    @Nullable
    public static String perform(ServerPlayer player, BlockPos pos, Action action, int slot, String hat, @Nullable CompoundTag lookTag) {
        boolean needsTotem = action == Action.PUT || action == Action.TAKE_OFF || action == Action.PUT_SAVED || action == Action.REQUEST;
        ShatterTotemBlockEntity totem = null;
        if (needsTotem) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 || !player.level().isLoaded(pos)
                    || !(player.level().getBlockEntity(pos) instanceof ShatterTotemBlockEntity t)) {
                return "too_far";
            }
            totem = t;
        }
        LookOwners owners = LookOwners.get(player.server);
        if (action != Action.REQUEST) {
            long now = System.currentTimeMillis();
            Long lastAny = lastAction.get(player.getUUID());
            if (lastAny != null && now - lastAny >= 0 && now - lastAny < gapMs) {
                return "too_soon_short";
            }
            lastAction.put(player.getUUID(), now);
        }
        boolean writes = action == Action.PUT || action == Action.SAVE || action == Action.PUT_SAVED;
        if (writes) {
            long now = System.currentTimeMillis();
            Long last = lastWrite.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                return "too_soon";
            }
        }
        TotemLook look = null;
        if (action == Action.PUT || action == Action.SAVE) {
            look = TotemLook.load(lookTag);
            if (look == null) {
                return "bad_look";
            }
        }
        String why = switch (action) {
            case REQUEST -> null;
            case PUT -> TotemLooks.apply(player, totem, look);
            case TAKE_OFF -> totem.isOwner(player.getUUID()) ? takeOff(totem) : "not_owner";
            case SAVE -> owners.saveLook(player, slot, look);
            case DELETE -> {
                owners.deleteLook(player, slot);
                yield null;
            }
            case PUT_SAVED -> {
                LookOwner r = owners.record(player.getUUID());
                yield r == null || slot < 0 || slot >= r.saved.size() || owners.cleared(r.saved.get(slot)) ? "no_saved" : TotemLooks.apply(player, totem, r.saved.get(slot));
            }
            case CHOOSE_HAT -> owners.chooseHat(player, hat);
        };
        if (why == null && writes) {
            lastWrite.put(player.getUUID(), System.currentTimeMillis());
            TotemLook done = action == Action.SAVE ? look : totem.getLook();
            BSPCore.LOGGER.info("{} {} a totem look{}: {}", player.getGameProfile().getName(), action == Action.SAVE ? "saved" : "put on",
                    action == Action.SAVE ? " in slot " + slot : " at " + pos.toShortString(), done);
        }
        return why;
    }

    private static String takeOff(ShatterTotemBlockEntity totem) {
        if (totem.getLook() != null) { // a plain totem: nothing to change, nothing to send
            totem.setLook(null);
        }
        return null;
    }

    /** Handles a request from the client: does it, says why not when refused, and sends the tab its data again. */
    public static void handle(ServerPlayer player, BlockPos pos, Action action, int slot, String hat, @Nullable CompoundTag lookTag) {
        String why = perform(player, pos, action, slot, hat, lookTag);
        if ("too_soon_short".equals(why)) {
            return; // a double click: no message, and the data follows the first
        }
        if (why != null) {
            player.displayClientMessage(why.equals("too_soon")
                    ? Component.translatable("message.bsp_core.looks.too_soon", COOLDOWN_MS / 1000)
                    : Component.translatable("message.bsp_core.looks." + why), true);
        }
        sendData(player, pos);
    }

    /** Sends the player the LOOKS tab's data: their record and, when the totem at {@code pos} is theirs, its own look. */
    public static void sendData(ServerPlayer player, BlockPos pos) {
        LookOwners owners = LookOwners.get(player.server);
        owners.check(player);
        LookOwner r = owners.record(player.getUUID());
        TotemLook own = player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof ShatterTotemBlockEntity t && t.isOwner(player.getUUID()) ? t.getLook() : null;
        Long last = lastWrite.get(player.getUUID());
        long wait = last == null ? 0 : Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - last));
        BSPNetwork.sendTo(player, new LookDataPacket(pos, r == null ? new LookOwner().save() : r.save(), own == null ? null : own.save(), wait));
    }
}
