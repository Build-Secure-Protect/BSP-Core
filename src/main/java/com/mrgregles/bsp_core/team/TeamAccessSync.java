package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.TeamEvent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps clients' view of totem access right when a team changes: a placed totem syncs the owner's
 * teammates with its data, so when someone joins or leaves, every loaded totem of everyone concerned
 * is sent again. The server itself never caches: it asks the team ledger on every check.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class TeamAccessSync {
    private TeamAccessSync() {}

    @SubscribeEvent
    public static void onJoined(TeamEvent.Joined event) {
        resync(event.getServer(), event.getTeamId(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onLeft(TeamEvent.Left event) {
        resync(event.getServer(), event.getTeamId(), event.getPlayer());
    }

    /** Resyncs the player's placed totems and those of the team's current members. */
    private static void resync(MinecraftServer server, UUID teamId, UUID player) {
        Set<UUID> owners = new HashSet<>();
        owners.add(player);
        Team team = TeamLedger.get(server).team(teamId);
        if (team != null) {
            owners.addAll(team.members().keySet());
        }
        resyncOwners(server, owners);
    }

    /** Resyncs every loaded placed totem of these owners. */
    public static void resyncOwners(MinecraftServer server, Set<UUID> owners) {
        TotemLedger totems = TotemLedger.get(server);
        for (UUID owner : owners) {
            for (GlobalPos where : totems.placedFor(owner)) {
                ServerLevel level = server.getLevel(where.dimension());
                if (level != null && level.isLoaded(where.pos()) && level.getBlockEntity(where.pos()) instanceof ShatterTotemBlockEntity totem) {
                    totem.resyncAccess();
                }
            }
        }
    }
}
