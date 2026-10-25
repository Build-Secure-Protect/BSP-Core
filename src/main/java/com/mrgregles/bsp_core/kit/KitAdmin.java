package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.KitAdminPacket;
import com.mrgregles.bsp_core.network.KitEditPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The server side of the admin panel's KITS tab: builds what the tab shows and applies what it
 * sends. The admin check is the packet handler's; {@link #apply} trusts its caller.
 */
public final class KitAdmin {
    private KitAdmin() {}

    /** Server ids an admin may pick: this server's, then every id a kit already names. Network storage adds the rest later. */
    public static List<String> knownServers(MinecraftServer server) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(Kits.serverId());
        ids.addAll(com.mrgregles.bsp_core.storage.NetworkStorage.knownServers());
        for (Kit k : Kits.all(server)) {
            ids.addAll(k.servers);
            ids.addAll(k.grantServers);
        }
        return new ArrayList<>(ids);
    }

    public static KitAdminPacket snapshot(ServerPlayer viewer, boolean open, String note) {
        MinecraftServer server = viewer.server;
        List<CompoundTag> kits = new ArrayList<>();
        for (Kit k : Kits.all(server)) {
            CompoundTag t = k.save();
            t.putInt("Missing", k.missing);
            kits.add(t);
        }
        Kits.RankNames ranks = Kits.rankNames();
        List<KitAdminPacket.Online> online = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(new KitAdminPacket.Online(p.getUUID(), p.getGameProfile().getName()));
        }
        return new KitAdminPacket(open, Admins.isAdmin(viewer), kits, knownServers(server), ranks.names(), ranks.ladder(), online, note);
    }

    /** Sends the tab's data; {@code note} is a message key under {@code gui.bsp_core.kit.note.} or "" for none. */
    public static void send(ServerPlayer viewer, boolean open, String note) {
        BSPNetwork.sendTo(viewer, snapshot(viewer, open, note));
    }

    /** Applies an edit and answers with the note to show; the caller checked the admin. */
    public static String apply(ServerPlayer admin, KitEditPacket msg) {
        MinecraftServer server = admin.server;
        switch (msg.action()) {
            case REFRESH -> {
                return "";
            }
            case SAVE -> {
                if (msg.kit() == null) {
                    return "bad";
                }
                Kit kit = Kit.load(msg.kit());
                if (msg.isNew() && Kits.kit(server, kit.id) != null) {
                    return "id_taken";
                }
                if (com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(kit.icon) || kit.items.stream().anyMatch(com.mrgregles.bsp_core.compat.InventorySyncPlaceholders::is)) {
                    // every redeem would hand out a copy of the item inside the placeholder
                    return note(admin, Kits.Outcome.fail("placeholder"), "saved");
                }
                Kits.Outcome out = Kits.put(server, kit);
                return note(admin, out, "saved");
            }
            case DELETE -> {
                return note(admin, Kits.delete(server, msg.kitId()), "deleted");
            }
            case TEST -> {
                return note(admin, Kits.test(admin, msg.kitId()), "tested");
            }
            case GIVE -> {
                Kits.Outcome out = Kits.give(server, msg.target(), msg.kitId(), KitEvent.How.ADMIN);
                return note(admin, out, server.getPlayerList().getPlayer(msg.target()) != null ? "gave" : "gave_offline");
            }
        }
        return "";
    }

    /** A failure goes to chat (the outcome's words); the note key names what happened for the panel. */
    private static String note(ServerPlayer admin, Kits.Outcome out, String doneKey) {
        if (out.ok()) {
            return doneKey;
        }
        Component why = out.message();
        if (why != null) {
            admin.displayClientMessage(why, false);
        }
        return "refused";
    }
}
