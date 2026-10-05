package com.mrgregles.bsp_core.decoy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A crafted part that goes into one of a Decoy Totem's sockets: a range coil, a trap charge, the Trap Amplifier or a Reinforced Casing. */
public class DecoyUpgradeItem extends Item {
    public enum Kind {
        RANGE1("range_coil_mk1"), RANGE2("range_coil_mk2"), RANGE3("range_coil_mk3"),
        BLAST("blast_charge"), HEX("hex_charge"), POISON("poison_charge"), FATIGUE("fatigue_charge"), WARP("warp_charge"),
        AMPLIFIER("trap_amplifier"), CASING("reinforced_casing");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public boolean charge() {
            return this == BLAST || this == HEX || this == POISON || this == FATIGUE || this == WARP;
        }
    }

    public final Kind kind;

    public DecoyUpgradeItem(Kind kind) {
        super(new Properties().stacksTo(kind.charge() ? 16 : 1));
        this.kind = kind;
    }

    @Nullable
    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof DecoyUpgradeItem item ? item.kind : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.decoy." + kind.id).withStyle(ChatFormatting.GRAY));
    }
}
