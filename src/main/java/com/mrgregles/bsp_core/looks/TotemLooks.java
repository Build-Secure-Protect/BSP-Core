package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Who may dress a totem, and which hats they have. Dressing (the LOOKS tab) takes {@link #DRESS_PERMISSION}, which the VIP rank
 * is given in LuckPerms; it includes every hat except those in {@code looks.exclusiveHats}. A single hat comes with
 * {@link TotemHats#permission}. Operators may do everything. Without LuckPerms only operators may dress totems.
 */
public final class TotemLooks {
    private TotemLooks() {}

    /** The LuckPerms permission for the LOOKS tab: colours, gradients, painting, the owner's head and every hat that is not exclusive. */
    public static final String DRESS_PERMISSION = "bsp_core.looks";

    /** Test hook: when set, answers (player, permission) in place of LuckPerms; null for "not known". */
    @Nullable
    public static BiFunction<UUID, String, Boolean> permissionSource;

    @Nullable
    private static Boolean permission(UUID player, String node) {
        return permissionSource != null ? permissionSource.apply(player, node) : LuckPermsBridge.hasPermission(player, node);
    }

    private static boolean operator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /**
     * Whether the player may dress their totems as far as this server can tell: true or false, or null when it cannot (LuckPerms did
     * not answer, or a networked server has no LuckPerms to ask), so a check here writes nothing the other servers would take as a no.
     */
    @Nullable
    public static Boolean dressingKnown(ServerPlayer player) {
        if (operator(player)) {
            return true;
        }
        if (permissionSource == null && !LuckPermsBridge.isPresent()) {
            return com.mrgregles.bsp_core.storage.NetworkStorage.enabled() ? null : false;
        }
        return permission(player.getUUID(), DRESS_PERMISSION);
    }

    /** Whether the player may dress their totems: an operator, or a holder of {@link #DRESS_PERMISSION}. */
    public static boolean mayDress(ServerPlayer player) {
        return operator(player) || Boolean.TRUE.equals(permission(player.getUUID(), DRESS_PERMISSION));
    }

    /** Hats that dressing does not include: they come only with their own permission (season prizes, the store). */
    public static boolean exclusive(String hat) {
        List<? extends String> list = BSPConfig.getOr(BSPConfig.LOOKS_EXCLUSIVE_HATS, List.<String>of());
        return list.stream().anyMatch(h -> h.trim().equalsIgnoreCase(hat));
    }

    /** Whether the player has this hat: with dressing (unless the hat is exclusive), or with the hat's own permission. */
    public static boolean ownsHat(ServerPlayer player, String hat) {
        if (!TotemHats.known(hat)) {
            return false;
        }
        if (operator(player) || !exclusive(hat) && mayDress(player)) {
            return true;
        }
        return Boolean.TRUE.equals(permission(player.getUUID(), TotemHats.permission(hat)));
    }

    /** Hats the player has by the hat's own permission (bought or given), in the tab's order; dressing's hats are not among them. */
    public static List<String> boughtHats(ServerPlayer player) {
        return TotemHats.IDS.stream().filter(h -> Boolean.TRUE.equals(permission(player.getUUID(), TotemHats.permission(h)))).toList();
    }

    /** Every hat the player has, in the tab's order. */
    public static List<String> hats(ServerPlayer player) {
        return TotemHats.IDS.stream().filter(h -> ownsHat(player, h)).toList();
    }

    /**
     * Why the player may not put this look on this totem, as a message key's last part ({@code message.bsp_core.looks.<reason>}),
     * or null when they may. Only the totem's owner dresses it, with a look made for them, with dressing and the look's hat.
     */
    @Nullable
    public static String refusal(ServerPlayer player, ShatterTotemBlockEntity totem, TotemLook look) {
        if (!totem.isOwner(player.getUUID())) {
            return "not_owner";
        }
        if (!look.owner().equals(player.getUUID())) {
            return "not_yours";
        }
        String problem = look.problem();
        if (problem != null) {
            return problem;
        }
        if (!mayDress(player)) {
            return "no_dressing";
        }
        if (!look.hat().isEmpty() && !ownsHat(player, look.hat())) {
            return "hat_not_owned";
        }
        return null;
    }

    /**
     * Why the player may not put this look on a Demo Totem, or null when they may: any admin, with a sound look made for them and any
     * known hat (exclusive ones too). No VIP rank or hat permission is needed, and no owner is asked.
     */
    @Nullable
    public static String demoRefusal(ServerPlayer player, TotemLook look) {
        if (!com.mrgregles.bsp_core.demo.DemoTotems.isAdmin(player)) {
            return "demo_admins";
        }
        if (!look.owner().equals(player.getUUID())) {
            return "not_yours";
        }
        return look.problem();
    }

    /** Puts the look on the demo when {@link #demoRefusal} allows it; returns the refusal, or null when it was put on. */
    @Nullable
    public static String applyDemo(ServerPlayer player, com.mrgregles.bsp_core.demo.DemoTotemBlockEntity demo, TotemLook look) {
        String why = demoRefusal(player, look);
        if (why == null) {
            demo.setLook(look.madeAt(System.currentTimeMillis()), player.getGameProfile().getName());
        }
        return why;
    }

    /** What the LOOKS tab is told for an admin at a Demo Totem: their own saved looks, with dressing and every hat. Never stored. */
    public static LookOwner demoRecord(@Nullable LookOwner record) {
        return demoRecord(record, "");
    }

    /** As {@link #demoRecord(LookOwner)}, with the demo's plain-body hat in {@code hatChoice} (the tab's draft starts from it). */
    public static LookOwner demoRecord(@Nullable LookOwner record, String plainHat) {
        LookOwner r = record == null ? new LookOwner() : record.copy();
        r.hatChoice = plainHat;
        r.dressing = true;
        r.owned.clear();
        r.owned.addAll(TotemHats.IDS);
        return r;
    }

    /** Puts the look on the totem when {@link #refusal} allows it; returns the refusal, or null when it was put on. */
    @Nullable
    public static String apply(ServerPlayer player, ShatterTotemBlockEntity totem, TotemLook look) {
        String why = refusal(player, totem, look);
        if (why == null) {
            totem.setLook(look.madeAt(System.currentTimeMillis())); // the server's clock, so a staff clear after this time takes it off
        }
        return why;
    }
}
