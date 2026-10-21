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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.world.level.Level;
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

    /**
     * A steal in progress anywhere on the network. {@code serverId} is that server's {@code
     * storage.serverId}; {@code local} is true for a steal on this server. {@code fractionLeft} is
     * exact for a local steal and, for a remote one, estimated from when it started and is expected
     * to end (a steal pauses while the thief is outside the radius, so it may run a little behind).
     */
    public record StealAnywhere(String serverId, boolean local, ResourceKey<Level> dimension, BlockPos pos, @Nullable UUID owner, UUID thief, String thiefName, float fractionLeft) {}

    /** A placed totem and who owns it. */
    public record Placed(UUID owner, GlobalPos where) {}

    /** Every placed totem this server knows of, in every dimension. */
    public static java.util.List<Placed> allPlaced(MinecraftServer server) {
        java.util.List<Placed> out = new java.util.ArrayList<>();
        TotemLedger.get(server).allPlaced().forEach((owner, set) -> set.forEach(where -> out.add(new Placed(owner, where))));
        return out;
    }

    /** The placed totems within {@code radius} blocks of {@code pos} in {@code level} (straight-line distance). */
    public static java.util.List<Placed> placedNear(ServerLevel level, BlockPos pos, int radius) {
        java.util.List<Placed> out = new java.util.ArrayList<>();
        double r2 = (double) radius * radius;
        TotemLedger.get(level.getServer()).allPlaced().forEach((owner, set) -> {
            for (GlobalPos where : set) {
                if (where.dimension() == level.dimension() && where.pos().distSqr(pos) <= r2) {
                    out.add(new Placed(owner, where));
                }
            }
        });
        return out;
    }

    /** The radius, in blocks, a thief must stay within while stealing (and within which raid rules apply). */
    public static int stealRadius() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.STEAL_RADIUS, 16);
    }

    /** Whether a block state is a placed Shatter Totem. */
    public static boolean isTotem(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
    }

    /**
     * Whether an item stack is a live Shatter Totem: {@code bsp_core:shatter_totem} and not a spent husk. A totem that has not
     * been given its identity yet counts as live; a husk (a stale copy left behind when the totem was reissued), any other
     * item and the empty stack do not. The same rule BSP-Core applies to every inventory it checks.
     *
     * <p>For inventory sync mods (BSP-InventorySync request R1): a snapshot must never carry a live totem, or the totem is
     * duplicated, one in the world or waiting in BSP-Core's records and one in the synced inventory. Drop every stack this
     * says yes to before saving.
     */
    public static boolean isTotem(ItemStack stack) {
        return TotemInventories.isTotem(stack);
    }

    /**
     * Hands a totem back to a player without ever dropping the totem: into the selected hotbar slot when it is empty, else
     * the first free slot. When the inventory is full, the item in the selected slot (or, when that holds a totem, the
     * first slot that does not) is dropped at the player's feet to make room; when every slot holds a totem, the totem
     * goes on the cursor. Server thread only.
     *
     * <p>For inventory sync mods (BSP-InventorySync request R2): live totems found in an inventory that a restore is about
     * to replace are handed back through this call, so they stay with the player and BSP-Core's rules see them arrive.
     */
    public static void giveBack(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        TotemInventories.giveBack(player, stack);
    }

    /** The steal running on the totem at {@code pos}, if one is. */
    public static Optional<Steal> thiefAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity be) {
            Optional<StealState> steal = be.getSteal();
            if (steal.isPresent()) {
                StealState s = steal.get();
                return Optional.of(new Steal(GlobalPos.of(level.dimension(), pos.immutable()), s.thief(), s.thiefName(), s.fractionLeft()));
            }
        }
        return Optional.empty();
    }

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

    /**
     * Every steal running on this server (read live from the totems) and on the other servers of
     * the network (as last read from the shared database; see {@code storage.noticeSeconds}).
     * Without network storage only this server's steals are listed.
     */
    public static java.util.List<StealAnywhere> stealsAnywhere(MinecraftServer server) {
        java.util.List<StealAnywhere> out = new java.util.ArrayList<>();
        String here = com.mrgregles.bsp_core.BSPConfig.STORAGE_SERVER_ID.get();
        TotemLedger.get(server).allPlaced().forEach((owner, set) -> {
            for (GlobalPos where : set) {
                Optional<ShatterTotemBlockEntity> be = totemAt(server, where);
                if (be.isPresent() && be.get().getSteal().isPresent()) {
                    StealState s = be.get().getSteal().get();
                    out.add(new StealAnywhere(here, true, where.dimension(), where.pos(), owner, s.thief(), s.thiefName(), s.fractionLeft()));
                }
            }
        });
        long now = System.currentTimeMillis();
        for (NetworkStorage.RemoteSteal r : NetworkStorage.stealsElsewhere()) {
            if (r.serverId().equals(here)) {
                continue;
            }
            ResourceLocation dim = ResourceLocation.tryParse(r.dimension());
            if (dim == null) {
                continue;
            }
            float left = r.endsMs() <= r.startedMs() ? 0 : (r.endsMs() - now) / (float) (r.endsMs() - r.startedMs());
            out.add(new StealAnywhere(r.serverId(), false, ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(r.x(), r.y(), r.z()),
                    r.owner(), r.thief(), r.thiefName(), Math.max(0f, Math.min(1f, left))));
        }
        return out;
    }

    /** The steals running anywhere on the network on totems owned by {@code owner}. */
    public static java.util.List<StealAnywhere> stealsAnywhere(MinecraftServer server, UUID owner) {
        java.util.List<StealAnywhere> out = new java.util.ArrayList<>();
        for (StealAnywhere s : stealsAnywhere(server)) {
            if (owner.equals(s.owner())) {
                out.add(s);
            }
        }
        return out;
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
