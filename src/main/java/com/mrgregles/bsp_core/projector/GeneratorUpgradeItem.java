package com.mrgregles.bsp_core.projector;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A part for a Totem Generator's sockets: a Reach Amplifier (how far it can push) or the Channel Expander (a third power). */
public class GeneratorUpgradeItem extends Item {
    public enum Kind {
        REACH1("reach_amplifier_mk1"), REACH2("reach_amplifier_mk2"), REACH3("reach_amplifier_mk3"), CHANNEL("channel_expander");

        public final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    public final Kind kind;

    public GeneratorUpgradeItem(Kind kind) {
        super(new Properties().stacksTo(1));
        this.kind = kind;
    }

    @Nullable
    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof GeneratorUpgradeItem item ? item.kind : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.generator." + kind.id).withStyle(ChatFormatting.GRAY));
    }
}
