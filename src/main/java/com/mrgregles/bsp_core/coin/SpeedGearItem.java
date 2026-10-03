package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Factory speed upgrade. {@code mark} is 1, 2 or 3. */
public class SpeedGearItem extends Item {
    public final int mark;

    public SpeedGearItem(int mark) {
        super(new Properties().stacksTo(16));
        this.mark = mark;
    }

    /** Fraction of press time this gear removes. */
    public double reduction() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_UPGRADE_REDUCTIONS, java.util.List.<Number>of()), mark, (Number) 0.0).doubleValue();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.speed_gear", Math.round(reduction() * 100)).withStyle(ChatFormatting.AQUA));
    }
}
