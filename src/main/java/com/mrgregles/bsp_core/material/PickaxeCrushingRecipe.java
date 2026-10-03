package com.mrgregles.bsp_core.material;

import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Crushing by hand: any pickaxe plus one Tetrium Ingot or Dirty Illyrium Ingot in a crafting grid.
 * The pickaxe stays in the grid and loses one point of durability, like mining a block. The result
 * is a crushed ingot, which resolves by chance into dust or nuggets (see {@link CrushedIngotItem}).
 * A fallback for packs with no crushing machine.
 */
public class PickaxeCrushingRecipe extends CustomRecipe {
    public PickaxeCrushingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** The crushed item for this grid, or EMPTY if it is not exactly one pickaxe and one crushable ingot. */
    private static ItemStack resultFor(CraftingContainer grid) {
        ItemStack ingot = ItemStack.EMPTY;
        boolean pickaxe = false;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof PickaxeItem) {
                if (pickaxe) {
                    return ItemStack.EMPTY;
                }
                pickaxe = true;
            } else if (ingot.isEmpty() && (s.is(ModItems.TETRIUM_INGOT.get()) || s.is(ModItems.DIRTY_ILLYRIUM_INGOT.get()))) {
                ingot = s;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (!pickaxe || ingot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ingot.is(ModItems.TETRIUM_INGOT.get()) ? ModItems.CRUSHED_TETRIUM.get() : ModItems.CRUSHED_DIRTY_ILLYRIUM.get());
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return !resultFor(grid).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        return resultFor(grid);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> left = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < left.size(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.getItem() instanceof PickaxeItem) {
                ItemStack kept = s.copy();
                kept.setDamageValue(kept.getDamageValue() + 1);
                left.set(i, kept.getDamageValue() >= kept.getMaxDamage() ? ItemStack.EMPTY : kept); // breaks when worn out
            }
        }
        return left;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.PICKAXE_CRUSHING.get();
    }
}
