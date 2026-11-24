package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The player resets this server knows of ({@code /bsp player reset}), the latest per player: on a single server the record itself,
 * on a network a copy of the database's {@code player_resets} kept as each one is forgotten here. A login compares a player's
 * {@code bsp_core:ResetAt} with it to know whether what they carry is still to be cleared.
 *
 * <p>{@link #since} is when this record was started. A reset older than that (made before this server joined the network, or
 * before its world was made) is kept apart ({@link #skip}) and never acted on, nor wiped at a login: the player has played on
 * since, and their newer data is not theirs to lose.
 */
public final class PlayerResetLedger extends SavedData {
    private static final String NAME = BSPCore.MODID + "_player_resets";

    private final Map<UUID, PlayerResetRow> resets = new HashMap<>();
    /** Resets older than {@link #since}, by player: when the latest was, so it is logged once and never applied (nor wiped at a login). */
    private final Map<UUID, Long> skipped = new HashMap<>();
    /** When this record was started (wall clock, ms). */
    private long since;

    private PlayerResetLedger(long since) {
        this.since = since;
    }

    public static PlayerResetLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerResetLedger::load, () -> new PlayerResetLedger(System.currentTimeMillis()), NAME);
    }

    /** The latest reset of the player known here, or null. */
    @Nullable
    public PlayerResetRow latest(UUID player) {
        return resets.get(player);
    }

    /** When this record was started. */
    public long since() {
        return since;
    }

    /** Test hook: the record's start, as a server that has been on the network that long. */
    public void since(long ms) {
        since = ms;
        setDirty();
    }

    /** Notes a reset; true when it is newer than the one known for the player (so it is still to be applied here). */
    public boolean note(PlayerResetRow row) {
        PlayerResetRow had = resets.get(row.id());
        if (had != null && had.resetMs() >= row.resetMs()) {
            return false;
        }
        resets.put(row.id(), row);
        setDirty();
        return true;
    }

    /** Notes a reset too old to apply here; true the first time it is seen (to log it once). */
    public boolean skip(PlayerResetRow row) {
        Long had = skipped.get(row.id());
        if (had != null && had >= row.resetMs()) {
            return false;
        }
        skipped.put(row.id(), row.resetMs());
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Since", since);
        CompoundTag old = new CompoundTag();
        skipped.forEach((id, ms) -> old.putLong(id.toString(), ms));
        tag.put("Skipped", old);
        ListTag list = new ListTag();
        resets.values().forEach(r -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", r.id());
            e.putString("Name", r.name());
            e.putLong("At", r.resetMs());
            e.putString("By", r.by());
            list.add(e);
        });
        tag.put("Resets", list);
        return tag;
    }

    private static PlayerResetLedger load(CompoundTag tag) {
        PlayerResetLedger l = new PlayerResetLedger(tag.contains("Since") ? tag.getLong("Since") : System.currentTimeMillis());
        for (Tag raw : tag.getList("Resets", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id")) {
                l.resets.put(e.getUUID("Id"), new PlayerResetRow(e.getUUID("Id"), e.getString("Name"), e.getLong("At"), e.getString("By")));
            }
        }
        CompoundTag old = tag.getCompound("Skipped");
        for (String key : old.getAllKeys()) {
            try {
                l.skipped.put(UUID.fromString(key), old.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
        return l;
    }
}
