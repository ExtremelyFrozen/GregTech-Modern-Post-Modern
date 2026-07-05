package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockInfoEmiRecipe extends ModularUIEMIRecipe {

    private final MultiblockMachineDefinition definition;

    public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
        super(MultiblockInfoEmiRecipe::createModularUI);
        this.definition = definition;
    }

    @Override
    public void addWidgets(@NotNull WidgetHolder widgets) {
        super.addWidgets(widgets);
        // numbers gotten from the size of the widget
        SlotWidget slotWidget = new SlotWidget(EmiStack.of(definition.getItem().asItem()), 138, 12)
                .recipeContext(this)
                .drawBack(false);

        widgets.add(slotWidget);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return MultiblockInfoEmiCategory.CATEGORY;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return definition.getId().withPrefix("/");
    }

    @Override
    public @NotNull List<EmiStack> getOutputs() {
        return List.of(EmiStack.of(definition.getItem()));
    }

    @Override
    public int getDisplayWidth() {
        return 160;
    }

    @Override
    public int getDisplayHeight() {
        return 160;
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (recipe instanceof MultiblockInfoEmiRecipe multiblockRecipe) {
            return PatternPreviewWidget.createModularUI(multiblockRecipe.definition);
        }
        GTCEu.LOGGER.error("Expected MultiblockInfoEmiRecipe, got {}", recipe.getClass().getName());
        throw new IllegalArgumentException("Expected MultiblockInfoEmiRecipe, got " + recipe.getClass().getName());
    }
}
