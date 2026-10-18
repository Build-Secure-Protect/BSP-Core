package com.mrgregles.bsp_core.link;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Shared helpers for the plasma link: saving a world position, naming a world, the outcomes of plugging a card in. */
public final class Links {
    private Links() {}

    /** What happened when a Link Card went into a Decelerator Core. */
    public enum Plug {
        LINKED, ALREADY, FULL, NO_ACCELERATOR, NO_WORLD, BLANK, BLACKLISTED, SELF;

        public String key() {
            return "message.bsp_core.link.plug." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What a link is doing this second, as the accelerator saw it. */
    public enum State {
        DELIVERING, IDLE, NO_RF_HERE, UNFORMED_HERE, ASLEEP, NO_RF_THERE, UNFORMED_THERE, FULL_THERE, MISSING, NO_WORLD;

        public String key() {
            return "gui.bsp_core.link.state." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Whether plasma could go through this second (the far side is loaded, formed, powered and has room). */
        public boolean live() {
            return this == DELIVERING || this == IDLE || this == NO_RF_HERE || this == UNFORMED_HERE;
        }
    }

    /** One link's state and the mB/t it carried this second. */
    public record Report(State state, int rate) {
        public static final Report NONE = new Report(State.MISSING, 0);
    }

    public static void saveAccess(CompoundTag tag, String key, com.mrgregles.bsp_core.plasma.PlasmaAccess access) {
        tag.putLongArray(key + "Users", ids(access.users));
        tag.putLongArray(key + "Editors", ids(access.editors));
    }

    public static com.mrgregles.bsp_core.plasma.PlasmaAccess loadAccess(CompoundTag tag, String key) {
        com.mrgregles.bsp_core.plasma.PlasmaAccess out = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
        out.users.addAll(uuids(tag.getLongArray(key + "Users")));
        out.editors.addAll(uuids(tag.getLongArray(key + "Editors")));
        return out;
    }

    private static long[] ids(java.util.Set<java.util.UUID> set) {
        long[] out = new long[set.size() * 2];
        int i = 0;
        for (java.util.UUID u : set) {
            out[i++] = u.getMostSignificantBits();
            out[i++] = u.getLeastSignificantBits();
        }
        return out;
    }

    private static java.util.List<java.util.UUID> uuids(long[] ids) {
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < ids.length; i += 2) {
            out.add(new java.util.UUID(ids[i], ids[i + 1]));
        }
        return out;
    }

    public static void save(CompoundTag tag, String key, @Nullable GlobalPos pos) {
        if (pos == null) {
            tag.remove(key + "Dim");
            tag.remove(key + "Pos");
            return;
        }
        tag.putString(key + "Dim", pos.dimension().location().toString());
        tag.putLong(key + "Pos", pos.pos().asLong());
    }

    @Nullable
    public static GlobalPos load(CompoundTag tag, String key) {
        if (!tag.contains(key + "Dim") || !tag.contains(key + "Pos")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key + "Dim"));
        if (id == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong(key + "Pos")));
    }

    /** "Overworld", "The Nether", "The End" for the vanilla worlds; any other world by its id's path, capitalised, underscores as spaces. */
    public static Component worldName(ResourceKey<Level> dim) {
        ResourceLocation id = dim.location();
        if (id.getNamespace().equals("minecraft")) {
            return Component.translatable("dimension.bsp_core." + id.getPath());
        }
        String path = id.getPath().replace('_', ' ');
        return Component.literal(path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    /** "Nether -112, 64, 338". */
    public static Component describe(GlobalPos pos) {
        return Component.translatable("message.bsp_core.link.where", worldName(pos.dimension()), pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());
    }

    /** Whether this world may take part in a link ({@code plasma.linkDimensionBlacklist}). */
    public static boolean allowed(ResourceKey<Level> dim) {
        for (String s : com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_DIMENSION_BLACKLIST, java.util.List.<String>of())) {
            if (dim.location().toString().equals(s)) {
                return false;
            }
        }
        return true;
    }
}
