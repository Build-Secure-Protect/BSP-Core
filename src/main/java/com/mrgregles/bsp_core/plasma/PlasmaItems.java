package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Plasma Battery or Power Cell carries, kept in the item's {@code Plasma} tag: stored mB and
 * the powers stamped into it with their levels. Batteries hold Base powers (the ones a projector can
 * receive), cells hold Carried powers.
 */
public final class PlasmaItems {
    public static final String TAG = "Plasma", STORED = "Stored", POWERS = "Powers";
    /** Powers a battery may hold: those a projector receives. */
    public static final Buff[] BATTERY_POWERS = com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity.SENDABLE;
    /** Powers a cell may hold: the totem's Carried powers, which the Wave Emitter gives its carrier. */
    public static final Buff[] CELL_POWERS = carried();

    private PlasmaItems() {}

    private static Buff[] carried() {
        List<Buff> out = new ArrayList<>();
        for (Buff b : Buff.values()) {
            if (b.branch() == TotemUpgrades.Branch.CARRIED) {
                out.add(b);
            }
        }
        return out.toArray(new Buff[0]);
    }

    public static boolean isBattery(ItemStack stack) {
        return stack.getItem() instanceof PlasmaBatteryBlock.Item;
    }

    public static boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof PowerCellItem;
    }

    public static boolean isChargeable(ItemStack stack) {
        return isBattery(stack) || isCell(stack);
    }

    /** Tier of a battery or cell, 0 for the first. */
    public static int tier(ItemStack stack) {
        return isBattery(stack) ? ((PlasmaBatteryBlock.Item) stack.getItem()).block().tier : isCell(stack) ? ((PowerCellItem) stack.getItem()).tier : 0;
    }

    public static int capacity(ItemStack stack) {
        if (isBattery(stack)) {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.BATTERY_CAPACITY, List.<Integer>of()), tier(stack) + 1, new int[]{40_000, 200_000, 1_000_000, 5_000_000}[tier(stack)]);
        }
        if (isCell(stack)) {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CELL_CAPACITY, List.<Integer>of()), tier(stack) + 1, new int[]{8_000, 24_000, 60_000}[tier(stack)]);
        }
        return 0;
    }

    /** How many powers the item may hold. */
    public static int maxPowers(ItemStack stack) {
        if (isBattery(stack)) {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.BATTERY_POWERS, List.<Integer>of()), tier(stack) + 1, new int[]{0, 2, 3, 4}[tier(stack)]);
        }
        if (isCell(stack)) {
            return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CELL_POWERS, List.<Integer>of()), tier(stack) + 1, tier(stack) + 1);
        }
        return 0;
    }

    public static Buff[] allowed(ItemStack stack) {
        return isCell(stack) ? CELL_POWERS : BATTERY_POWERS;
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrCreateTag().getCompound(TAG);
    }

    private static void put(ItemStack stack, CompoundTag tag) {
        stack.getOrCreateTag().put(TAG, tag);
    }

    public static int stored(ItemStack stack) {
        return Math.min(capacity(stack), tag(stack).getInt(STORED));
    }

    public static void setStored(ItemStack stack, int mB) {
        CompoundTag t = tag(stack);
        t.putInt(STORED, Math.max(0, Math.min(capacity(stack), mB)));
        put(stack, t);
    }

    /** Levels by {@link Buff#ordinal()} stamped into the item; zeros elsewhere. */
    public static int[] powers(ItemStack stack) {
        int[] out = new int[Buff.values().length];
        CompoundTag p = tag(stack).getCompound(POWERS);
        for (Buff b : Buff.values()) {
            out[b.ordinal()] = p.getInt(b.key);
        }
        return out;
    }

    public static int powerCount(ItemStack stack) {
        int n = 0;
        for (int l : powers(stack)) {
            n += l > 0 ? 1 : 0;
        }
        return n;
    }

    public static void setPower(ItemStack stack, Buff buff, int level) {
        CompoundTag t = tag(stack), p = t.getCompound(POWERS);
        if (level <= 0) {
            p.remove(buff.key);
        } else {
            p.putInt(buff.key, level);
        }
        t.put(POWERS, p);
        put(stack, t);
    }

    /** Copies the whole plasma tag from a block entity's record onto a stack, or the other way. */
    public static CompoundTag read(ItemStack stack) {
        return tag(stack).copy();
    }

    public static void write(ItemStack stack, CompoundTag plasma) {
        put(stack, plasma.copy());
    }

    public static void tooltip(ItemStack stack, List<Component> tooltip) {
        int cap = capacity(stack), stored = stored(stack);
        tooltip.add(Component.translatable("tooltip.bsp_core.plasma.stored", String.format("%,d", stored), String.format("%,d", cap)).withStyle(ChatFormatting.AQUA));
        int max = maxPowers(stack);
        if (max <= 0) {
            tooltip.add(Component.translatable("tooltip.bsp_core.plasma.no_powers").withStyle(ChatFormatting.GRAY));
            return;
        }
        int[] p = powers(stack);
        int n = 0;
        for (Buff b : allowed(stack)) {
            if (p[b.ordinal()] > 0) {
                tooltip.add(Component.translatable("tooltip.bsp_core.plasma.power", Component.translatable(b.translationKey()), p[b.ordinal()]).withStyle(ChatFormatting.GOLD));
                n++;
            }
        }
        tooltip.add(Component.translatable(n == 0 ? "tooltip.bsp_core.plasma.powers_empty" : "tooltip.bsp_core.plasma.powers", n, max).withStyle(ChatFormatting.GRAY));
    }
}
