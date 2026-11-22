package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.demo.DemoTotemBlockEntity;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LookDataPacket;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the LOOKS tab asks of the server, checked here: put a look on a totem or take it off, keep a look in the saved list or drop
 * one, put a saved look on, pick the hat for undressed totems, or just send the tab its data. Putting a look on and saving one wait
 * {@link #COOLDOWN_MS} between tries, and both are logged, so paintings can be traced to who made them. At a Demo Totem only admins
 * change the look ({@link TotemLooks#demoRefusal}), with no wait between looks; everyone may ask for its data.
 */
public final class LookActions {
    private LookActions() {}

    public enum Action { REQUEST, PUT, TAKE_OFF, SAVE, DELETE, PUT_SAVED, CHOOSE_HAT }

    /**
     * The {@code slot} a Demo Totem's tab sends with CHOOSE_HAT (the hat on its plain body). The server then never takes it for the
     * player's own hat choice, even when the demo is out of reach or gone.
     */
    public static final int DEMO_SLOT = 1;

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
        BlockEntity at = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 && player.level().isLoaded(pos)
                ? player.level().getBlockEntity(pos) : null;
        ShatterTotemBlockEntity totem = at instanceof ShatterTotemBlockEntity t ? t : null;
        DemoTotemBlockEntity demo = at instanceof DemoTotemBlockEntity d ? d : null;
        if (needsTotem && totem == null && demo == null) {
            return "too_far";
        }
        // an admin dressing a demo: no 10 s wait between looks, and none started for their own totems
        boolean demoAdmin = demo != null && com.mrgregles.bsp_core.demo.DemoTotems.isAdmin(player);
        // a hat for a demo (its tab says so, or a demo stands there, near or not) is never the player's own hat choice
        boolean demoHat = action == Action.CHOOSE_HAT && (slot == DEMO_SLOT || demo != null
                || player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof DemoTotemBlockEntity);
        if (demoHat && demo == null) {
            return "too_far";
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
        if (writes && !demoAdmin) {
            long now = System.currentTimeMillis();
            Long last = lastWrite.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                return "too_soon";
            }
        }
        boolean hadDemoDress = demo != null && (demo.getLook() != null || !demo.getPlainHat().isEmpty());
        TotemLook look = null;
        if (action == Action.PUT || action == Action.SAVE) {
            look = TotemLook.load(lookTag);
            if (look == null) {
                return "bad_look";
            }
        }
        String why = switch (action) {
            case REQUEST -> null;
            case PUT -> demo != null ? TotemLooks.applyDemo(player, demo, look) : TotemLooks.apply(player, totem, look);
            case TAKE_OFF -> demo != null ? demoAdmin ? takeOff(demo) : "demo_admins" : totem.isOwner(player.getUUID()) ? takeOff(totem) : "not_owner";
            case SAVE -> owners.saveLook(player, slot, look, demoAdmin);
            case DELETE -> {
                owners.deleteLook(player, slot);
                yield null;
            }
            case PUT_SAVED -> {
                LookOwner r = owners.record(player.getUUID());
                yield r == null || slot < 0 || slot >= r.saved.size() || owners.cleared(r.saved.get(slot)) ? "no_saved"
                        : demo != null ? TotemLooks.applyDemo(player, demo, r.saved.get(slot)) : TotemLooks.apply(player, totem, r.saved.get(slot));
            }
            case CHOOSE_HAT -> demoHat ? demoAdmin ? plainHat(player, demo, pos, hat) : "demo_admins" : owners.chooseHat(player, hat);
        };
        if (why == null && writes) {
            if (!demoAdmin) {
                lastWrite.put(player.getUUID(), System.currentTimeMillis());
            }
            TotemLook done = action == Action.SAVE ? look : demo != null ? demo.getLook() : totem.getLook();
            BSPCore.LOGGER.info("{} {} a {} look{}: {}", player.getGameProfile().getName(), action == Action.SAVE ? "saved" : "put on",
                    demo != null ? "demo totem" : "totem", action == Action.SAVE ? " in slot " + slot : " at " + pos.toShortString(), done);
        } else if (why == null && action == Action.TAKE_OFF && demo != null && hadDemoDress) {
            BSPCore.LOGGER.info("{} took the look or hat off a demo totem at {}", player.getGameProfile().getName(), pos.toShortString());
        }
        return why;
    }

    /** A demo's plain baked body with a hat (or none: {@link LookOwner#NO_HAT} or ""); any look comes off. Logged. */
    @Nullable
    private static String plainHat(ServerPlayer player, DemoTotemBlockEntity demo, BlockPos pos, String hat) {
        String h = LookOwner.NO_HAT.equals(hat) ? "" : hat;
        if (!h.isEmpty() && !TotemHats.known(h)) {
            return "unknown_hat";
        }
        if (demo.getLook() == null && demo.getPlainHat().equals(h)) {
            return null; // nothing changed: nothing sent, nothing logged
        }
        demo.setPlain(h);
        BSPCore.LOGGER.info("{} put a demo totem at {} on its plain body, {}", player.getGameProfile().getName(), pos.toShortString(),
                h.isEmpty() ? "with no hat" : "wearing " + h);
        return null;
    }

    private static String takeOff(DemoTotemBlockEntity demo) {
        if (demo.getLook() != null || !demo.getPlainHat().isEmpty()) {
            demo.setPlain(""); // plain gold, no hat
        }
        return null;
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
        BlockEntity at = player.level().isLoaded(pos) ? player.level().getBlockEntity(pos) : null;
        TotemLook own = at instanceof ShatterTotemBlockEntity t && t.isOwner(player.getUUID()) ? t.getLook() : null;
        Long last = lastWrite.get(player.getUUID());
        long wait = last == null ? 0 : Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - last));
        if (at instanceof DemoTotemBlockEntity demo) {
            // a demo's look is for everyone to see; an admin gets dressing and every hat for it, and no wait
            own = demo.getLook();
            if (com.mrgregles.bsp_core.demo.DemoTotems.isAdmin(player)) {
                r = TotemLooks.demoRecord(r, demo.getPlainHat()); // the block's own update may come after this
                wait = 0;
            } else {
                r = r == null ? new LookOwner() : r.copy(); // anyone else: their own record, with the demo's hat (never stored)
                r.hatChoice = demo.getPlainHat();
            }
        }
        BSPNetwork.sendTo(player, new LookDataPacket(pos, r == null ? new LookOwner().save() : r.save(), own == null ? null : own.save(), wait));
    }
}
