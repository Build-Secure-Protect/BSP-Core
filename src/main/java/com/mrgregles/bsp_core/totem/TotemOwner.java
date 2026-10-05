package com.mrgregles.bsp_core.totem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The owner of a Shatter Totem: the player whose totem it currently is. Stored identically in the
 * item's NBT and in the placed block's block entity so the two forms round-trip without loss.
 *
 * @param uuid the owner's player UUID
 * @param name the owner's last known name, for display only
 */
public record TotemOwner(UUID uuid, String name) {
    public static final String TAG_OWNER_UUID = "OwnerUUID";
    public static final String TAG_OWNER_NAME = "OwnerName";

    public void save(CompoundTag tag) {
        tag.putUUID(TAG_OWNER_UUID, uuid);
        tag.putString(TAG_OWNER_NAME, name);
    }

    public static Optional<TotemOwner> load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID(TAG_OWNER_UUID)) {
            return Optional.empty();
        }
        return Optional.of(new TotemOwner(tag.getUUID(TAG_OWNER_UUID), tag.getString(TAG_OWNER_NAME)));
    }

    public static Optional<TotemOwner> fromStack(ItemStack stack) {
        return load(stack.getTag());
    }

    /** When the current owner got the totem, in ms; chunk loading treats the totem a player has held longest as their main one. */
    public static final String TAG_OWNED_SINCE = "OwnedSince";

    public void applyTo(ItemStack stack) {
        if (!fromStack(stack).map(o -> o.uuid().equals(uuid)).orElse(false)) {
            stack.getOrCreateTag().putLong(TAG_OWNED_SINCE, System.currentTimeMillis());
        }
        save(stack.getOrCreateTag());
    }

    public static void clear(CompoundTag tag) {
        tag.remove(TAG_OWNER_UUID);
        tag.remove(TAG_OWNER_NAME);
    }
}
