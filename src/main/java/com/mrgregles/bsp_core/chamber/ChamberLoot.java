package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a Mob Chamber's kills drop goes into the tank's 27-slot loot store instead of the floor (a full store loses the rest), and a
 * wither skeleton's bones come out as Wither Bones. Each kill also turns {@link #TAG_MB} of plain plasma into the pool of every power its
 * drops give ({@link MobPowers}) at level I: a token, the real tagging is done by feeding items ({@link ChamberFeeder}).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChamberLoot {
    /** Plain plasma a kill tags with each power its drops give (the owner's 10 mB). */
    public static final long TAG_MB = 10;

    private ChamberLoot() {}

    /** The chamber master a mob belongs to, if that tank still stands as a chamber. */
    @Nullable
    public static TankPartBlockEntity chamberOf(LivingEntity mob) {
        if (!ChamberSpawner.isChamberMob(mob)) {
            return null;
        }
        BlockPos at = BlockPos.of(mob.getPersistentData().getLong(ChamberSpawner.TAG));
        return mob.level().getBlockEntity(at) instanceof TankPartBlockEntity m && m.isMaster() && m.chamber() ? m : null;
    }

    /** A drop as the chamber keeps it: a wither skeleton's bones are Wither Bones. */
    public static ItemStack convert(LivingEntity mob, ItemStack stack) {
        if (mob instanceof WitherSkeleton && stack.is(Items.BONE)) {
            return new ItemStack(ModItems.WITHER_BONE.get(), stack.getCount());
        }
        return stack;
    }

    /** A chamber kill counts as a player's for drops, never for XP. */
    @SubscribeEvent
    public static void onXp(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (!event.getEntity().level().isClientSide() && ChamberSpawner.isChamberMob(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide()) {
            return;
        }
        TankPartBlockEntity m = chamberOf(mob);
        if (m == null) {
            return;
        }
        Set<Buff> powers = EnumSet.noneOf(Buff.class);
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = convert(mob, drop.getItem());
            MobPowers.Entry entry = MobPowers.of(stack);
            if (entry != null) {
                powers.add(entry.power());
            }
            m.storeLoot(stack.copy()); // what does not fit is lost
        }
        event.getDrops().clear();
        for (Buff b : powers) {
            m.tagFromPlain(b, 1, TAG_MB); // level I, beside whatever was fed at higher levels
        }
    }
}
