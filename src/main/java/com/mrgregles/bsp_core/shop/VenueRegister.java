package com.mrgregles.bsp_core.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This server's Market Stalls, Vendor Posts and Stall Displays, where they stand and which shop
 * each one sells (BSP-Server Utilities request SU-BC9). Kept as they are placed, assigned,
 * changed and broken, and learned as their chunks load, so a venue from before the register is
 * known the first time its chunk loads. Saved with the overworld; one register per server.
 */
public class VenueRegister extends SavedData {
    private static final String DATA_NAME = "bsp_core_venues";

    /**
     * One venue: {@code kind} {@code stall}, {@code post} or {@code display}; its dimension and block;
     * the shop it sells (empty when unassigned); its own opening hours (null: the shop's); a
     * display's pinned item id (empty for none).
     */
    public record Venue(String kind, String dim, BlockPos pos, String shop, @Nullable OpenHours hours, String pin) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", kind);
            t.putString("Dim", dim);
            t.putLong("Pos", pos.asLong());
            t.putString("Shop", shop);
            if (hours != null) {
                t.put("Hours", hours.save());
            }
            t.putString("Pin", pin);
            return t;
        }

        static Venue load(CompoundTag t) {
            return new Venue(t.getString("Kind"), t.getString("Dim"), BlockPos.of(t.getLong("Pos")), t.getString("Shop"),
                    t.contains("Hours") ? OpenHours.load(t.getCompound("Hours")) : null, t.getString("Pin"));
        }
    }

    private final Map<String, Venue> venues = new LinkedHashMap<>();
    /** When this server's venues last changed (epoch ms; 0: not since start). */
    public long changedAt;

    public static VenueRegister get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VenueRegister::load, VenueRegister::new, DATA_NAME);
    }

    private static String key(String dim, BlockPos pos) {
        return dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public List<Venue> all() {
        return List.copyOf(venues.values());
    }

    /** Records a venue as it is now; posts {@code ShopEvent.VenuesChanged} when anything about it changed. */
    public static void note(ServerLevel level, Venue v) {
        MinecraftServer server = level.getServer();
        VenueRegister r = get(server);
        String k = key(v.dim(), v.pos());
        Venue was = r.venues.get(k);
        if (v.equals(was)) {
            return;
        }
        r.venues.put(k, v);
        if (was != null && !was.shop().isEmpty() && !Objects.equals(was.shop(), v.shop()) && !v.shop().isEmpty()) {
            r.changed(server, was.shop()); // moved from one shop to another: both are told
        }
        r.changed(server, was != null && !Objects.equals(was.shop(), v.shop()) && v.shop().isEmpty() ? was.shop() : v.shop());
    }

    /** Forgets the venue at this block (broken or replaced); posts the event when there was one. */
    public static void forget(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        VenueRegister r = get(server);
        Venue was = r.venues.remove(key(level.dimension().location().toString(), pos));
        if (was != null) {
            r.changed(server, was.shop());
        }
    }

    private void changed(MinecraftServer server, String shop) {
        changedAt = System.currentTimeMillis();
        setDirty();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.mrgregles.bsp_core.api.event.ShopEvent.VenuesChanged(server, shop.isEmpty() ? null : shop));
    }

    /** The dimension id of a level, as venues name it ("minecraft:overworld"). */
    public static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** {@code hours} as a venue keeps it: null for always, which means the shop's own hours count. */
    @Nullable
    public static OpenHours own(OpenHours hours) {
        return hours.mode() == OpenHours.Mode.ALWAYS ? null : hours;
    }

    /**
     * Runs {@code then} on the server thread after the current work, for a block entity on a live
     * server level: a venue noted while its chunk loads waits until the chunk is in place.
     */
    public static void later(@Nullable net.minecraft.world.level.Level level, Runnable then) {
        if (level instanceof ServerLevel sl && !sl.isClientSide) {
            sl.getServer().execute(then);
        }
    }

    /**
     * A venue's block entity was removed. A chunk unloading keeps the venue ({@code unloading}); a
     * block broken or replaced forgets it, checked a moment later so a block entity swapped for one
     * of the same kind (a stall's skin) and a server stopping (its chunks no longer loaded) keep it.
     */
    public static void removed(net.minecraft.world.level.block.entity.BlockEntity be, boolean unloading) {
        if (unloading || !(be.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Class<?> type = be.getClass();
        sl.getServer().execute(() -> {
            if (sl.isLoaded(pos) && !type.isInstance(sl.getBlockEntity(pos))) {
                forget(sl, pos);
            }
        });
    }

    /** The registry id of a Stall Display's pinned card's item, empty for none. */
    public static String pinItem(MinecraftServer server, String shopId, String pinShop, int pinned) {
        Shop s = pinned < 0 ? null : Shops.ledger(server).shop(pinShop.isEmpty() ? shopId : pinShop);
        ShopItem it = s == null ? null : s.item(pinned);
        if (it == null || it.stack.isEmpty()) {
            return "";
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it.stack.getItem());
        return key == null ? "" : key.toString();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        venues.values().forEach(v -> list.add(v.save()));
        tag.put("Venues", list);
        return tag;
    }

    public static VenueRegister load(CompoundTag tag) {
        VenueRegister r = new VenueRegister();
        for (Tag t : tag.getList("Venues", Tag.TAG_COMPOUND)) {
            Venue v = Venue.load((CompoundTag) t);
            r.venues.put(key(v.dim(), v.pos()), v);
        }
        return r;
    }

    /** GameTests: every venue in this dimension inside the box, in register order. */
    public List<Venue> within(String dim, BlockPos from, BlockPos to) {
        List<Venue> out = new ArrayList<>();
        for (Venue v : venues.values()) {
            BlockPos p = v.pos();
            if (v.dim().equals(dim) && p.getX() >= Math.min(from.getX(), to.getX()) && p.getX() <= Math.max(from.getX(), to.getX())
                    && p.getY() >= Math.min(from.getY(), to.getY()) && p.getY() <= Math.max(from.getY(), to.getY())
                    && p.getZ() >= Math.min(from.getZ(), to.getZ()) && p.getZ() <= Math.max(from.getZ(), to.getZ())) {
                out.add(v);
            }
        }
        return out;
    }
}
