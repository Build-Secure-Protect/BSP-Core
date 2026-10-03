package com.mrgregles.bsp_core.data;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/** Helpers for BSP data stored in a player's persistent NBT, which survives death. */
public final class PlayerPersistent {
    private static final String TAG_TOTEM_GRANTED = BSPCore.MODID + ":TotemGranted";
    private static final String TAG_LAST_MAIN_DIM = BSPCore.MODID + ":LastMainDim";
    private static final String TAG_LAST_MAIN_POS = BSPCore.MODID + ":LastMainPos";

    private PlayerPersistent() {}

    /** The sub-tag of the player's persistent data that survives death. */
    public static CompoundTag get(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean isTotemGranted(Player player) {
        return get(player).getBoolean(TAG_TOTEM_GRANTED);
    }

    public static void setTotemGranted(Player player, boolean granted) {
        get(player).putBoolean(TAG_TOTEM_GRANTED, granted);
    }

    /** Records where the player last stood in a dimension that may hold a totem. */
    public static void setLastMainPosition(Player player, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = get(player);
        tag.putString(TAG_LAST_MAIN_DIM, dimension.location().toString());
        tag.putLong(TAG_LAST_MAIN_POS, pos.asLong());
    }

    public static Optional<LastMainPosition> getLastMainPosition(Player player) {
        CompoundTag tag = get(player);
        if (!tag.contains(TAG_LAST_MAIN_DIM) || !tag.contains(TAG_LAST_MAIN_POS)) {
            return Optional.empty();
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(TAG_LAST_MAIN_DIM));
        if (dim == null) {
            return Optional.empty();
        }
        return Optional.of(new LastMainPosition(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
                BlockPos.of(tag.getLong(TAG_LAST_MAIN_POS))));
    }

    public record LastMainPosition(ResourceKey<Level> dimension, BlockPos pos) {
        @Nullable
        public net.minecraft.server.level.ServerLevel level(net.minecraft.server.MinecraftServer server) {
            return server.getLevel(dimension);
        }
    }
}
