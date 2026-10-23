package com.mrgregles.bsp_core.land;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LandOfficeDataPacket;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Land Office screen's menu: no slots. The server sends the plots for sale, the open areas and
 * the player's own claims in this world, their balance, and the ground the player outlined with
 * Region Guard's wand (if the office was opened for one); the client draws the map and asks for
 * quotes and purchases through {@code LandOfficeActionPacket}. The outlined ground is kept here on
 * the server, so a purchase never trusts the client for it.
 */
public class LandOfficeMenu extends AbstractContainerMenu {
    // client-side copies
    public final List<Plot> plots = new ArrayList<>();
    public long balance;
    public boolean present, allowed;
    @Nullable
    public int[] selection;
    public String message = "";
    /** When Region Guard prices claims in Shatter Coins: the coin wording for the screen; null for pieces. */
    @Nullable
    public com.mrgregles.bsp_core.network.LandOfficeDataPacket.CoinTexts coins;
    public boolean messageOk = true;
    public int serial;

    /** The Land Office block this was opened at, or null when opened for a wand outline. */
    @Nullable
    private final net.minecraft.core.BlockPos at;

    public LandOfficeMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public LandOfficeMenu(int id, Inventory inv, @Nullable net.minecraft.core.BlockPos at) {
        super(ModMenus.LAND_OFFICE.get(), id);
        this.at = at;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (at != null && player.level().getBlockEntity(at) instanceof LandOfficeBlockEntity office) {
            office.userClosed();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Opens the office at its block for a player, with whatever they have outlined with the wand; the block's deed cards flutter while the screen is open. */
    public static void open(ServerPlayer sp, net.minecraft.core.BlockPos at) {
        RegionGuardClaims.ensureListeners();
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new LandOfficeMenu(id, inv, at), Component.translatable("gui.bsp_core.land.title")), buf -> {});
        if (sp.level().getBlockEntity(at) instanceof LandOfficeBlockEntity office) {
            office.userOpened();
        }
        if (sp.containerMenu instanceof LandOfficeMenu menu) {
            menu.selection = RegionGuardClaims.selection(sp);
            quoteAndSync(sp, menu);
        }
    }

    /** Opens the office for a player, with the ground they outlined (minX, minZ, maxX, maxZ) or none. */
    public static void open(ServerPlayer sp, @Nullable int[] selection) {
        RegionGuardClaims.ensureListeners();
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new LandOfficeMenu(id, inv), Component.translatable("gui.bsp_core.land.title")), buf -> {});
        if (sp.containerMenu instanceof LandOfficeMenu menu) {
            menu.selection = selection;
            quoteAndSync(sp, menu);
        }
    }

    /** An outlined ground inside an open area is quoted straight away, so the screen opens with the price. */
    private static void quoteAndSync(ServerPlayer sp, LandOfficeMenu menu) {
        String msg = "";
        boolean ok = true;
        if (menu.selection != null) {
            for (Plot p : RegionGuardClaims.listings(sp.serverLevel(), sp.getUUID(), sp.blockPosition())) {
                if (p.kind() == Plot.OPEN && p.contains(menu.selection)) {
                    var q = RegionGuardClaims.quoteOpen(sp, p.id(), menu.selection);
                    msg = q.message();
                    ok = q.ok();
                    break;
                }
            }
        }
        sync(sp, msg, ok);
    }

    /** Sends the office's data as it stands now, with a message for the foot of the screen. */
    public static void sync(ServerPlayer sp, String message, boolean ok) {
        if (!(sp.containerMenu instanceof LandOfficeMenu menu)) {
            return;
        }
        List<Plot> plots = RegionGuardClaims.listings(sp.serverLevel(), sp.getUUID(), sp.blockPosition());
        BSPNetwork.sendTo(sp, new LandOfficeDataPacket(plots, Bank.pieces(sp.server, sp.getUUID()), RegionGuardClaims.present(),
                RegionGuardClaims.claimsAllowed(sp.serverLevel()), menu.selection, message, ok, coinTexts(sp, plots, menu.selection)));
    }

    /**
     * When Region Guard prices claims in Shatter Coins (API 6): its wording for every plot's price, cost per block and the quote for
     * the outlined ground, and the coins the player carries. Null for pieces, or a Region Guard too old to word coin prices. Public for the tests.
     */
    @Nullable
    public static LandOfficeDataPacket.CoinTexts coinTexts(ServerPlayer sp, List<Plot> plots, @Nullable int[] selection) {
        if (!RegionGuardClaims.paysInCoins()) {
            return null;
        }
        var level = sp.serverLevel();
        List<String> price = new ArrayList<>(), perBlock = new ArrayList<>(), outline = new ArrayList<>();
        for (Plot p : plots) {
            String pt = p.kind() == Plot.FOR_SALE ? RegionGuardClaims.priceText(level, p.id(), p.price()) : "";
            String bt = p.kind() == Plot.OPEN ? RegionGuardClaims.priceText(level, p.id(), p.costPerBlock()) : "";
            String ot = "";
            if (p.kind() == Plot.OPEN && selection != null && p.contains(selection)) {
                long w = selection[2] - selection[0] + 1, d = selection[3] - selection[1] + 1;
                ot = RegionGuardClaims.priceText(level, p.id(), w * d * p.costPerBlock());
            }
            if (pt == null || bt == null || ot == null) {
                return null; // Region Guard could not word it: pieces, as before
            }
            price.add(pt);
            perBlock.add(bt);
            outline.add(ot);
        }
        return new LandOfficeDataPacket.CoinTexts(carried(sp), price, perBlock, outline);
    }

    /** The coins the player carries, largest first ("3 gold, 12 copper"), or "" for none. */
    public static String carried(ServerPlayer sp) {
        List<String> parts = new ArrayList<>();
        var tiers = com.mrgregles.bsp_core.coin.CoinTier.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            int n = sp.getInventory().countItem(tiers[i].coin());
            if (n > 0) {
                parts.add(n + " " + tiers[i].key);
            }
        }
        return String.join(", ", parts);
    }
}
