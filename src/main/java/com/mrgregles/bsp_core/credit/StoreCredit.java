package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.AdminService;
import com.mrgregles.bsp_core.admin.LoginSettle;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Store credit: kinds, tickets, minting and voiding (Store Credit task 1; spending comes in task 2).
 * <p>A ticket item carries its kind. The first time it is seen in a player's inventory it is minted: it gets a serial and a record
 * holding the kind's value, bound to that player if the kind binds. Every copy of an item shares its serial, so copies share one
 * balance. Bundles that hand out the same stack again and again (kits, shop cards, season rewards) keep their tickets blank, so
 * each taker's ticket is minted fresh.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class StoreCredit {
    private StoreCredit() {}

    public static final String KIND = "Kind", SERIAL = "Serial", SHOWN = "Shown";
    /** Ticks between looks at a player's inventory and ender chest. */
    public static final int SCAN_TICKS = 10;
    public static final int GIVE_MAX = 64;
    private static final char[] SERIAL_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Nullable
    private static LongSupplier clock;

    /** GameTests only: the wall clock store credit reads; null for the real one. */
    public static void useClock(@Nullable LongSupplier c) {
        clock = c;
    }

    public static long now() {
        LongSupplier c = clock;
        return c != null ? c.getAsLong() : System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ the item

    public static boolean isTicket(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.STORE_CREDIT.get());
    }

    /** The ticket's kind id, "" for none. */
    public static String kindOf(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? "" : t.getString(KIND);
    }

    /** The ticket's serial, "" until it is minted. */
    public static String serialOf(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? "" : t.getString(SERIAL);
    }

    /** A new, unminted ticket of a kind: what {@code /give ...{Kind:"<id>"}} makes. */
    public static ItemStack fresh(String kindId) {
        ItemStack s = new ItemStack(ModItems.STORE_CREDIT.get());
        s.getOrCreateTag().putString(KIND, kindId);
        return s;
    }

    /** A ticket without its serial (any other stack as it is), for a bundle that hands the stack out again and again. */
    public static ItemStack blank(ItemStack stack) {
        if (!isTicket(stack)) {
            return stack;
        }
        String kind = kindOf(stack);
        return kind.isEmpty() ? new ItemStack(ModItems.STORE_CREDIT.get()) : fresh(kind);
    }

    /** Whether the stack is a ticket that {@link #blank} would change. */
    public static boolean minted(ItemStack stack) {
        return isTicket(stack) && stack.hasTag() && (stack.getTag().contains(SERIAL) || stack.getTag().contains(SHOWN));
    }

    // ------------------------------------------------------------------ kinds

    public static Collection<CreditKind> kinds(MinecraftServer server) {
        return CreditLedger.get(server).kinds();
    }

    @Nullable
    public static CreditKind kind(MinecraftServer server, String id) {
        return CreditLedger.get(server).kind(id);
    }

    /** Saves a kind (new or changed). Returns what is wrong with it as a sentence, or null when it was saved. */
    @Nullable
    public static String saveKind(MinecraftServer server, CreditKind kind) {
        CreditKind k = kind.copy();
        k.tidy();
        String problem = k.problem();
        if (problem != null) {
            return problem;
        }
        if (CreditSync.down()) {
            return DOWN;
        }
        k.edited = now();
        CreditLedger.get(server).putKind(k);
        CreditSync.kindSaved(k);
        return null;
    }

    /** What an admin is told while the network database is not connected. */
    public static final String DOWN = "The network database is not connected, so store credit cannot change now. Try again shortly.";

    /** Whether store credit cannot change now: a network server whose database is not connected. */
    public static boolean storageDown() {
        return CreditSync.down();
    }

    /** Deletes a kind; its tickets stay and show "Withdrawn", and spend nothing. False for an unknown kind, or while storage is down. */
    public static boolean deleteKind(MinecraftServer server, String id) {
        if (CreditSync.down() || CreditLedger.get(server).removeKind(id) == null) {
            return false;
        }
        CreditSync.kindDeleted(id);
        return true;
    }

    // ------------------------------------------------------------------ tickets

    @Nullable
    public static CreditTicket ticket(MinecraftServer server, String serial) {
        return serial.isEmpty() ? null : CreditLedger.get(server).ticket(serial);
    }

    @Nullable
    public static CreditTicket ticket(MinecraftServer server, ItemStack stack) {
        return isTicket(stack) ? ticket(server, serialOf(stack)) : null;
    }

    /** Voids a ticket: it spends nothing, and its item disappears the next time it is seen. False for an unknown serial. */
    public static boolean voidTicket(MinecraftServer server, String serial) {
        return voidTicket(server, serial, ok -> { });
    }

    /**
     * As {@link #voidTicket(MinecraftServer, String)}; {@code then} gets whether it held: on a network the database must take it,
     * and when it does not the ticket is live again here (it would still spend on the other servers).
     */
    public static boolean voidTicket(MinecraftServer server, String serial, java.util.function.Consumer<Boolean> then) {
        CreditLedger l = CreditLedger.get(server);
        CreditTicket t = l.ticket(serial);
        if (t == null || CreditSync.down()) {
            return false;
        }
        t.voided = true;
        l.setDirty();
        CreditSync.voided(serial, ok -> {
            if (!ok) {
                t.voided = false;
                l.setDirty();
                BSPCore.LOGGER.error("Store credit: the network did not take the void of ticket {}; it is live again", serial);
            }
            then.accept(ok);
        });
        BSPCore.LOGGER.info("Store credit ticket {} ({}) voided", serial, t.kind);
        return true;
    }

    /**
     * Gives {@code count} tickets of a kind. A player here gets them minted in their name at once (bound to them if the kind binds)
     * and in their inventory, dropped at their feet if it is full; a player who is away gets them at their next login, through
     * the same delivery as season prizes. Returns how many were given, or -1 for an unknown kind.
     */
    public static int give(MinecraftServer server, UUID player, String kindId, int count) {
        CreditKind kind = kind(server, kindId);
        if (kind == null) {
            return -1;
        }
        int n = Math.max(1, Math.min(GIVE_MAX, count));
        List<ItemStack> items = new ArrayList<>(n);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        for (int i = 0; i < n; i++) {
            ItemStack s = fresh(kindId);
            if (online != null && LoginSettle.canReceive(online)) {
                mint(online, s);
            }
            items.add(s);
        }
        AdminService.deliver(server, player, items, "message.bsp_core.credit.given");
        BSPCore.LOGGER.info("Gave {} store credit ticket(s) of {} to {}{}", n, kindId, player, online == null ? " (at their next login)" : "");
        return n;
    }

    /**
     * Mints an unminted ticket for {@code player}: a new serial and a record holding the kind's value, bound to the player if the
     * kind binds. Returns the ticket, or null when the stack is not an unminted ticket of a kind that exists.
     */
    @Nullable
    public static CreditTicket mint(ServerPlayer player, ItemStack stack) {
        if (!isTicket(stack) || !serialOf(stack).isEmpty()) {
            return null;
        }
        CreditLedger l = CreditLedger.get(player.server);
        CreditKind kind = l.kind(kindOf(stack));
        if (kind == null || CreditSync.down()) { // with the database away it stays unminted until the next look after it is back
            return null;
        }
        String serial;
        do {
            serial = newSerial();
        } while (l.hasSerial(serial));
        CreditTicket t = new CreditTicket(serial, kind.id);
        t.left = kind.value;
        t.createdMs = now();
        if (kind.bind) {
            t.owner = player.getUUID();
            t.ownerName = player.getGameProfile().getName();
        }
        l.putTicket(t);
        CreditSync.minted(t);
        stack.getOrCreateTag().putString(SERIAL, serial);
        refresh(stack, t, kind);
        BSPCore.LOGGER.info("Store credit ticket {} ({}, {} pieces) minted for {}{}", serial, kind.id, kind.value, player.getGameProfile().getName(), kind.bind ? ", bound" : "");
        return t;
    }

    static String newSerial() {
        StringBuilder b = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                b.append('-');
            }
            b.append(SERIAL_CHARS[RANDOM.nextInt(SERIAL_CHARS.length)]);
        }
        return b.toString();
    }

    /** Rewrites the copy of the ticket's state the tooltip reads, when it changed. */
    public static void refresh(ItemStack stack, CreditTicket t, @Nullable CreditKind kind) {
        CompoundTag shown = new CompoundTag();
        shown.putString("Name", kind == null ? "" : kind.name);
        if (kind != null) { // the look, places and time follow the kind as it is now; the client knows only what is written here
            shown.putString("Colour", kind.colour);
            shown.putString("Mark", kind.mark);
            net.minecraft.nbt.ListTag places = new net.minecraft.nbt.ListTag();
            placeNames(kind).forEach(n -> places.add(net.minecraft.nbt.StringTag.valueOf(n)));
            shown.put("Places", places);
            shown.putLong("Duration", kind.durationMs);
            long end = t.endsMs(kind);
            if (end > 0) {
                shown.putLong("Ends", end);
            }
        }
        shown.putLong("Left", t.left);
        shown.putLong("Of", kind == null ? t.left : Math.max(kind.value, t.left));
        if (!t.ownerName.isEmpty()) {
            shown.putString("Owner", t.ownerName);
        }
        if (t.voided) {
            shown.putBoolean("Voided", true);
        }
        if (kind == null) {
            shown.putBoolean("Withdrawn", true);
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!shown.equals(tag.getCompound(SHOWN))) {
            tag.put(SHOWN, shown);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CreditSpend.setWants(event.getEntity().getUUID(), true); // the switch is for this visit only
        CreditSpend.forget(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ the look at each player

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % SCAN_TICKS == 0) {
            scan(player);
        }
    }

    /**
     * Looks at every ticket the player holds (inventory, cursor, crafting grid, ender chest): mints the unminted, refreshes what the
     * tooltip shows, sends the warnings a day and an hour before a ticket runs out, and takes away tickets that are voided, used up
     * or out of time, saying so. Public for the GameTests, whose players never tick. Returns how many stacks were taken away.
     */
    public static int scan(ServerPlayer player) {
        Look look = new Look(player, CreditLedger.get(player.server), now());
        look.container(player.getInventory());
        look.container(player.inventoryMenu.getCraftSlots());
        look.container(player.getEnderChestInventory());
        if (look.gone(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (look.voided) {
            player.displayClientMessage(Component.translatable("message.bsp_core.credit.voided").withStyle(ChatFormatting.RED), false);
        }
        if (look.taken > 0) {
            player.inventoryMenu.broadcastChanges();
        }
        return look.taken;
    }

    /** Refreshes the player's tickets now (after a purchase), as the next look would. */
    public static void refreshHeld(ServerPlayer player) {
        scan(player);
    }

    /** One pass over a player's tickets. */
    private static final class Look {
        final ServerPlayer player;
        final CreditLedger ledger;
        final long now;
        final java.util.Set<String> told = new java.util.HashSet<>();
        int taken;
        boolean voided;

        Look(ServerPlayer player, CreditLedger ledger, long now) {
            this.player = player;
            this.ledger = ledger;
            this.now = now;
        }

        void container(Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (gone(c.getItem(i))) {
                    c.setItem(i, ItemStack.EMPTY);
                }
            }
        }

        /** One stack: true when it must go (voided, used up or out of time). */
        boolean gone(ItemStack stack) {
            if (!isTicket(stack) || kindOf(stack).isEmpty()) {
                return false;
            }
            String serial = serialOf(stack);
            if (serial.isEmpty()) {
                mint(player, stack);
                return false;
            }
            CreditTicket t = ledger.ticket(serial);
            if (t == null) {
                CreditSync.fetch(player.server, serial); // carried in from another server: read it from the network (alone, it stays as it is)
                return false;
            }
            CreditKind k = ledger.kind(t.kind);
            String name = k == null ? t.kind : k.name;
            if (t.voided) {
                BSPCore.LOGGER.info("Took voided store credit ticket {} from {}", serial, player.getGameProfile().getName());
                voided = true;
                taken++;
                return true;
            }
            if (t.expired(k, now)) {
                if (told.add(serial)) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.credit.expired", name, com.mrgregles.bsp_core.bank.Bank.format(t.left)).withStyle(ChatFormatting.RED), false);
                    notice(player, new com.mrgregles.bsp_core.network.CreditNoticePacket(true, name, k == null ? "purple" : k.colour, k == null ? "" : k.mark, t.left, t.endsMs(k)));
                }
                BSPCore.LOGGER.info("Took store credit ticket {} from {}: out of time with {} pieces left", serial, player.getGameProfile().getName(), t.left);
                taken++;
                return true;
            }
            if (t.left <= 0 && k != null) {
                if (told.add(serial)) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.credit.used_up", name).withStyle(ChatFormatting.GOLD), false);
                }
                taken++;
                return true;
            }
            warn(t, k);
            refresh(stack, t, k);
            return false;
        }

        /** A day and an hour before the end, once each, to the player who may spend it. */
        void warn(CreditTicket t, @javax.annotation.Nullable CreditKind k) {
            long end = t.endsMs(k);
            if (end <= 0 || !t.usableBy(player.getUUID()) || !told.add("warn " + t.serial)) {
                return;
            }
            long left = end - now;
            String key = null;
            if (left <= 3_600_000L && (t.warned & CreditTicket.WARNED_HOUR) == 0) {
                t.warned |= CreditTicket.WARNED_HOUR | CreditTicket.WARNED_DAY;
                key = "message.bsp_core.credit.warn_hour";
            } else if (left <= 24 * 3_600_000L && (t.warned & CreditTicket.WARNED_DAY) == 0) {
                t.warned |= CreditTicket.WARNED_DAY;
                key = "message.bsp_core.credit.warn_day";
            }
            if (key != null) {
                ledger.setDirty();
                CreditSync.warned(t.serial, t.warned);
                player.displayClientMessage(Component.translatable(key, k.name, com.mrgregles.bsp_core.bank.Bank.format(t.left), date(end)).withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    /** Where a kind spends, as players read it: shop names, "any Land Office plot", plot names, "teleports". */
    public static List<String> placeNames(CreditKind kind) {
        List<String> out = new ArrayList<>();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (String id : kind.shops) {
            com.mrgregles.bsp_core.shop.Shop shop = server == null ? null : com.mrgregles.bsp_core.shop.Shops.ledger(server).shop(id);
            out.add(shop == null || shop.name.isBlank() ? id : shop.name);
        }
        if (kind.anyPlot) {
            out.add("any Land Office plot");
        }
        for (String p : kind.plots) {
            out.add(p + " plots");
        }
        if (kind.teleports) {
            out.add("teleports");
        }
        return out;
    }

    @Nullable
    private static java.util.function.BiConsumer<ServerPlayer, com.mrgregles.bsp_core.network.CreditNoticePacket> noticeHook;

    /** GameTests only: sees every stamp sent (null to send them as usual). */
    public static void listenNotices(@Nullable java.util.function.BiConsumer<ServerPlayer, com.mrgregles.bsp_core.network.CreditNoticePacket> hook) {
        noticeHook = hook;
    }

    /** Sends the full-screen stamp (a clock started, or a ticket ran out). */
    static void notice(ServerPlayer player, com.mrgregles.bsp_core.network.CreditNoticePacket packet) {
        var hook = noticeHook;
        if (hook != null) {
            hook.accept(player, packet);
        } else {
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(player, packet);
        }
    }

    /** Takes every copy of a ticket out of the player's inventory, crafting grid, cursor and ender chest. */
    public static void takeAway(ServerPlayer player, String serial) {
        java.util.function.Predicate<ItemStack> is = s -> isTicket(s) && serial.equals(serialOf(s));
        for (Container c : List.of(player.getInventory(), player.inventoryMenu.getCraftSlots(), player.getEnderChestInventory())) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (is.test(c.getItem(i))) {
                    c.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        if (is.test(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** A ticket item for an existing record (a refund to a ticket whose item was used up). */
    public static ItemStack itemOf(CreditTicket t, @javax.annotation.Nullable CreditKind kind) {
        ItemStack s = fresh(t.kind);
        s.getOrCreateTag().putString(SERIAL, t.serial);
        refresh(s, t, kind);
        return s;
    }

    private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("d MMMM 'at' HH:mm", java.util.Locale.ENGLISH);

    /** A wall-clock time as players read it ("7 December at 18:40"), in the shops' time zone. */
    public static String date(long ms) {
        return DATE.format(java.time.Instant.ofEpochMilli(ms).atZone(com.mrgregles.bsp_core.shop.Shops.zone()));
    }
}
