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

    private TotemIdentity() {}

    /** Gives a totem an id and an instance if it has none yet; returns true when something was added. */
    public static boolean ensure(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean added = false;
        if (!tag.hasUUID(TAG_ID)) {
            tag.putUUID(TAG_ID, UUID.randomUUID());
            added = true;
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
    }
}
