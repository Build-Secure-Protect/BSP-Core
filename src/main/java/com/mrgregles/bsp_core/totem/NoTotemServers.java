package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servers listed in {@code restrictions.noTotemServers}: a Shatter Totem cannot be placed or dropped anywhere on them, but may be
 * held. On such a server {@link BSPConfig#isDimensionAllowed} answers no for every world, which refuses placing and dropping;
 * this class handles the rest: a totem carried at logout is kept in the database for the player and, when their previous
 * position on a server that allows placement is known, that server is asked to place it back there; a player holding a totem is
 * told the rule once per login and after each respawn; the admin's totem commands warn; totems already standing there are listed
 * at start.
 *
 * <p>The previous position: every server that allows placement records where each player last stood in a world that allows it
 * (every 30 seconds while they play there, and at logout) in the shared table {@code totem_spots}. A no-totem server reads the
 * record at login. The receiving server places the totems at that spot when it collects the request ({@code totem_returns},
 * announced over Redis and polled every {@code storage.noticeSeconds}); a player who logs in anywhere first simply collects
 * the totems in hand, and nothing is placed.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class NoTotemServers {
    /** A player's last spot on a server that allows totem placement. */
    public record Spot(String serverId, String dimension, net.minecraft.core.BlockPos pos) {}

    /** Ticks between saves of a player's spot while they play on a server that allows placement. */
    static final int SPOT_SAVE_TICKS = 600;

    /** Players already told the rule since their login or last respawn. */
    private static final Set<UUID> TOLD = new HashSet<>();
    /** On a no-totem server: the previous spot of each player online here, as read at login. */
    private static final java.util.Map<UUID, Spot> PREVIOUS = new java.util.HashMap<>();
    /** On a no-totem server: players whose previous spot has been looked up (found or not), so the rule is told with the right ending. */
    private static final Set<UUID> LOOKED_UP = new HashSet<>();
    /** On a server that allows placement: each online player's latest spot here, and the last one saved. */
    private static final java.util.Map<UUID, Spot> SEEN = new java.util.HashMap<>(), SAVED = new java.util.HashMap<>();

    private NoTotemServers() {}

    /** Whether this server allows no totem placement anywhere. */
    public static boolean here() {
        return BSPConfig.isNoTotemServer();
    }

    /**
     * Whether the player's previous position on a server that allows placement is known, so a logout here sends the totem back
     * there. Unknown for now: the totem waits in the database and comes back at the next login.
     */
    public static boolean previousPositionKnown(ServerPlayer player) {
        return PREVIOUS.containsKey(player.getUUID());
    }

    /** The player's previous spot on a server that allows placement, as read at login here; null when they never stood on one. */
    @javax.annotation.Nullable
    public static Spot previous(UUID player) {
        return PREVIOUS.get(player);
    }

    /** What the lookup at login found (null: none). Public for the tests. */
    public static void lookedUp(UUID player, @javax.annotation.Nullable Spot spot) {
        if (spot != null) {
            PREVIOUS.put(player, spot);
        } else {
            PREVIOUS.remove(player);
        }
        LOOKED_UP.add(player);
    }

    /** The spot to record for a player standing on this server now: null on a no-totem server or outside a world that allows placement. */
    @javax.annotation.Nullable
    public static Spot spotHere(ServerPlayer player) {
        if (here() || !BSPConfig.isDimensionAllowed(player.level().dimension().location())) {
            return null;
        }
        return new Spot(BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, ""), player.level().dimension().location().toString(),
                TotemPlacer.grounded(player.serverLevel(), player.blockPosition()));
    }

    /**
     * On the server named in a return request: places the totems among {@code encoded} (items waiting for the player) at the spot,
     * and returns what is left to go back into the database (other items, and totems that found no spot). A player online here
     * gets everything in hand instead. The player is told where the totems stand, at their next login if they are away.
     */
    public static List<String> placeReturned(MinecraftServer server, UUID player, Spot spot, List<String> encoded) {
        List<ItemStack> items = new ArrayList<>();
        for (String snbt : encoded) {
            try {
                ItemStack stack = ItemStack.of(net.minecraft.nbt.TagParser.parseTag(snbt));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core: could not read an item owed to {}: {}", player, snbt);
            }
        }
        List<String> left = new ArrayList<>();
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            com.mrgregles.bsp_core.admin.AdminService.deliver(server, player, items, "message.bsp_core.transfer.arrived");
            return left;
        }
        net.minecraft.resources.ResourceLocation dim = net.minecraft.resources.ResourceLocation.tryParse(spot.dimension());
        net.minecraft.server.level.ServerLevel level = dim == null ? null
                : server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
        int placed = 0;
        for (ItemStack stack : items) {
            if (TotemInventories.isTotem(stack) && level != null && TotemPlacer.place(level, spot.pos(), stack)) {
                placed++;
            } else {
                left.add(stack.save(new CompoundTag()).toString());
            }
        }
        if (placed > 0) {
            BSPCore.LOGGER.info("{} logged out on a server without totem placement: {} totem(s) placed back at {} in {}", player, placed, spot.pos(), spot.dimension());
            NetworkStorage.tell(server, player, Component.translatable("message.bsp_core.no_totem.placed_back",
                    spot.pos().getX() + " " + spot.pos().getY() + " " + spot.pos().getZ(), spot.serverId()).withStyle(ChatFormatting.GOLD));
        }
        return left;
    }

    /**
     * Logout on a no-totem server with totems in hand: nothing is placed here. The totems are counted as in transit (never lost or
     * reissued while away) and kept in the database (or, without one, on this server) for the player's next login.
     */
    public static void keepForNextLogin(ServerPlayer player, List<ItemStack> totems) {
        MinecraftServer server = player.server;
        TotemLedger ledger = TotemLedger.get(server);
        long now = server.overworld().getGameTime();
        for (ItemStack totem : totems) {
            TotemIdentity.ensure(totem);
            ledger.inTransit(TotemIdentity.id(totem), TotemIdentity.instance(totem), player.getUUID(), totem.getTag(), now);
        }
        if (NetworkStorage.enabled()) {
            List<String> encoded = new ArrayList<>();
            totems.forEach(t -> encoded.add(t.save(new CompoundTag()).toString()));
            NetworkStorage.addPending(player.getUUID(), encoded);
        } else {
            SeasonData season = SeasonData.get(server);
            List<ItemStack> owed = season.pendingItems.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
            totems.forEach(t -> owed.add(t.copy()));
            season.setDirty();
        }
        Spot back = PREVIOUS.get(player.getUUID());
        if (back != null && NetworkStorage.enabled()) {
            NetworkStorage.requestTotemReturn(player.getUUID(), back); // written after the items, by the same storage thread
            BSPCore.LOGGER.info("{} logged out holding {} totem(s) on a server without totem placement; {} is asked to place them back at {}",
                    player.getGameProfile().getName(), totems.size(), back.serverId(), back.pos());
        } else {
            BSPCore.LOGGER.info("{} logged out holding {} totem(s) on a server without totem placement; kept for their next login",
                    player.getGameProfile().getName(), totems.size());
        }
    }

    /** The rule as the player reads it, for one holding a totem here; the variant depends on whether a logout can send it back. */
    public static Component rule(ServerPlayer player) {
        return Component.translatable(previousPositionKnown(player) ? "message.bsp_core.no_totem.held" : "message.bsp_core.no_totem.held_kept")
                .withStyle(ChatFormatting.GOLD);
    }

    /** Tells a player holding a totem here the rule, once since their login or last respawn. Returns whether it was said now. */
    public static boolean tellOnce(ServerPlayer player) {
        if (!here() || TOLD.contains(player.getUUID()) || !holdsTotem(player)
                || (NetworkStorage.enabled() && !LOOKED_UP.contains(player.getUUID()))) { // wait for the lookup, so the line ends right
            return false;
        }
        TOLD.add(player.getUUID());
        player.displayClientMessage(rule(player), false);
        return true;
    }

    /** Forgets that the player was told, so the next check tells them again (login, respawn; tests). */
    public static void forgetTold(UUID player) {
        TOLD.remove(player);
    }

    private static boolean holdsTotem(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (TotemInventories.isTotem(inv.getItem(i))) {
                return true;
            }
        }
        return TotemInventories.isTotem(player.containerMenu.getCarried());
    }

    /** A line for an admin running a totem command here: the command still runs. */
    public static void warnAdmin(CommandSourceStack source) {
        if (here()) {
            source.sendSystemMessage(Component.translatable("message.bsp_core.no_totem.admin").withStyle(ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID id = event.getEntity().getUUID();
        forgetTold(id);
        PREVIOUS.remove(id);
        LOOKED_UP.remove(id);
        if (here() && NetworkStorage.enabled()) {
            NetworkStorage.readTotemSpot(id, spot -> lookedUp(id, spot));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forgetTold(event.getEntity().getUUID());
    }

    /** Runs after the logout placement (which reads the previous spot): forgets the player here, and on a server that allows placement records their last spot. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        forgetTold(id);
        PREVIOUS.remove(id);
        LOOKED_UP.remove(id);
        Spot last = event.getEntity() instanceof ServerPlayer sp && spotHere(sp) != null ? spotHere(sp) : SEEN.get(id);
        SEEN.remove(id);
        SAVED.remove(id);
        if (last != null && NetworkStorage.enabled()) {
            NetworkStorage.saveTotemSpot(id, last);
        }
    }

    /**
     * Once a second: on a no-totem server a player holding a totem hears the rule (once per login and respawn); on a server that
     * allows placement the player's spot is noted, and saved every {@link #SPOT_SAVE_TICKS} when it moved.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.isAlive()) {
            return;
        }
        if (here()) {
            tellOnce(player);
            return;
        }
        Spot spot = spotHere(player);
        if (spot != null) {
            SEEN.put(player.getUUID(), spot);
        }
        if (player.tickCount % SPOT_SAVE_TICKS == 0) {
            Spot seen = SEEN.get(player.getUUID());
            if (seen != null && !seen.equals(SAVED.get(player.getUUID())) && NetworkStorage.enabled()) {
                SAVED.put(player.getUUID(), seen);
                NetworkStorage.saveTotemSpot(player.getUUID(), seen);
            }
        }
    }

    /** At start on a no-totem server: totems already standing here are listed for an admin; they are not moved. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!here()) {
            return;
        }
        int[] n = {0};
        TotemLedger.get(event.getServer()).allPlaced().forEach((owner, positions) -> positions.forEach(where -> {
            n[0]++;
            BSPCore.LOGGER.warn("This server allows no totem placement (restrictions.noTotemServers), but a Shatter Totem owned by {} stands at {} in {}",
                    owner, where.pos(), where.dimension().location());
        }));
        BSPCore.LOGGER.info("No Shatter Totem placement anywhere on this server (restrictions.noTotemServers); {} totem(s) already standing", n[0]);
    }
}
