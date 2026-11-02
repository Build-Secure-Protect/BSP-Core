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
    private static final String TAG_LOGOUT_PLACED = BSPCore.MODID + ":LogoutPlaced";
    /** The server the last main position was on: BSP-InventorySync copies this data between servers, so a position is only good on its own. */
    private static final String TAG_LAST_MAIN_SERVER = BSPCore.MODID + ":LastMainServer";

    /** True when a record tagged with {@code server} belongs here; an untagged one (written before records were tagged) counts as here. */
    /**
     * This server's configured id. Not {@code Bank.serverId()}, which says "local" while the database is not connected (the first
     * seconds after a start): a record tagged or compared then would never match this server again.
     */
    private static String serverId() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.STORAGE_SERVER_ID, "server");
    }

    private static boolean here(String server) {
        return server.isEmpty() || server.equals(serverId());
    }

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

    /** The last season this player was brought into (given their fresh totem for, or first joined in). */
    public static int seasonSeen(Player player) {
        return get(player).getInt(BSPCore.MODID + ":SeasonSeen");
    }

    public static void setSeasonSeen(Player player, int season) {
        get(player).putInt(BSPCore.MODID + ":SeasonSeen", season);
    }

    public static int coinEpoch(Player player) {
        return get(player).getInt(BSPCore.MODID + ":CoinEpoch");
    }

    public static void setCoinEpoch(Player player, int epoch) {
        get(player).putInt(BSPCore.MODID + ":CoinEpoch", epoch);
    }

    /** Records where the player last stood in a dimension that may hold a totem. */
    public static void setLastMainPosition(Player player, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = get(player);
        tag.putString(TAG_LAST_MAIN_DIM, dimension.location().toString());
        tag.putLong(TAG_LAST_MAIN_POS, pos.asLong());
        tag.putString(TAG_LAST_MAIN_SERVER, serverId());
    }

    public static Optional<LastMainPosition> getLastMainPosition(Player player) {
        CompoundTag tag = get(player);
        if (!tag.contains(TAG_LAST_MAIN_DIM) || !tag.contains(TAG_LAST_MAIN_POS) || !here(tag.getString(TAG_LAST_MAIN_SERVER))) {
            return Optional.empty(); // none, or a position on another server of the network
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(TAG_LAST_MAIN_DIM));
        if (dim == null) {
            return Optional.empty();
        }
        return Optional.of(new LastMainPosition(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
                BlockPos.of(tag.getLong(TAG_LAST_MAIN_POS))));
    }

    /** A totem the logout rule placed for the player: where, which totem (id and instance) and when, so it can come back on their return. */
    public record LogoutPlaced(ResourceKey<Level> dimension, BlockPos pos, java.util.UUID id, java.util.UUID instance, long whenMs) {
        @Nullable
        public net.minecraft.server.level.ServerLevel level(net.minecraft.server.MinecraftServer server) {
            return server.getLevel(dimension);
        }
    }

    public static void addLogoutPlaced(Player player, LogoutPlaced placed) {
        CompoundTag tag = get(player);
        net.minecraft.nbt.ListTag list = tag.getList(TAG_LOGOUT_PLACED, net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag e = new CompoundTag();
        e.putString("Dim", placed.dimension().location().toString());
        e.putLong("Pos", placed.pos().asLong());
        e.putUUID("Id", placed.id());
        e.putUUID("Instance", placed.instance());
        e.putLong("When", placed.whenMs());
        e.putString("Server", serverId());
        list.add(e);
        tag.put(TAG_LOGOUT_PLACED, list);
    }

    /**
     * Takes the logout placements this server made for the player, leaving those of other servers for when the player is back
     * there: BSP-InventorySync copies this data between servers, and another server's placement is not in this world.
     */
    public static java.util.List<LogoutPlaced> takeLogoutPlaced(Player player) {
        CompoundTag tag = get(player);
        java.util.List<LogoutPlaced> out = new java.util.ArrayList<>();
        net.minecraft.nbt.ListTag others = new net.minecraft.nbt.ListTag();
        for (net.minecraft.nbt.Tag raw : tag.getList(TAG_LOGOUT_PLACED, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (!here(e.getString("Server"))) {
                others.add(e);
                continue;
            }
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            if (dim == null || !e.hasUUID("Id") || !e.hasUUID("Instance")) {
                continue;
            }
            out.add(new LogoutPlaced(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos")), e.getUUID("Id"), e.getUUID("Instance"), e.getLong("When")));
        }
        if (others.isEmpty()) {
            tag.remove(TAG_LOGOUT_PLACED);
        } else {
            tag.put(TAG_LOGOUT_PLACED, others);
        }
        return out;
    }

    /** Drops the player's logout placements of one totem (on every server); returns how many. */
    public static int removeLogoutPlaced(Player player, java.util.UUID totemId) {
        CompoundTag tag = get(player);
        net.minecraft.nbt.ListTag keep = new net.minecraft.nbt.ListTag();
        int n = 0;
        for (net.minecraft.nbt.Tag raw : tag.getList(TAG_LOGOUT_PLACED, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id") && e.getUUID("Id").equals(totemId)) {
                n++;
            } else {
                keep.add(e);
            }
        }
        if (n > 0) {
            if (keep.isEmpty()) {
                tag.remove(TAG_LOGOUT_PLACED);
            } else {
                tag.put(TAG_LOGOUT_PLACED, keep);
            }
        }
        return n;
    }

    public record LastMainPosition(ResourceKey<Level> dimension, BlockPos pos) {
        @Nullable
        public net.minecraft.server.level.ServerLevel level(net.minecraft.server.MinecraftServer server) {
            return server.getLevel(dimension);
        }
    }
}
