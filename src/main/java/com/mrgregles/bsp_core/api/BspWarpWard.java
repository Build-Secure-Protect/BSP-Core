package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.chamber.WarpWard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Warp Ward for other mods (BSP-Teleport request WW-1). A Projector carrying the Mob Chamber's Warp Ward power keeps outsiders from
 * teleporting into or out of its reach. BSP-Core stops ender pearls and chorus fruit itself; a teleport mod asks before it moves a player.
 * An outsider is anyone the projector's Ward does not leave alone (not the owner, their team, Ward access or allies with the perk), who
 * is not covered by new-player protection and not in creative or spectator. Server thread only.
 *
 * <pre>{@code
 * Component why = BspWarpWard.why(player, targetLevel, targetPos);
 * if (why != null) { player.displayClientMessage(why, true); return; }   // BSP-Core's own words: held in, or kept out
 * }</pre>
 */
public final class BspWarpWard {
    private BspWarpWard() {}

    /** Whether a Warp Ward stops {@code player} going from where they stand to {@code to} in {@code toLevel}. */
    public static boolean blocks(ServerPlayer player, ServerLevel toLevel, BlockPos to) {
        return WarpWard.why(player, toLevel, to) != null;
    }

    /** Why a Warp Ward stops the move ("A Warp Ward holds you here" / "keeps you out"), or null if nothing does. */
    @Nullable
    public static Component why(ServerPlayer player, ServerLevel toLevel, BlockPos to) {
        return WarpWard.why(player, toLevel, to);
    }
}
