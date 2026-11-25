package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Inventory Link rule (owner decisions RC1 to RC5): a machine screen operated from the Holo Grid through a transmitter without the
 * Inventory Link upgrade is locked. Items move only inside the machine and every button and switch works, but nothing moves between the
 * machine and the player's own inventory. Two layers:
 * <ul>
 * <li>The known ways are refused before they happen ({@link #allowClick}, and the mixins for the recipe book, JEI, EMI, AE2 and Refined
 * Storage): clicks on the player's slots, shift-clicks, hotbar swaps, double-click gathering, dropping out of the screen.</li>
 * <li>Anything else (another mod's own transfer, a menu button that hands an item over) is undone by comparing, item by item with its
 * data, what the player carries and what the machine and the cursor hold with how both were ({@link #settle}): every tick, after each
 * menu button, around the opening use, before a pickup from the ground, and before death or leaving. Only a true transfer is undone, the
 * same items gone from one side and arrived on the other; a pickup, a drop, a gift, wear, sorting one's own inventory or the machine's
 * own work changes one side only and is left alone.</li>
 * </ul>
 * An item still held on the cursor when the screen closes goes back into the machine, or, when it is full, drops beside it with a message
 * ({@link #beforeRemoved}). A screen keeps the state it opened with: one opened before the upgrade was bought stays locked until closed.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class InventoryLink {
    /** A locked screen: its machine, its player, and what each side held when last compared. */
    private static final class Lock {
        final BlockEntity machine;
        final ServerPlayer player;
        Map<Kind, Integer> playerBase, machineBase;

        Lock(BlockEntity machine, ServerPlayer player, AbstractContainerMenu menu) {
            this.machine = machine;
            this.player = player;
            this.playerBase = carried(player);
            this.machineBase = held(machine, menu);
        }
    }

    /** An item with its data (enchantments, contents, charge), as one kind to count; count left out. */
    record Kind(Item item, @Nullable net.minecraft.nbt.CompoundTag tag) {
        static Kind of(ItemStack st) {
            return new Kind(st.getItem(), st.getTag() == null ? null : st.getTag().copy());
        }

        boolean matches(ItemStack st) {
            return st.is(item) && java.util.Objects.equals(tag, st.getTag());
        }
    }

    /** The locked menus; let go when the menu is. Read on the server thread only ({@link #locked(AbstractContainerMenu)}). */
    private static final Map<AbstractContainerMenu, Lock> LOCKS = Collections.synchronizedMap(new WeakHashMap<>());
    /** AE2 terminal actions that move items into the player's inventory (by name: BSP-Core never loads an AE2 class). */
    private static final Set<String> AE2_TO_INVENTORY = Set.of("SHIFT_CLICK", "MOVE_REGION", "ROLL_UP", "ROLL_DOWN", "CRAFT_SHIFT", "CRAFT_ALL");

    private InventoryLink() {}

    /** Locks {@code menu}, the screen of {@code machine} just opened for {@code player} (and for tests). */
    public static void lock(AbstractContainerMenu menu, BlockEntity machine, ServerPlayer player) {
        LOCKS.put(menu, new Lock(machine, player, menu));
    }

    /** Whether {@code menu} is a locked screen: on the server thread only (a client's menus are never locked, and AE2 asks on both). */
    public static boolean locked(AbstractContainerMenu menu) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return menu != null && server != null && server.isSameThread() && LOCKS.containsKey(menu);
    }

    /** Whether the player's open screen is a locked one. */
    public static boolean locked(Player player) {
        return player instanceof ServerPlayer && locked(player.containerMenu);
    }

    /** Whether a slot holds the player's own items: their inventory, armour or offhand, as vanilla or as Forge's item handler wrappers. */
    public static boolean playerSlot(Slot slot) {
        if (slot.container instanceof Inventory) {
            return true;
        }
        if (slot instanceof SlotItemHandler h) {
            IItemHandler handler = h.getItemHandler();
            if (handler instanceof InvWrapper w && w.getInv() instanceof Inventory) {
                return true;
            }
            String n = handler.getClass().getSimpleName();
            return n.startsWith("Player") && n.contains("Inv");
        }
        return false;
    }

    /** Whether a slot click in {@code menu} may go ahead: always, unless the menu is locked and the click would reach the player's items. */
    public static boolean allowClick(AbstractContainerMenu menu, int slotId, int button, ClickType type) {
        if (!locked(menu)) {
            return true;
        }
        return switch (type) {
            // shift-click, number keys and F (hotbar and offhand), Q, middle-click copy, double-click gathering: all reach the inventory
            case QUICK_MOVE, SWAP, THROW, CLONE, PICKUP_ALL -> false;
            // a click outside the screen drops what is held; a drag's start and end markers carry no slot
            case PICKUP -> slotId != AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && !playerSlot(menu, slotId);
            case QUICK_CRAFT -> slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE || !playerSlot(menu, slotId);
        };
    }

    private static boolean playerSlot(AbstractContainerMenu menu, int slotId) {
        return slotId >= 0 && slotId < menu.slots.size() && playerSlot(menu.slots.get(slotId));
    }

    /** For AE2's terminals: whether {@code action} (an AE2 {@code InventoryAction}) may run in {@code menu}. */
    public static boolean allowAe2(AbstractContainerMenu menu, Object action) {
        return !locked(menu) || action == null || !AE2_TO_INVENTORY.contains(action.toString());
    }

    // ------------------------------------------------------------------ undoing what moved anyway

    /** What the player carries (inventory, armour, offhand), per kind. Totals, not slots: sorting one's own inventory is not a move. */
    static Map<Kind, Integer> carried(Player player) {
        Map<Kind, Integer> out = new HashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            count(out, inv.getItem(i));
        }
        return out;
    }

    /** What the machine (all its item handlers) and the screen's cursor hold, per kind. */
    static Map<Kind, Integer> held(BlockEntity machine, @Nullable AbstractContainerMenu menu) {
        Map<Kind, Integer> out = new HashMap<>();
        if (!machine.isRemoved()) {
            for (IItemHandler h : handlers(machine)) {
                for (int s = 0; s < h.getSlots(); s++) {
                    count(out, h.getStackInSlot(s));
                }
            }
        }
        if (menu != null) {
            count(out, menu.getCarried());
        }
        return out;
    }

    private static void count(Map<Kind, Integer> into, ItemStack st) {
        if (!st.isEmpty()) {
            into.merge(Kind.of(st), st.getCount(), Integer::sum);
        }
    }

    /** After a menu button in a locked screen: undoes what it moved (for {@code RemoteMenuPacketsMixin}). */
    public static void afterButton(AbstractContainerMenu menu) {
        Lock l = locked(menu) ? LOCKS.get(menu) : null;
        if (l != null) {
            settle(l, menu);
        }
    }

    /** Every tick: each locked screen still open is checked. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LOCKS.isEmpty()) {
            return;
        }
        List<Map.Entry<AbstractContainerMenu, Lock>> open;
        synchronized (LOCKS) {
            open = new ArrayList<>(LOCKS.entrySet());
        }
        for (var e : open) {
            if (e.getValue().player.containerMenu == e.getKey()) {
                settle(e.getValue(), e.getKey());
            }
        }
    }

    /** Before a pickup from the ground: anything moved before it is undone first; after it, the picked-up items are the player's. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforePickup(EntityItemPickupEvent event) {
        Lock l = event.getEntity() instanceof ServerPlayer sp && locked(sp) ? LOCKS.get(sp.containerMenu) : null;
        if (l != null && !event.isCanceled()) {
            settle(l, l.player.containerMenu);
        }
    }

    @SubscribeEvent
    public static void afterPickup(PlayerEvent.ItemPickupEvent event) {
        Lock l = event.getEntity() instanceof ServerPlayer sp && locked(sp) ? LOCKS.get(sp.containerMenu) : null;
        if (l != null) {
            l.playerBase = carried(l.player);
        }
    }

    /** Dying with a locked screen open: settled and let go before the inventory drops (the screen itself closes on respawn). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer sp && locked(sp)) {
            release(sp.containerMenu, sp);
        }
    }

    /** Leaving with a locked screen open: settled and let go before the player is saved. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && locked(sp)) {
            release(sp.containerMenu, sp);
        }
    }

    /**
     * Compares both sides with how they were and undoes every true transfer: for each kind of item, what the player gained that the
     * machine (with the cursor) lost goes back to the machine, and what the player lost that the machine gained comes back to the player.
     * A change on one side only is not a transfer and stays. The result is the new "how it was" for both.
     */
    private static void settle(Lock l, AbstractContainerMenu menu) {
        if (!l.player.isAlive() || l.player.hasDisconnected() || l.player.isRemoved()) {
            return;
        }
        Map<Kind, Integer> pNow = carried(l.player), mNow = held(l.machine, menu);
        if (pNow.equals(l.playerBase) && mNow.equals(l.machineBase)) {
            return;
        }
        Set<Kind> kinds = new HashSet<>(pNow.keySet());
        kinds.addAll(l.playerBase.keySet());
        boolean moved = false;
        for (Kind k : kinds) {
            int dp = pNow.getOrDefault(k, 0) - l.playerBase.getOrDefault(k, 0);
            int dm = mNow.getOrDefault(k, 0) - l.machineBase.getOrDefault(k, 0);
            if (dp > 0 && dm < 0) {
                moved = true;
                takeFromPlayer(l, k, Math.min(dp, -dm)); // from the machine into the inventory: back it goes
            } else if (dp < 0 && dm > 0) {
                moved = true;
                giveBack(l, menu, k, Math.min(-dp, dm)); // from the inventory into the machine: back it comes
            }
        }
        if (moved) {
            l.player.getInventory().setChanged();
            l.player.inventoryMenu.broadcastChanges();
            menu.broadcastChanges();
        }
        l.playerBase = carried(l.player);
        l.machineBase = held(l.machine, menu);
    }

    /** Takes {@code count} of kind {@code k} from the player (main inventory first, worn armour and the offhand last) back to the machine. */
    private static void takeFromPlayer(Lock l, Kind k, int count) {
        Inventory inv = l.player.getInventory();
        int[] order = new int[inv.getContainerSize()];
        int n = 0;
        for (int i = inv.items.size() - 1; i >= 0; i--) {
            order[n++] = i;
        }
        for (int i = inv.items.size(); i < inv.getContainerSize(); i++) {
            order[n++] = i;
        }
        for (int i = 0; i < n && count > 0; i++) {
            ItemStack st = inv.getItem(order[i]);
            if (k.matches(st)) {
                ItemStack part = st.split(Math.min(count, st.getCount()));
                count -= part.getCount();
                putBack(l.player, l.machine, part);
            }
        }
    }

    /** Takes {@code count} of kind {@code k} out of the cursor or the machine and gives it to the player (dropped at their feet if full). */
    private static void giveBack(Lock l, AbstractContainerMenu menu, Kind k, int count) {
        List<ItemStack> got = new ArrayList<>();
        ItemStack carriedNow = menu.getCarried();
        if (k.matches(carriedNow)) {
            ItemStack part = carriedNow.split(Math.min(count, carriedNow.getCount()));
            count -= part.getCount();
            got.add(part);
        }
        if (count > 0 && !l.machine.isRemoved()) {
            for (IItemHandler h : handlers(l.machine)) {
                for (int s = 0; s < h.getSlots() && count > 0; s++) {
                    if (k.matches(h.getStackInSlot(s))) {
                        ItemStack part = h.extractItem(s, count, false);
                        count -= part.getCount();
                        if (!part.isEmpty()) {
                            got.add(part);
                        }
                    }
                }
            }
        }
        for (ItemStack back : got) {
            if (!l.player.getInventory().add(back)) {
                l.player.drop(back, false); // theirs, never lost
            }
        }
    }

    // ------------------------------------------------------------------ closing

    /**
     * A locked menu being closed (or its player leaving): what moved last is undone, and what is held on the cursor goes back into the
     * machine instead of into the player's inventory; when the machine is full or gone, it drops beside the machine, and the player is
     * told (owner decision RC3).
     */
    public static void beforeRemoved(AbstractContainerMenu menu, Player player) {
        if (player instanceof ServerPlayer sp && LOCKS.containsKey(menu)) {
            release(menu, sp);
        }
    }

    private static void release(AbstractContainerMenu menu, ServerPlayer player) {
        Lock l = LOCKS.get(menu);
        if (l == null) {
            return;
        }
        settle(l, menu);
        LOCKS.remove(menu);
        ItemStack heldNow = menu.getCarried();
        if (!heldNow.isEmpty()) {
            menu.setCarried(ItemStack.EMPTY);
            putBack(player, l.machine, heldNow);
        }
    }

    /**
     * Puts {@code stack} into the machine (any side); what does not fit drops beside the machine, with a message to the player. For a
     * locked screen, and for what a machine hands over when it is used to open its screen locked ({@link RemoteOperate}).
     */
    public static void putBack(ServerPlayer player, BlockEntity machine, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Level level = machine.getLevel();
        if (level == null) {
            player.drop(stack, false); // no world to put it in: never lost
            return;
        }
        Component name = stack.getHoverName();
        ItemStack left = stack;
        if (!machine.isRemoved()) {
            for (IItemHandler h : handlers(machine)) {
                left = ItemHandlerHelper.insertItemStacked(h, left, false);
                if (left.isEmpty()) {
                    return;
                }
            }
        }
        BlockPos pos = machine.getBlockPos();
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, left);
        player.displayClientMessage(Component.translatable("message.bsp_core.tracking.link_dropped", left.getCount(), name).withStyle(ChatFormatting.YELLOW), true);
    }

    /** The machine's item handlers: unsided first, then each side's own. */
    private static List<IItemHandler> handlers(BlockEntity machine) {
        List<IItemHandler> out = new ArrayList<>();
        machine.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(out::add);
        for (Direction d : Direction.values()) {
            machine.getCapability(ForgeCapabilities.ITEM_HANDLER, d).ifPresent(h -> {
                if (!out.contains(h)) {
                    out.add(h);
                }
            });
        }
        return out;
    }

    /** A snapshot of both sides before an action, for {@link #settleSince}. */
    public record Before(Map<Kind, Integer> player, Map<Kind, Integer> machine) {
    }

    /** What the player carries and the machine holds now, for {@link #settleSince} around an action. */
    public static Before before(ServerPlayer player, BlockEntity machine) {
        return new Before(carried(player), held(machine, null));
    }

    /** Undoes the transfers since {@code before} (the opening use of a machine whose screen opens locked). */
    public static void settleSince(ServerPlayer player, BlockEntity machine, Before before, AbstractContainerMenu menu) {
        Lock l = new Lock(machine, player, menu);
        l.playerBase = before.player();
        l.machineBase = before.machine();
        settle(l, menu);
    }

    /** For tests and leaving: forgets every lock. */
    public static void clear() {
        LOCKS.clear();
    }
}
