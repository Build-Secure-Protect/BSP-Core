package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
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
 * The Vendor Post's assignment screen menu: the post's settings and the list of shops to pick from
 * come in the open buffer; the screen sends a {@code VendorConfigPacket} back and closes. Also used
 * by the Market Stall.
 */
public class VendorMenu extends AbstractContainerMenu {
    /** The stock mobs an admin can pick, in order; a villager carries its profession as entity NBT. */
    public static final String[] MOBS = {"minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem", "minecraft:zombie_villager", "minecraft:piglin", "minecraft:allay",
            "minecraft:witch", "minecraft:pillager", "minecraft:sniffer", "minecraft:armor_stand"};
    public static final String[] PROFESSIONS = {"none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith", "nitwit"};

    public final BlockPos pos;
    public final boolean editable;
    public String shopId, mob, mobNbt;
    public OpenHours hours;
    public final List<String[]> shops = new ArrayList<>();

    public VendorMenu(int id, Inventory inv, BlockPos pos, boolean editable, String shopId, OpenHours hours, String mob, String mobNbt, List<String[]> shops) {
        super(ModMenus.VENDOR.get(), id);
        this.pos = pos;
        this.editable = editable;
        this.shopId = shopId;
        this.hours = hours;
        this.mob = mob;
        this.mobNbt = mobNbt;
        this.shops.addAll(shops);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }

    public static void open(ServerPlayer sp, VendorPostBlockEntity post) {
        boolean editable = com.mrgregles.bsp_core.admin.Admins.isAdmin(sp);
        List<String[]> shops = new ArrayList<>();
        for (Shop s : Shops.ledger(sp.server).all().values()) {
            if (s.kind != Shop.Kind.SUB) {
                shops.add(new String[]{s.id, s.name});
            }
        }
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new VendorMenu(id, inv, post.getBlockPos(), editable, post.shopId, post.hours, post.mob, post.mobNbt, shops),
                Component.translatable("gui.bsp_core.vendor.title")), buf -> {
            buf.writeBlockPos(post.getBlockPos());
            buf.writeBoolean(editable);
            buf.writeUtf(post.shopId, 32);
            buf.writeNbt(post.hours.save());
            buf.writeUtf(post.mob, 64);
            buf.writeUtf(post.mobNbt, 255);
            buf.writeVarInt(shops.size());
            for (String[] s : shops) {
                buf.writeUtf(s[0], 32);
                buf.writeUtf(s[1], 64);
            }
        });
    }
}
