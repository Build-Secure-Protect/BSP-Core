package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * For a teleport mod sending a player to another server of the network. Totems are placed as blocks
 * on an ordinary logout; a transfer must not do that, so the mod calls {@link #beginTransfer} just
 * before the player leaves: every carried totem is taken from them at once and stored for the
 * journey (in the network's pending items table, so the server they arrive at hands it back), and
 * the player is told. If the teleport is cancelled the mod calls {@link #endTransfer} and the totems
 * come straight back. If the player is still here after {@code restrictions.transferSeconds} the
 * transfer is taken as not happened and the totems come back on their own. A totem still waiting in
 * the database after {@code restrictions.transferTotemTimeoutHours} (the owner never logged in
 * anywhere) is placed back at the departure point. Without a shared database the totems are held in
 * memory instead and placed at the departure point if the player does log out, exactly as on an
 * ordinary logout. An ordinary logout, with no transfer announced, places totems as it always did.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class BspTransfer {
    /** A transfer in progress: when it began, the totems held here (none when they went to the database), and whether they did. */
    private record Pending(long since, List<ItemStack> held, boolean stored) {}

    private static final Map<UUID, Pending> ACTIVE = new ConcurrentHashMap<>();
    private static int tick;

    private BspTransfer() {}

    public static int transferSeconds() {
        return BSPConfig.getOr(BSPConfig.TRANSFER_SECONDS, 10);
    }

    /**
     * Takes the player's carried totems for the transfer and tells them. Call just before sending
     * them away. Returns how many totems were taken (0 is fine: the flag is set either way, so a
     * logout within the window is treated as the transfer).
     */
    public static int beginTransfer(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        endTransfer(player); // a second call starts afresh
        List<ItemStack> totems = TotemInventories.removeAll(player);
        totems.addAll(com.mrgregles.bsp_core.zone.ZoneHandler.takeHeld(player));
        markInTransit(server, player, totems);
        boolean stored = false;
        if (!totems.isEmpty() && NetworkStorage.enabled()) {
            List<String> encoded = new ArrayList<>();
            totems.forEach(t -> encoded.add(t.save(new CompoundTag()).toString()));
            NetworkStorage.addPending(player.getUUID(), encoded);
            stored = true;
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                // remember the departure point: if the totem is still waiting after the timeout, it is placed back here
                var target = com.mrgregles.bsp_core.totem.TotemPlacer.safeTargetFor(sp);
                com.mrgregles.bsp_core.totem.TransferLedger.get(server).put(player.getUUID(), System.currentTimeMillis(), net.minecraft.core.GlobalPos.of(target.level().dimension(), target.pos()));
            }
        }
        ACTIVE.put(player.getUUID(), new Pending(System.currentTimeMillis(), stored ? List.of() : totems, stored));
        if (!totems.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.bsp_core.transfer.stored", totems.size()).withStyle(ChatFormatting.GOLD), false);
            BSPCore.LOGGER.info("{} is transferring with {} totem(s) ({})", player.getGameProfile().getName(), totems.size(), stored ? "stored in the database" : "held here");
        }
        return totems.size();
    }

    public static boolean isTransferring(UUID player) {
        return ACTIVE.containsKey(player);
    }

    /** Totems taken from the player for a transfer and held here (without network storage), not yet handed back. */
    public static int heldTotems(UUID player) {
        Pending p = ACTIVE.get(player);
        return p == null ? 0 : (int) p.held.stream().filter(TotemInventories::isTotem).count();
    }

    /**
     * The teleport was cancelled (damage, movement, anything) or never happened: the totems come
     * straight back into the player's inventory, into the hand slot or the first free slot, moving
     * another item out of the way if it must. Returns false when no transfer was in progress.
     */
    public static boolean endTransfer(Player player) {
        return end(player, "message.bsp_core.transfer.returned");
    }

    private static boolean end(Player player, String messageKey) {
        Pending p = ACTIVE.remove(player.getUUID());
        if (p == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (p.stored && server != null) {
            UUID id = player.getUUID();
            NetworkStorage.takePending(List.of(id), owed -> {
                Player again = server.getPlayerList().getPlayer(id);
                List<ItemStack> items = decode(owed.getOrDefault(id, List.of()));
                if (again != null) {
                    handBack(again, items, messageKey);
                } else if (!items.isEmpty()) {
                    // they left after all: the items go back to the table for the server they reach
                    List<String> encoded = new ArrayList<>();
                    items.forEach(t -> encoded.add(t.save(new CompoundTag()).toString()));
                    NetworkStorage.addPending(id, encoded);
                }
            });
        } else {
            handBack(player, p.held, messageKey);
        }
        return true;
    }

    private static List<ItemStack> decode(List<String> encoded) {
        List<ItemStack> out = new ArrayList<>();
        for (String snbt : encoded) {
            try {
                ItemStack stack = ItemStack.of(TagParser.parseTag(snbt));
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core: could not read a stored item: {}", snbt);
            }
        }
        return out;
    }

    /** The ledger stops counting these totems as held: they are in transit, so a slow proxy or a return visit never reissues them. */
    private static void markInTransit(MinecraftServer server, Player player, List<ItemStack> totems) {
        com.mrgregles.bsp_core.data.TotemLedger ledger = com.mrgregles.bsp_core.data.TotemLedger.get(server);
        long now = server.overworld().getGameTime();
        for (ItemStack totem : totems) {
            com.mrgregles.bsp_core.totem.TotemIdentity.ensure(totem);
            ledger.inTransit(com.mrgregles.bsp_core.totem.TotemIdentity.id(totem), com.mrgregles.bsp_core.totem.TotemIdentity.instance(totem), player.getUUID(), totem.getTag(), now);
        }
    }

    /**
     * Totems go back through {@link TotemInventories#giveBack} (never dropped); anything else is added or dropped. A totem that
     * arrives is recorded as held with the instance it carries: it came through the database, so it is the real one even when this
     * server's ledger remembers another (a reissue on another server), and the identity check must not spend it. The player's
     * departure record here is cleared (they are back). {@code messageKey} null says nothing (the caller tells the player itself).
     */
    public static void handBack(Player player, List<ItemStack> items, @Nullable String messageKey) {
        if (items.isEmpty()) {
            return;
        }
        if (player.getServer() != null) {
            com.mrgregles.bsp_core.totem.TransferLedger.get(player.getServer()).remove(player.getUUID()); // they are back on this server
        }
        for (ItemStack stack : items) {
            if (TotemInventories.isTotem(stack)) {
                if (player.getServer() != null) {
                    com.mrgregles.bsp_core.totem.TotemIdentity.ensure(stack);
                    com.mrgregles.bsp_core.data.TotemLedger.get(player.getServer()).arrived(com.mrgregles.bsp_core.totem.TotemIdentity.id(stack), com.mrgregles.bsp_core.totem.TotemIdentity.instance(stack),
                            player.getUUID(), stack.getTag(), player.getServer().overworld().getGameTime());
                }
                TotemInventories.giveBack(player, stack.copy());
            } else if (!player.getInventory().add(stack.copy())) {
                player.drop(stack.copy(), false);
            }
        }
        if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD), false);
        }
    }

    /**
     * The logout handler: the player left while a transfer was in progress. Returns the totems held
     * here that must still be placed at the departure point (none when they travel through the
     * database), or null when no transfer was in progress and the logout is an ordinary one.
     */
    @Nullable
    public static List<ItemStack> takeOnLogout(UUID player) {
        Pending p = ACTIVE.remove(player);
        return p == null ? null : p.held;
    }

    /** Once a second: transfers that never happened give the totems back. Once a minute: totems nobody collected within the timeout are placed back here. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0) {
            return;
        }
        if (!ACTIVE.isEmpty()) {
            expire(event.getServer(), System.currentTimeMillis());
        }
        if (tick % 1200 == 0) {
            placeBackTimedOut(event.getServer(), System.currentTimeMillis());
        }
    }

    public static long timeoutMs() {
        return BSPConfig.getOr(BSPConfig.TRANSFER_TIMEOUT_HOURS, 48) * 3_600_000L;
    }

    /**
     * Departures older than {@code restrictions.transferTotemTimeoutHours}: if the player's totems
     * are still waiting in the database (they never logged in anywhere), they are placed back at the
     * departure point; anything else waiting with them stays owed. A departure whose totems are gone
     * (collected on another server) is simply forgotten.
     */
    public static void placeBackTimedOut(MinecraftServer server, long now) {
        var ledger = com.mrgregles.bsp_core.totem.TransferLedger.get(server);
        List<UUID> due = ledger.due(now, timeoutMs());
        if (due.isEmpty()) {
            return;
        }
        if (!NetworkStorage.enabled()) {
            due.forEach(ledger::remove);
            return;
        }
        NetworkStorage.takePending(due, owed -> {
            for (UUID id : due) {
                var departure = ledger.departure(id);
                ledger.remove(id);
                List<ItemStack> items = decode(owed.getOrDefault(id, List.of()));
                List<String> keep = new ArrayList<>();
                int placed = 0;
                for (ItemStack stack : items) {
                    if (TotemInventories.isTotem(stack) && departure != null && placeAt(server, departure.where(), stack)) {
                        placed++;
                    } else {
                        keep.add(stack.save(new CompoundTag()).toString());
                    }
                }
                if (!keep.isEmpty()) {
                    NetworkStorage.addPending(id, keep);
                }
                if (placed > 0) {
                    BSPCore.LOGGER.info("{} never arrived after a transfer: {} totem(s) placed back at {}", id, placed, departure.where());
                }
            }
        });
    }

    private static boolean placeAt(MinecraftServer server, net.minecraft.core.GlobalPos where, ItemStack totem) {
        var level = server.getLevel(where.dimension());
        if (level == null) {
            return false;
        }
        if (!com.mrgregles.bsp_core.totem.TotemPlacer.place(level, where.pos(), totem)) {
            level.addFreshEntity(new com.mrgregles.bsp_core.totem.ShatterTotemItemEntity(level, where.pos().getX() + 0.5, where.pos().getY() + 0.5, where.pos().getZ() + 0.5, totem));
        }
        return true;
    }

    /** Ends every transfer older than the window for players still online; forgets those who are gone. */
    public static void expire(MinecraftServer server, long now) {
        long window = transferSeconds() * 1000L;
        for (Map.Entry<UUID, Pending> e : new ArrayList<>(ACTIVE.entrySet())) {
            if (now - e.getValue().since < window) {
                continue;
            }
            Player player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                end(player, "message.bsp_core.transfer.timeout");
            } else {
                ACTIVE.remove(e.getKey());
            }
        }
    }
}
