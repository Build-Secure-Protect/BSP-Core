package com.mrgregles.bsp_core.data;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/** Helpers for BSP data stored in a player's persistent NBT, which survives death. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BSPCore.MODID)
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

    /** The prefix of every key BSP-Core keeps in {@code PlayerPersisted}; nothing else in that compound is BSP-Core's. */
    public static final String KEY_PREFIX = BSPCore.MODID + ":";

    /**
     * The sub-tag of the player's persistent data that survives death, as the live compound: BSP-Core edits its own
     * {@code bsp_core:*} keys in it and nothing else. The compound is shared with every other mod (Waystones' known waystones
     * under {@code BalmData}, Integrated Dynamics, ...), so an existing compound is never replaced and {@code ForgeData} itself
     * never is either. It is made when the player has none (or, as vanilla's {@code getCompound} would read it, when the key holds
     * something that is not a compound).
     */
    public static CompoundTag get(Player player) {
        CompoundTag forgeData = player.getPersistentData();
        if (forgeData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return forgeData.getCompound(Player.PERSISTED_NBT_TAG); // the live reference
        }
        CompoundTag persisted = new CompoundTag();
        forgeData.put(Player.PERSISTED_NBT_TAG, persisted); // only when missing
        return persisted;
    }

    /**
     * Logs, at DEBUG (the server's {@code logs/debug.log}), that BSP-Core wrote these keys of the player's {@code PlayerPersisted},
     * with how many other keys the compound holds, so a write that coincides with other mods' data going missing is easy to find.
     */
    public static void edited(Player player, String... keys) {
        if (!BSPCore.LOGGER.isDebugEnabled()) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int others = 0;
        for (String k : persisted.getAllKeys()) {
            if (!k.startsWith(KEY_PREFIX)) {
                others++;
            }
        }
        BSPCore.LOGGER.debug("BSP-Core persistent data of {}: wrote {} in PlayerPersisted ({} other mods' keys kept: {})",
                player.getGameProfile().getName(), String.join(", ", keys), others,
                persisted.getAllKeys().stream().filter(k -> !k.startsWith(KEY_PREFIX)).sorted().toList());
    }

    /**
     * Copies BSP-Core's own {@code bsp_core:*} keys from {@code from} to {@code to} where {@code to} lacks them; returns the keys
     * copied. A key {@code to} already has is left alone, and no other mod's key is read or written.
     */
    public static java.util.List<String> copyOwnKeys(Player from, Player to) {
        CompoundTag fromForgeData = from.getPersistentData();
        if (!fromForgeData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return java.util.List.of();
        }
        CompoundTag source = fromForgeData.getCompound(Player.PERSISTED_NBT_TAG);
        java.util.List<String> copied = new java.util.ArrayList<>();
        CompoundTag target = null;
        for (String key : source.getAllKeys()) {
            if (!key.startsWith(KEY_PREFIX)) {
                continue;
            }
            if (target == null) {
                target = get(to);
            }
            if (target == source || target.contains(key)) {
                continue;
            }
            Tag value = source.get(key);
            if (value != null) {
                target.put(key, value.copy());
                copied.add(key);
            }
        }
        return copied;
    }

    /**
     * A respawn, or the way home from the End: Forge has already passed {@code PlayerPersisted} to the new player whole (and other
     * mods' Clone handlers may have run), so this only fills in a {@code bsp_core:*} key that is missing. It never rebuilds or
     * replaces the compound.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        Player now = event.getEntity(), was = event.getOriginal();
        if (now.level().isClientSide || now == was) {
            return;
        }
        java.util.List<String> copied = copyOwnKeys(was, now);
        if (!copied.isEmpty()) {
            edited(now, copied.toArray(String[]::new));
        }
    }

    /** One of BSP-Core's long keys as a saved player file holds it (the root of {@code playerdata/<uuid>.dat}), 0 when absent. */
    public static long longInPlayerFile(CompoundTag fileRoot, String key) {
        return fileRoot.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).getLong(key);
    }

    public static boolean isTotemGranted(Player player) {
        return get(player).getBoolean(TAG_TOTEM_GRANTED);
    }

    public static void setTotemGranted(Player player, boolean granted) {
        get(player).putBoolean(TAG_TOTEM_GRANTED, granted);
        edited(player, TAG_TOTEM_GRANTED);
    }

    /** The last season this player was brought into (given their fresh totem for, or first joined in). */
    public static int seasonSeen(Player player) {
        return get(player).getInt(BSPCore.MODID + ":SeasonSeen");
    }

    public static void setSeasonSeen(Player player, int season) {
        get(player).putInt(BSPCore.MODID + ":SeasonSeen", season);
        edited(player, BSPCore.MODID + ":SeasonSeen");
    }

    public static int coinEpoch(Player player) {
        return get(player).getInt(BSPCore.MODID + ":CoinEpoch");
    }

    public static void setCoinEpoch(Player player, int epoch) {
        get(player).putInt(BSPCore.MODID + ":CoinEpoch", epoch);
        edited(player, BSPCore.MODID + ":CoinEpoch");
    }

    /** Records where the player last stood in a dimension that may hold a totem. */
    public static void setLastMainPosition(Player player, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = get(player);
        String dim = dimension.location().toString(), server = serverId();
        // written every second while the player moves: logged only when the dimension or server changes
        boolean moved = !dim.equals(tag.getString(TAG_LAST_MAIN_DIM)) || !server.equals(tag.getString(TAG_LAST_MAIN_SERVER));
        tag.putString(TAG_LAST_MAIN_DIM, dim);
        tag.putLong(TAG_LAST_MAIN_POS, pos.asLong());
        tag.putString(TAG_LAST_MAIN_SERVER, server);
        if (moved) {
            edited(player, TAG_LAST_MAIN_DIM, TAG_LAST_MAIN_POS, TAG_LAST_MAIN_SERVER);
        }
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
        edited(player, TAG_LOGOUT_PLACED);
    }

    /**
     * Takes the logout placements this server made for the player, leaving those of other servers for when the player is back
     * there: BSP-InventorySync copies this data between servers, and another server's placement is not in this world.
     */
    public static java.util.List<LogoutPlaced> takeLogoutPlaced(Player player) {
        CompoundTag tag = get(player);
        boolean had = tag.contains(TAG_LOGOUT_PLACED);
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
        if (had) {
            edited(player, TAG_LOGOUT_PLACED);
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
            edited(player, TAG_LOGOUT_PLACED);
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
