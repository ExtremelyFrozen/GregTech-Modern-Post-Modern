package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.integration.xei.GTLDLib2RecipeUI;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import org.jetbrains.annotations.Nullable;

/**
 * LDLib2-backed EMI recipe display for GT recipes.
 */
public class GTLDLib2EmiRecipe extends ModularUIEMIRecipe {

    private final EmiRecipeCategory category;
    private final GTRecipeDefinition recipe;
    private final LDLib2RecipeUISize size;

    public GTLDLib2EmiRecipe(GTRecipeDefinition recipe, EmiRecipeCategory category) {
        super(GTLDLib2EmiRecipe::createModularUI);
        this.category = category;
        this.recipe = recipe;
        this.size = recipe.recipeType.getRecipeUI().getLDLib2XEIRecipeUISize();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public int getDisplayWidth() {
        return size.width();
    }

    @Override
    public int getDisplayHeight() {
        return size.height();
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (recipe instanceof GTLDLib2EmiRecipe gtRecipe) {
            int tier = RecipeHelper.getRecipeEUtTier(gtRecipe.recipe);
            return GTLDLib2RecipeUI.createModularUI(gtRecipe.recipe, tier, tier);
        }
        GTCEu.LOGGER.error("Expected GTLDLib2EmiRecipe, got {}", recipe.getClass().getName());
        throw new IllegalArgumentException("Expected GTLDLib2EmiRecipe, got " + recipe.getClass().getName());
    }
}
