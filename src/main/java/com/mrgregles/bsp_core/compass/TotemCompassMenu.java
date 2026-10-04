package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Slotless menu for the Totem Compass. Buttons: 0-4 load one coin of that tier, 5 start tracking,
 * 6 buy a cooldown upgrade. Values are synced through data slots so the screen is always current.
 */
public class TotemCompassMenu extends AbstractContainerMenu {
    public static final int BTN_START = 5, BTN_UPGRADE = 6;
    private final Player player;
    private final InteractionHand hand;
    private final ContainerData data;

    public TotemCompassMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.TOTEM_COMPASS.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.data = player.level().isClientSide ? new SimpleContainerData(6) : new ContainerData() {
            @Override
            public int get(int i) {
                ItemStack s = compass();
                long now = player.level().getGameTime();
                return switch (i) {
                    case 0 -> Math.min(Short.MAX_VALUE, TotemCompassItem.stored(s));
                    case 1 -> Math.min(Short.MAX_VALUE, TotemCompassItem.trackingLeft(s, now));
                    case 2 -> Math.min(Short.MAX_VALUE, TotemCompassItem.cooldownLeft(s, now));
                    case 3 -> TotemCompassItem.level(s);
                    case 4 -> Math.min(Short.MAX_VALUE, TotemCompassItem.upgradeCost(s));
                    case 5 -> TotemCompassItem.cooldownSeconds(s);
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 6;
            }
        };
        addDataSlots(data);
    }

    private ItemStack compass() {
        ItemStack held = player.getItemInHand(hand);
        return held.getItem() instanceof TotemCompassItem ? held : ItemStack.EMPTY;
    }

    public int stored() { return data.get(0); }
    public int trackingLeft() { return data.get(1); }
    public int cooldownLeft() { return data.get(2); }
    public int level() { return data.get(3); }
    public int upgradeCost() { return (short) data.get(4); }
    public int cooldownSeconds() { return data.get(5); }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        ItemStack compass = compass();
        if (compass.isEmpty() || !(p instanceof ServerPlayer sp)) {
            return false;
        }
        CoinTier tier = CoinTier.byOrdinal(id);
        if (tier != null) {
            TotemCompassItem.loadCoin(sp, compass, tier);
        } else if (id == BTN_START) {
            TotemCompassItem.startTracking(sp, compass);
        } else if (id == BTN_UPGRADE) {
            TotemCompassItem.buyUpgrade(sp, compass);
        } else {
            return false;
        }
        sp.inventoryMenu.sendAllDataToRemote(); // this menu has no slots, so the client would not see coins leave otherwise
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return !compass().isEmpty();
    }
}
