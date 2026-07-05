package com.gregtechceu.gtceu.integration.jei.circuit;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.integration.xei.widgets.GTProgrammedCircuitWidget;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;

import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import org.jetbrains.annotations.NotNull;

public class GTProgrammedCircuitCategory extends ModularUIRecipeCategory<GTProgrammedCircuitWidget> {

    public final static RecipeType<GTProgrammedCircuitWidget> RECIPE_TYPE = new RecipeType<>(
            GTCEu.id("programmed_circuit"), GTProgrammedCircuitWidget.class);
    private final int width;
    private final int height;
    private final IDrawable icon;

    public GTProgrammedCircuitCategory(IJeiHelpers helpers) {
        super(GTProgrammedCircuitWidget::createModularUI);
        width = GTProgrammedCircuitWidget.WIDTH;
        height = GTProgrammedCircuitWidget.HEIGHT;
        icon = helpers.getGuiHelper().createDrawableItemStack(GTItems.PROGRAMMED_CIRCUIT.asStack());
    }

    @Override
    public @NotNull RecipeType<GTProgrammedCircuitWidget> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("gtpm.jei.programmed_circuit");
    }
}
