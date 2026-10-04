package com.mrgregles.bsp_core.compat.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One machine job as JEI shows it. The machines' jobs are code, not recipe files, so the plugin
 * builds these from the same config values the machines use.
 *
 * @param inputs    one list of alternatives per input slot
 * @param catalysts items that must be fitted but are not used up, or wear slowly (filters, upgrades)
 * @param fluid     fluid drawn from the tank per job, or null
 * @param outputs   one stack per output slot
 * @param outputTips an extra tooltip line per output (same order), entries may be null
 * @param lines     text under the slots: time, energy, notes
 */
public record Process(List<List<ItemStack>> inputs, List<List<ItemStack>> catalysts, @Nullable Fluid fluid, int fluidMb,
                      List<ItemStack> outputs, List<Component> outputTips, List<Component> lines) {
}
