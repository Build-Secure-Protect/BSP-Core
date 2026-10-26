package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Shopping Network: the shops flagged remote, listed on the Portable Banker for a player who has unlocked it. */
public class ShopListMenu extends AbstractContainerMenu {
    public record Entry(String id, String name, Shop.Kind kind, int items, boolean open) {}

    public final List<Entry> shops = new ArrayList<>();

    public ShopListMenu(int id, Inventory inv, List<Entry> shops) {
        super(ModMenus.SHOP_LIST.get(), id);
        this.shops.addAll(shops);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Every shop on the network, top-level only (sub-sections open from their parents). */
    public static List<Entry> remote(MinecraftServer server, net.minecraft.world.level.Level level) {
        return remote(server, level, null);
    }

    /** As {@link #remote(MinecraftServer, net.minecraft.world.level.Level)}, leaving out shops hidden from this player by their requirements. */
    public static List<Entry> remote(MinecraftServer server, net.minecraft.world.level.Level level, @javax.annotation.Nullable Player viewer) {
        List<Entry> out = new ArrayList<>();
        for (Shop s : Shops.ledger(server).all().values()) {
            Requirement.Lock lock = viewer == null ? null : s.requires.check(viewer);
            if (s.remote && s.kind != Shop.Kind.SUB && (lock == null || !lock.hide())) {
                out.add(new Entry(s.id, s.name, s.kind, s.all().size(), s.hours.isOpen(level)));
            }
        }
        return out;
    }

    /** Whether this player may open this shop remotely: the account has the network and the shop is on it. */
    public static boolean mayOpen(MinecraftServer server, UUID player, Shop shop) {
        return shop.remote && Bank.account(server, player).remoteShops;
    }

    public static void open(ServerPlayer sp) {
        List<Entry> list = remote(sp.server, sp.level(), sp);
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new ShopListMenu(id, inv, list), Component.translatable("gui.bsp_core.network.title")), buf -> {
            buf.writeVarInt(list.size());
            for (Entry e : list) {
                buf.writeUtf(e.id, 32);
                buf.writeUtf(e.name, 64);
                buf.writeEnum(e.kind);
                buf.writeVarInt(e.items);
                buf.writeBoolean(e.open);
            }
        });
    }
}
