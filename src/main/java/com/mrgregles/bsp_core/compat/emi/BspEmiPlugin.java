package com.mrgregles.bsp_core.compat.emi;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.BspProcesses;
import com.mrgregles.bsp_core.compat.Process;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EMI support, the same content as the JEI plugin: one category per BSP-Core machine with its jobs from the live config, coin
 * pressing, crushing by hand, and an information page for every block without a crafting recipe. Only loaded when EMI is installed.
 * The Shift-hover Assembly Guide of the JEI plugin has no EMI equivalent; the guide stays on the controller block (sneak + right-click).
 */
@EmiEntrypoint
public class BspEmiPlugin implements EmiPlugin {
    private static final Map<String, EmiRecipeCategory> CATEGORIES = new HashMap<>();

    public static EmiRecipeCategory category(String machine) {
        return CATEGORIES.computeIfAbsent(machine, m -> new EmiRecipeCategory(new ResourceLocation(BSPCore.MODID, m), EmiStack.of(BspProcesses.icon(m))));
    }

    @Override
    public void register(EmiRegistry registry) {
        for (String machine : BspProcesses.MACHINES) {
            EmiRecipeCategory category = category(machine);
            registry.addCategory(category);
            registry.addWorkstation(category, EmiStack.of(BspProcesses.icon(machine)));
        }
        BspProcesses.all().forEach((machine, jobs) -> {
            int i = 0;
            for (Process job : jobs) {
                registry.addRecipe(new ProcessEmiRecipe(category(machine), new ResourceLocation(BSPCore.MODID, "/" + machine + "/" + i++), job));
            }
        });
        for (Map.Entry<String, List<String>> page : BspProcesses.INFO) {
            List<EmiIngredient> stacks = new ArrayList<>();
            for (String name : page.getValue()) {
                ItemStack s = stack(name);
                if (!s.isEmpty()) {
                    stacks.add(EmiStack.of(s));
                }
            }
            if (!stacks.isEmpty()) {
                registry.addRecipe(new EmiInfoRecipe(stacks, List.of(Component.translatable(page.getKey())), new ResourceLocation(BSPCore.MODID, "/info/" + page.getKey().substring(page.getKey().lastIndexOf('.') + 1))));
            }
        }
    }

    private static ItemStack stack(String name) {
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, name));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
