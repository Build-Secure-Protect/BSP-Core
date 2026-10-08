package com.mrgregles.bsp_core.compat.emi;

import com.mrgregles.bsp_core.compat.Process;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One machine job in EMI, laid out like the JEI category: inputs, then catalysts and the fluid, an arrow, the outputs, and the
 * lines of text (time, fuel, notes) underneath.
 */
public class ProcessEmiRecipe implements EmiRecipe {
    private static final int WIDTH = 150, HEIGHT = 62, OUT_X = 108;
    private final EmiRecipeCategory category;
    private final ResourceLocation id;
    private final Process job;
    private final List<EmiIngredient> inputs = new ArrayList<>(), catalysts = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    public ProcessEmiRecipe(EmiRecipeCategory category, ResourceLocation id, Process job) {
        this.category = category;
        this.id = id;
        this.job = job;
        for (List<ItemStack> options : job.inputs()) {
            inputs.add(EmiIngredient.of(options.stream().map(EmiStack::of).toList()));
        }
        for (List<ItemStack> options : job.catalysts()) {
            catalysts.add(EmiIngredient.of(options.stream().map(EmiStack::of).toList()));
        }
        if (job.fluid() != null) {
            inputs.add(EmiStack.of(job.fluid(), job.fluidMb() * 81L)); // EMI counts droplets: 81,000 to the bucket
        }
        for (ItemStack out : job.outputs()) {
            outputs.add(EmiStack.of(out));
        }
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return catalysts;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false; // the jobs are not crafting recipes; EMI's tree would mislead
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int x = 5;
        for (EmiIngredient in : inputs) {
            widgets.addSlot(in, x, 5);
            x += 18;
        }
        for (EmiIngredient cat : catalysts) {
            widgets.addSlot(cat, x, 5).catalyst(true);
            x += 18;
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, Math.max(x + 2, 82), 5);
        for (int i = 0; i < outputs.size(); i++) {
            var slot = widgets.addSlot(outputs.get(i), OUT_X + i * 18, 5).recipeContext(this);
            if (i < job.outputTips().size() && job.outputTips().get(i) != null) {
                slot.appendTooltip(job.outputTips().get(i));
            }
        }
        int y = 28;
        for (Component line : job.lines()) {
            if (y > HEIGHT - 9) {
                break;
            }
            widgets.addText(line, 5, y, 0xFF8B8B8B, false);
            y += 10;
        }
    }
}
