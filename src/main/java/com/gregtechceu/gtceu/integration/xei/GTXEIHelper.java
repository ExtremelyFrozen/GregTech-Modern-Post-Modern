package com.gregtechceu.gtceu.integration.xei;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

import org.jetbrains.annotations.Nullable;

public final class GTXEIHelper {

    private static final IngredientIO INPUT = IngredientIO.INPUT;
    private static final IngredientIO OUTPUT = IngredientIO.OUTPUT;
    private static final IngredientIO CATALYST = IngredientIO.CATALYST;
    private static final IngredientIO NONE = IngredientIO.valueOf("RENDER_ONLY");

    private GTXEIHelper() {}

    public static IngredientIO input() {
        return INPUT;
    }

    public static IngredientIO output() {
        return OUTPUT;
    }

    public static IngredientIO catalyst() {
        return CATALYST;
    }

    public static IngredientIO none() {
        return NONE;
    }

    public static boolean hasRecipeRole(@Nullable IngredientIO ingredientIO) {
        return ingredientIO != null && ingredientIO != NONE;
    }

    public static boolean isInput(@Nullable IngredientIO ingredientIO) {
        return ingredientIO == INPUT;
    }

    public static boolean isOutput(@Nullable IngredientIO ingredientIO) {
        return ingredientIO == OUTPUT;
    }

    public static boolean isCatalyst(@Nullable IngredientIO ingredientIO) {
        return ingredientIO == CATALYST;
    }
}
