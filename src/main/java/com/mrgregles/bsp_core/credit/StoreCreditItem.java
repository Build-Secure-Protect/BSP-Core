package com.mrgregles.bsp_core.credit;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A store credit ticket ({@code bsp_core:store_credit}). One per stack. Its tag holds the kind ({@code Kind}), the serial once
 * the ticket is minted ({@code Serial}), and a copy of what the tooltip shows ({@code Shown}), which the server rewrites whenever
 * the ticket changes; the record itself is in {@link CreditLedger}. A ticket given with {@code /give ...{Kind:"<id>"}} has no
 * serial until it is first seen in a player's inventory.
 */
public class StoreCreditItem extends Item {
    public StoreCreditItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag shown = shown(stack);
        String name = shown == null ? "" : shown.getString("Name");
        return name.isEmpty() ? super.getName(stack) : Component.translatable("item.bsp_core.store_credit.named", name);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag shown = shown(stack);
        if (StoreCredit.kindOf(stack).isEmpty()) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (shown == null) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.unminted").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (shown.getBoolean("Voided")) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.voided").withStyle(ChatFormatting.RED));
            return;
        }
        if (shown.getBoolean("Withdrawn")) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.withdrawn").withStyle(ChatFormatting.RED));
            return;
        }
        long left = shown.getLong("Left"), of = shown.getLong("Of");
        lines.add((left == of ? Component.translatable("item.bsp_core.store_credit.tip.full", com.mrgregles.bsp_core.bank.Bank.format(of))
                : Component.translatable("item.bsp_core.store_credit.tip.left", com.mrgregles.bsp_core.bank.Bank.format(left), com.mrgregles.bsp_core.bank.Bank.format(of)))
                .withStyle(ChatFormatting.GREEN));
        List<String> places = new java.util.ArrayList<>();
        shown.getList("Places", net.minecraft.nbt.Tag.TAG_STRING).forEach(t -> places.add(t.getAsString()));
        String where = places.isEmpty() ? Component.translatable("item.bsp_core.store_credit.tip.nowhere").getString() : String.join(", ", places);
        boolean firstLine = true;
        for (String line : wrap(Component.translatable("item.bsp_core.store_credit.tip.places", where).getString(), 38)) {
            lines.add(Component.literal(firstLine ? line : "  " + line).withStyle(ChatFormatting.GRAY));
            firstLine = false;
        }
        long ends = shown.getLong("Ends"), duration = shown.getLong("Duration");
        if (ends > 0) {
            long rest = ends - System.currentTimeMillis();
            lines.add(rest <= 0 ? Component.translatable("item.bsp_core.store_credit.tip.out_of_time").withStyle(ChatFormatting.RED)
                    : Component.translatable("item.bsp_core.store_credit.tip.time_left", span(rest)).withStyle(rest < 24 * 3_600_000L ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        } else if (duration > 0) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.time_from", span(duration)).withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.no_limit").withStyle(ChatFormatting.YELLOW));
        }
        String owner = shown.getString("Owner");
        if (!owner.isEmpty()) {
            lines.add(Component.translatable("item.bsp_core.store_credit.tip.owner", owner).withStyle(ChatFormatting.GRAY));
        }
        String serial = StoreCredit.serialOf(stack);
        if (!serial.isEmpty()) {
            lines.add(Component.literal("#" + serial).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A time as players read it: "30 days", "29 days 4 hours", "3 hours 10 minutes", "12 minutes", "less than a minute". Public for the tests. */
    public static String span(long ms) {
        long m = Math.max(0, ms) / 60_000L, h = m / 60, d = h / 24;
        if (d > 0) {
            long hh = h % 24;
            return d + (d == 1 ? " day" : " days") + (hh > 0 ? " " + hh + (hh == 1 ? " hour" : " hours") : "");
        }
        if (h > 0) {
            long mm = m % 60;
            return h + (h == 1 ? " hour" : " hours") + (mm > 0 ? " " + mm + (mm == 1 ? " minute" : " minutes") : "");
        }
        return m > 0 ? m + (m == 1 ? " minute" : " minutes") : "less than a minute";
    }

    /** Splits text at spaces into lines of at most {@code max} characters (a tooltip does not wrap by itself). */
    static List<String> wrap(String text, int max) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > max) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? " " : "").append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** The tint for the body layer (tint index 0): the kind's dye colour made a little lighter, as the texture's greys darken it. */
    public static int tint(ItemStack stack) {
        CompoundTag shown = shown(stack);
        net.minecraft.world.item.DyeColor dye = shown == null ? null : net.minecraft.world.item.DyeColor.byName(shown.getString("Colour"), null);
        float[] c = (dye == null ? net.minecraft.world.item.DyeColor.PURPLE : dye).getTextureDiffuseColors();
        int r = Math.round((c[0] + (1 - c[0]) * 0.15f) * 255), g = Math.round((c[1] + (1 - c[1]) * 0.15f) * 255), b = Math.round((c[2] + (1 - c[2]) * 0.15f) * 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The tint for the print layer (tint index 1): the dye much lighter, or darker for the palest dyes so the lines still show. */
    public static int printTint(ItemStack stack) {
        CompoundTag shown = shown(stack);
        net.minecraft.world.item.DyeColor dye = shown == null ? null : net.minecraft.world.item.DyeColor.byName(shown.getString("Colour"), null);
        float[] c = (dye == null ? net.minecraft.world.item.DyeColor.PURPLE : dye).getTextureDiffuseColors();
        boolean pale = c[0] * 0.3f + c[1] * 0.59f + c[2] * 0.11f > 0.78f;
        int r = Math.round((pale ? c[0] * 0.8f : c[0] + (1 - c[0]) * 0.6f) * 255), g = Math.round((pale ? c[1] * 0.8f : c[1] + (1 - c[1]) * 0.6f) * 255),
                b = Math.round((pale ? c[2] * 0.8f : c[2] + (1 - c[2]) * 0.6f) * 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The item property {@code bsp_core:mark}: 0 no mark, 0.5 C, 1 D. */
    public static float mark(ItemStack stack) {
        CompoundTag shown = shown(stack);
        String m = shown == null ? "" : shown.getString("Mark");
        return m.equals("D") ? 1f : m.equals("C") ? 0.5f : 0f;
    }

    @Nullable
    private static CompoundTag shown(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(StoreCredit.SHOWN, net.minecraft.nbt.Tag.TAG_COMPOUND) ? tag.getCompound(StoreCredit.SHOWN) : null;
    }
}
