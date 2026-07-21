package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.integration.xei.GTLDLib2RecipeUI;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeIngredientHandler;

import net.minecraft.resources.ResourceLocation;

import com.google.common.base.Suppliers;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * LDLib2-backed EMI recipe display for GT recipes.
 */
public class GTLDLib2EmiRecipe extends ModularUIEMIRecipe {

    private final EmiRecipeCategory category;
    private final GTRecipeDefinition recipe;
    private final LDLib2RecipeUISize size;
    private final Supplier<EMIRecipeIngredientHandler> xeiIngredients;

    public GTLDLib2EmiRecipe(GTRecipeDefinition recipe, EmiRecipeCategory category, LDLib2RecipeUISize size) {
        super(GTLDLib2EmiRecipe::createModularUI);
        this.category = category;
        this.recipe = recipe;
        this.size = size;
        this.xeiIngredients = Suppliers.memoize(this::createIngredients);
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

    @Override
    public List<EmiIngredient> getInputs() {
        return xeiIngredients.get().inputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return xeiIngredients.get().catalysts;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return xeiIngredients.get().outputs;
    }

    private EMIRecipeIngredientHandler createIngredients() {
        int tier = RecipeHelper.getRecipeEUtTier(recipe);
        UIElement root = new UIElement();
        GTLDLib2RecipeUI.createXEIIngredientElements(recipe, tier, tier).forEach(root::addChild);

        EMIRecipeIngredientHandler ingredients = new EMIRecipeIngredientHandler();
        UIEvent event = UIEvent.create(EMIUIEvents.RECIPE_INGREDIENT);
        event.target = root;
        event.customData = ingredients;
        UIEventDispatcher.dispatchAllChildren(event);
        return ingredients;
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
