package com.gregtechceu.gtceu.integration.xei;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

import org.jetbrains.annotations.Nullable;

public final class GTXEIHelper {

    private static final IngredientIO NONE = IngredientIO.valueOf("RENDER_ONLY");

    private GTXEIHelper() {}

    public static IngredientIO none() {
        return NONE;
    }

    public static boolean hasRecipeRole(@Nullable IngredientIO ingredientIO) {
        return ingredientIO != null && ingredientIO != NONE;
    }
}
