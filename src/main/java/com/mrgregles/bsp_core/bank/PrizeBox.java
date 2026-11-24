package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The prize compartment of the Safety Deposit Box (season reset, owner decision SR4): season prizes go straight here, never into
 * the inventory, so a season reset's wipe never reaches them and a full box never pushes them out. It belongs to the player, not to
 * a rented box, and it is take-out only: nothing can be put in. Kept by every season reset (and by a player reset).
 *
 * <p>On a network the database's {@code bank_prizes} is the record (one row per stack); this server's {@link Ledger} then holds only
 * prizes it could not write there yet, sent again at the winner's next look. On one server the ledger is the record.
 */
public final class PrizeBox {
    private PrizeBox() {}

    /** One stack in the compartment: its row id (0 or less when not yet in the database), the stack, the season and place won. */
    public record Entry(long id, ItemStack stack, int season, int place) {}

    /** Puts the prizes of one place in the winner's compartment; on a network a failed write is kept here and sent again later. */
    public static void add(MinecraftServer server, UUID player, List<ItemStack> stacks, int season, int place) {
        List<ItemStack> items = stacks.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
        if (items.isEmpty()) {
            return;
        }
        Ledger ledger = Ledger.get(server);
        if (!NetworkStorage.enabled()) {
            items.forEach(st -> ledger.put(player, new Entry(ledger.nextId++, st, season, place)));
            return;
        }
        NetworkStorage.prizeAdd(player, encode(items), season, place, ok -> {
            if (!ok) {
                items.forEach(st -> ledger.put(player, new Entry(-(ledger.nextId++), st, season, place)));
                BSPCore.LOGGER.warn("Season prizes for {} are kept on this server until the database takes them", player);
            }
        });
    }

    /** What the winner's compartment holds now, oldest first ({@code then} on the server thread; null when the network is unreachable). */
    public static void load(MinecraftServer server, UUID player, Consumer<List<Entry>> then) {
        Ledger ledger = Ledger.get(server);
        if (!NetworkStorage.enabled()) {
            then.accept(ledger.of(player));
            return;
        }
        // prizes this server could not write before go up first, then the database's rows are read
        List<Entry> waiting = ledger.of(player);
        Runnable read = () -> NetworkStorage.prizesRead(player, rows -> {
            if (rows == null) {
                then.accept(null);
                return;
            }
            List<Entry> out = new ArrayList<>();
            for (Object[] r : rows) {
                ItemStack st = decode((String) r[1]);
                if (!st.isEmpty()) {
                    out.add(new Entry((Long) r[0], st, (Integer) r[2], (Integer) r[3]));
                }
            }
            then.accept(out);
        });
        if (waiting.isEmpty() || !UPLOADING.add(player)) {
            read.run(); // nothing kept here, or being sent already: what has landed is read
            return;
        }
        upload(server, player, waiting, 0, () -> {
            UPLOADING.remove(player);
            read.run();
        });
    }

    /** Sends every prize this server keeps for want of the database (the database just connected). */
    public static void uploadWaiting(MinecraftServer server) {
        if (!NetworkStorage.enabled()) {
            return;
        }
        Ledger ledger = Ledger.get(server);
        for (UUID player : List.copyOf(ledger.prizes.keySet())) {
            List<Entry> waiting = ledger.of(player);
            if (!waiting.isEmpty() && UPLOADING.add(player)) {
                upload(server, player, waiting, 0, () -> UPLOADING.remove(player));
            }
        }
    }

    /** Players whose kept prizes are being sent now: never twice at once (a duplicate row would be a second prize). */
    private static final java.util.Set<UUID> UPLOADING = new java.util.HashSet<>();

    private static void upload(MinecraftServer server, UUID player, List<Entry> waiting, int i, Runnable then) {
        if (i >= waiting.size()) {
            then.run();
            return;
        }
        Entry e = waiting.get(i);
        NetworkStorage.prizeAdd(player, encode(List.of(e.stack())), e.season(), e.place(), ok -> {
            if (ok) {
                Ledger.get(server).remove(player, e.id());
            }
            upload(server, player, waiting, i + 1, then);
        });
    }

