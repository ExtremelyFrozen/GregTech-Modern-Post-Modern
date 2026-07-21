package com.gregtechceu.gtceu.integration.xei;

import org.jetbrains.annotations.Nullable;

public final class GTXEIHelper {

    private GTXEIHelper() {}

    public static GTXEIIngredientRole input() {
        return GTXEIIngredientRole.INPUT;
    }

    public static GTXEIIngredientRole output() {
        return GTXEIIngredientRole.OUTPUT;
    }

    public static GTXEIIngredientRole catalyst() {
        return GTXEIIngredientRole.CATALYST;
    }

    public static GTXEIIngredientRole none() {
        return GTXEIIngredientRole.NONE;
    }

    public static boolean hasRecipeRole(@Nullable GTXEIIngredientRole role) {
        return role != null && role != GTXEIIngredientRole.NONE;
    }

    public static boolean isInput(@Nullable GTXEIIngredientRole role) {
        return role == GTXEIIngredientRole.INPUT;
    }

    public static boolean isOutput(@Nullable GTXEIIngredientRole role) {
        return role == GTXEIIngredientRole.OUTPUT;
    }

    public static boolean isCatalyst(@Nullable GTXEIIngredientRole role) {
        return role == GTXEIIngredientRole.CATALYST;
    }
}
