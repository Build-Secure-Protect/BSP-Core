package com.mrgregles.bsp_core.material;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One piece of the Totem Mould, which goes into the Totem Forge's hollow (Totem Forge brief, 2026-10-27).
 * A piece survives {@link #POURS} pours and cracks on the last; breaking a forge's controller before the
 * totem has set counts as one. Its look is the whole totem with this piece picked out in ceramic and the
 * rest a pale ghost (models/item/*_mould.json, from tools/gen_mould_assets.py).
 */
public class TotemMouldItem extends Item {
    public static final int POURS = 3;

    /** The pieces, with the places they fill in TotemForgeRenderer.PIECES order: the feet and the arms are each needed twice. */
    public enum Piece {
        BASE("base", 0), FOOT("foot", 1, 2), TORSO("torso", 3), ARM("arm", 4, 5), HEAD("head", 6);

        public final String key;
        public final int[] places;

        Piece(String key, int... places) {
            this.key = key;
            this.places = places;
        }

        public String id() {
            return key + "_mould";
        }
    }

    private final Piece piece;

    public TotemMouldItem(Piece piece) {
        super(new Item.Properties().durability(POURS).rarity(Rarity.UNCOMMON));
        this.piece = piece;
    }

    public Piece piece() {
        return piece;
    }

    public static int poursLeft(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.bsp_core.mould.pours", poursLeft(stack), POURS).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.bsp_core.mould." + (piece.places.length > 1 ? "twice" : "once")).withStyle(ChatFormatting.DARK_GRAY));
    }

    // a mould cracks after its pours: nothing may stretch them (Unbreaking, Mending) or mend them on an anvil
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }
}
