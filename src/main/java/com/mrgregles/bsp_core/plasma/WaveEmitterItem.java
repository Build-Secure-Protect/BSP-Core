package com.mrgregles.bsp_core.plasma;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The Wave Emitter ("Slab" tricorder): held in the offhand with a Power Cell clicked into it, it
 * gives its carrier the cell's Carried powers as the totem would, drawing {@code plasma.emitterDraw}
 * mB/t while switched on. Right-click toggles it; a cell in the other hand goes in; sneak + right-click
 * takes the cell out.
 */
public class WaveEmitterItem extends Item {
    public static final String CELL = "Cell", ON = "On";
    private static final UUID DAMAGE_ID = UUID.fromString("7a1e9c2d-3b4f-4c5a-8d6e-1f2a3b4c5d01"), SWIFT_ID = UUID.fromString("7a1e9c2d-3b4f-4c5a-8d6e-1f2a3b4c5d02"),
            VITALITY_ID = UUID.fromString("7a1e9c2d-3b4f-4c5a-8d6e-1f2a3b4c5d03");

    public WaveEmitterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack cell(ItemStack emitter) {
        CompoundTag tag = emitter.getTag();
        return tag == null || !tag.contains(CELL) ? ItemStack.EMPTY : ItemStack.of(tag.getCompound(CELL));
    }

    public static void setCell(ItemStack emitter, ItemStack cell) {
        if (cell.isEmpty()) {
            emitter.getOrCreateTag().remove(CELL);
        } else {
            emitter.getOrCreateTag().put(CELL, cell.save(new CompoundTag()));
        }
    }

    public static boolean isOn(ItemStack emitter) {
        return emitter.getTag() != null && emitter.getTag().getBoolean(ON);
    }

    public static void setOn(ItemStack emitter, boolean on) {
        emitter.getOrCreateTag().putBoolean(ON, on);
    }

    /** True while the emitter is on and its cell still has plasma. */
    public static boolean running(ItemStack emitter) {
        return isOn(emitter) && PlasmaItems.stored(cell(emitter)) > 0;
    }

    /** Level of {@code buff} the emitter gives its carrier right now. */
    public static int level(ItemStack emitter, Buff buff) {
        return running(emitter) ? PlasmaItems.powers(cell(emitter))[buff.ordinal()] : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack emitter = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(emitter, true);
        }
        // right-clicked at the air (no block within four blocks): the screen, where the cell goes in and the switch lives
        if (player.pick(4.0, 0f, false).getType() != net.minecraft.world.phys.HitResult.Type.BLOCK && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new WaveEmitterMenu(id, inv, hand), Component.translatable("item.bsp_core.wave_emitter")),
                    buf -> buf.writeEnum(hand));
            return InteractionResultHolder.sidedSuccess(emitter, false);
        }
        if (PlasmaItems.isCell(other) && cell(emitter).isEmpty()) {
            setCell(emitter, other.copy());
            other.shrink(1);
            player.displayClientMessage(Component.translatable("message.bsp_core.emitter.cell_in").withStyle(ChatFormatting.AQUA), true);
        } else if (player.isShiftKeyDown() && !cell(emitter).isEmpty()) {
            ItemStack out = cell(emitter);
            setCell(emitter, ItemStack.EMPTY);
            setOn(emitter, false);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            player.displayClientMessage(Component.translatable("message.bsp_core.emitter.cell_out").withStyle(ChatFormatting.AQUA), true);
        } else if (cell(emitter).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.bsp_core.emitter.no_cell").withStyle(ChatFormatting.RED), true);
        } else {
            setOn(emitter, !isOn(emitter));
            player.displayClientMessage(Component.translatable(isOn(emitter) ? "message.bsp_core.emitter.on" : "message.bsp_core.emitter.off").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(emitter, false);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || !(entity instanceof Player player) || !isOn(stack)) {
            return;
        }
        ItemStack cell = cell(stack);
        if (player.getOffhandItem() != stack || cell.isEmpty()) {
            return; // it only works, and only draws, in the offhand
        }
        int stored = PlasmaItems.stored(cell), draw = BSPConfig.getOr(BSPConfig.EMITTER_DRAW, 20) * 20;
        if (stored <= 0) {
            setOn(stack, false);
            player.displayClientMessage(Component.translatable("message.bsp_core.emitter.empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        PlasmaItems.setStored(cell, stored - Math.min(stored, draw));
        setCell(stack, cell);
        if (level(stack, Buff.NIGHT_SIGHT) > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.OFFHAND || !running(stack)) {
            return super.getAttributeModifiers(slot, stack);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> mods = ImmutableMultimap.builder();
        int damage = level(stack, Buff.DAMAGE), swift = level(stack, Buff.SWIFTNESS), vitality = level(stack, Buff.VITALITY);
        if (damage > 0) {
            mods.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, "Wave Emitter damage", BSPConfig.getOr(BSPConfig.DAMAGE_PER_LEVEL, 0.5) * damage, AttributeModifier.Operation.ADDITION));
        }
        if (swift > 0) {
            mods.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SWIFT_ID, "Wave Emitter swiftness", BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SWIFTNESS_BONUS, List.<Double>of()), swift, 0.0), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (vitality > 0) {
            mods.put(Attributes.MAX_HEALTH, new AttributeModifier(VITALITY_ID, "Wave Emitter vitality", BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.VITALITY_HEALTH, List.<Integer>of()), vitality, 0), AttributeModifier.Operation.ADDITION));
        }
        return mods.build();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return running(stack);
    }

    /** The cell's charge changes every second while running; that must not make the hand dip and re-raise the emitter each time. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemStack cell = cell(stack);
        if (cell.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.bsp_core.emitter.no_cell").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(isOn(stack) ? "tooltip.bsp_core.emitter.on" : "tooltip.bsp_core.emitter.off", BSPConfig.getOr(BSPConfig.EMITTER_DRAW, 20)).withStyle(isOn(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.bsp_core.emitter.cell", cell.getHoverName()).withStyle(ChatFormatting.GRAY));
            PlasmaItems.tooltip(cell, tooltip);
        }
        tooltip.add(Component.translatable("tooltip.bsp_core.emitter.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
