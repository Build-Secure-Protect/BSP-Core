package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Keeps each online player's client up to date with their own machine tracking: their tags (positions only for tags on this server, which
 * the green outline needs; the screens never show coordinates), slots and prices, the Remote Control unlock, and their Holo Grid layout.
 * Sent whenever {@link TrackerOwners} reports a change, which includes the network read at login.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TrackerSync {
    static {
        TrackerOwners.CHANGED.add(TrackerSync::changed);
    }

    private TrackerSync() {}

    /** Makes sure the listener is in place (the class loads with the mod's event subscribers; tests call this too). */
    public static void init() {
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        init(); // loads the class, and with it the listener
        MekanismAdapter.register();
        BspMachineAdapter.register();
    }

    private static void changed(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            send(online);
        }
    }

    /** Sends the player their tracking view now. */
    public static void send(ServerPlayer player) {
        TrackerSyncPacket packet = new TrackerSyncPacket(view(player));
        BSPNetwork.sendChecked(player, packet, TrackerSyncPacket::encode, "machine tracking");
    }

    /** What the client is told: see the class comment. */
    public static CompoundTag view(ServerPlayer player) {
        MinecraftServer server = player.server;
        int season = TrackerOwners.season(server);
        TrackerRecord r = TrackerOwners.get(server).peek(server, player.getUUID());
        String here = TrackerRules.serverId();
        CompoundTag v = new CompoundTag();
        v.putInt("slots", TrackerRules.slots(r, season));
        v.putInt("free", TrackerRules.freeSlots());
        v.putInt("max", TrackerRules.maxSlots());
        v.putInt("nextPrice", TrackerRules.nextPrice(r, season));
        v.putBoolean("remote", r.remoteIn(season));
        v.putInt("remotePrice", TrackerRules.remotePrice());
        v.putString("server", here);
        v.putInt("outline", com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.TRACKING_OUTLINE_RANGE, 48));
        ListTag tags = new ListTag();
        for (TrackedTag t : r.tags) {
            CompoundTag c = new CompoundTag();
            c.putString("id", t.id());
            c.putString("name", t.name());
            c.putString("block", t.block().toString());
            c.putString("server", t.server());
            c.putString("dim", t.dim().toString());
            if (t.server().equals(here)) {
                c.put("pos", NbtUtils.writeBlockPos(t.pos()));
            }
            tags.add(c);
        }
        v.put("tags", tags);
        v.put("layout", r.layout.copy());
        return v;
    }
}
