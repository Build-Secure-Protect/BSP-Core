package com.mrgregles.bsp_core.totem;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The carried form of the Shatter Totem.
 *
 * <ul>
 *   <li>One per stack, cannot burn, cannot be damaged, cannot enter shulker boxes or bundles.</li>
 *   <li>Right-click with it in the main hand to open the upgrade tree. Sneak + right-click a block to place it.</li>
 *   <li>Held in the offhand it grants its upgraded buffs: extra attack damage, Resistance, faster mining.</li>
 * </ul>
 */
public class ShatterTotemItem extends BlockItem {
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("5c3c2b86-5a4e-4a3b-9d6f-0b0b2c4e1f10");
    private static final UUID SWIFTNESS_MODIFIER_ID = UUID.fromString("5c3c2b86-5a4e-4a3b-9d6f-0b0b2c4e1f11");
    private static final UUID VITALITY_MODIFIER_ID = UUID.fromString("5c3c2b86-5a4e-4a3b-9d6f-0b0b2c4e1f12");

    public ShatterTotemItem(Block block) {
        super(block, new Properties()
                .stacksTo(1)
                .fireResistant()
                .rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(DamageSource source) {
        return false;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    // --- dropped form uses our own entity ---

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Nullable
    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        if (location instanceof ShatterTotemItemEntity) {
            return null; // already ours
        }
        if (location instanceof ItemEntity vanilla) {
            return ShatterTotemItemEntity.from(level, vanilla, stack);
        }
        return new ShatterTotemItemEntity(level, location.getX(), location.getY(), location.getZ(), stack);
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    // --- use: upgrade tree on plain right-click, placement on sneak + right-click ---

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            openUpgradeTree(player, hand);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && ctx.getHand() == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()) {
            openUpgradeTree(player, ctx.getHand());
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        return super.useOn(ctx);
    }

    private static void openUpgradeTree(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new TotemUpgradeMenu(id, inv, hand), Component.translatable("gui.bsp_core.upgrades.title")),
                    buf -> buf.writeEnum(hand));
        }
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!BSPConfig.isDimensionAllowed(ctx.getLevel().dimension().location())) {
            Player player = ctx.getPlayer();
            if (player != null && !ctx.getLevel().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.bsp_core.shatter_totem.dimension_blocked").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(ctx);
    }

    // --- offhand buffs ---

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.OFFHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> mods = ImmutableMultimap.builder();
        int level = TotemUpgrades.getLevel(stack, TotemUpgrades.Buff.DAMAGE);
        if (level > 0) {
            mods.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(DAMAGE_MODIFIER_ID, "Shatter Totem damage", BSPConfig.getOr(BSPConfig.DAMAGE_PER_LEVEL, 0.5) * level, AttributeModifier.Operation.ADDITION));
        }
        int swift = TotemUpgrades.getLevel(stack, TotemUpgrades.Buff.SWIFTNESS);
        if (swift > 0) {
            double bonus = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SWIFTNESS_BONUS, List.<Double>of()), swift, 0.0);
            mods.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SWIFTNESS_MODIFIER_ID, "Shatter Totem swiftness", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        int vitality = TotemUpgrades.getLevel(stack, TotemUpgrades.Buff.VITALITY);
        if (vitality > 0) {
            int health = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.VITALITY_HEALTH, List.<Integer>of()), vitality, 0);
            mods.put(Attributes.MAX_HEALTH, new AttributeModifier(VITALITY_MODIFIER_ID, "Shatter Totem vitality", health, AttributeModifier.Operation.ADDITION));
        }
        return mods.build();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || !(entity instanceof Player player)) {
            return;
        }
        TotemUpgrades.migrate(stack); // a totem upgraded under the old flat system starts again at Tier I
        if (player.getOffhandItem() != stack) {
            return;
        }
        if (TotemUpgrades.getLevel(stack, TotemUpgrades.Buff.NIGHT_SIGHT) > 0) {
            // long enough that the vanilla "running out" flicker (under 10 seconds) never shows while held
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
        }
    }

    /** Mining speed multiplier granted to {@code player} by whatever totem is in their offhand. */
    public static float miningSpeedMultiplier(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (!TotemInventories.isTotem(offhand)) {
            return 1.0F;
        }
        return 1.0F + (float) (BSPConfig.getOr(BSPConfig.MINING_SPEED_PER_LEVEL, 0.10) * TotemUpgrades.getLevel(offhand, TotemUpgrades.Buff.MINING_SPEED));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(TotemOwner.fromStack(stack)
                .map(owner -> Component.translatable("tooltip.bsp_core.shatter_totem.owner", owner.name())
                        .withStyle(ChatFormatting.GOLD))
                .orElse(Component.translatable("tooltip.bsp_core.shatter_totem.unowned")
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.bsp_core.shatter_totem.tier", TotemUpgrades.roman(TotemUpgrades.getTier(stack))).withStyle(ChatFormatting.AQUA));
        for (TotemUpgrades.Buff buff : TotemUpgrades.Buff.values()) {
            int lvl = TotemUpgrades.getLevel(stack, buff);
            if (lvl > 0) {
                tooltip.add(Component.translatable("tooltip.bsp_core.shatter_totem.buff",
                        Component.translatable(buff.translationKey()), lvl).withStyle(ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable("tooltip.bsp_core.shatter_totem.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
