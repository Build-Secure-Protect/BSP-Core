package com.mrgregles.bsp_core.material;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Lets a BSP machine run on RF. While powered it works faster and uses the same share less lava. {@code mark} is 1-3. */
public class RfUpgradeItem extends Item {
    public final int mark;

    public RfUpgradeItem(int mark) {
        super(new Properties().stacksTo(1));
        this.mark = mark;
    }

    /** Fractional speed gain and lava saving, e.g. 0.30 for Mk III. */
    public double bonus() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.RF_BONUS, List.<Number>of()), mark, (Number) 0.0).doubleValue();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.rf_upgrade", Math.round(bonus() * 100)).withStyle(ChatFormatting.YELLOW));
    }
}
