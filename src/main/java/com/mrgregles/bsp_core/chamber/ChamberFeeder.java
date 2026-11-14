package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Feeding a power: a chamber takes the drop picked on its CHAMBER tab from its loot store, one item at a time, and turns plain plasma into
 * that drop's power at the picked level. One item of worth 1 tags {@code mob_chamber.mbPerWorth} (24,000 mB, a minute of a projecting Projector) at
 * level I; level II takes twice the items for the same plasma, level III four times ({@link MobPowers#mbTagged}). An item is used only
 * when the plain plasma covers all of it, so feeding waits for plasma and stops when the items run out.
 */
public final class ChamberFeeder {
    private ChamberFeeder() {}

    /** Items of {@code item} in the chamber's loot store. */
    public static int stocked(TankPartBlockEntity m, Item item) {
        int n = 0;
        for (int i = 0; i < m.loot().getSlots(); i++) {
            ItemStack s = m.loot().getStackInSlot(i);
            n += s.is(item) ? s.getCount() : 0;
        }
        return n;
    }

    /** Feeds up to {@code maxItems} of the picked drop; returns how many items went in. */
    public static int feed(TankPartBlockEntity m, int maxItems) {
        Item item = m.feedItem();
        MobPowers.Entry entry = item == null ? null : MobPowers.of(item);
        if (entry == null || maxItems <= 0) {
            return 0;
        }
        int lvl = m.feedLevel();
        long per = MobPowers.mbTagged(entry, lvl);
        if (per <= 0) {
            return 0;
        }
        int n = (int) Math.min(Math.min(stocked(m, item), m.pools().plain() / per), maxItems);
        if (n <= 0) {
            return 0;
        }
        int left = n;
        for (int i = 0; i < m.loot().getSlots() && left > 0; i++) {
            if (m.loot().getStackInSlot(i).is(item)) {
                left -= m.loot().extractItem(i, left, false).getCount();
            }
        }
        int used = n - left;
        m.tagFromPlain(entry.power(), lvl, used * per);
        return used;
    }
}
