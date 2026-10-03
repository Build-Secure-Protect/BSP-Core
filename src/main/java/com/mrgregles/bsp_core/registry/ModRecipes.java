package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.material.PickaxeCrushingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BSPCore.MODID);

    public static final RegistryObject<RecipeSerializer<PickaxeCrushingRecipe>> PICKAXE_CRUSHING =
            SERIALIZERS.register("pickaxe_crushing", () -> new SimpleCraftingRecipeSerializer<>(PickaxeCrushingRecipe::new));

    private ModRecipes() {}
}
