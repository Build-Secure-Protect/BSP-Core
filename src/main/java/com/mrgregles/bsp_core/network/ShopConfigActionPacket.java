package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopConfigMenu;
import com.mrgregles.bsp_core.shop.ShopItem;
import com.mrgregles.bsp_core.shop.ShopOpener;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Client → server: an edit on the configurator. The server checks the admin, applies it through
 * {@link #apply}, saves the shop and answers with a {@link ShopConfigDataPacket}.
 */
public record ShopConfigActionPacket(Action action, String a, String b, int i, int j) {
    public enum Action { SELECT, CREATE, DELETE, SET, ADD_HAND, ADD_ITEM, SET_ITEM, REMOVE_ITEM, MOVE_ITEM, TEST, OPEN_SHOP }

    public static void encode(ShopConfigActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.a, 128);
        buf.writeUtf(m.b, 128);
        buf.writeVarInt(m.i);
        buf.writeVarInt(m.j);
    }

    public static ShopConfigActionPacket decode(FriendlyByteBuf buf) {
        return new ShopConfigActionPacket(buf.readEnum(Action.class), buf.readUtf(128), buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ShopConfigActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof ShopConfigMenu menu) {
            ctx.get().enqueueWork(() -> run(player, menu, msg));
        }
        ctx.get().setPacketHandled(true);
    }

    private static void run(ServerPlayer player, ShopConfigMenu menu, ShopConfigActionPacket msg) {
        MinecraftServer server = player.server;
        boolean admin = Admins.isAdmin(player);
        String selected = menu.shop == null ? "" : menu.shop.id;
        switch (msg.action) {
            case SELECT -> selected = msg.a;
            case TEST, OPEN_SHOP -> {
                Shop s = Shops.ledger(server).shop(msg.a);
                if (s != null) {
                    ShopOpener.open(player, s, null, false, null, msg.action == Action.TEST);
                }
                return;
            }
            default -> {
                if (!admin) {
                    sync(player, menu, selected, "read_only", null);
                    return;
                }
                String key = apply(server, player, msg, selected);
                if (msg.action == Action.CREATE && key.equals("created")) {
                    selected = msg.a.toLowerCase(Locale.ROOT);
                } else if (msg.action == Action.DELETE) {
                    selected = "";
                }
                sync(player, menu, selected, key, new String[]{msg.a});
                return;
            }
        }
        sync(player, menu, selected, "", null);
    }

    /** Applies an edit to the selected shop and saves it; returns the message key. Pure enough for a game test. */
    public static String apply(MinecraftServer server, @Nullable ServerPlayer player, ShopConfigActionPacket msg, String selected) {
        if (msg.action == Action.CREATE) {
            Shop.Kind kind;
            try {
                kind = Shop.Kind.valueOf(msg.b.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                kind = Shop.Kind.SHOP;
            }
            return Shops.create(server, msg.a, msg.a.replace('_', ' '), kind) == null ? "bad_id" : "created";
        }
        Shop shop = Shops.ledger(server).shop(selected);
        if (shop == null) {
            return "no_shop";
        }
        switch (msg.action) {
            case DELETE -> {
                Shops.delete(server, shop.id);
                return "deleted";
            }
            case SET -> {
                String v = msg.b;
                try {
                    switch (msg.a) {
                        case "name" -> shop.name = v.isBlank() ? shop.name : v.trim();
                        case "kind" -> shop.kind = Shop.Kind.valueOf(v.toUpperCase(Locale.ROOT));
                        case "parent" -> shop.parent = v.trim().toLowerCase(Locale.ROOT);
                        case "remote" -> shop.remote = Boolean.parseBoolean(v);
                        case "buyonly" -> shop.buyOnly = Boolean.parseBoolean(v);
                        case "style" -> shop.style = v.trim();
                        case "columns" -> shop.columns = Integer.parseInt(v.trim()) < Shop.MIN_COLUMNS ? 0 : Math.min(Shop.MAX_COLUMNS, Integer.parseInt(v.trim()));
                        case "rows" -> shop.rows = Math.max(2, Math.min(3, Integer.parseInt(v.trim())));
                        case "slots" -> shop.displaySlots = Math.max(1, Integer.parseInt(v.trim()));
                        case "interval" -> shop.intervalMs = Math.max(1, Long.parseLong(v.trim())) * 3_600_000L;
                        case "offer" -> shop.offerPercent = Math.max(0, Math.min(99, Integer.parseInt(v.trim())));
                        case "hoursmode" -> shop.hours = new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.valueOf(v.toUpperCase(Locale.ROOT)), shop.hours.openMin(), shop.hours.closeMin());
                        case "hoursopen" -> shop.hours = new com.mrgregles.bsp_core.shop.OpenHours(shop.hours.mode(), clock(v, shop.hours.mode()), shop.hours.closeMin());
                        case "hoursclose" -> shop.hours = new com.mrgregles.bsp_core.shop.OpenHours(shop.hours.mode(), shop.hours.openMin(), clock(v, shop.hours.mode()));
                        case "offerhours" -> shop.offerEndMs = Long.parseLong(v.trim()) <= 0 ? 0 : System.currentTimeMillis() + Long.parseLong(v.trim()) * 3_600_000L;
                        default -> {
                            return "bad_field";
                        }
                    }
                } catch (IllegalArgumentException e) {
                    return "bad_value";
                }
            }
            case ADD_HAND -> {
                if (player == null || player.getMainHandItem().isEmpty()) {
                    return "hold_item";
                }
                ShopItem it = new ShopItem();
                it.stack = player.getMainHandItem().copy();
                place(shop, it, msg.i);
            }
            case ADD_ITEM -> {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(msg.a));
                if (item == null) {
                    return "bad_item";
                }
                ShopItem it = new ShopItem();
                it.stack = new ItemStack(item, Math.max(1, Math.min(64, msg.j)));
                place(shop, it, msg.i);
            }
            case SET_ITEM -> {
                ShopItem it = shop.item(msg.i);
                if (it == null) {
                    return "no_item";
                }
                String v = msg.b;
                try {
                    switch (msg.a) {
                        case "buy" -> it.buy = Long.parseLong(v.trim());
                        case "sell" -> it.sell = Long.parseLong(v.trim());
                        case "currency" -> it.currency = Math.max(-1, Math.min(4, Integer.parseInt(v.trim())));
                        case "stockglobal" -> it.stockGlobal = Integer.parseInt(v.trim());
                        case "stockplayer" -> it.stockPlayer = Integer.parseInt(v.trim());
                        case "restock" -> it.restockMs = Math.max(0, Long.parseLong(v.trim())) * 60_000L;
                        case "count" -> it.stack.setCount(Math.max(1, Math.min(64, Integer.parseInt(v.trim()))));
                        case "instant" -> it.instant = Boolean.parseBoolean(v);
                        case "hidden" -> it.hidden = Boolean.parseBoolean(v);
                        case "section" -> it.section = v.trim().toLowerCase(Locale.ROOT);
                        default -> {
                            return "bad_field";
                        }
                    }
                } catch (NumberFormatException e) {
                    return "bad_value";
                }
                Shops.ledger(server).stock(shop.id, msg.i).left = -1;
            }
            case REMOVE_ITEM -> {
                if (!shop.remove(msg.i)) {
                    return "no_item";
                }
            }
            case MOVE_ITEM -> {
                List<ShopItem> all = new ArrayList<>(shop.all());
                if (msg.i < 0 || msg.i >= all.size() || msg.j < 0 || msg.j >= all.size()) {
                    return "no_item";
                }
                ShopItem moved = all.remove(msg.i);
                all.add(msg.j, moved);
                shop.pages.clear();
                shop.pages.add(new ArrayList<>());
                all.forEach(shop::add);
            }
            default -> {
                return "";
            }
        }
        Shops.save(server, shop);
        return "saved";
    }

    /** "hh:mm" on the clock, or ticks in game time. */
    static int clock(String v, com.mrgregles.bsp_core.shop.OpenHours.Mode mode) {
        String[] p = v.trim().split(":");
        if (mode == com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME) {
            return Math.max(0, Math.min(23999, Integer.parseInt(p[0])));
        }
        return Math.max(0, Math.min(1439, Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0)));
    }

    /** Puts a new item at {@code index} (shifting the rest) or at the end when the index is past the end. */
    private static void place(Shop shop, ShopItem it, int index) {
        List<ShopItem> all = new ArrayList<>(shop.all());
        all.add(Math.max(0, Math.min(index, all.size())), it);
        shop.pages.clear();
        shop.pages.add(new ArrayList<>());
        all.forEach(shop::add);
    }

    /** Sends the list and the selected shop. */
    public static void sync(ServerPlayer player, ShopConfigMenu menu, String selected, String key, @Nullable String[] args) {
        var ledger = Shops.ledger(player.server);
        List<ShopConfigMenu.Summary> list = new ArrayList<>();
        for (Shop s : ledger.all().values()) {
            list.add(new ShopConfigMenu.Summary(s.id, s.name, s.kind, s.all().size(), s.remote));
        }
        Shop s = ledger.shop(selected);
        menu.shop = s;
        BSPNetwork.sendTo(player, new ShopConfigDataPacket(list, s == null ? null : s.save(), key, args == null ? new String[0] : args));
    }
}
