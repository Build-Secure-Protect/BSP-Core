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
 * Slotless menu for the Totem Compass. Buttons: 0-4 load one coin of that tier, 5 start tracking. The upgrades are bought at a
 * Compass Tuner and tuned into the compass, so the compass itself sells nothing. Values are synced through data slots so the
 * screen is always current.
 */
public class TotemCompassMenu extends AbstractContainerMenu {
    public static final int BTN_START = 5, DATA_COUNT = 9;
    /** What data slot 4 says about the compass's levels. */
    public static final int UNTUNED = 0, TUNED = 1, UPGRADED_BEFORE_TUNER = 2;
    private final Player player;
    private final InteractionHand hand;
    private final ContainerData data;

    public TotemCompassMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.TOTEM_COMPASS.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.data = player.level().isClientSide ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int i) {
                ItemStack s = compass();
                long now = System.currentTimeMillis();
                return switch (i) {
                    case 0 -> Math.min(Short.MAX_VALUE, TotemCompassItem.stored(s));
                    case 1 -> Math.min(Short.MAX_VALUE, TotemCompassItem.trackingLeft(s, now));
                    // the player's own wait too: a second compass shows the cooldown its holder is still under
                    case 2 -> Math.min(Short.MAX_VALUE, player instanceof ServerPlayer sp && !s.isEmpty()
                            ? TotemCompassItem.cooldownLeft(sp, s) : TotemCompassItem.cooldownLeft(s, now));
                    case 3 -> TotemCompassItem.level(s);
                    case 4 -> tuneState(s);
                    case 5 -> TotemCompassItem.cooldownSeconds(s);
                    case 6 -> TotemCompassItem.rangeLevel(s, TotemCompassItem.Range.ACROSS);
                    case 7 -> TotemCompassItem.rangeLevel(s, TotemCompassItem.Range.DEPTH);
                    // tracking and no rival within reach: sent here, since the client's copy of the compass is not updated while this slotless menu is open
                    case 8 -> TotemCompassItem.trackingLeft(s, now) > 0 && TotemCompassItem.target(s) == null ? (TotemCompassItem.radiationDetected(s) ? 2 : 1) : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    /** Tuned by a tuner; or with levels and no tuned mark (bought on the compass before the tuner); or neither. */
    public static int tuneState(ItemStack s) {
        if (TotemCompassItem.isTuned(s)) {
            return TUNED;
        }
        return TotemCompassItem.upgradedBeforeTuner(s) ? UPGRADED_BEFORE_TUNER : UNTUNED;
    }

    private ItemStack compass() {
        ItemStack held = player.getItemInHand(hand);
        return held.getItem() instanceof TotemCompassItem ? held : ItemStack.EMPTY;
    }

    public InteractionHand hand() { return hand; }
    public int stored() { return data.get(0); }
    public int trackingLeft() { return data.get(1); }
    public int cooldownLeft() { return data.get(2); }
    public int level() { return data.get(3); }
    public int tuneState() { return data.get(4); }
    public int cooldownSeconds() { return data.get(5); }
    /** The compass's level on a range ladder; the screen reads the blocks and prices from the synced server config. */
    public int rangeLevel(TotemCompassItem.Range r) { return data.get(r == TotemCompassItem.Range.ACROSS ? 6 : 7); }
    /** Tracking, but the server found no rival totem within reach. */
    /** Tracking, nothing in reach and no rival's radiation felt. */
    public boolean noneInReach() { return data.get(8) == 1; }
    /** Tracking, nothing in reach, but a rival totem's radiation reaches the holder. */
    public boolean radiationDetected() { return data.get(8) == 2; }

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
