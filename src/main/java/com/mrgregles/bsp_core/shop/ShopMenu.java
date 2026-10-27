package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's shop screen menu: no slots; the shop's visible page comes down in a
 * {@code ShopDataPacket} and actions go up in a {@code ShopActionPacket}. Opened at a vendor or
 * stall (the player stays within reach), from the Shopping Network (no position) or by command.
 */
public class ShopMenu extends AbstractContainerMenu {
    /** One card on the page as the client sees it: the buy side's stock limit and the sell side's limit as this player has them; a section card carries its section's name for the tooltip. */
    public record Card(int index, ItemStack stack, long buy, long sell, int currency, Limit stock, Limit selling, boolean instant, String section, String sectionName, boolean anyData, List<String> note,
                       @Nullable Requirement.Lock lock) {}

    /**
     * One side's limit as this player sees it: lots left (-1 no limit) out of how many, what is left
     * for everyone when a per-player limit is the one shown (-1 none), the refill rule as text
     * ({@link Refill#parse}) and milliseconds to the next refill (-1 unknown).
     */
    public record Limit(int left, int of, int everyone, String rule, long refillIn) {
        public static final Limit NONE = new Limit(-1, -1, -1, "", -1);

        public boolean limited() {
            return left >= 0;
        }

        public void write(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeVarInt(left + 1);
            if (left >= 0) {
                buf.writeVarInt(of + 1);
                buf.writeVarInt(everyone + 1);
                buf.writeUtf(rule, 32);
                buf.writeLong(refillIn);
            }
        }

        public static Limit read(net.minecraft.network.FriendlyByteBuf buf) {
            int left = buf.readVarInt() - 1;
            return left < 0 ? NONE : new Limit(left, buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUtf(32), buf.readLong());
        }
    }

    public final String shopId;
    @Nullable
    public final BlockPos at;
    public final boolean remote;
    @Nullable
    public final OpenHours hours;
    /** Test mode (the configurator's preview): buys and sells report what would happen and move nothing. */
    public final boolean test;
    // client-side copies
    public String name = "", path = "";
    public Shop.Kind kind = Shop.Kind.SHOP;
    public boolean buyOnly, open = true, admin;
    /** The shop's tree holds more than a page of cards: the screen shows its search box. */
    public boolean searchable;
    public int offerPercent, page, pages = 1, untilOpen, columns = 7, rows = 3;
    public long offerEnd, rotatesIn, balance;
    public String opensAt = "";
    public final List<Card> cards = new ArrayList<>();
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    public ShopMenu(int id, Inventory inv, String shopId, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours, boolean test) {
        super(ModMenus.SHOP.get(), id);
        this.shopId = shopId;
        this.at = at;
        this.remote = remote;
        this.hours = hours;
        this.test = test;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return at == null || player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < 64;
    }
}
