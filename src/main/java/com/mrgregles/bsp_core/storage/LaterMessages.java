package com.mrgregles.bsp_core.storage;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat lines kept for players who were offline when staff did something to their account (a refund, a cancelled market lot), shown
 * at their next login on this server. Only for a server without network storage: on a network the database holds them
 * ({@link NetworkStorage#tellOrKeep}). Saved with the overworld; at most {@link #MAX} per player, oldest dropped first.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public class LaterMessages extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_later_messages";
    public static final int MAX = 50;

    private final Map<UUID, List<String>> waiting = new HashMap<>();

    public static LaterMessages get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LaterMessages::load, LaterMessages::new, DATA_NAME);
    }

    /** Keeps {@code message} for {@code player}'s next login. */
    public void add(UUID player, Component message) {
        List<String> list = waiting.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(Component.Serializer.toJson(message));
        while (list.size() > MAX) {
            list.remove(0);
        }
        setDirty();
    }

    /** The player's kept lines, oldest first, now removed. */
    public List<Component> take(UUID player) {
        List<String> list = waiting.remove(player);
        if (list == null) {
            return List.of();
        }
        setDirty();
        List<Component> out = new ArrayList<>();
        for (String json : list) {
            try {
                Component c = Component.Serializer.fromJson(json);
                if (c != null) {
                    out.add(c);
                }
            } catch (RuntimeException e) {
                BSPCore.LOGGER.warn("A kept message for {} could not be read: {}", player, e.toString());
            }
        }
        return out;
    }

    public int waiting(UUID player) {
        return waiting.getOrDefault(player, List.of()).size();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            for (Component c : get(p.getServer()).take(p.getUUID())) {
                p.displayClientMessage(c, false);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        waiting.forEach((id, list) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            ListTag lines = new ListTag();
            for (String json : list) {
                lines.add(StringTag.valueOf(json));
            }
            t.put("Lines", lines);
            players.add(t);
        });
        tag.put("Players", players);
        return tag;
    }

    public static LaterMessages load(CompoundTag tag) {
        LaterMessages m = new LaterMessages();
        for (Tag x : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            List<String> lines = new ArrayList<>();
            for (Tag l : t.getList("Lines", Tag.TAG_STRING)) {
                lines.add(l.getAsString());
            }
            if (!lines.isEmpty()) {
                m.waiting.put(t.getUUID("Id"), lines);
            }
        }
        return m;
    }
}
