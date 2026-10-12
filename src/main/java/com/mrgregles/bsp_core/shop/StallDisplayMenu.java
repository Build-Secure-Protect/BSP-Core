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
    /** A shop item that can be pinned: its index in the shop and the stack. */
    public record Pick(int index, ItemStack stack) {}

    public final BlockPos pos;
    public final String shopId, shopName;
    public final List<Pick> items = new ArrayList<>();
    /** Every shop (id, name) an admin may show. */
    public final List<String[]> shops = new ArrayList<>();

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

    /** The shop's item cards (not sections), whatever their stock, so an admin can pin any of them. */
    public static List<Pick> picks(ServerPlayer sp, StallDisplayBlockEntity display) {
        List<Pick> out = new ArrayList<>();
        Shop shop = display.shop(sp.serverLevel());
        if (shop != null) {
            for (int i : Shops.visible(sp.server, shop, System.currentTimeMillis())) {
                ShopItem it = shop.item(i);
                if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                    out.add(new Pick(i, it.stack));
                }
            }
        }
        return out;
    }

    public static void open(ServerPlayer sp, StallDisplayBlockEntity display) {
        List<Pick> items = picks(sp, display);
        Shop shop = display.shop(sp.serverLevel());
        String shopId = display.shopId, name = shop == null ? "" : shop.name;
        List<String[]> shops = new ArrayList<>();
        for (Shop s : Shops.ledger(sp.server).all().values()) {
            if (s.kind != Shop.Kind.SUB) {
                shops.add(new String[]{s.id, s.name});
            }
        }
        BlockPos pos = display.getBlockPos();
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new StallDisplayMenu(id, inv, pos, shopId, name, items, shops),
                Component.translatable("gui.bsp_core.display.title")), buf -> {
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
        });
    }
}
