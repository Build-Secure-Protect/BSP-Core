package com.mrgregles.bsp_core.compat.jei;

import com.mrgregles.bsp_core.compat.Process;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One JEI page layout for every BSP-Core machine: inputs, tank fluid, fitted parts, arrow, outputs, then notes. */
public class ProcessCategory implements IRecipeCategory<Process> {
    private static final int WIDTH = 150, HEIGHT = 62, OUT_X = 108;

    private final RecipeType<Process> type;
    private final Component title;
    private final IDrawable background, icon, slot;

    public ProcessCategory(IGuiHelper gui, RecipeType<Process> type, String titleKey, ItemStack icon) {
        this.type = type;
        this.title = Component.translatable(titleKey);
        this.background = gui.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = gui.createDrawableItemStack(icon);
        this.slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<Process> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Process recipe, IFocusGroup focuses) {
        int x = 1;
        for (var options : recipe.inputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 5).setBackground(slot, -1, -1).addItemStacks(options);
            x += 18;
        }
        if (recipe.fluid() != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 5).setBackground(slot, -1, -1)
                    .setFluidRenderer(Math.max(1, recipe.fluidMb()), false, 16, 16).addFluidStack(recipe.fluid(), recipe.fluidMb());
            x += 18;
        }
        x += 4;
        for (var options : recipe.catalysts()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, x, 5).setBackground(slot, -1, -1).addItemStacks(options);
            x += 18;
        }
        for (int i = 0; i < recipe.outputs().size(); i++) {
            Component tip = i < recipe.outputTips().size() ? recipe.outputTips().get(i) : null;
            var out = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + i * 18, 5).setBackground(slot, -1, -1).addItemStack(recipe.outputs().get(i));
            if (tip != null) {
                out.addTooltipCallback((view, tooltip) -> tooltip.add(tip));
            }
        }
    }

    @Override
    public void draw(Process recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, ">>", OUT_X - 16, 9, 0xFF19A88C, false);
        for (int i = 0; i < recipe.lines().size(); i++) {
            g.drawString(font, recipe.lines().get(i), 1, 28 + i * 11, 0xFF505050, false);
        }
    }
}
