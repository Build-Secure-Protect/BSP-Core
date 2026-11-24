package com.mrgregles.bsp_core.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * One machine a player tagged with the Machine Tagger: where it is (server, world, position), the name they gave it, the block it was
 * when tagged, and the season it was tagged in (a tag from an earlier season counts for nothing and is dropped when next seen).
 *
 * @param id     short id, unique among the player's tags (8 hex characters)
 * @param server the {@code storage.serverId} of the server it is on ("" on a server without one, such as single player)
 * @param dim    the world's id, such as {@code minecraft:overworld}
 * @param block  the block's registry id when it was tagged, such as {@code mekanism:elite_smelting_factory}
 */
public record TrackedTag(String id, String server, ResourceLocation dim, BlockPos pos, String name, ResourceLocation block, int season, long madeMs) {
    /** The longest name a player may give a tag. */
    public static final int NAME_MAX = 24;

    /** The tag with a new name; an empty one ("") means the block's own name. */
    public TrackedTag renamed(String newName) {
        return new TrackedTag(id, server, dim, pos, cleanName(newName, ""), block, season, madeMs);
    }

    /** Whether this tag is the machine at that place. */
    public boolean at(String serverId, ResourceLocation world, BlockPos at) {
        return server.equals(serverId) && dim.equals(world) && pos.equals(at);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("server", server);
        t.putString("dim", dim.toString());
        t.put("pos", NbtUtils.writeBlockPos(pos));
        t.putString("name", name);
        t.putString("block", block.toString());
        t.putInt("season", season);
        t.putLong("made", madeMs);
        return t;
    }

    /** The tag, or null when the stored one is broken. */
    @Nullable
    public static TrackedTag load(CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        ResourceLocation block = ResourceLocation.tryParse(t.getString("block"));
        String id = t.getString("id");
        if (dim == null || block == null || id.isEmpty() || !t.contains("pos")) {
            return null;
        }
        return new TrackedTag(id, t.getString("server"), dim, NbtUtils.readBlockPos(t.getCompound("pos")), cleanName(t.getString("name"), ""), block,
                t.getInt("season"), t.getLong("made"));
    }

    /**
     * A name a player typed, made safe to keep and show: no formatting codes or control characters, spaces trimmed and squeezed, at most
     * {@link #NAME_MAX} characters. An empty result gives {@code fallback}.
     */
    public static String cleanName(@Nullable String typed, String fallback) {
        if (typed == null) {
            return fallback;
        }
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < typed.length() && b.length() < NAME_MAX; i++) {
            char c = typed.charAt(i);
            if (c == '§') {
                i++; // a formatting code: the sign and the letter after it
                continue;
            }
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || Character.isSurrogate(c)) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = b.length() > 0;
                continue;
            }
            if (space) {
                if (b.length() + 1 >= NAME_MAX) {
                    break;
                }
                b.append(' ');
                space = false;
            }
            b.append(c);
        }
        return b.length() == 0 ? fallback : b.toString();
    }
}
