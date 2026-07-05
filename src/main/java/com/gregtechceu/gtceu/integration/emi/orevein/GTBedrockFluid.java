package com.gregtechceu.gtceu.integration.emi.orevein;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.integration.xei.widgets.GTBedrockFluidWidget;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class GTBedrockFluid extends ModularUIEMIRecipe {

    private final Holder<BedrockFluidDefinition> fluid;

    public GTBedrockFluid(Holder<BedrockFluidDefinition> fluid) {
        super(GTBedrockFluid::createModularUI);
        this.fluid = fluid;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return GTBedrockFluidEmiCategory.CATEGORY;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return Objects.requireNonNull(fluid.getKey(), "Bedrock fluid holder is missing a key")
                .location()
                .withPrefix("/bedrock_fluid_diagram/");
    }

    @Override
    public int getDisplayWidth() {
        return GTBedrockFluidWidget.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return GTBedrockFluidWidget.HEIGHT;
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (recipe instanceof GTBedrockFluid bedrockFluid) {
            return GTBedrockFluidWidget.createModularUI(bedrockFluid.fluid, GTBedrockFluidWidget.HEIGHT);
        }
        GTCEu.LOGGER.error("Expected GTBedrockFluid, got {}", recipe.getClass().getName());
        throw new IllegalArgumentException("Expected GTBedrockFluid, got " + recipe.getClass().getName());
    }
}
