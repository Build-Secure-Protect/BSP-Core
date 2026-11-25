package com.mrgregles.bsp_core.client.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of the player's machine tracking: the view the server sends ({@code TrackerSyncPacket}) and the latest reading of each
 * watched machine ({@code TrackerSnapshotPacket}), with the time it arrived here. Read by the tagger's screen, the Holo Grid, the popout,
 * the pinned box and the green outline. Client thread only.
 */
public final class ClientTracking {
    private static CompoundTag view = new CompoundTag();
    private static final Map<String, CompoundTag> READINGS = new HashMap<>();
    /**
     * For gliding (owner decision MS1): what each machine's box showed when its latest reading arrived, and how long to glide from that to
     * the reading (the time since the reading before, so about a tick on the same server and about a second from another server).
     */
    private static final Map<String, CompoundTag> FROM = new HashMap<>();
    private static final Map<String, Long> GLIDE_MS = new HashMap<>();
    /** Readings further apart than this (the machine sat still, or the box was just opened) jump straight to the new values. */
    private static final long MAX_GLIDE_MS = 2500;
    /** Never blended: item counts are whole and exact, and these are not amounts. */
    private static final Set<String> KEEP = Set.of("items", "n", "slots", "st", "arrived", "liveMs", "max", "feMax", "capacity", "rateLimit", "op", "real");
    /** Bumped on every change, so screens and the popout redraw only when something changed. */
    private static int version;

    private ClientTracking() {}

    public static void accept(CompoundTag v) {
        view = v;
        Set<String> ids = new HashSet<>();
        ListTag tags = v.getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            ids.add(tags.getCompound(i).getString("id"));
        }
        READINGS.keySet().retainAll(ids);
        FROM.keySet().retainAll(ids);
        GLIDE_MS.keySet().retainAll(ids);
        version++;
        GridModel.refresh();
        WatchSet.update();
    }

    public static void reading(String id, CompoundTag reading) {
        CompoundTag was = READINGS.get(id);
        long now = System.currentTimeMillis();
        if (reading.getByte("st") != 0 && was != null && was.contains("block")) {
            // asleep, gone or offline: keep the last reading to show, marked with the new status and when it was last live (this clock)
            CompoundTag kept = was.copy();
            kept.putByte("st", reading.getByte("st"));
            kept.putLong("liveMs", was.contains("liveMs") ? was.getLong("liveMs") : was.getLong("arrived"));
            kept.remove("op");
            READINGS.put(id, kept);
        } else {
            CompoundTag r = reading; // fresh from the packet, shared with nothing
            if (r.getCompound("real").getBoolean("keepNbt")) {
                // the machine's saved data has not changed since the last reading: the copy already here is used
                CompoundTag real = r.getCompound("real");
                CompoundTag lastReal = was == null ? new CompoundTag() : was.getCompound("real");
                if (lastReal.contains("nbt")) {
                    real.put("nbt", lastReal.get("nbt"));
                    real.remove("keepNbt");
                } else {
                    r.remove("real"); // none here yet (a fresh start): the panel until the next full copy, at most a second away
                }
            }
            r.putLong("arrived", now);
            long gap = was == null ? Long.MAX_VALUE : now - was.getLong("arrived");
            if (was != null && was.getByte("st") == 0 && gap <= MAX_GLIDE_MS) {
                FROM.put(id, smoothed(id)); // from what the box shows right now, so a reading arriving mid-glide never jumps
                GLIDE_MS.put(id, gap);
            } else {
                FROM.remove(id);
                GLIDE_MS.remove(id);
            }
            READINGS.put(id, r);
        }
        version++;
    }

    /**
     * The reading as its box draws it: the bars (progress, power, fluids, gases, heat) glide from what was shown when the reading arrived to
     * the reading's own values, reaching them as the next reading is due. Item counts and statuses are the reading's own.
     */
    public static CompoundTag smoothed(String id) {
        CompoundTag to = reading(id);
        CompoundTag from = FROM.get(id);
        Long glide = GLIDE_MS.get(id);
        if (from == null || glide == null || glide <= 0) {
            return to;
        }
        double f = (System.currentTimeMillis() - to.getLong("arrived")) / (double) glide;
        if (f >= 1) {
            FROM.remove(id);
            GLIDE_MS.remove(id);
            return to;
        }
        return (CompoundTag) blend(from, to, Math.max(0, f), "");
    }

    /** {@code to} with its amounts moved {@code f} of the way from {@code from}'s; anything that does not match is {@code to}'s own. */
    private static Tag blend(Tag from, Tag to, double f, String key) {
        if (KEEP.contains(key)) {
            return to; // never changed by whoever reads it, so not copied (a real screen's saved data can be big)
        }
        if (from instanceof CompoundTag a && to instanceof CompoundTag b) {
            CompoundTag out = new CompoundTag();
            for (String k : b.getAllKeys()) {
                Tag bt = b.get(k);
                Tag at = a.get(k);
                out.put(k, at == null || bt == null ? bt : blend(at, bt, f, k));
            }
            return out;
        }
        if (from instanceof ListTag a && to instanceof ListTag b) {
            if (a.size() != b.size() || a.getElementType() != b.getElementType()) {
                return b;
            }
            ListTag out = new ListTag();
            for (int i = 0; i < b.size(); i++) {
                Tag at = a.get(i), bt = b.get(i);
                if (at instanceof CompoundTag ac && bt instanceof CompoundTag bc
                        && !(ac.getString("id").equals(bc.getString("id")) && ac.getString("role").equals(bc.getString("role")))) {
                    out.add(bt); // another fluid or gas in that tank now
                } else {
                    out.add(blend(at, bt, f, key));
                }
            }
            return out;
        }
        if (from instanceof NumericTag a && to instanceof NumericTag b && a.getId() == b.getId()) {
            double av = a.getAsDouble(), bv = b.getAsDouble();
            if (("progress".equals(key) || "procs".equals(key)) && bv < av) {
                av = 0; // a process finished and began again: it fills from empty rather than sliding back
            }
            double v = av + (bv - av) * f;
            return switch (b.getId()) {
                case Tag.TAG_INT -> IntTag.valueOf((int) Math.round(v));
                case Tag.TAG_LONG -> LongTag.valueOf(Math.round(v));
                case Tag.TAG_FLOAT -> FloatTag.valueOf((float) v);
                case Tag.TAG_DOUBLE -> DoubleTag.valueOf(v);
                default -> b.copy(); // bytes and shorts are flags and codes
            };
        }
        return to.copy();
    }

    public static CompoundTag view() {
        return view;
    }

    public static CompoundTag reading(String id) {
        CompoundTag r = READINGS.get(id);
        return r == null ? new CompoundTag() : r;
    }

    public static int version() {
        return version;
    }

    /** Forgets everything (on leaving a server). */
    public static void clear() {
        view = new CompoundTag();
        READINGS.clear();
        FROM.clear();
        GLIDE_MS.clear();
        version++;
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLeave(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
            GridModel.clear();
        }
    }
}
