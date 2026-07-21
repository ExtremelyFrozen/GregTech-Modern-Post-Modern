package com.gregtechceu.gtceu.integration.emi.orevein;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.integration.xei.widgets.GTBedrockOreWidget;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import org.jetbrains.annotations.Nullable;

public class GTBedrockOre extends ModularUIEMIRecipe {

    private final Holder<BedrockOreDefinition> bedrockOre;

    public GTBedrockOre(Holder<BedrockOreDefinition> bedrockOre) {
        super(GTBedrockOre::createModularUI);
        this.bedrockOre = bedrockOre;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return GTBedrockOreEmiCategory.CATEGORY;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return bedrockOre.getKey()
                .location()
                .withPrefix("/bedrock_ore_diagram/");
    }

    @Override
    public int getDisplayWidth() {
        return GTBedrockOreWidget.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return GTBedrockOreWidget.HEIGHT;
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (recipe instanceof GTBedrockOre bedrockOre) {
            return GTBedrockOreWidget.createModularUI(bedrockOre.bedrockOre, GTBedrockOreWidget.HEIGHT);
        }
        GTCEu.LOGGER.error("Expected GTBedrockOre, got {}", recipe.getClass().getName());
        throw new IllegalArgumentException("Expected GTBedrockOre, got " + recipe.getClass().getName());
    }
}
