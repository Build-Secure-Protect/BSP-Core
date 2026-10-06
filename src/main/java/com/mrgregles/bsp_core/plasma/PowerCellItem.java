package com.mrgregles.bsp_core.plasma;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A Power Cell ("Cartridge"): a small plasma store with Carried powers, charged in a Battery Charger and used by the Wave Emitter. */
public class PowerCellItem extends Item {
    public final int tier;

    public PowerCellItem(int tier) {
        super(new Item.Properties().stacksTo(1));
        this.tier = tier;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * PlasmaItems.stored(stack) / Math.max(1, PlasmaItems.capacity(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x4FB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PlasmaItems.tooltip(stack, tooltip);
    }
}
