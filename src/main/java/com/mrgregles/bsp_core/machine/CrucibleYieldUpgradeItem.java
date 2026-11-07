package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** The Crucible Yield Upgrade: fits a Tetrium Crucible's yield slots (up to three) for more nuggets per ore. */
public class CrucibleYieldUpgradeItem extends Item {
    public CrucibleYieldUpgradeItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int per;
        try {
            per = BSPConfig.TCRUC_PER_UPGRADE.get();
        } catch (IllegalStateException notLoaded) { // in the main menu the server config is not loaded
            per = BSPConfig.TCRUC_PER_UPGRADE.getDefault();
        }
        tooltip.add(Component.translatable("item.bsp_core.crucible_yield_upgrade.tip", per).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bsp_core.crucible_yield_upgrade.tip2").withStyle(ChatFormatting.GRAY));
    }
}
