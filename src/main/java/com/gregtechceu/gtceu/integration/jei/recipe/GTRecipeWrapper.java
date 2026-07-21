package com.gregtechceu.gtceu.integration.jei.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;

public class GTRecipeWrapper extends ModularWrapper<Widget> {

    public final GTRecipeDefinition recipe;

    public GTRecipeWrapper(GTRecipeDefinition recipe) {
        super(new GTRecipeWidget(recipe));
        this.recipe = recipe;
    }
}
