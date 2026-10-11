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
 * The Stall Display's settings screen menu (admins): the link, the interval, a pinned item, size and
 * hover height, and the linked shop's items to pin from. Every change is a {@code StallDisplayConfigPacket}
 * applied at once; the screen re-reads the state through the block entity's own sync.
 */
public class StallDisplayMenu extends AbstractContainerMenu {
    /** A shop item that can be pinned: its index in the shop and the stack. */
    public record Pick(int index, ItemStack stack) {}

    public final BlockPos pos;
    public final BlockPos linked;
    public final String shopName;
    public final List<Pick> items = new ArrayList<>();

    public StallDisplayMenu(int id, Inventory inv, BlockPos pos, BlockPos linked, String shopName, List<Pick> items) {
        super(ModMenus.STALL_DISPLAY.get(), id);
        this.pos = pos;
        this.linked = linked;
        this.shopName = shopName;
        this.items.addAll(items);
    }

    public static StallDisplayMenu read(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockPos linked = buf.readBoolean() ? buf.readBlockPos() : null;
        String name = buf.readUtf(64);
        int n = buf.readVarInt();
        List<Pick> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new Pick(buf.readVarInt(), buf.readItem()));
        }
        return new StallDisplayMenu(id, inv, pos, linked, name, items);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }

    /** The linked shop's item cards (not sections), whatever their stock, so an admin can pin any of them. */
    public static List<Pick> picks(ServerPlayer sp, StallDisplayBlockEntity display) {
        List<Pick> out = new ArrayList<>();
        Shop shop = display.shop((net.minecraft.server.level.ServerLevel) sp.level());
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
        Shop shop = display.shop((net.minecraft.server.level.ServerLevel) sp.level());
        String name = shop == null ? "" : shop.name;
        BlockPos pos = display.getBlockPos(), linked = display.linked;
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new StallDisplayMenu(id, inv, pos, linked, name, items),
                Component.translatable("gui.bsp_core.display.title")), buf -> {
            buf.writeBlockPos(pos);
            buf.writeBoolean(linked != null);
            if (linked != null) {
                buf.writeBlockPos(linked);
            }
            buf.writeUtf(name, 64);
            buf.writeVarInt(items.size());
            for (Pick p : items) {
                buf.writeVarInt(p.index);
                buf.writeItem(p.stack);
            }
        });
    }
}
