package com.mrgregles.bsp_core.compat.jei;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.BspProcesses;
import com.mrgregles.bsp_core.compat.Process;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JEI support. Shows what each BSP-Core machine does (their jobs are code, so the pages are built
 * here from the live config values), crushing by hand, coin pressing, and an information page for
 * every multiblock block. Only loaded when JEI is installed.
 */
@JeiPlugin
public class BspJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(BSPCore.MODID, "jei");
    public static final RecipeType<Process> TETRIUM_CRUCIBLE = RecipeType.create(BSPCore.MODID, "tetrium_crucible", Process.class);
    public static final RecipeType<Process> COMBINATION_FORGE = RecipeType.create(BSPCore.MODID, "combination_forge", Process.class);
    public static final RecipeType<Process> ILLYRIUM_CRUCIBLE = RecipeType.create(BSPCore.MODID, "illyrium_crucible", Process.class);
    public static final RecipeType<Process> MAGNETIC_CENTRIFUGE = RecipeType.create(BSPCore.MODID, "magnetic_centrifuge", Process.class);
    public static final RecipeType<Process> ILLYRIUM_REFINERY = RecipeType.create(BSPCore.MODID, "illyrium_refinery", Process.class);
    public static final RecipeType<Process> COIN_PRESSING = RecipeType.create(BSPCore.MODID, "coin_pressing", Process.class);
    public static final RecipeType<Process> HAND_CRUSHING = RecipeType.create(BSPCore.MODID, "hand_crushing", Process.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /** Once JEI is running, holding Shift over a multiblock block in JEI opens its Assembly Guide. */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        JeiGuideHover.attach(runtime);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        var gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(
                new ProcessCategory(gui, TETRIUM_CRUCIBLE, "block.bsp_core.tetrium_crucible", new ItemStack(ModItems.TETRIUM_CRUCIBLE.get())),
                new ProcessCategory(gui, COMBINATION_FORGE, "block.bsp_core.combination_forge", new ItemStack(ModItems.COMBINATION_FORGE.get())),
                new ProcessCategory(gui, ILLYRIUM_CRUCIBLE, "jei.bsp_core.illyrium_crucible", stack("illyrium_crucible")),
                new ProcessCategory(gui, ILLYRIUM_REFINERY, "jei.bsp_core.illyrium_refinery", stack("illyrium_refinery")),
                new ProcessCategory(gui, MAGNETIC_CENTRIFUGE, "jei.bsp_core.magnetic_centrifuge", stack("magnetic_centrifuge")),
                new ProcessCategory(gui, COIN_PRESSING, "jei.bsp_core.coin_pressing", new ItemStack(ModItems.COIN_FACTORY.get())),
                new ProcessCategory(gui, HAND_CRUSHING, "jei.bsp_core.hand_crushing", new ItemStack(Items.IRON_PICKAXE)));
    }

    // ------------------------------------------------------------------ recipes (from BspProcesses, shared with EMI)

    private static RecipeType<Process> type(String machine) {
        return switch (machine) {
            case "tetrium_crucible" -> TETRIUM_CRUCIBLE;
            case "combination_forge" -> COMBINATION_FORGE;
            case "illyrium_crucible" -> ILLYRIUM_CRUCIBLE;
            case "illyrium_refinery" -> ILLYRIUM_REFINERY;
            case "magnetic_centrifuge" -> MAGNETIC_CENTRIFUGE;
            case "coin_pressing" -> COIN_PRESSING;
            default -> HAND_CRUSHING;
        };
    }

    private static ItemStack stack(String name) {
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, name));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        BspProcesses.all().forEach((machine, jobs) -> reg.addRecipes(type(machine), jobs));
        // information pages: how each multiblock goes together, and the items with no recipe
        for (Map.Entry<String, List<String>> page : BspProcesses.INFO) {
            List<ItemStack> stacks = new ArrayList<>();
            for (String name : page.getValue()) {
                ItemStack s = stack(name);
                if (!s.isEmpty()) {
                    stacks.add(s);
                }
            }
            if (!stacks.isEmpty()) {
                reg.addIngredientInfo(stacks, VanillaTypes.ITEM_STACK, Component.translatable(page.getKey()));
            }
        }
    }

    /** Clicking the progress dial on a machine screen shows that machine's jobs. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration reg) {
        reg.addGuiContainerHandler(com.mrgregles.bsp_core.client.MachineScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
            @Override
            public java.util.Collection<mezz.jei.api.gui.handlers.IGuiClickableArea> getGuiClickableAreas(com.mrgregles.bsp_core.client.MachineScreen screen, double mouseX, double mouseY) {
                var machine = screen.getMenu().getMachine();
                RecipeType<Process> type = machine instanceof com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity ? TETRIUM_CRUCIBLE
                        : machine instanceof com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity ? COMBINATION_FORGE
                        : machine instanceof com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity ? ILLYRIUM_CRUCIBLE
                        : machine instanceof com.mrgregles.bsp_core.machine.IllyriumRefineryBlockEntity ? ILLYRIUM_REFINERY
                        : machine instanceof com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity ? MAGNETIC_CENTRIFUGE : null;
                return type == null ? List.of() : List.of(mezz.jei.api.gui.handlers.IGuiClickableArea.createBasic(90, 24, 31, 31, type));
            }
        });
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(ModItems.TETRIUM_CRUCIBLE.get()), TETRIUM_CRUCIBLE);
        reg.addRecipeCatalyst(new ItemStack(ModItems.COMBINATION_FORGE.get()), COMBINATION_FORGE);
        reg.addRecipeCatalyst(stack("illyrium_crucible"), ILLYRIUM_CRUCIBLE);
        reg.addRecipeCatalyst(stack("illyrium_refinery"), ILLYRIUM_REFINERY);
        reg.addRecipeCatalyst(stack("magnetic_centrifuge"), MAGNETIC_CENTRIFUGE);
        reg.addRecipeCatalyst(new ItemStack(ModItems.COIN_FACTORY.get()), COIN_PRESSING);
        reg.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE), HAND_CRUSHING);
    }
}
