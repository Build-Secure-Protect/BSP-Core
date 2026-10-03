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

/**
 * A refinery filter. Each refinement of one Pure Illyrium Dust uses it once; when its uses run out
 * it is consumed. Uses per tier come from {@code refinery.filterUses}, so wear is stored in NBT
 * rather than as vanilla durability.
 */
public class FilterItem extends Item {
    public enum Tier { IRON, DIAMOND, NETHERITE, ILLYRIUM }

    private static final String TAG_USED = "Used";
    public final Tier tier;

    public FilterItem(Tier tier) {
        super(new Properties().stacksTo(1));
        this.tier = tier;
    }

    public int maxUses() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FILTER_USES, List.of()), tier.ordinal() + 1, 1);
    }

    public int usesLeft(ItemStack stack) {
        int used = stack.hasTag() ? stack.getTag().getInt(TAG_USED) : 0;
        return Math.max(0, maxUses() - used);
    }

    /** Uses the filter once. Returns true if the filter is now spent and should be removed. */
    public boolean useOnce(ItemStack stack) {
        int used = (stack.hasTag() ? stack.getTag().getInt(TAG_USED) : 0) + 1;
        stack.getOrCreateTag().putInt(TAG_USED, used);
        return used >= maxUses();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return usesLeft(stack) < maxUses();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * usesLeft(stack) / Math.max(1, maxUses()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x19D3B0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.filter", usesLeft(stack), maxUses()).withStyle(ChatFormatting.GRAY));
    }
}
