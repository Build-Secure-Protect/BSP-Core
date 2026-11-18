package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stall Display's screen menu (admins): the shop it shows (and every shop to pick from), the
 * shop's items to pin one of, and the display's settings, which live in the block entity and are
 * read from it on the client. Every change is a {@code StallDisplayConfigPacket}; picking a shop
 * reopens the screen with that shop's items.
 */
public class StallDisplayMenu extends AbstractContainerMenu {
    /** An item that can be pinned: its place in the pin list (what a pin sends back) and the stack. */
    public record Pick(int index, ItemStack stack) {}

    /** What the screen's opening data may hold; Forge refuses more than 32,600 bytes. */
    static final int OPEN_BYTES = 32_000;

    public final BlockPos pos;
    public final String shopId, shopName;
    public final List<Pick> items = new ArrayList<>();
    /** Every shop (id, name) an admin may show. */
    public final List<String[]> shops = new ArrayList<>();
    /** Server only: the card behind each pick, in the same order, so a pin names the card the admin saw even if the shop changed since. */
    public final List<Shops.Card> cards = new ArrayList<>();

    public StallDisplayMenu(int id, Inventory inv, BlockPos pos, String shopId, String shopName, List<Pick> items, List<String[]> shops) {
        super(ModMenus.STALL_DISPLAY.get(), id);
        this.pos = pos;
        this.shopId = shopId;
        this.shopName = shopName;
        this.items.addAll(items);
        this.shops.addAll(shops);
    }

    public static StallDisplayMenu read(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String shopId = buf.readUtf(32), name = buf.readUtf(64);
        int n = buf.readVarInt();
        List<Pick> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new Pick(buf.readVarInt(), buf.readItem()));
        }
        int m = buf.readVarInt();
        List<String[]> shops = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            shops.add(new String[]{buf.readUtf(32), buf.readUtf(64)});
        }
        return new StallDisplayMenu(id, inv, pos, shopId, name, items, shops);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }

    /**
     * The item cards of the shop and its sections ({@link Shops#reachable}), whatever their stock, so
     * an admin can pin any of them: as many as fit in {@code bytes} of the screen's opening data.
     */
    public static List<Shops.Card> pinnable(ServerPlayer sp, StallDisplayBlockEntity display, int bytes) {
        Shop shop = display.shop(sp.serverLevel());
        List<Shops.Card> out = new ArrayList<>();
        if (shop == null) {
            return out;
        }
        FriendlyByteBuf scratch = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        for (Shops.Card c : Shops.reachable(sp.server, shop, Shops.now())) {
            scratch.writeVarInt(out.size());
            scratch.writeItem(c.shop().item(c.index()).stack);
            if (scratch.writerIndex() > bytes) {
                break;
            }
            out.add(c);
        }
        return out;
    }

    public static void open(ServerPlayer sp, StallDisplayBlockEntity display) {
        Shop shop = display.shop(sp.serverLevel());
        String shopId = display.shopId, name = shop == null ? "" : shop.name;
        List<String[]> shops = new ArrayList<>();
        for (Shop s : Shops.ledger(sp.server).all().values()) {
            if (s.kind != Shop.Kind.SUB) {
                shops.add(new String[]{s.id, s.name});
            }
        }
        BlockPos pos = display.getBlockPos();
        FriendlyByteBuf head = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(head, pos, shopId, name, List.of(), shops);
        List<Shops.Card> cards = pinnable(sp, display, OPEN_BYTES - head.writerIndex() - 5);
        List<Pick> items = new ArrayList<>();
        for (int k = 0; k < cards.size(); k++) {
            items.add(new Pick(k, cards.get(k).shop().item(cards.get(k).index()).stack));
        }
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> {
            StallDisplayMenu menu = new StallDisplayMenu(id, inv, pos, shopId, name, items, shops);
            menu.cards.addAll(cards);
            return menu;
        }, Component.translatable("gui.bsp_core.display.title")), buf -> write(buf, pos, shopId, name, items, shops));
    }

    private static void write(FriendlyByteBuf buf, BlockPos pos, String shopId, String name, List<Pick> items, List<String[]> shops) {
        buf.writeBlockPos(pos);
        buf.writeUtf(shopId, 32);
        buf.writeUtf(name, 64);
        buf.writeVarInt(items.size());
        for (Pick p : items) {
            buf.writeVarInt(p.index);
            buf.writeItem(p.stack);
        }
        buf.writeVarInt(shops.size());
        for (String[] s : shops) {
            buf.writeUtf(s[0], 32);
            buf.writeUtf(s[1], 64);
        }
    }
}
