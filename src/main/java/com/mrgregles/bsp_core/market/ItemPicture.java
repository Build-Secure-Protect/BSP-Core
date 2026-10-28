package com.mrgregles.bsp_core.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/**
 * A stack as a screen or board needs to draw it, and nothing more: the item, the count, a custom name, the enchantment glint,
 * a potion's or dyed item's colour and a model override. A shulker box full of written books becomes a few dozen bytes, so
 * the market's lists stay small however heavy the lots are; the full stack is sent only for the one lot a player opens.
 */
public final class ItemPicture {
    private ItemPicture() {}

    public static ItemStack of(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(stack.getItem(), stack.getCount());
        CompoundTag from = stack.getTag();
        if (from == null) {
            return out;
        }
        CompoundTag tag = new CompoundTag();
        for (String key : new String[]{"Potion", "CustomPotionColor", "CustomModelData"}) {
            if (from.contains(key)) {
                tag.put(key, from.get(key).copy());
            }
        }
        if (from.contains("display", 10)) {
            CompoundTag display = from.getCompound("display"), keep = new CompoundTag();
            if (display.contains("Name", 8)) {
                String name = display.getString("Name");
                if (name.length() > 512) { // a name of many styled parts: its plain text, cut, is picture enough
                    String plain = stack.getHoverName().getString();
                    name = net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(plain.substring(0, Math.min(64, plain.length()))));
                }
                keep.putString("Name", name);
            }
            if (display.contains("color")) {
                keep.put("color", display.get("color").copy());
            }
            if (!keep.isEmpty()) {
                tag.put("display", keep);
            }
        }
        if (stack.hasFoil()) {
            // one empty entry is enough for the glint; it names no enchantment, so the tooltip lists none
            ListTag glint = new ListTag();
            glint.add(new CompoundTag());
            tag.put("Enchantments", glint);
        }
        if (!tag.isEmpty()) {
            out.setTag(tag);
        }
        return out;
    }
}
