package com.mrgregles.bsp_core.totem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Every Shatter Totem has a permanent id and a current instance. The server's ledger knows the current instance of each id; a
 * totem that goes missing from its holder (dragged into an ME network, carried off by a hopper) is reissued to them with a new
 * instance, and any copy still carrying the old instance is spent: an empty husk that cannot be placed, upgraded or carried for
 * its powers. So a totem can never be put beyond stealing by storing it away.
 */
public final class TotemIdentity {
    public static final String TAG_ID = "TotemId", TAG_INSTANCE = "Instance", TAG_SPENT = "Spent";
    /** Set on a totem poured in a Totem Forge: it is named "Forged Shatter Totem" and otherwise works like any other. Kept with the identity, so it survives placing and picking up. */
    public static final String TAG_FORGED = "Forged";
    /**
     * The season a totem was made in. A totem from an earlier season is a husk wherever it turns up (a chest, the ground, the
     * database, a placement): the season's end replaces every totem. A totem without one was made in season 1, before the stamp.
     */
    public static final String TAG_SEASON = "Season";

    /** The season the totem was made in (1 when it carries none). */
    public static int season(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_SEASON) ? tag.getInt(TAG_SEASON) : 1;
    }

    public static void stampSeason(ItemStack stack, int season) {
        stack.getOrCreateTag().putInt(TAG_SEASON, season);
    }

    /** Whether a live-looking totem is from a season before the current one, on this server's count. */
    public static boolean oldSeason(ItemStack stack, net.minecraft.server.MinecraftServer server) {
        return !isSpent(stack) && season(stack) < com.mrgregles.bsp_core.admin.SeasonData.get(server).number;
    }

    public static boolean isForged(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(TAG_FORGED);
    }

    public static void setForged(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_FORGED, true);
    }

    private TotemIdentity() {}

    /** Gives a totem an id and an instance if it has none yet; returns true when something was added. */
    public static boolean ensure(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean added = false;
        if (!tag.hasUUID(TAG_ID)) {
            tag.putUUID(TAG_ID, UUID.randomUUID());
            added = true;
            // a brand-new totem: made in the season in progress
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && !tag.contains(TAG_SEASON)) {
                tag.putInt(TAG_SEASON, com.mrgregles.bsp_core.admin.SeasonData.get(server).number);
            }
        }
        if (!tag.hasUUID(TAG_INSTANCE)) {
            tag.putUUID(TAG_INSTANCE, UUID.randomUUID());
            added = true;
        }
        return added;
    }

    @Nullable
    public static UUID id(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : null;
    }

    @Nullable
    public static UUID instance(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_INSTANCE) ? tag.getUUID(TAG_INSTANCE) : null;
    }

    public static void setInstance(ItemStack stack, UUID instance) {
        stack.getOrCreateTag().putUUID(TAG_INSTANCE, instance);
    }

    public static boolean isSpent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_SPENT);
    }

    /** Turns a stale copy into a husk: no owner, no upgrades, no access list, marked spent. */
    public static void spend(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        UUID id = tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : null;
        CompoundTag fresh = new CompoundTag();
        if (id != null) {
            fresh.putUUID(TAG_ID, id);
        }
        fresh.putBoolean(TAG_SPENT, true);
        stack.setTag(fresh);
    }

    /** The id and instance of a totem's tag, copied into another tag (block entity to item and back). */
    public static void copy(@Nullable CompoundTag from, CompoundTag to) {
        if (from == null) {
            return;
        }
        if (from.hasUUID(TAG_ID)) {
            to.putUUID(TAG_ID, from.getUUID(TAG_ID));
        }
        if (from.hasUUID(TAG_INSTANCE)) {
            to.putUUID(TAG_INSTANCE, from.getUUID(TAG_INSTANCE));
        }
        if (from.getBoolean(TAG_FORGED)) {
            to.putBoolean(TAG_FORGED, true);
        }
        if (from.contains(TAG_SEASON)) {
            to.putInt(TAG_SEASON, from.getInt(TAG_SEASON));
        }
    }
}
