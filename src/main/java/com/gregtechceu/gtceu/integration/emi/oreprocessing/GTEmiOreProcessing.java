package com.gregtechceu.gtceu.integration.emi.oreprocessing;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.integration.xei.widgets.GTOreByProductWidget;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import org.jetbrains.annotations.Nullable;

public class GTEmiOreProcessing extends ModularUIEMIRecipe {

    final Material material;

    public GTEmiOreProcessing(Material material) {
        super(GTEmiOreProcessing::createModularUI);
        this.material = material;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return GTOreProcessingEmiCategory.CATEGORY;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return material.getResourceLocation().withPrefix("/");
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public int getDisplayWidth() {
        return GTOreByProductWidget.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return GTOreByProductWidget.HEIGHT;
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (recipe instanceof GTEmiOreProcessing oreProcessing) {
            return GTOreByProductWidget.createModularUI(oreProcessing.material);
        }
        GTCEu.LOGGER.error("Expected GTEmiOreProcessing, got {}", recipe.getClass().getName());
        throw new IllegalArgumentException("Expected GTEmiOreProcessing, got " + recipe.getClass().getName());
    }
}
