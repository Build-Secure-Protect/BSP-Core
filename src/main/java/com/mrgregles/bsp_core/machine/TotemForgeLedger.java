package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which Totem Forge each player has a totem pouring or setting in: one at a time (owner, 2026-10-27), so
 * a player with no totem cannot fill five forges and get five free totems. Kept in this world, and with
 * network storage on also as a row of the shared {@code state} table ({@code forge_pour:<uuid>} = the
 * server, dimension and position, or empty when free), so the limit holds across the network.
 */
public class TotemForgeLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_totem_forge";
    private final Map<UUID, GlobalPos> pours = new HashMap<>();
    /** When each claimed totem will have set (real ms), or {@link #TOLD} once its pourer has been told: kept here, so they hear it while the forge's chunk is unloaded. */
    private final Map<UUID, Long> ready = new HashMap<>();
    private static final long TOLD = -1;

    /** How the claims reach the network's shared {@code state} table. GameTests swap it with {@link #useNetwork}, setting and restoring it within one tick. */
    public interface Network {
        boolean enabled();

        /** Writes the key, whatever it held. */
        void put(String key, String value);

        /**
         * Claims the key for {@code value} if it is free (empty or missing); {@code then} hears, on the server thread, who holds
         * it afterwards ({@code value} when this server got it) and how many Shatter Totems wait in the network for {@code player}
         * (travelling between servers, owed hand-backs). Never called back when the database cannot be reached.
         */
        void claim(String key, String value, UUID player, java.util.function.BiConsumer<String, Integer> then);

        /** Frees the key only if it still holds {@code value}. */
        void release(String key, String value);
    }

    public static final Network STORAGE = new Network() {
        @Override
        public boolean enabled() {
            return NetworkStorage.enabled();
        }

        @Override
        public void put(String key, String value) {
            NetworkStorage.putState(Map.of(key, value), false);
        }

        @Override
        public void claim(String key, String value, UUID player, java.util.function.BiConsumer<String, Integer> then) {
            NetworkStorage.claimPour(key, value, player, then);
        }

        @Override
        public void release(String key, String value) {
            NetworkStorage.casState(key, value, "", won -> {});
        }
    };

    private static Network network = STORAGE;

    public static Network network() {
        return network;
    }

    /** Swaps the network for a GameTest; returns the one it replaced, to restore. */
    public static Network useNetwork(Network n) {
        Network old = network;
        network = n;
        return old;
    }

    public static TotemForgeLedger get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not loaded; cannot access the Totem Forge ledger");
        }
        return overworld.getDataStorage().computeIfAbsent(TotemForgeLedger::load, TotemForgeLedger::new, DATA_NAME);
    }

    public static String networkKey(UUID player) {
        return "forge_pour:" + player;
    }

    /** The value a network claim stores: this server, the dimension and the forge's position. */
    public static String networkValue(GlobalPos pos) {
        return BSPConfig.STORAGE_SERVER_ID.get() + "|" + pos.dimension().location() + "|" + pos.pos().getX() + "," + pos.pos().getY() + "," + pos.pos().getZ();
    }

    /** The forge on this server where the player has a pour, if any. */
    public Optional<GlobalPos> pourOf(UUID player) {
        return Optional.ofNullable(pours.get(player));
    }

    public void claim(UUID player, GlobalPos forge) {
        pours.put(player, forge);
        setDirty();
        if (network.enabled()) {
            network.put(networkKey(player), networkValue(forge));
        }
    }

    /** The pour has ended: the totem in the player's claimed forge will have set at {@code readyAt} (real ms). */
    public void readyAt(UUID player, long readyAt) {
        if (pours.containsKey(player)) {
            ready.put(player, readyAt);
            setDirty();
        }
    }

    /** When the player's claimed totem will have set (real ms); empty before the pour ends, and once they have been told. */
    public java.util.OptionalLong readyTime(UUID player) {
        Long v = ready.get(player);
        return v == null || v == TOLD ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(v);
    }

    /** Whether the player has been told that their claimed totem has set. */
    public boolean told(UUID player) {
        Long v = ready.get(player);
        return v != null && v == TOLD;
    }

    /** Marks the player told that their totem has set; true only the first time, so the forge and the ledger never both tell them. */
    public boolean markTold(UUID player) {
        if (!pours.containsKey(player)) {
            return true; // no claim here (an operator cleared it): nothing to remember
        }
        Long was = ready.put(player, TOLD);
        setDirty();
        return was == null || was != TOLD;
    }

    /**
     * Tells each pourer whose totem has set by {@code now} and who has not heard yet, on whichever server they are (or at their
     * next login), whether or not the forge's chunk is loaded. Returns how many were told. Run once a minute.
     */
    public int tellDue(MinecraftServer server, long now) {
        int told = 0;
        for (var e : java.util.List.copyOf(ready.entrySet())) {
            GlobalPos at = pours.get(e.getKey());
            if (e.getValue() != TOLD && e.getValue() <= now && at != null && markTold(e.getKey())) {
                NetworkStorage.tell(server, e.getKey(), TotemForgeBlockEntity.setMessage(at.pos()));
                told++;
            }
        }
        return told;
    }

    /** Frees the player's claim if it is this forge's. */
    public void release(UUID player, GlobalPos forge) {
        if (forge.equals(pours.get(player))) {
            pours.remove(player);
            ready.remove(player);
            setDirty();
            if (network.enabled()) {
                network.release(networkKey(player), networkValue(forge));
            }
        }
    }

    /** An operator's clear: frees the player's claim here, wherever it points, and their network row whatever it holds. Returns the claim it freed here, if any. */
    public Optional<GlobalPos> clear(UUID player) {
        GlobalPos was = pours.remove(player);
        ready.remove(player);
        if (was != null) {
            setDirty();
        }
        if (network.enabled()) {
            network.put(networkKey(player), "");
        }
        return Optional.ofNullable(was);
    }

    public static TotemForgeLedger load(CompoundTag tag) {
        TotemForgeLedger ledger = new TotemForgeLedger();
        CompoundTag all = tag.getCompound("Pours");
        for (String id : all.getAllKeys()) {
            CompoundTag e = all.getCompound(id);
            GlobalPos pos = pos(e);
            if (pos != null) {
                ledger.pours.put(UUID.fromString(id), pos);
                if (e.contains("Ready")) {
                    ledger.ready.put(UUID.fromString(id), e.getLong("Ready"));
                }
            }
        }
        return ledger;
    }

    @Nullable
    private static GlobalPos pos(CompoundTag e) {
        ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
        return dim == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos")));
    }

    /** Once a minute: pourers whose totem has set hear it, wherever the forge is. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BSPCore.MODID)
    public static final class Ticker {
        private static int ticks;

        private Ticker() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && ++ticks >= 1200) {
                ticks = 0;
                get(event.getServer()).tellDue(event.getServer(), System.currentTimeMillis());
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        pours.forEach((id, pos) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
            if (ready.containsKey(id)) {
                e.putLong("Ready", ready.get(id));
            }
            all.put(id.toString(), e);
        });
        tag.put("Pours", all);
        return tag;
    }
}