    /** What is left of each row after the winner took from the compartment (an empty stack for a row emptied). */
    public static void taken(MinecraftServer server, UUID player, Map<Long, ItemStack> left) {
        if (left.isEmpty()) {
            return;
        }
        if (!NetworkStorage.enabled()) {
            Ledger ledger = Ledger.get(server);
            left.forEach((id, st) -> ledger.update(player, id, st));
            return;
        }
        Map<Long, String> rows = new HashMap<>();
        left.forEach((id, st) -> {
            if (id > 0) {
                rows.put(id, st.isEmpty() ? "" : st.save(new CompoundTag()).toString());
            } else {
                Ledger.get(server).update(player, id, st); // still kept here: taken from this server's copy
            }
        });
        NetworkStorage.prizesTaken(player, rows);
    }

    private static List<String> encode(List<ItemStack> items) {
        List<String> out = new ArrayList<>();
        items.forEach(st -> out.add(st.save(new CompoundTag()).toString()));
        return out;
    }

    private static ItemStack decode(String snbt) {
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception e) {
            BSPCore.LOGGER.error("BSP-Core bank: an unreadable prize in a compartment: {}", snbt);
            return ItemStack.EMPTY;
        }
    }

    /** This server's prize compartments: the record on one server, the not-yet-written ones on a network. */
    public static final class Ledger extends SavedData {
        private static final String DATA_NAME = BSPCore.MODID + "_prizes";
        private final Map<UUID, List<Entry>> prizes = new LinkedHashMap<>();
        long nextId = 1;

        public static Ledger get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Ledger::load, Ledger::new, DATA_NAME);
        }

        public List<Entry> of(UUID player) {
            return List.copyOf(prizes.getOrDefault(player, List.of()));
        }

        void put(UUID player, Entry e) {
            prizes.computeIfAbsent(player, k -> new ArrayList<>()).add(e);
            setDirty();
        }

        void remove(UUID player, long id) {
            update(player, id, ItemStack.EMPTY);
        }

        void update(UUID player, long id, ItemStack left) {
            List<Entry> list = prizes.get(player);
            if (list == null) {
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                if (e.id() == id) {
                    if (left.isEmpty()) {
                        list.remove(i);
                    } else {
                        list.set(i, new Entry(id, left.copy(), e.season(), e.place()));
                    }
                    break;
                }
            }
            if (list.isEmpty()) {
                prizes.remove(player);
            }
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("NextId", nextId);
            ListTag players = new ListTag();
            prizes.forEach((id, list) -> {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", id);
                ListTag rows = new ListTag();
                for (Entry e : list) {
                    CompoundTag r = new CompoundTag();
                    r.putLong("Row", e.id());
                    r.put("Item", e.stack().save(new CompoundTag()));
                    r.putInt("Season", e.season());
                    r.putInt("Place", e.place());
                    rows.add(r);
                }
                p.put("Rows", rows);
                players.add(p);
            });
            tag.put("Players", players);
            return tag;
        }

        private static Ledger load(CompoundTag tag) {
            Ledger l = new Ledger();
            l.nextId = Math.max(1, tag.getLong("NextId"));
            for (Tag raw : tag.getList("Players", Tag.TAG_COMPOUND)) {
                CompoundTag p = (CompoundTag) raw;
                if (!p.hasUUID("Id")) {
                    continue;
                }
                List<Entry> list = new ArrayList<>();
                for (Tag rr : p.getList("Rows", Tag.TAG_COMPOUND)) {
                    CompoundTag r = (CompoundTag) rr;
                    ItemStack st = ItemStack.of(r.getCompound("Item"));
                    if (!st.isEmpty()) {
                        list.add(new Entry(r.getLong("Row"), st, r.getInt("Season"), r.getInt("Place")));
                    }
                }
                if (!list.isEmpty()) {
                    l.prizes.put(p.getUUID("Id"), list);
                }
            }
            return l;
        }
    }
}
