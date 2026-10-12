package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.chunk.ChunkLoading;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.StealState;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only lookups about Shatter Totems for other mods (BSP-Teleport first): where a player's
 * totems stand, which is the main one, whether one is being stolen right now, and the highest level
 * of an upgrade across the totems a player owns. Nothing here changes a totem.
 */
public final class BspTotems {
    private BspTotems() {}

    /** A steal in progress on one of a player's totems. */
    public record Steal(GlobalPos where, UUID thief, String thiefName, float fractionLeft) {}

    /** Where the player's placed totems stand, on this server, in every dimension; empty when none. */
    public static Set<GlobalPos> placed(MinecraftServer server, UUID owner) {
        Set<GlobalPos> set = TotemLedger.get(server).placedFor(owner);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    /** True when the totem at {@code pos} is its owner's main totem (the one that loads chunks and runs auras). */
    public static boolean isMain(ServerLevel level, BlockPos pos) {
        return ChunkLoading.isMain(level, pos);
    }

    /** The block entity of a placed totem, if one stands at {@code where} on this server. */
    public static Optional<ShatterTotemBlockEntity> totemAt(MinecraftServer server, GlobalPos where) {
        ServerLevel level = server.getLevel(where.dimension());
        return level != null && level.getBlockEntity(where.pos()) instanceof ShatterTotemBlockEntity be ? Optional.of(be) : Optional.empty();
    }

    /** The steal in progress on any of the owner's placed totems, if there is one. */
    public static Optional<Steal> beingStolen(MinecraftServer server, UUID owner) {
        for (GlobalPos where : placed(server, owner)) {
            Optional<ShatterTotemBlockEntity> be = totemAt(server, where);
            if (be.isPresent()) {
                Optional<StealState> steal = be.get().getSteal();
                if (steal.isPresent()) {
                    StealState s = steal.get();
                    return Optional.of(new Steal(where, s.thief(), s.thiefName(), s.fractionLeft()));
                }
            }
        }
        return Optional.empty();
    }

    /** The upgrade named like the enum constant ("SWIFTNESS", case does not matter); null when there is no such upgrade. */
    @Nullable
    public static TotemUpgrades.Buff upgrade(String name) {
        try {
            return TotemUpgrades.Buff.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The highest level of {@code buff} across the totems {@code player} owns: those placed on this
     * server and those carried in their inventory. 0 when they own none, or none has it.
     */
    public static int highestLevel(Player player, TotemUpgrades.Buff buff) {
        MinecraftServer server = player.getServer();
        int best = server == null ? 0 : highestPlacedLevel(server, player.getUUID(), buff);
        for (ItemStack stack : player.getInventory().items) {
            best = Math.max(best, carriedLevel(stack, player.getUUID(), buff));
        }
        best = Math.max(best, carriedLevel(player.getOffhandItem(), player.getUUID(), buff));
        return best;
    }

    /** As {@link #highestLevel(Player, TotemUpgrades.Buff)} for a player by id; counts carried totems only while they are online here. */
    public static int highestLevel(MinecraftServer server, UUID player, TotemUpgrades.Buff buff) {
        Player online = server.getPlayerList().getPlayer(player);
        return online != null ? highestLevel(online, buff) : highestPlacedLevel(server, player, buff);
    }

    private static int highestPlacedLevel(MinecraftServer server, UUID owner, TotemUpgrades.Buff buff) {
        int best = 0;
        for (GlobalPos where : placed(server, owner)) {
            Optional<ShatterTotemBlockEntity> be = totemAt(server, where);
            if (be.isPresent() && be.get().isOwner(owner)) {
                best = Math.max(best, be.get().getUpgradeLevel(buff));
            }
        }
        return best;
    }

    private static int carriedLevel(ItemStack stack, UUID owner, TotemUpgrades.Buff buff) {
        if (!TotemInventories.isTotem(stack)) {
            return 0;
        }
        Optional<TotemOwner> o = TotemOwner.fromStack(stack);
        return o.isPresent() && o.get().uuid().equals(owner) ? TotemUpgrades.getLevel(stack, buff) : 0;
    }
}
